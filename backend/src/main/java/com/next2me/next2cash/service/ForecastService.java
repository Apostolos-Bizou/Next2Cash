package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.ForecastEntryDTO;
import com.next2me.next2cash.dto.ForecastResponse;
import com.next2me.next2cash.dto.ScenarioComparisonResponse;
import com.next2me.next2cash.dto.ScenarioForecastDTO;
import com.next2me.next2cash.model.Project;
import com.next2me.next2cash.model.ProjectStatus;
import com.next2me.next2cash.model.RecurrencePattern;
import com.next2me.next2cash.model.Scenario;
import com.next2me.next2cash.model.ScenarioStatus;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.ProjectRepository;
import com.next2me.next2cash.repository.RecurrencePatternRepository;
import com.next2me.next2cash.repository.ScenarioRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ForecastService -- orchestrates virtual cash flow forecasts.
 *
 * Created in S85 (Forecast Engine, May 2026).
 *
 * Phase A (S85 Step A):
 *   - Loads PLANNED + isRecurring=true mother transactions for an entity
 *   - For each mother, fetches its RecurrencePattern
 *   - Calls RecurrenceEngineService.materializeForecastInstances() to expand
 *     into virtual instances over the requested horizon
 *   - Enriches each instance with denormalized project info
 *   - Aggregates totals
 *
 * Phase B (S85 Step B):
 *   - Adds virtual INCOME entries from Project.expectedMonthlyRevenue
 *   - Only LIVE projects participate (status == "LIVE")
 *
 * Scenario adjustments (S98):
 *   - generateForecast now accepts an optional scenarioId. When given (and the
 *     scenario is not Baseline / 0-0), each forecast amount is scaled by the
 *     scenario's revenue/expense adjust %: income *= (1 + revAdj/100),
 *     expense *= (1 + expAdj/100). Adjustments apply ONLY to future (PLANNED +
 *     project-revenue) flows -- ACTUAL transactions never reach this service,
 *     so they are inherently unaffected. scenarioId == null or Baseline yields
 *     identical output to the pre-S98 behaviour (factor 1.0).
 *   - compareScenarios runs the forecast once per active scenario of the entity
 *     and returns all totals side-by-side plus per-month cumulative net curves.
 *
 * The orchestrator is read-only -- it never writes to the database.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

    private static final int DAYS_PER_MONTH_UPPER = 31;

    static final int MAX_HORIZON_MONTHS = 120;
    static final int DEFAULT_HORIZON_MONTHS = 24;

    private static final String INCOME_TYPE = "income";
    private static final String INCOME_CATEGORY = "Project Revenue";
    private static final String INCOME_FREQUENCY = "MONTHLY";
    private static final String INCOME_DESCRIPTION_PREFIX = "Expected revenue: ";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TransactionRepository transactionRepository;
    private final RecurrencePatternRepository recurrencePatternRepository;
    private final ProjectRepository projectRepository;
    private final RecurrenceEngineService recurrenceEngineService;
    private final ScenarioRepository scenarioRepository;

    /**
     * Backwards-compatible entry point (pre-S98 signature). Equivalent to the
     * Baseline scenario: no revenue/expense adjustments applied.
     */
    public ForecastResponse generateForecast(UUID entityId, int horizonMonths) {
        return generateForecast(entityId, horizonMonths, null);
    }

    /**
     * Generate a forecast for the given entity over the next N months under an
     * optional scenario.
     *
     * @param entityId       owner entity UUID (required)
     * @param horizonMonths  number of months to project (1..120; clamped)
     * @param scenarioId     optional scenario UUID; null = Baseline (no adjust)
     * @return forecast response with flat list of virtual entries + totals
     */
    public ForecastResponse generateForecast(UUID entityId, int horizonMonths, UUID scenarioId) {
        Objects.requireNonNull(entityId, "entityId must not be null");

        int months = clampHorizon(horizonMonths);
        int horizonDays = months * DAYS_PER_MONTH_UPPER;
        LocalDate asOf = LocalDate.now();
        LocalDate horizonEnd = asOf.plusMonths(months);

        // Resolve scenario adjustment factors. Baseline / null / unknown -> 1.0.
        BigDecimal revenueFactor = BigDecimal.ONE;
        BigDecimal expenseFactor = BigDecimal.ONE;
        if (scenarioId != null) {
            Scenario scenario = scenarioRepository.findById(scenarioId).orElse(null);
            if (scenario != null && scenario.getOwnerEntityId() != null
                    && scenario.getOwnerEntityId().equals(entityId)) {
                revenueFactor = factorFromPct(scenario.getRevenueAdjustPct());
                expenseFactor = factorFromPct(scenario.getExpenseAdjustPct());
            } else if (scenario == null) {
                log.warn("Forecast requested with unknown scenarioId={}; using Baseline factors", scenarioId);
            } else {
                log.warn("Scenario {} does not belong to entity {}; using Baseline factors",
                        scenarioId, entityId);
            }
        }

        log.info("Forecast requested: entityId={}, horizonMonths={}, asOf={}, scenarioId={}, revFactor={}, expFactor={}",
                entityId, months, asOf, scenarioId, revenueFactor, expenseFactor);

        List<Transaction> allEntityTxns = transactionRepository.findAll().stream()
                .filter(t -> entityId.equals(t.getEntityId()))
                .toList();

        List<Transaction> mothers = allEntityTxns.stream()
                .filter(t -> "PLANNED".equalsIgnoreCase(t.getEntryMode()))
                .filter(t -> Boolean.TRUE.equals(t.getIsRecurring()))
                .filter(t -> t.getRecurrencePatternId() != null)
                .filter(t -> !"void".equalsIgnoreCase(t.getRecordStatus()))
                .toList();

        log.info("Found {} mother transactions for entity {}", mothers.size(), entityId);

        Map<UUID, RecurrencePattern> patternMap = new HashMap<>();
        if (!mothers.isEmpty()) {
            Set<UUID> patternIds = mothers.stream()
                    .map(Transaction::getRecurrencePatternId)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
            for (RecurrencePattern p : recurrencePatternRepository.findAllById(patternIds)) {
                patternMap.put(p.getId(), p);
            }
        }

        Map<UUID, Project> projectMap = new HashMap<>();
        for (Project p : projectRepository.findByOwnerEntityIdIn(Set.of(entityId))) {
            projectMap.put(p.getId(), p);
        }

        List<ForecastEntryDTO> entries = new ArrayList<>();
        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;
        int patternsUsed = 0;
        int projectScoped = 0;
        int opex = 0;

        for (Transaction mother : mothers) {
            RecurrencePattern pattern = patternMap.get(mother.getRecurrencePatternId());
            if (pattern == null) {
                log.warn("Mother txn #{} references missing pattern {}; skipping",
                        mother.getId(), mother.getRecurrencePatternId());
                continue;
            }

            List<Transaction> virtuals = recurrenceEngineService
                    .materializeForecastInstances(mother, pattern, asOf, horizonDays);

            if (virtuals.isEmpty()) {
                continue;
            }
            patternsUsed++;

            Project project = mother.getProjectId() != null
                    ? projectMap.get(mother.getProjectId())
                    : null;

            for (Transaction v : virtuals) {
                ForecastEntryDTO dto = toEntryDto(v, mother, pattern, project,
                        revenueFactor, expenseFactor);
                entries.add(dto);

                BigDecimal amt = dto.amount() != null ? dto.amount() : BigDecimal.ZERO;
                if (INCOME_TYPE.equalsIgnoreCase(dto.type())) {
                    totalIncome = totalIncome.add(amt);
                } else {
                    totalExpenses = totalExpenses.add(amt);
                }
                if (dto.isOpex()) {
                    opex++;
                } else {
                    projectScoped++;
                }
            }
        }

        for (Project project : projectMap.values()) {
            if (!ProjectStatus.LIVE.equals(project.getStatus())) {
                continue;
            }
            BigDecimal monthlyRevenue = project.getExpectedMonthlyRevenue();
            if (monthlyRevenue == null || monthlyRevenue.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            List<ForecastEntryDTO> incomeEntries = generateProjectIncomeEntries(
                    project, asOf, horizonEnd, revenueFactor);
            for (ForecastEntryDTO entry : incomeEntries) {
                entries.add(entry);
                totalIncome = totalIncome.add(entry.amount());
                projectScoped++;
            }

            log.debug("Project {} (LIVE) contributed {} income entries",
                    project.getName(), incomeEntries.size());
        }

        entries.sort(Comparator.comparing(ForecastEntryDTO::date));

        BigDecimal net = totalIncome.subtract(totalExpenses);

        return new ForecastResponse(
                entityId,
                asOf,
                months,
                horizonDays,
                entries,
                totalExpenses,
                totalIncome,
                net,
                entries.size(),
                patternsUsed,
                projectScoped,
                opex
        );
    }

    /**
     * Run the forecast once per active scenario of the entity and return all
     * scenario totals side-by-side, ordered Baseline first. netVsBaseline is
     * each scenario's net minus the Baseline net. cumulativeNet is the running
     * monthly net (index 0 = end of month asOf's month) over the horizon.
     */
    public ScenarioComparisonResponse compareScenarios(UUID entityId, int horizonMonths) {
        Objects.requireNonNull(entityId, "entityId must not be null");
        int months = clampHorizon(horizonMonths);
        LocalDate asOf = LocalDate.now();

        List<Scenario> scenarios = scenarioRepository
                .findActiveByOwnerEntityIdIn(Set.of(entityId));

        // Order: Baseline first, then by name (mirrors repo ORDER BY scenario_type).
        scenarios.sort(Comparator
                .comparing((Scenario s) -> !ScenarioStatus.BASELINE.equalsIgnoreCase(s.getScenarioType()))
                .thenComparing(s -> s.getName() == null ? "" : s.getName()));

        // Shared month labels (YearMonth across horizon, e.g. "2026-05").
        List<String> monthLabels = new ArrayList<>();
        YearMonth startYm = YearMonth.from(asOf);
        for (int i = 0; i <= months; i++) {
            monthLabels.add(startYm.plusMonths(i).toString());
        }

        BigDecimal baselineNet = null;
        List<ScenarioForecastDTO> rows = new ArrayList<>();

        for (Scenario s : scenarios) {
            ForecastResponse fr = generateForecast(entityId, months, s.getId());
            boolean isBaseline = ScenarioStatus.BASELINE.equalsIgnoreCase(s.getScenarioType());
            if (isBaseline && baselineNet == null) {
                baselineNet = fr.netCashFlow();
            }

            List<BigDecimal> cumulative = buildCumulativeNet(fr.entries(), asOf, months);

            rows.add(new ScenarioForecastDTO(
                    s.getId(),
                    s.getName(),
                    s.getScenarioType(),
                    s.getColor(),
                    s.getRevenueAdjustPct(),
                    s.getExpenseAdjustPct(),
                    isBaseline,
                    fr.totalIncome(),
                    fr.totalExpenses(),
                    fr.netCashFlow(),
                    null, // netVsBaseline filled in below once baseline known
                    cumulative
            ));
        }

        // If no explicit baseline found, treat the first row as reference.
        final BigDecimal ref = (baselineNet != null)
                ? baselineNet
                : (rows.isEmpty() ? BigDecimal.ZERO : rows.get(0).netCashFlow());

        List<ScenarioForecastDTO> finalRows = new ArrayList<>();
        for (ScenarioForecastDTO r : rows) {
            BigDecimal vs = r.netCashFlow().subtract(ref);
            finalRows.add(new ScenarioForecastDTO(
                    r.scenarioId(), r.name(), r.scenarioType(), r.color(),
                    r.revenueAdjustPct(), r.expenseAdjustPct(), r.isBaseline(),
                    r.totalIncome(), r.totalExpenses(), r.netCashFlow(),
                    vs, r.cumulativeNet()
            ));
        }

        log.info("Scenario comparison: entityId={}, scenarios={}, horizonMonths={}",
                entityId, finalRows.size(), months);

        return new ScenarioComparisonResponse(entityId, asOf, months, monthLabels, finalRows);
    }

    // -- helpers ---------------------------------------------------------

    static int clampHorizon(int requested) {
        if (requested < 1) {
            return DEFAULT_HORIZON_MONTHS;
        }
        return Math.min(requested, MAX_HORIZON_MONTHS);
    }

    /** Convert a percentage shift (e.g. 20 or -5) to a multiplier (1.20 / 0.95). */
    private static BigDecimal factorFromPct(BigDecimal pct) {
        if (pct == null) {
            return BigDecimal.ONE;
        }
        return BigDecimal.ONE.add(pct.divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    /** Apply a factor to an amount, null-safe, rounded to 2 dp. */
    private static BigDecimal applyFactor(BigDecimal amount, BigDecimal factor) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (factor == null || factor.compareTo(BigDecimal.ONE) == 0) {
            return amount;
        }
        return amount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Build a per-month cumulative net series over the horizon from the flat
     * forecast entries. Index i corresponds to the YearMonth (asOf + i months).
     * Income adds, expense subtracts; the value at each index is the running
     * total up to and including that month.
     */
    private static List<BigDecimal> buildCumulativeNet(List<ForecastEntryDTO> entries,
                                                       LocalDate asOf, int months) {
        BigDecimal[] monthly = new BigDecimal[months + 1];
        for (int i = 0; i <= months; i++) {
            monthly[i] = BigDecimal.ZERO;
        }
        YearMonth start = YearMonth.from(asOf);

        for (ForecastEntryDTO e : entries) {
            if (e.date() == null || e.amount() == null) {
                continue;
            }
            int idx = (int) start.until(YearMonth.from(e.date()), java.time.temporal.ChronoUnit.MONTHS);
            if (idx < 0) {
                idx = 0;
            }
            if (idx > months) {
                continue;
            }
            BigDecimal signed = INCOME_TYPE.equalsIgnoreCase(e.type())
                    ? e.amount()
                    : e.amount().negate();
            monthly[idx] = monthly[idx].add(signed);
        }

        List<BigDecimal> cumulative = new ArrayList<>(months + 1);
        BigDecimal running = BigDecimal.ZERO;
        for (int i = 0; i <= months; i++) {
            running = running.add(monthly[i]);
            cumulative.add(running.setScale(2, RoundingMode.HALF_UP));
        }
        return cumulative;
    }

    private List<ForecastEntryDTO> generateProjectIncomeEntries(Project project,
                                                                LocalDate asOf,
                                                                LocalDate horizonEnd,
                                                                BigDecimal revenueFactor) {
        List<ForecastEntryDTO> result = new ArrayList<>();

        LocalDate effectiveStart = (project.getStartDate() != null
                && project.getStartDate().isAfter(asOf))
                ? project.getStartDate()
                : asOf;

        int targetDay = project.getStartDate() != null
                ? project.getStartDate().getDayOfMonth()
                : asOf.getDayOfMonth();

        YearMonth ym = YearMonth.from(effectiveStart);
        YearMonth endYm = YearMonth.from(horizonEnd);

        while (!ym.isAfter(endYm)) {
            int dayInMonth = Math.min(targetDay, ym.lengthOfMonth());
            LocalDate occurrence = ym.atDay(dayInMonth);

            if (!occurrence.isBefore(effectiveStart) && !occurrence.isAfter(horizonEnd)) {
                result.add(toIncomeEntryDto(project, occurrence, revenueFactor));
            }
            ym = ym.plusMonths(1);
        }

        return result;
    }

    private ForecastEntryDTO toIncomeEntryDto(Project project, LocalDate date,
                                              BigDecimal revenueFactor) {
        return new ForecastEntryDTO(
                null,
                null,
                date,
                INCOME_TYPE,
                applyFactor(project.getExpectedMonthlyRevenue(), revenueFactor),
                INCOME_DESCRIPTION_PREFIX + project.getName(),
                INCOME_CATEGORY,
                null,
                null,
                project.getId(),
                project.getName(),
                project.getStatus(),
                project.getColor(),
                INCOME_FREQUENCY,
                100,
                false
        );
    }

    private ForecastEntryDTO toEntryDto(Transaction virtual,
                                       Transaction mother,
                                       RecurrencePattern pattern,
                                       Project project,
                                       BigDecimal revenueFactor,
                                       BigDecimal expenseFactor) {
        boolean isOpex = mother.getProjectId() == null;
        BigDecimal factor = INCOME_TYPE.equalsIgnoreCase(virtual.getType())
                ? revenueFactor
                : expenseFactor;
        return new ForecastEntryDTO(
                pattern.getId(),
                mother.getId(),
                virtual.getDocDate(),
                virtual.getType(),
                applyFactor(virtual.getAmount(), factor),
                virtual.getDescription(),
                virtual.getCategory(),
                virtual.getSubcategory(),
                virtual.getCounterparty(),
                mother.getProjectId(),
                project != null ? project.getName() : null,
                project != null ? project.getStatus() : null,
                project != null ? project.getColor() : null,
                pattern.getFrequency(),
                mother.getConfidencePct() != null ? mother.getConfidencePct() : 100,
                isOpex
        );
    }
}
