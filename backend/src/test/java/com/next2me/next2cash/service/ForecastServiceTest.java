package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.ForecastResponse;
import com.next2me.next2cash.model.Project;
import com.next2me.next2cash.model.ProjectStatus;
import com.next2me.next2cash.model.RecurrencePattern;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.ProjectRepository;
import com.next2me.next2cash.repository.ScenarioRepository;
import com.next2me.next2cash.repository.RecurrencePatternRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ForecastService -- S85 Forecast Engine.
 *
 * Uses real RecurrenceEngineService (pure logic, no DB) and mocks the
 * three repositories. Verifies that:
 *   - Empty entity (no mother transactions) returns an empty response
 *   - A single MONTHLY mother produces ~horizonMonths virtual entries
 *     with totals aggregated correctly
 *   - A single LIVE project with expectedMonthlyRevenue produces monthly
 *     virtual income entries (Phase B)
 */
@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private RecurrencePatternRepository recurrencePatternRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ScenarioRepository scenarioRepository;

    // Real engine -- it is pure logic and already independently tested
    private final RecurrenceEngineService engine = new RecurrenceEngineService();

    private ForecastService service;

    private final UUID entityId = UUID.fromString("58202b71-4ddb-45c9-8e3c-39e816bde972");

    @BeforeEach
    void setUp() {
        service = new ForecastService(
                transactionRepository,
                recurrencePatternRepository,
                projectRepository,
                engine,
                scenarioRepository
        );
        // Default empty stubs so any path won't NPE on missing mock
        lenient().when(projectRepository.findByOwnerEntityIdIn(anySet()))
                .thenReturn(List.of());
    }

    @Test
    void emptyEntity_returnsEmptyForecast() {
        when(transactionRepository.findAll()).thenReturn(List.of());

        ForecastResponse r = service.generateForecast(entityId, 12);

        assertThat(r).isNotNull();
        assertThat(r.entityId()).isEqualTo(entityId);
        assertThat(r.horizonMonths()).isEqualTo(12);
        assertThat(r.entries()).isEmpty();
        assertThat(r.entryCount()).isZero();
        assertThat(r.patternCount()).isZero();
        assertThat(r.totalExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.totalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.netCashFlow()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void monthlyPattern_produces12EntriesIn12Months() {
        // Pattern: monthly, day 10, started yesterday so today is "after startDate"
        UUID patternId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(1);

        RecurrencePattern pattern = new RecurrencePattern();
        pattern.setId(patternId);
        pattern.setFrequency("MONTHLY");
        pattern.setIntervalCount(1);
        pattern.setDayOfMonth(today.plusDays(5).getDayOfMonth()); // next occurrence ~5 days away
        pattern.setStartDate(startDate);
        pattern.setTimezone("Europe/Athens");

        // Mother PLANNED + recurring transaction
        Transaction mother = new Transaction();
        mother.setId(99001);
        mother.setEntityId(entityId);
        mother.setType("expense");
        mother.setAmount(new BigDecimal("100.00"));
        mother.setDocDate(startDate);
        mother.setEntryMode("PLANNED");
        mother.setIsRecurring(Boolean.TRUE);
        mother.setRecurrencePatternId(patternId);
        mother.setRecordStatus("active");
        mother.setConfidencePct(100);
        mother.setProjectId(null);  // OpEx
        mother.setDescription("Test monthly OpEx");
        mother.setCategory("OTHER");

        // Unrelated transaction in same entity (ACTUAL -- should be ignored)
        Transaction actual = new Transaction();
        actual.setId(99002);
        actual.setEntityId(entityId);
        actual.setType("expense");
        actual.setAmount(new BigDecimal("50.00"));
        actual.setEntryMode("ACTUAL");
        actual.setIsRecurring(Boolean.FALSE);
        actual.setRecordStatus("active");

        when(transactionRepository.findAll()).thenReturn(List.of(mother, actual));
        when(recurrencePatternRepository.findAllById(any())).thenReturn(List.of(pattern));

        ForecastResponse r = service.generateForecast(entityId, 12);

        assertThat(r).isNotNull();
        assertThat(r.entries()).isNotEmpty();
        assertThat(r.patternCount()).isEqualTo(1);
        assertThat(r.entries().size()).isBetween(10, 14);  // ~12, allows month-boundary flex
        assertThat(r.totalExpenses()).isGreaterThan(BigDecimal.ZERO);
        assertThat(r.totalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.netCashFlow()).isLessThan(BigDecimal.ZERO);  // pure burn
        assertThat(r.opexCount()).isEqualTo(r.entryCount());      // all OpEx
        assertThat(r.projectScopedCount()).isZero();

        // All entries should refer back to mother + pattern
        r.entries().forEach(e -> {
            assertThat(e.motherTransactionId()).isEqualTo(99001);
            assertThat(e.patternId()).isEqualTo(patternId);
            assertThat(e.patternFrequency()).isEqualTo("MONTHLY");
            assertThat(e.isOpex()).isTrue();
            assertThat(e.projectId()).isNull();
            assertThat(e.amount()).isEqualByComparingTo("100.00");
        });
    }

    /**
     * S85 Step B: A LIVE project with expectedMonthlyRevenue should produce
     * one virtual income entry per month within the horizon.
     */
    @Test
    void liveProject_addsMonthlyRevenue() {
        // No mother transactions -- isolate the Phase B income path
        when(transactionRepository.findAll()).thenReturn(List.of());

        LocalDate today = LocalDate.now();

        Project liveProject = new Project();
        liveProject.setId(UUID.randomUUID());
        liveProject.setName("Next2View");
        liveProject.setOwnerEntityId(entityId);
        liveProject.setStatus(ProjectStatus.LIVE);
        liveProject.setStartDate(today.minusMonths(2));  // already live for 2 months
        liveProject.setExpectedMonthlyRevenue(new BigDecimal("5000.00"));
        liveProject.setColor("#10B981");

        // Override the default empty stub from setUp() for this test
        when(projectRepository.findByOwnerEntityIdIn(anySet()))
                .thenReturn(List.of(liveProject));

        ForecastResponse r = service.generateForecast(entityId, 12);

        assertThat(r).isNotNull();
        // 12-month horizon should produce 12 monthly income entries
        // (small flex allowed for month-boundary edge cases)
        assertThat(r.entries().size()).isBetween(11, 13);
        assertThat(r.entryCount()).isEqualTo(r.entries().size());
        assertThat(r.patternCount()).isZero();  // no mothers used
        assertThat(r.projectScopedCount()).isEqualTo(r.entries().size());
        assertThat(r.opexCount()).isZero();

        // Income totals
        assertThat(r.totalExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.totalIncome())
                .isGreaterThanOrEqualTo(new BigDecimal("55000.00"))   // 11 * 5000
                .isLessThanOrEqualTo(new BigDecimal("65000.00"));     // 13 * 5000
        assertThat(r.netCashFlow()).isGreaterThan(BigDecimal.ZERO);

        // Per-entry sanity: every entry is project-scoped income
        r.entries().forEach(e -> {
            assertThat(e.type()).isEqualTo("income");
            assertThat(e.amount()).isEqualByComparingTo("5000.00");
            assertThat(e.projectId()).isEqualTo(liveProject.getId());
            assertThat(e.projectName()).isEqualTo("Next2View");
            assertThat(e.projectStatus()).isEqualTo(ProjectStatus.LIVE);
            assertThat(e.projectColor()).isEqualTo("#10B981");
            assertThat(e.patternFrequency()).isEqualTo("MONTHLY");
            assertThat(e.category()).isEqualTo("Project Revenue");
            assertThat(e.isOpex()).isFalse();
            assertThat(e.patternId()).isNull();             // not from a mother
            assertThat(e.motherTransactionId()).isNull();   // not from a mother
            assertThat(e.confidencePct()).isEqualTo(100);
        });

        // Entries should be chronologically sorted
        for (int i = 1; i < r.entries().size(); i++) {
            assertThat(r.entries().get(i).date())
                    .isAfterOrEqualTo(r.entries().get(i - 1).date());
        }
    }
}
