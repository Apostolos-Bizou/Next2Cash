package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.ForecastResponse;
import com.next2me.next2cash.model.Project;
import com.next2me.next2cash.model.RecurrencePattern;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.ProjectRepository;
import com.next2me.next2cash.repository.RecurrencePatternRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ForecastService — S85 Forecast Engine.
 *
 * Uses real RecurrenceEngineService (pure logic, no DB) and mocks the
 * three repositories. Verifies that:
 *   - Empty entity (no mother transactions) returns an empty response
 *   - A single MONTHLY mother produces ~horizonMonths virtual entries
 *     with totals aggregated correctly
 */
@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private RecurrencePatternRepository recurrencePatternRepository;
    @Mock private ProjectRepository projectRepository;

    // Real engine — it is pure logic and already independently tested
    private final RecurrenceEngineService engine = new RecurrenceEngineService();

    private ForecastService service;

    private final UUID entityId = UUID.fromString("58202b71-4ddb-45c9-8e3c-39e816bde972");

    @BeforeEach
    void setUp() {
        service = new ForecastService(
                transactionRepository,
                recurrencePatternRepository,
                projectRepository,
                engine
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
        mother.setCategory("ΛΟΙΠΑ");

        // Unrelated transaction in same entity (ACTUAL — should be ignored)
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
}
