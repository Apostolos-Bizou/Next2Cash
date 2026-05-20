package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.ForecastEntryDTO;
import com.next2me.next2cash.dto.ForecastResponse;
import com.next2me.next2cash.model.Project;
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
 * ForecastService — orchestrates virtual cash flow forecasts.
 *
 * Created in S85 (Forecast Engine, May 2026). Phase A (minimal):
 *   - Loads PLANNED + isRecurring=true mother transactions for an entity
 *   - For each mother, fetches its RecurrencePattern
 *   - Calls RecurrenceEngineService.materializeForecastInstances() to expand
 *     into virtual instances over the requested horizon
 *   - Enriches each instance with denormalized project info
 *   - Aggregates totals
 *
 * Income from project.expectedMonthlyRevenue is intentionally OUT OF SCOPE
 * for Phase A; it lands in Phase B.
 *
 * The orchestrator is read-only — it never writes to the database.
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

        if (mothers.isEmpty()) {
            return emptyResponse(entityId, asOf, months, horizonDays);
        }

        // 2) Bulk-load needed patterns by id
        Set<UUID> patternIds = mothers.stream()
                .map(Transaction::getRecurrencePatternId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        Map<UUID, RecurrencePattern> patternMap = new HashMap<>();
        for (RecurrencePattern p : recurrencePatternRepository.findAllById(patternIds)) {
            patternMap.put(p.getId(), p);
        }

        // 3) Bulk-load projects for this entity (denormalization source)
        Map<UUID, Project> projectMap = new HashMap<>();
        for (Project p : projectRepository.findByOwnerEntityIdIn(Set.of(entityId))) {
            projectMap.put(p.getId(), p);
        }

        // 4) Materialize virtual instances for each mother
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
                if ("income".equalsIgnoreCase(dto.type())) {
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

        // 5) Sort chronologically (deterministic for snapshot UIs)
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

    // ── helpers ────────────────────────────────────────────────────────

    static int clampHorizon(int requested) {
        if (requested < 1) {
            return DEFAULT_HORIZON_MONTHS;
        }
        return Math.min(requested, MAX_HORIZON_MONTHS);
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

    private ForecastResponse emptyResponse(UUID entityId, LocalDate asOf,
                                           int months, int horizonDays) {
        return new ForecastResponse(
                entityId,
                asOf,
                months,
                horizonDays,
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                0,
                0
        );
    }
}
