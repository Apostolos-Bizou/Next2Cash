package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.ForecastEntryDTO;
import com.next2me.next2cash.dto.ForecastResponse;
import com.next2me.next2cash.model.Project;
import com.next2me.next2cash.model.ProjectStatus;
import com.next2me.next2cash.model.RecurrencePattern;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.ProjectRepository;
import com.next2me.next2cash.repository.RecurrencePatternRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
 * Phase B (S85 Step B, this session):
 *   - Adds virtual INCOME entries from Project.expectedMonthlyRevenue
 *   - Only LIVE projects participate (status == "LIVE")
 *   - Income occurs monthly on the day-of-month derived from project.startDate
 *     (or today's day if startDate is null), clamped for short months
 *   - Generation window: max(project.startDate, today) -> today + horizonMonths
 *   - Source-of-revenue entries carry projectId, but patternId/motherTransactionId
 *     are null (they originate from project metadata, not a recurring mother)
 *
 * The orchestrator is read-only -- it never writes to the database.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

    // Conservative day count per month: ensures we never under-shoot. The
    // pattern's own endDate / maxOccurrences will trim overshoots cleanly.
    private static final int DAYS_PER_MONTH_UPPER = 31;

    static final int MAX_HORIZON_MONTHS = 120;
    static final int DEFAULT_HORIZON_MONTHS = 24;

    // Constants for project-revenue virtual entries (Phase B)
    private static final String INCOME_TYPE = "income";
    private static final String INCOME_CATEGORY = "Project Revenue";
    private static final String INCOME_FREQUENCY = "MONTHLY";
    private static final String INCOME_DESCRIPTION_PREFIX = "Expected revenue: ";

    private final TransactionRepository transactionRepository;
    private final RecurrencePatternRepository recurrencePatternRepository;
    private final ProjectRepository projectRepository;
    private final RecurrenceEngineService recurrenceEngineService;

    /**
     * Generate a forecast for the given entity over the next N months.
     *
     * @param entityId       owner entity UUID (required)
     * @param horizonMonths  number of months to project (1..120; clamped)
     * @return forecast response with flat list of virtual entries + totals
     */
    public ForecastResponse generateForecast(UUID entityId, int horizonMonths) {
        Objects.requireNonNull(entityId, "entityId must not be null");

        int months = clampHorizon(horizonMonths);
        int horizonDays = months * DAYS_PER_MONTH_UPPER;
        LocalDate asOf = LocalDate.now();
        LocalDate horizonEnd = asOf.plusMonths(months);

        log.info("Forecast requested: entityId={}, horizonMonths={}, asOf={}",
                entityId, months, asOf);

        // 1) Load all mother transactions for this entity that are PLANNED + recurring.
        //    These are the "anchors" from which we expand future instances.
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

        // 2) Bulk-load needed patterns by id (only if we have mothers)
        Map<UUID, RecurrencePattern> patternMap = new HashMap<>();
        if (!mothers.isEmpty()) {
            Set<UUID> patternIds = mothers.stream()
                    .map(Transaction::getRecurrencePatternId)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
            for (RecurrencePattern p : recurrencePatternRepository.findAllById(patternIds)) {
                patternMap.put(p.getId(), p);
            }
        }

        // 3) Bulk-load projects for this entity (used for both denormalization
        //    in Phase A expense block AND as the source for Phase B income block)
        Map<UUID, Project> projectMap = new HashMap<>();
        for (Project p : projectRepository.findByOwnerEntityIdIn(Set.of(entityId))) {
            projectMap.put(p.getId(), p);
        }

        // 4) Materialize virtual EXPENSE instances for each mother (Phase A)
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
                ForecastEntryDTO dto = toEntryDto(v, mother, pattern, project);
                entries.add(dto);

                // Tally
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

        // 5) Phase B: Materialize virtual INCOME instances from LIVE projects
        //    Each LIVE project with expectedMonthlyRevenue > 0 contributes one
        //    income entry per month within the horizon.
        for (Project project : projectMap.values()) {
            if (!ProjectStatus.LIVE.equals(project.getStatus())) {
                continue;
            }
            BigDecimal monthlyRevenue = project.getExpectedMonthlyRevenue();
            if (monthlyRevenue == null || monthlyRevenue.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            List<ForecastEntryDTO> incomeEntries = generateProjectIncomeEntries(
                    project, asOf, horizonEnd);
            for (ForecastEntryDTO entry : incomeEntries) {
                entries.add(entry);
                totalIncome = totalIncome.add(entry.amount());
                projectScoped++;
            }

            log.debug("Project {} (LIVE) contributed {} income entries",
                    project.getName(), incomeEntries.size());
        }

        // 6) Sort chronologically (deterministic for snapshot UIs)
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

    // -- helpers ---------------------------------------------------------

    static int clampHorizon(int requested) {
        if (requested < 1) {
            return DEFAULT_HORIZON_MONTHS;
        }
        return Math.min(requested, MAX_HORIZON_MONTHS);
    }

    /**
     * Generate one virtual income entry per month within the horizon for a
     * single LIVE project. Generation starts at max(project.startDate, asOf)
     * and continues until horizonEnd. The day-of-month is derived from the
     * project's startDate (or today's day if startDate is null), clamped to
     * the last day of the month when the target day exceeds month length.
     */
    private List<ForecastEntryDTO> generateProjectIncomeEntries(Project project,
                                                                LocalDate asOf,
                                                                LocalDate horizonEnd) {
        List<ForecastEntryDTO> result = new ArrayList<>();

        LocalDate effectiveStart = (project.getStartDate() != null
                && project.getStartDate().isAfter(asOf))
                ? project.getStartDate()
                : asOf;

        int targetDay = project.getStartDate() != null
                ? project.getStartDate().getDayOfMonth()
                : asOf.getDayOfMonth();

        // Walk month by month from effectiveStart's YearMonth until horizonEnd
        YearMonth ym = YearMonth.from(effectiveStart);
        YearMonth endYm = YearMonth.from(horizonEnd);

        while (!ym.isAfter(endYm)) {
            int dayInMonth = Math.min(targetDay, ym.lengthOfMonth());
            LocalDate occurrence = ym.atDay(dayInMonth);

            // Skip occurrences before effectiveStart (e.g. earlier in the
            // first month) and after horizonEnd
            if (!occurrence.isBefore(effectiveStart) && !occurrence.isAfter(horizonEnd)) {
                result.add(toIncomeEntryDto(project, occurrence));
            }
            ym = ym.plusMonths(1);
        }

        return result;
    }

    private ForecastEntryDTO toIncomeEntryDto(Project project, LocalDate date) {
        return new ForecastEntryDTO(
                null,                          // patternId -- not from a recurring mother
                null,                          // motherTransactionId -- same
                date,
                INCOME_TYPE,
                project.getExpectedMonthlyRevenue(),
                INCOME_DESCRIPTION_PREFIX + project.getName(),
                INCOME_CATEGORY,
                null,                          // subcategory
                null,                          // counterparty
                project.getId(),
                project.getName(),
                project.getStatus(),
                project.getColor(),
                INCOME_FREQUENCY,
                100,                           // confidencePct -- LIVE = certain
                false                          // isOpex -- always project-scoped
        );
    }

    private ForecastEntryDTO toEntryDto(Transaction virtual,
                                       Transaction mother,
                                       RecurrencePattern pattern,
                                       Project project) {
        boolean isOpex = mother.getProjectId() == null;
        return new ForecastEntryDTO(
                pattern.getId(),
                mother.getId(),
                virtual.getDocDate(),
                virtual.getType(),
                virtual.getAmount(),
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
