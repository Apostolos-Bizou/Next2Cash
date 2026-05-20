package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.PricingCalculatorResponse;
import com.next2me.next2cash.dto.PricingCalculatorResponse.ProjectBreakdownEntry;
import com.next2me.next2cash.dto.PricingCalculatorResponse.ScenarioA;
import com.next2me.next2cash.dto.PricingCalculatorResponse.ScenarioB;
import com.next2me.next2cash.dto.PricingCalculatorResponse.ScenarioC;
import com.next2me.next2cash.dto.PricingCalculatorResponse.TierEntry;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PricingCalculatorService -- reverse pricing math engine + CFO metrics + scenarios.
 *
 * Created in S86 (Pricing Calculator + AI CFO Advisor, May 2026).
 *
 * Two modes:
 *   GROUP   : projectId == null -- consolidates all LIVE projects, OpEx counted 100%.
 *   PROJECT : specific projectId -- single-project view with OpEx allocation %.
 *
 * Direct Burn auto-compute strategy:
 *   1. Iterate PLANNED + isRecurring=true mother transactions.
 *   2. Group by projectId (null = OpEx).
 *   3. Normalize each amount to monthly via pattern frequency.
 *   4. Sum per group.
 *
 * Override: if project.directBurnMonthly is set explicitly (non-null), use that
 * instead of the auto-computed sum.
 *
 * The service is READ-ONLY -- it never mutates the database for the calculation.
 * Config updates happen via a separate path (PUT /api/projects/{id}/pricing-config).
 */
@Service
@RequiredArgsConstructor
public class PricingCalculatorService {

    private static final Logger log = LoggerFactory.getLogger(PricingCalculatorService.class);

    private static final int MONEY_SCALE   = 2;
    private static final int RATIO_SCALE   = 4;
    private static final int PERCENT_SCALE = 2;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE      = new BigDecimal("12");

    // Scenario A defaults
    private static final BigDecimal SCENARIO_A_FLAT_PRICE = new BigDecimal("99");

    // Scenario C default tiered mix (60/30/10)
    private static final BigDecimal TIER_STARTER_PRICE = new BigDecimal("49");
    private static final BigDecimal TIER_PRO_PRICE     = new BigDecimal("149");
    private static final BigDecimal TIER_ENTERPRISE_PRICE = new BigDecimal("499");
    private static final BigDecimal TIER_STARTER_WEIGHT = new BigDecimal("0.60");
    private static final BigDecimal TIER_PRO_WEIGHT     = new BigDecimal("0.30");
    private static final BigDecimal TIER_ENTERPRISE_WEIGHT = new BigDecimal("0.10");

    private final TransactionRepository transactionRepository;
    private final RecurrencePatternRepository recurrencePatternRepository;
    private final ProjectRepository projectRepository;

    // =====================================================================
    //  PUBLIC API
    // =====================================================================

    /**
     * Build a pricing calculator response.
     *
     * @param projectId    UUID of project; null = GROUP mode (consolidated)
     * @param targetMargin target margin as decimal (0.15 = 15%); 0.05-0.50 acceptable
     */
    public PricingCalculatorResponse calculate(UUID projectId, BigDecimal targetMargin) {
        if (targetMargin == null) targetMargin = new BigDecimal("0.15");
        targetMargin = clamp(targetMargin, new BigDecimal("0.01"), new BigDecimal("0.95"));

        PricingCalculatorResponse out = new PricingCalculatorResponse();
        out.setTargetMargin(targetMargin);

        // --- Build burn map: projectId (null=OpEx) -> monthly burn ---
        Map<UUID, BigDecimal> burnByProject = computeBurnByProject();
        BigDecimal totalOpex = burnByProject.getOrDefault(null, BigDecimal.ZERO);

        // --- Branch on mode ---
        if (projectId == null) {
            buildGroupMode(out, burnByProject, totalOpex, targetMargin);
        } else {
            Optional<Project> projOpt = projectRepository.findById(projectId);
            if (projOpt.isEmpty()) {
                throw new IllegalArgumentException("Project not found: " + projectId);
            }
            buildProjectMode(out, projOpt.get(), burnByProject, totalOpex, targetMargin);
        }

        return out;
    }

    // =====================================================================
    //  GROUP MODE
    // =====================================================================

    private void buildGroupMode(PricingCalculatorResponse out,
                                Map<UUID, BigDecimal> burnByProject,
                                BigDecimal totalOpex,
                                BigDecimal targetMargin) {
        out.setMode("GROUP");
        out.setProjectId(null);
        out.setProjectName("Όλος ο Όμιλος");

        // Load all LIVE projects
        List<Project> liveProjects = projectRepository.findByStatus("LIVE");

        // --- Cost: sum of direct burns of LIVE projects + 100% of OpEx ---
        BigDecimal totalDirectBurn = BigDecimal.ZERO;
        BigDecimal totalCurrentMrr = BigDecimal.ZERO;
        Integer totalCurrentCustomers = 0;
        BigDecimal weightedCac = BigDecimal.ZERO;
        BigDecimal weightedGrossMargin = BigDecimal.ZERO;
        BigDecimal weightedMonthlyChurn = BigDecimal.ZERO;
        BigDecimal weightedAnnualChurn = BigDecimal.ZERO;
        BigDecimal weightedAnnualBilling = BigDecimal.ZERO;
        BigDecimal weightedAnnualDiscount = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;

        List<ProjectBreakdownEntry> breakdown = new ArrayList<>();

        for (Project p : liveProjects) {
            BigDecimal projectBurn = effectiveDirectBurn(p, burnByProject);
            totalDirectBurn = totalDirectBurn.add(projectBurn);

            BigDecimal mrr = nz(p.getCurrentMrr());
            totalCurrentMrr = totalCurrentMrr.add(mrr);

            int customers = p.getCurrentCustomers() == null ? 0 : p.getCurrentCustomers();
            totalCurrentCustomers += customers;

            // Weight CFO inputs by direct burn (heuristic)
            BigDecimal weight = projectBurn.max(BigDecimal.ONE);
            weightedCac          = weightedCac.add(nz(p.getCacPerCustomer()).multiply(weight));
            weightedGrossMargin  = weightedGrossMargin.add(nzDefault(p.getGrossMarginPct(), "75").multiply(weight));
            weightedMonthlyChurn = weightedMonthlyChurn.add(nzDefault(p.getMonthlyChurnPct(), "3").multiply(weight));
            weightedAnnualChurn  = weightedAnnualChurn.add(nzDefault(p.getAnnualChurnPct(), "0.5").multiply(weight));
            weightedAnnualBilling = weightedAnnualBilling.add(nz(p.getAnnualBillingPct()).multiply(weight));
            weightedAnnualDiscount = weightedAnnualDiscount.add(nzDefault(p.getAnnualDiscountPct(), "15").multiply(weight));
            weightSum = weightSum.add(weight);

            // Breakdown row (pct filled later when we know total)
            ProjectBreakdownEntry e = new ProjectBreakdownEntry();
            e.setProjectId(p.getId());
            e.setProjectName(p.getName());
            e.setStatus(p.getStatus());
            e.setDirectBurn(money(projectBurn));
            e.setCurrentMrr(money(mrr));
            e.setCurrentCustomers(customers);
            breakdown.add(e);
        }

        // Fill pctOfGroup
        for (ProjectBreakdownEntry e : breakdown) {
            if (totalDirectBurn.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pct = e.getDirectBurn()
                    .multiply(ONE_HUNDRED)
                    .divide(totalDirectBurn, PERCENT_SCALE, RoundingMode.HALF_UP);
                e.setPctOfGroup(pct);
            } else {
                e.setPctOfGroup(BigDecimal.ZERO);
            }
        }
        out.setProjectBreakdown(breakdown);

        // Total cost = direct burns of LIVE projects + 100% OpEx
        BigDecimal allocatedOpex = totalOpex;
        BigDecimal totalCost = totalDirectBurn.add(allocatedOpex);

        out.setDirectBurn(money(totalDirectBurn));
        out.setAllocatedOpex(money(allocatedOpex));
        out.setTotalCost(money(totalCost));

        // Blended inputs (weighted averages)
        BigDecimal blendedCac = weightSum.signum() > 0
            ? weightedCac.divide(weightSum, MONEY_SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal blendedGrossMargin = weightSum.signum() > 0
            ? weightedGrossMargin.divide(weightSum, PERCENT_SCALE, RoundingMode.HALF_UP)
            : new BigDecimal("75");
        BigDecimal blendedMonthlyChurn = weightSum.signum() > 0
            ? weightedMonthlyChurn.divide(weightSum, PERCENT_SCALE, RoundingMode.HALF_UP)
            : new BigDecimal("3");
        BigDecimal blendedAnnualChurn = weightSum.signum() > 0
            ? weightedAnnualChurn.divide(weightSum, PERCENT_SCALE, RoundingMode.HALF_UP)
            : new BigDecimal("0.5");
        BigDecimal blendedAnnualBilling = weightSum.signum() > 0
            ? weightedAnnualBilling.divide(weightSum, PERCENT_SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal blendedAnnualDiscount = weightSum.signum() > 0
            ? weightedAnnualDiscount.divide(weightSum, PERCENT_SCALE, RoundingMode.HALF_UP)
            : new BigDecimal("15");

        out.setCacPerCustomer(blendedCac);
        out.setGrossMarginPct(blendedGrossMargin);
        out.setMonthlyChurnPct(blendedMonthlyChurn);
        out.setAnnualChurnPct(blendedAnnualChurn);
        out.setAnnualBillingPct(blendedAnnualBilling);
        out.setAnnualDiscountPct(blendedAnnualDiscount);

        // Run shared math
        fillReversePricing(out, totalCost, totalCurrentMrr, totalCurrentCustomers, targetMargin);
        fillCfoMetrics(out, totalCurrentMrr, totalCurrentCustomers, blendedCac,
                       blendedGrossMargin, blendedMonthlyChurn, blendedAnnualChurn, blendedAnnualBilling);
        fillScenariosGroup(out, liveProjects);
        fillCashFlow(out, totalDirectBurn.add(allocatedOpex));
        fillPathToTarget(out);
    }

    // =====================================================================
    //  PROJECT MODE
    // =====================================================================

    private void buildProjectMode(PricingCalculatorResponse out,
                                  Project project,
                                  Map<UUID, BigDecimal> burnByProject,
                                  BigDecimal totalOpex,
                                  BigDecimal targetMargin) {
        out.setMode("PROJECT");
        out.setProjectId(project.getId());
        out.setProjectName(project.getName());

        BigDecimal directBurn = effectiveDirectBurn(project, burnByProject);

        // Allocated OpEx = totalOpex * (project.opexAllocationPct / 100)
        BigDecimal allocPct = nz(project.getOpexAllocationPct());
        BigDecimal allocatedOpex = totalOpex
            .multiply(allocPct)
            .divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalCost = directBurn.add(allocatedOpex);

        out.setDirectBurn(money(directBurn));
        out.setAllocatedOpex(money(allocatedOpex));
        out.setTotalCost(money(totalCost));

        // Project-level CFO inputs
        BigDecimal cac = nz(project.getCacPerCustomer());
        BigDecimal grossMargin = nzDefault(project.getGrossMarginPct(), "75");
        BigDecimal monthlyChurn = nzDefault(project.getMonthlyChurnPct(), "3");
        BigDecimal annualChurn  = nzDefault(project.getAnnualChurnPct(), "0.5");
        BigDecimal annualBilling = nz(project.getAnnualBillingPct());
        BigDecimal annualDiscount = nzDefault(project.getAnnualDiscountPct(), "15");

        out.setCacPerCustomer(cac);
        out.setGrossMarginPct(grossMargin);
        out.setMonthlyChurnPct(monthlyChurn);
        out.setAnnualChurnPct(annualChurn);
        out.setAnnualBillingPct(annualBilling);
        out.setAnnualDiscountPct(annualDiscount);

        BigDecimal currentMrr = nz(project.getCurrentMrr());
        Integer currentCustomers = project.getCurrentCustomers() == null ? 0 : project.getCurrentCustomers();

        fillReversePricing(out, totalCost, currentMrr, currentCustomers, targetMargin);
        fillCfoMetrics(out, currentMrr, currentCustomers, cac, grossMargin, monthlyChurn, annualChurn, annualBilling);
        fillScenariosProject(out);
        fillCashFlow(out, totalCost);
        fillPathToTarget(out);
    }

    // =====================================================================
    //  SHARED MATH BLOCKS
    // =====================================================================

    /**
     * Reverse pricing core: required revenue = totalCost / (1 - margin).
     * Also computes billing-mix adjustment (sticker price target).
     */
    private void fillReversePricing(PricingCalculatorResponse out,
                                    BigDecimal totalCost,
                                    BigDecimal currentMrr,
                                    Integer currentCustomers,
                                    BigDecimal targetMargin) {
        BigDecimal divisor = BigDecimal.ONE.subtract(targetMargin);
        BigDecimal requiredRevenue = divisor.signum() > 0
            ? totalCost.divide(divisor, MONEY_SCALE, RoundingMode.HALF_UP)
            : totalCost;

        BigDecimal profit = requiredRevenue.subtract(totalCost);
        BigDecimal gap = requiredRevenue.subtract(currentMrr);

        out.setRequiredRevenue(money(requiredRevenue));
        out.setProfitAtMargin(money(profit));
        out.setCurrentMrr(money(currentMrr));
        out.setGap(money(gap));
        out.setCurrentCustomers(currentCustomers == null ? 0 : currentCustomers);

        // Sticker price adjustment: revenue * (annualPct/100) * (discount/100)
        BigDecimal annualBillingPct = out.getAnnualBillingPct();
        BigDecimal annualDiscountPct = out.getAnnualDiscountPct();
        BigDecimal stickerAdjustment = requiredRevenue
            .multiply(annualBillingPct).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP)
            .multiply(annualDiscountPct).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal adjustedTarget = requiredRevenue.add(stickerAdjustment);

        out.setStickerAdjustment(money(stickerAdjustment));
        out.setAdjustedTarget(money(adjustedTarget));
    }

    /**
     * CFO metrics: ARPU, CAC Payback, LTV, LTV:CAC, churn-adjusted target, Rule of 40.
     */
    private void fillCfoMetrics(PricingCalculatorResponse out,
                                BigDecimal currentMrr,
                                Integer currentCustomers,
                                BigDecimal cac,
                                BigDecimal grossMarginPct,
                                BigDecimal monthlyChurnPct,
                                BigDecimal annualChurnPct,
                                BigDecimal annualBillingPct) {
        // ARPU
        BigDecimal arpu = (currentCustomers != null && currentCustomers > 0)
            ? currentMrr.divide(new BigDecimal(currentCustomers), MONEY_SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        out.setArpu(money(arpu));

        BigDecimal grossMarginRatio = grossMarginPct.divide(ONE_HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP);

        // CAC Payback = CAC / (ARPU * grossMargin%)
        BigDecimal divisor1 = arpu.multiply(grossMarginRatio);
        BigDecimal cacPayback = divisor1.signum() > 0
            ? cac.divide(divisor1, MONEY_SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        out.setCacPaybackMonths(cacPayback);

        // Blended churn (weighted by billing mix)
        BigDecimal monthlyShare = ONE_HUNDRED.subtract(annualBillingPct);
        BigDecimal blendedChurnPct = monthlyShare
            .multiply(monthlyChurnPct)
            .add(annualBillingPct.multiply(annualChurnPct))
            .divide(ONE_HUNDRED, PERCENT_SCALE, RoundingMode.HALF_UP);
        out.setEffectiveBlendedChurn(blendedChurnPct);

        BigDecimal blendedChurnRatio = blendedChurnPct.divide(ONE_HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP);

        // LTV = (ARPU * grossMargin%) / monthlyChurn%
        BigDecimal ltv = blendedChurnRatio.signum() > 0
            ? arpu.multiply(grossMarginRatio).divide(blendedChurnRatio, MONEY_SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        out.setLtv(money(ltv));

        // LTV:CAC
        BigDecimal ltvCacRatio = cac.signum() > 0
            ? ltv.divide(cac, RATIO_SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        out.setLtvCacRatio(ltvCacRatio);

        // Naive vs churn-adjusted target customers
        BigDecimal requiredRevenue = out.getRequiredRevenue();
        BigDecimal flatPrice = SCENARIO_A_FLAT_PRICE;
        int naiveTarget = flatPrice.signum() > 0
            ? requiredRevenue.divide(flatPrice, 0, RoundingMode.UP).intValue()
            : 0;
        out.setNaiveTargetCustomers(naiveTarget);

        // Churn-adjusted: naive / (1 - annualizedChurn)
        BigDecimal annualizedChurn = annualizedFromMonthly(blendedChurnRatio);
        BigDecimal divisor2 = BigDecimal.ONE.subtract(annualizedChurn);
        int churnAdjusted = divisor2.signum() > 0
            ? new BigDecimal(naiveTarget).divide(divisor2, 0, RoundingMode.UP).intValue()
            : naiveTarget;
        out.setChurnAdjustedTargetCustomers(churnAdjusted);
        out.setTargetCustomers(churnAdjusted);

        // Rule of 40: placeholder = (grossMargin - 50). Will refine with growth rate later.
        BigDecimal ruleOf40 = grossMarginPct.subtract(new BigDecimal("50"));
        out.setRuleOf40(ruleOf40);
    }

    /**
     * Project mode scenarios: A=Flat, B=Fixed customers, C=Tiered 3 plans.
     */
    private void fillScenariosProject(PricingCalculatorResponse out) {
        BigDecimal requiredRevenue = out.getRequiredRevenue();
        BigDecimal annualDiscount = out.getAnnualDiscountPct();

        // ---- Scenario A: Flat €99 ----
        ScenarioA a = new ScenarioA();
        a.setMonthlyPrice(SCENARIO_A_FLAT_PRICE);
        a.setAnnualPrice(annualPriceFromMonthly(SCENARIO_A_FLAT_PRICE, annualDiscount));
        a.setEffectiveMonthlyFromAnnual(effectiveMonthly(SCENARIO_A_FLAT_PRICE, annualDiscount));
        a.setCustomersNeeded(divCeil(requiredRevenue, SCENARIO_A_FLAT_PRICE));
        out.setScenarioA(a);

        // ---- Scenario B: Fixed N customers (current count, fallback 10) ----
        ScenarioB b = new ScenarioB();
        int fixedCustomers = out.getCurrentCustomers() != null && out.getCurrentCustomers() > 0
            ? out.getCurrentCustomers() : 10;
        b.setFixedCustomers(fixedCustomers);
        BigDecimal pricePerCustomer = requiredRevenue.divide(new BigDecimal(fixedCustomers), MONEY_SCALE, RoundingMode.HALF_UP);
        b.setPricePerCustomerMonthly(pricePerCustomer);
        b.setPricePerCustomerAnnual(annualPriceFromMonthly(pricePerCustomer, annualDiscount));
        b.setEffectiveMonthlyFromAnnual(effectiveMonthly(pricePerCustomer, annualDiscount));
        out.setScenarioB(b);

        // ---- Scenario C: Tiered (Starter/Pro/Enterprise) 60/30/10 ----
        ScenarioC c = new ScenarioC();
        c.setLabel("Tiered (3 πλάνα)");

        List<TierEntry> tiers = new ArrayList<>();
        tiers.add(buildTier("Starter",    TIER_STARTER_PRICE,    TIER_STARTER_WEIGHT,    annualDiscount, requiredRevenue));
        tiers.add(buildTier("Pro",        TIER_PRO_PRICE,        TIER_PRO_WEIGHT,        annualDiscount, requiredRevenue));
        tiers.add(buildTier("Enterprise", TIER_ENTERPRISE_PRICE, TIER_ENTERPRISE_WEIGHT, annualDiscount, requiredRevenue));
        c.setTiers(tiers);

        BigDecimal blendedPrice = TIER_STARTER_PRICE.multiply(TIER_STARTER_WEIGHT)
            .add(TIER_PRO_PRICE.multiply(TIER_PRO_WEIGHT))
            .add(TIER_ENTERPRISE_PRICE.multiply(TIER_ENTERPRISE_WEIGHT))
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        c.setBlendedPrice(blendedPrice);
        c.setCustomersNeeded(divCeil(requiredRevenue, blendedPrice));
        out.setScenarioC(c);
    }

    /**
     * Group mode scenarios: A=Flat across all, B=Fixed total customers, C=Per-Product Mix.
     */
    private void fillScenariosGroup(PricingCalculatorResponse out, List<Project> liveProjects) {
        BigDecimal requiredRevenue = out.getRequiredRevenue();
        BigDecimal annualDiscount = out.getAnnualDiscountPct();

        // ---- Scenario A: Flat €99 across all products ----
        ScenarioA a = new ScenarioA();
        a.setMonthlyPrice(SCENARIO_A_FLAT_PRICE);
        a.setAnnualPrice(annualPriceFromMonthly(SCENARIO_A_FLAT_PRICE, annualDiscount));
        a.setEffectiveMonthlyFromAnnual(effectiveMonthly(SCENARIO_A_FLAT_PRICE, annualDiscount));
        a.setCustomersNeeded(divCeil(requiredRevenue, SCENARIO_A_FLAT_PRICE));
        out.setScenarioA(a);

        // ---- Scenario B: Blended fixed-customer price ----
        ScenarioB b = new ScenarioB();
        int fixedCustomers = out.getCurrentCustomers() != null && out.getCurrentCustomers() > 0
            ? out.getCurrentCustomers() : 10;
        b.setFixedCustomers(fixedCustomers);
        BigDecimal blendedPrice = requiredRevenue.divide(new BigDecimal(fixedCustomers), MONEY_SCALE, RoundingMode.HALF_UP);
        b.setPricePerCustomerMonthly(blendedPrice);
        b.setPricePerCustomerAnnual(annualPriceFromMonthly(blendedPrice, annualDiscount));
        b.setEffectiveMonthlyFromAnnual(effectiveMonthly(blendedPrice, annualDiscount));
        out.setScenarioB(b);

        // ---- Scenario C: Per-Product Mix ----
        ScenarioC c = new ScenarioC();
        c.setLabel("Per-Product Mix");

        // Default per-product prices: bump prices for sales-led products (could be config later)
        BigDecimal[] productPrices = new BigDecimal[] {
            new BigDecimal("99"),
            new BigDecimal("149"),
            new BigDecimal("249")
        };

        List<TierEntry> tiers = new ArrayList<>();
        BigDecimal sumWeightedPrice = BigDecimal.ZERO;
        BigDecimal totalCustomers = BigDecimal.ZERO;
        int idx = 0;

        for (Project p : liveProjects) {
            BigDecimal price = productPrices[Math.min(idx, productPrices.length - 1)];
            idx++;
            BigDecimal weight = BigDecimal.ONE.divide(new BigDecimal(Math.max(liveProjects.size(), 1)), RATIO_SCALE, RoundingMode.HALF_UP);
            BigDecimal projectRevenue = requiredRevenue.multiply(weight);
            int customersForProject = divCeil(projectRevenue, price);

            TierEntry t = new TierEntry();
            t.setName(p.getName());
            t.setMonthlyPrice(price);
            t.setAnnualPrice(annualPriceFromMonthly(price, annualDiscount));
            t.setWeight(weight);
            t.setCustomersNeeded(customersForProject);
            tiers.add(t);

            sumWeightedPrice = sumWeightedPrice.add(price.multiply(weight));
            totalCustomers = totalCustomers.add(new BigDecimal(customersForProject));
        }

        c.setTiers(tiers);
        c.setBlendedPrice(sumWeightedPrice.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        c.setCustomersNeeded(totalCustomers.intValue());
        out.setScenarioC(c);
    }

    /**
     * Cash flow impact: cash upfront from annual prepayments, monthly recurring, runway boost.
     */
    private void fillCashFlow(PricingCalculatorResponse out, BigDecimal monthlyBurn) {
        BigDecimal annualBillingPct = out.getAnnualBillingPct();
        if (annualBillingPct.signum() == 0) {
            // No annual contracts -- no cash flow boost
            out.setCashUpfront(BigDecimal.ZERO);
            out.setMonthlyRecurring(money(out.getRequiredRevenue()));
            out.setTotalAnnualized(money(out.getRequiredRevenue().multiply(TWELVE)));
            out.setRunwayBoostMonths(BigDecimal.ZERO);
            return;
        }

        // Use Scenario A customer count as basis (most representative)
        ScenarioA a = out.getScenarioA();
        int customers = a != null && a.getCustomersNeeded() != null ? a.getCustomersNeeded() : 0;

        BigDecimal annualShare = annualBillingPct.divide(ONE_HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP);
        BigDecimal monthlyShare = BigDecimal.ONE.subtract(annualShare);

        BigDecimal annualPrice = a != null && a.getAnnualPrice() != null ? a.getAnnualPrice() : BigDecimal.ZERO;
        BigDecimal monthlyPrice = a != null && a.getMonthlyPrice() != null ? a.getMonthlyPrice() : BigDecimal.ZERO;

        BigDecimal cashUpfront = new BigDecimal(customers)
            .multiply(annualShare)
            .multiply(annualPrice);
        BigDecimal monthlyRecurring = new BigDecimal(customers)
            .multiply(monthlyShare)
            .multiply(monthlyPrice);

        BigDecimal totalAnnualized = cashUpfront.add(monthlyRecurring.multiply(TWELVE));

        BigDecimal runwayBoost = monthlyBurn.signum() > 0
            ? cashUpfront.divide(monthlyBurn, MONEY_SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        out.setCashUpfront(money(cashUpfront));
        out.setMonthlyRecurring(money(monthlyRecurring));
        out.setTotalAnnualized(money(totalAnnualized));
        out.setRunwayBoostMonths(runwayBoost);
    }

    /**
     * Path to target: months until break-even at default acquisition rate.
     * Uses heuristic: 10 new customers per month, factoring in churn.
     */
    private void fillPathToTarget(PricingCalculatorResponse out) {
        int target = out.getTargetCustomers() == null ? 0 : out.getTargetCustomers();
        int current = out.getCurrentCustomers() == null ? 0 : out.getCurrentCustomers();
        BigDecimal monthlyChurnRatio = out.getEffectiveBlendedChurn()
            .divide(ONE_HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP);

        BigDecimal acquisitionRate = new BigDecimal("10");  // default heuristic
        BigDecimal needed = new BigDecimal(Math.max(target - current, 0));

        // Net new = acquisition - (current * churn)
        BigDecimal churnLoss = new BigDecimal(current).multiply(monthlyChurnRatio);
        BigDecimal netNew = acquisitionRate.subtract(churnLoss);

        BigDecimal months = netNew.signum() > 0
            ? needed.divide(netNew, MONEY_SCALE, RoundingMode.HALF_UP)
            : new BigDecimal("999");
        out.setMonthsToBreakEven(months);

        // All-monthly comparison: more churn -> longer path
        BigDecimal allMonthlyChurnRatio = nzDefault(out.getMonthlyChurnPct(), "3")
            .divide(ONE_HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP);
        BigDecimal allMonthlyChurnLoss = new BigDecimal(current).multiply(allMonthlyChurnRatio);
        BigDecimal allMonthlyNetNew = acquisitionRate.subtract(allMonthlyChurnLoss);
        BigDecimal allMonthlyMonths = allMonthlyNetNew.signum() > 0
            ? needed.divide(allMonthlyNetNew, MONEY_SCALE, RoundingMode.HALF_UP)
            : new BigDecimal("999");
        out.setMonthsToBreakEvenAllMonthly(allMonthlyMonths);
    }

    // =====================================================================
    //  AUTO-COMPUTE: Burn by Project
    // =====================================================================

    /**
     * Compute monthly burn per project (key = projectId, null = OpEx).
     *
     * Source: PLANNED + isRecurring=true mother transactions, normalized by
     * their RecurrencePattern frequency to a monthly rate.
     */
    Map<UUID, BigDecimal> computeBurnByProject() {
        Map<UUID, BigDecimal> burn = new HashMap<>();

        // Load all patterns once
        Map<UUID, RecurrencePattern> patternById = new HashMap<>();
        for (RecurrencePattern p : recurrencePatternRepository.findActiveAsOf(LocalDate.now())) {
            patternById.put(p.getId(), p);
        }

        // Get all transactions; we filter in code for clarity. For scale, push to repo query.
        List<Transaction> all = transactionRepository.findAll();
        for (Transaction t : all) {
            if (!"PLANNED".equalsIgnoreCase(t.getEntryMode())) continue;
            if (t.getIsRecurring() == null || !t.getIsRecurring()) continue;
            if (t.getRecurrencePatternId() == null) continue;
            // Only expense rows contribute to burn (income would be revenue)
            if (t.getAmount() == null || t.getAmount().signum() <= 0) continue;
            // Filter on transaction type/income vs expense if such field exists
            // (Transaction has type/income/expense via category typically -- skipping here as project_id is what matters)

            RecurrencePattern pat = patternById.get(t.getRecurrencePatternId());
            if (pat == null) continue;

            BigDecimal monthly = normalizeToMonthly(t.getAmount(), pat);
            UUID projKey = t.getProjectId();  // null = OpEx

            burn.merge(projKey, monthly, BigDecimal::add);
        }

        return burn;
    }

    /**
     * Normalize a pattern instance to a monthly amount.
     *
     * Heuristic:
     *   DAILY     : amount * 30 / intervalCount
     *   WEEKLY    : amount * 4.33 / intervalCount
     *   MONTHLY   : amount / intervalCount
     *   QUARTERLY : amount / (3 * intervalCount)
     *   YEARLY    : amount / (12 * intervalCount)
     *   CUSTOM    : amount (conservative -- treat as monthly)
     */
    BigDecimal normalizeToMonthly(BigDecimal amount, RecurrencePattern pat) {
        String freq = pat.getFrequency() == null ? "MONTHLY" : pat.getFrequency().toUpperCase();
        Integer interval = pat.getIntervalCount() == null ? 1 : pat.getIntervalCount();
        BigDecimal intervalBD = new BigDecimal(Math.max(interval, 1));

        BigDecimal result;
        switch (freq) {
            case "DAILY":
                result = amount.multiply(new BigDecimal("30"))
                    .divide(intervalBD, MONEY_SCALE, RoundingMode.HALF_UP);
                break;
            case "WEEKLY":
                result = amount.multiply(new BigDecimal("4.33"))
                    .divide(intervalBD, MONEY_SCALE, RoundingMode.HALF_UP);
                break;
            case "QUARTERLY":
                result = amount.divide(intervalBD.multiply(new BigDecimal("3")), MONEY_SCALE, RoundingMode.HALF_UP);
                break;
            case "YEARLY":
                result = amount.divide(intervalBD.multiply(TWELVE), MONEY_SCALE, RoundingMode.HALF_UP);
                break;
            case "MONTHLY":
            default:
                result = amount.divide(intervalBD, MONEY_SCALE, RoundingMode.HALF_UP);
                break;
        }
        return result;
    }

    /**
     * Effective direct burn for a project: manual override if set, else auto from patterns.
     */
    BigDecimal effectiveDirectBurn(Project p, Map<UUID, BigDecimal> burnByProject) {
        if (p.getDirectBurnMonthly() != null && p.getDirectBurnMonthly().signum() > 0) {
            return p.getDirectBurnMonthly();
        }
        return burnByProject.getOrDefault(p.getId(), BigDecimal.ZERO);
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================

    private TierEntry buildTier(String name, BigDecimal price, BigDecimal weight,
                                 BigDecimal annualDiscount, BigDecimal requiredRevenue) {
        TierEntry t = new TierEntry();
        t.setName(name);
        t.setMonthlyPrice(price);
        t.setAnnualPrice(annualPriceFromMonthly(price, annualDiscount));
        t.setWeight(weight);
        BigDecimal share = requiredRevenue.multiply(weight);
        t.setCustomersNeeded(divCeil(share, price));
        return t;
    }

    private BigDecimal annualPriceFromMonthly(BigDecimal monthlyPrice, BigDecimal annualDiscountPct) {
        BigDecimal discountRatio = annualDiscountPct.divide(ONE_HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP);
        return monthlyPrice.multiply(TWELVE)
            .multiply(BigDecimal.ONE.subtract(discountRatio))
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal effectiveMonthly(BigDecimal monthlyPrice, BigDecimal annualDiscountPct) {
        BigDecimal discountRatio = annualDiscountPct.divide(ONE_HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP);
        return monthlyPrice
            .multiply(BigDecimal.ONE.subtract(discountRatio))
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Annualized churn from monthly: 1 - (1 - monthlyChurn)^12.
     * Simplified via approximation for small monthly churn:
     *   annualized ~= 1 - (1 - 12*monthly)  for very small monthly (linear approx)
     * For accuracy at higher churn, we iterate.
     */
    private BigDecimal annualizedFromMonthly(BigDecimal monthlyRatio) {
        BigDecimal retention = BigDecimal.ONE.subtract(monthlyRatio);
        BigDecimal cum = BigDecimal.ONE;
        for (int i = 0; i < 12; i++) {
            cum = cum.multiply(retention);
        }
        return BigDecimal.ONE.subtract(cum).setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private int divCeil(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) return 0;
        return numerator.divide(denominator, 0, RoundingMode.UP).intValue();
    }

    private BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal nzDefault(BigDecimal v, String defaultStr) {
        return v == null ? new BigDecimal(defaultStr) : v;
    }

    private BigDecimal clamp(BigDecimal v, BigDecimal min, BigDecimal max) {
        if (v.compareTo(min) < 0) return min;
        if (v.compareTo(max) > 0) return max;
        return v;
    }
}
