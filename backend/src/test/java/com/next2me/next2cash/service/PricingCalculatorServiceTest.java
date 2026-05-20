package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.PricingCalculatorResponse;
import com.next2me.next2cash.model.Project;
import com.next2me.next2cash.model.RecurrencePattern;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.ProjectRepository;
import com.next2me.next2cash.repository.RecurrencePatternRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PricingCalculatorService (S86).
 *
 * Coverage:
 *  - GROUP mode (empty + with projects)
 *  - PROJECT mode (with/without OpEx allocation, manual override)
 *  - Reverse pricing math
 *  - CFO metrics
 *  - Scenarios
 *  - Pattern normalization
 *
 * Note: Mockito strict-stubs mode requires every stub to be used by the test
 * that defines it. We therefore stub the repositories per-test rather than in
 * a shared @BeforeEach (which would trigger UnnecessaryStubbingException for
 * tests that call normalizeToMonthly directly without invoking calculate()).
 */
@ExtendWith(MockitoExtension.class)
class PricingCalculatorServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private RecurrencePatternRepository recurrencePatternRepository;
    @Mock private ProjectRepository projectRepository;

    @InjectMocks
    private PricingCalculatorService service;

    private static final UUID ENTITY_ID = UUID.randomUUID();

    // =====================================================================
    //  GROUP MODE
    // =====================================================================

    @Test
    void calculate_groupMode_noProjects_returnsZeroBurn() {
        when(projectRepository.findByStatus("LIVE")).thenReturn(Collections.emptyList());
        when(transactionRepository.findAll()).thenReturn(Collections.emptyList());
        when(recurrencePatternRepository.findActiveAsOf(any(LocalDate.class)))
            .thenReturn(Collections.emptyList());

        PricingCalculatorResponse out = service.calculate(null, new BigDecimal("0.15"));

        assertThat(out.getMode()).isEqualTo("GROUP");
        assertThat(out.getProjectName()).isEqualTo("Όλος ο Όμιλος");
        assertThat(out.getDirectBurn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(out.getAllocatedOpex()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(out.getTotalCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(out.getProjectBreakdown()).isEmpty();
    }

    @Test
    void calculate_groupMode_oneLiveProject_aggregatesCorrectly() {
        Project p = makeProject("Next2Cash", "LIVE", new BigDecimal("4985"),
            new BigDecimal("0"), new BigDecimal("1200"), 12);

        when(projectRepository.findByStatus("LIVE")).thenReturn(List.of(p));

        Transaction opexTxn = makeOpexTransaction(new BigDecimal("6870"));
        RecurrencePattern opexPat = makeMonthlyPattern(opexTxn.getRecurrencePatternId());
        when(transactionRepository.findAll()).thenReturn(List.of(opexTxn));
        when(recurrencePatternRepository.findActiveAsOf(any(LocalDate.class)))
            .thenReturn(List.of(opexPat));

        PricingCalculatorResponse out = service.calculate(null, new BigDecimal("0.15"));

        assertThat(out.getMode()).isEqualTo("GROUP");
        assertThat(out.getDirectBurn()).isEqualByComparingTo(new BigDecimal("4985.00"));
        assertThat(out.getAllocatedOpex()).isEqualByComparingTo(new BigDecimal("6870.00"));
        assertThat(out.getTotalCost()).isEqualByComparingTo(new BigDecimal("11855.00"));

        // Required revenue = 11855 / 0.85 = 13947.06
        assertThat(out.getRequiredRevenue()).isEqualByComparingTo(new BigDecimal("13947.06"));
        assertThat(out.getProfitAtMargin()).isEqualByComparingTo(new BigDecimal("2092.06"));

        assertThat(out.getProjectBreakdown()).hasSize(1);
        assertThat(out.getProjectBreakdown().get(0).getPctOfGroup())
            .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // =====================================================================
    //  PROJECT MODE
    // =====================================================================

    @Test
    void calculate_projectMode_withOpexAllocation_appliesPct() {
        UUID projectId = UUID.randomUUID();

        Project p = makeProjectWithId(projectId, "Next2Cash", "LIVE",
            new BigDecimal("4000"), new BigDecimal("50"),
            new BigDecimal("1200"), 12);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(p));

        Transaction opexTxn = makeOpexTransaction(new BigDecimal("10000"));
        RecurrencePattern opexPat = makeMonthlyPattern(opexTxn.getRecurrencePatternId());
        when(transactionRepository.findAll()).thenReturn(List.of(opexTxn));
        when(recurrencePatternRepository.findActiveAsOf(any(LocalDate.class)))
            .thenReturn(List.of(opexPat));

        PricingCalculatorResponse out = service.calculate(projectId, new BigDecimal("0.15"));

        assertThat(out.getMode()).isEqualTo("PROJECT");
        assertThat(out.getProjectId()).isEqualTo(projectId);
        assertThat(out.getDirectBurn()).isEqualByComparingTo(new BigDecimal("4000.00"));
        assertThat(out.getAllocatedOpex()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(out.getTotalCost()).isEqualByComparingTo(new BigDecimal("9000.00"));
    }

    @Test
    void calculate_projectMode_directBurnOverride_skipsAutoCompute() {
        UUID projectId = UUID.randomUUID();

        Project p = makeProjectWithId(projectId, "Polaris", "LIVE",
            new BigDecimal("9999"), new BigDecimal("0"),
            BigDecimal.ZERO, 0);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(p));

        Transaction t = makeProjectTransaction(projectId, new BigDecimal("123"));
        RecurrencePattern pat = makeMonthlyPattern(t.getRecurrencePatternId());
        when(transactionRepository.findAll()).thenReturn(List.of(t));
        when(recurrencePatternRepository.findActiveAsOf(any(LocalDate.class)))
            .thenReturn(List.of(pat));

        PricingCalculatorResponse out = service.calculate(projectId, new BigDecimal("0.15"));

        // Override of 9999 used (NOT 123 from pattern)
        assertThat(out.getDirectBurn()).isEqualByComparingTo(new BigDecimal("9999.00"));
    }

    // =====================================================================
    //  REVERSE PRICING + CFO METRICS
    // =====================================================================

    @Test
    void reversePricing_15pctMargin_computesRequiredRevenue() {
        Project p = makeProject("X", "LIVE", new BigDecimal("8500"),
            new BigDecimal("0"), BigDecimal.ZERO, 0);
        when(projectRepository.findByStatus("LIVE")).thenReturn(List.of(p));
        when(transactionRepository.findAll()).thenReturn(Collections.emptyList());
        when(recurrencePatternRepository.findActiveAsOf(any(LocalDate.class)))
            .thenReturn(Collections.emptyList());

        PricingCalculatorResponse out = service.calculate(null, new BigDecimal("0.15"));

        // 8500 / 0.85 = 10000
        assertThat(out.getRequiredRevenue()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(out.getProfitAtMargin()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void cfoMetrics_validInputs_computesLtvAndPayback() {
        UUID projectId = UUID.randomUUID();
        Project p = makeProjectWithId(projectId, "X", "LIVE",
            new BigDecimal("1000"), new BigDecimal("0"),
            new BigDecimal("1000"), 10);
        p.setCacPerCustomer(new BigDecimal("300"));
        p.setGrossMarginPct(new BigDecimal("80"));
        p.setMonthlyChurnPct(new BigDecimal("5"));
        p.setAnnualBillingPct(BigDecimal.ZERO);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(p));
        when(transactionRepository.findAll()).thenReturn(Collections.emptyList());
        when(recurrencePatternRepository.findActiveAsOf(any(LocalDate.class)))
            .thenReturn(Collections.emptyList());

        PricingCalculatorResponse out = service.calculate(projectId, new BigDecimal("0.15"));

        // ARPU = 1000 / 10 = 100
        assertThat(out.getArpu()).isEqualByComparingTo(new BigDecimal("100.00"));
        // CAC Payback = 300 / (100 * 0.80) = 3.75
        assertThat(out.getCacPaybackMonths()).isEqualByComparingTo(new BigDecimal("3.75"));
        // LTV = (100 * 0.80) / 0.05 = 1600
        assertThat(out.getLtv()).isEqualByComparingTo(new BigDecimal("1600.00"));
        // LTV:CAC = 1600 / 300 = ~5.33
        assertThat(out.getLtvCacRatio()).isGreaterThan(new BigDecimal("5"));
        assertThat(out.getLtvCacRatio()).isLessThan(new BigDecimal("6"));
    }

    // =====================================================================
    //  SCENARIOS
    // =====================================================================

    @Test
    void scenarios_flat99_computesCustomersNeeded() {
        Project p = makeProject("X", "LIVE", new BigDecimal("11855"),
            new BigDecimal("0"), BigDecimal.ZERO, 0);
        when(projectRepository.findByStatus("LIVE")).thenReturn(List.of(p));
        when(transactionRepository.findAll()).thenReturn(Collections.emptyList());
        when(recurrencePatternRepository.findActiveAsOf(any(LocalDate.class)))
            .thenReturn(Collections.emptyList());

        PricingCalculatorResponse out = service.calculate(null, new BigDecimal("0.15"));

        // Required = 13947.06; customers = ceil(13947.06 / 99) = 141
        assertThat(out.getScenarioA().getMonthlyPrice()).isEqualByComparingTo(new BigDecimal("99"));
        assertThat(out.getScenarioA().getCustomersNeeded()).isEqualTo(141);
    }

    @Test
    void scenarios_tieredBlendedPrice_correctMath() {
        UUID projectId = UUID.randomUUID();
        Project p = makeProjectWithId(projectId, "X", "LIVE",
            new BigDecimal("5000"), new BigDecimal("0"),
            BigDecimal.ZERO, 0);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(p));
        when(transactionRepository.findAll()).thenReturn(Collections.emptyList());
        when(recurrencePatternRepository.findActiveAsOf(any(LocalDate.class)))
            .thenReturn(Collections.emptyList());

        PricingCalculatorResponse out = service.calculate(projectId, new BigDecimal("0.15"));

        // 0.6*49 + 0.3*149 + 0.1*499 = 29.4 + 44.7 + 49.9 = 124.00
        assertThat(out.getScenarioC().getBlendedPrice())
            .isEqualByComparingTo(new BigDecimal("124.00"));
        assertThat(out.getScenarioC().getTiers()).hasSize(3);
        assertThat(out.getScenarioC().getLabel()).isEqualTo("Tiered (3 πλάνα)");
    }

    // =====================================================================
    //  PATTERN NORMALIZATION (direct unit tests, no mock setup needed)
    // =====================================================================

    @Test
    void normalizeToMonthly_yearlyPattern_dividesBy12() {
        RecurrencePattern yearly = new RecurrencePattern();
        yearly.setFrequency("YEARLY");
        yearly.setIntervalCount(1);

        BigDecimal monthly = service.normalizeToMonthly(new BigDecimal("1200"), yearly);

        // 1200 / 12 = 100
        assertThat(monthly).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void normalizeToMonthly_monthlyPattern_returnsSameAmount() {
        RecurrencePattern monthly = new RecurrencePattern();
        monthly.setFrequency("MONTHLY");
        monthly.setIntervalCount(1);

        BigDecimal result = service.normalizeToMonthly(new BigDecimal("500"), monthly);

        assertThat(result).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================

    private Project makeProject(String name, String status,
                                BigDecimal directBurn, BigDecimal opexAllocPct,
                                BigDecimal mrr, int customers) {
        return makeProjectWithId(UUID.randomUUID(), name, status, directBurn, opexAllocPct, mrr, customers);
    }

    private Project makeProjectWithId(UUID id, String name, String status,
                                       BigDecimal directBurn, BigDecimal opexAllocPct,
                                       BigDecimal mrr, int customers) {
        Project p = new Project();
        p.setId(id);
        p.setName(name);
        p.setStatus(status);
        p.setOwnerEntityId(ENTITY_ID);
        p.setDirectBurnMonthly(directBurn);
        p.setOpexAllocationPct(opexAllocPct);
        p.setCurrentMrr(mrr);
        p.setCurrentCustomers(customers);
        p.setCacPerCustomer(BigDecimal.ZERO);
        p.setGrossMarginPct(new BigDecimal("75"));
        p.setMonthlyChurnPct(new BigDecimal("3"));
        p.setAnnualBillingPct(BigDecimal.ZERO);
        p.setAnnualDiscountPct(new BigDecimal("15"));
        p.setAnnualChurnPct(new BigDecimal("0.5"));
        return p;
    }

    private Transaction makeOpexTransaction(BigDecimal amount) {
        Transaction t = new Transaction();
        t.setEntryMode("PLANNED");
        t.setIsRecurring(true);
        t.setRecurrencePatternId(UUID.randomUUID());
        t.setProjectId(null);
        t.setAmount(amount);
        return t;
    }

    private Transaction makeProjectTransaction(UUID projectId, BigDecimal amount) {
        Transaction t = new Transaction();
        t.setEntryMode("PLANNED");
        t.setIsRecurring(true);
        t.setRecurrencePatternId(UUID.randomUUID());
        t.setProjectId(projectId);
        t.setAmount(amount);
        return t;
    }

    private RecurrencePattern makeMonthlyPattern(UUID id) {
        RecurrencePattern pat = new RecurrencePattern();
        pat.setId(id);
        pat.setFrequency("MONTHLY");
        pat.setIntervalCount(1);
        pat.setStartDate(LocalDate.now().minusMonths(1));
        return pat;
    }
}
