package com.next2me.next2cash.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Composite response for GET /api/pricing-calculator (S86.5).
 *
 * Contains all fields needed by the Pricing Calculator UI:
 *  - Mode metadata (group/project)
 *  - Inputs echo (targetMargin, etc.)
 *  - Cost breakdown
 *  - Reverse pricing math
 *  - Billing mix adjustment
 *  - CFO metrics (CAC payback, LTV:CAC, Rule of 40, churn-adjusted)
 *  - 3 pricing scenarios (Flat / Fixed / Tiered or Per-Product)
 *  - Cash flow impact (annual prepay benefit)
 *  - Path to target narrative numbers
 *  - Group-only: per-project breakdown
 */
public class PricingCalculatorResponse {

    // ============ Mode & Echo ============
    private String mode;             // "GROUP" or "PROJECT"
    private UUID projectId;          // null in GROUP mode
    private String projectName;      // "Όλος ο Όμιλος" in GROUP mode
    private BigDecimal targetMargin; // 0.05-0.50 (e.g. 0.15)
    private BigDecimal grossMarginPct;
    private BigDecimal monthlyChurnPct;
    private BigDecimal annualChurnPct;
    private BigDecimal annualBillingPct;
    private BigDecimal annualDiscountPct;
    private BigDecimal cacPerCustomer;

    // ============ Cost Breakdown ============
    private BigDecimal directBurn;
    private BigDecimal allocatedOpex;
    private BigDecimal totalCost;

    // ============ Reverse Pricing ============
    private BigDecimal requiredRevenue;
    private BigDecimal profitAtMargin;
    private BigDecimal currentMrr;
    private BigDecimal gap;

    // ============ Billing Mix Adjustment ============
    private BigDecimal stickerAdjustment;  // εκπτωτικό extra
    private BigDecimal adjustedTarget;     // sticker price target with annual discount accounted
    private BigDecimal effectiveBlendedChurn;

    // ============ CFO Metrics ============
    private BigDecimal arpu;
    private BigDecimal cacPaybackMonths;
    private BigDecimal ltv;
    private BigDecimal ltvCacRatio;
    private Integer    churnAdjustedTargetCustomers;
    private Integer    naiveTargetCustomers;
    private BigDecimal ruleOf40;  // growth% + margin% (placeholder calculation)

    // ============ Scenarios ============
    private ScenarioA scenarioA;
    private ScenarioB scenarioB;
    private ScenarioC scenarioC;

    // ============ Cash Flow Impact ============
    private BigDecimal cashUpfront;
    private BigDecimal monthlyRecurring;
    private BigDecimal totalAnnualized;
    private BigDecimal runwayBoostMonths;

    // ============ Path to Target ============
    private Integer currentCustomers;
    private Integer targetCustomers;
    private BigDecimal monthsToBreakEven;
    private BigDecimal monthsToBreakEvenAllMonthly;  // για comparison

    // ============ Group-Only: Per-Project Breakdown ============
    private List<ProjectBreakdownEntry> projectBreakdown;

    // ============ Inner classes ============

    public static class ScenarioA {
        private BigDecimal monthlyPrice;       // 99
        private BigDecimal annualPrice;        // 1010 (12 * 99 * 0.85)
        private Integer    customersNeeded;
        private BigDecimal effectiveMonthlyFromAnnual;
        public BigDecimal getMonthlyPrice() { return monthlyPrice; }
        public void setMonthlyPrice(BigDecimal v) { this.monthlyPrice = v; }
        public BigDecimal getAnnualPrice() { return annualPrice; }
        public void setAnnualPrice(BigDecimal v) { this.annualPrice = v; }
        public Integer getCustomersNeeded() { return customersNeeded; }
        public void setCustomersNeeded(Integer v) { this.customersNeeded = v; }
        public BigDecimal getEffectiveMonthlyFromAnnual() { return effectiveMonthlyFromAnnual; }
        public void setEffectiveMonthlyFromAnnual(BigDecimal v) { this.effectiveMonthlyFromAnnual = v; }
    }

    public static class ScenarioB {
        private Integer    fixedCustomers;
        private BigDecimal pricePerCustomerMonthly;
        private BigDecimal pricePerCustomerAnnual;
        private BigDecimal effectiveMonthlyFromAnnual;
        public Integer getFixedCustomers() { return fixedCustomers; }
        public void setFixedCustomers(Integer v) { this.fixedCustomers = v; }
        public BigDecimal getPricePerCustomerMonthly() { return pricePerCustomerMonthly; }
        public void setPricePerCustomerMonthly(BigDecimal v) { this.pricePerCustomerMonthly = v; }
        public BigDecimal getPricePerCustomerAnnual() { return pricePerCustomerAnnual; }
        public void setPricePerCustomerAnnual(BigDecimal v) { this.pricePerCustomerAnnual = v; }
        public BigDecimal getEffectiveMonthlyFromAnnual() { return effectiveMonthlyFromAnnual; }
        public void setEffectiveMonthlyFromAnnual(BigDecimal v) { this.effectiveMonthlyFromAnnual = v; }
    }

    public static class ScenarioC {
        // For PROJECT mode = 3 tiers; for GROUP mode = per-product prices
        private String label;  // "Tiered (3 πλάνα)" or "Per-Product Mix"
        private List<TierEntry> tiers;
        private BigDecimal blendedPrice;
        private Integer    customersNeeded;
        public String getLabel() { return label; }
        public void setLabel(String v) { this.label = v; }
        public List<TierEntry> getTiers() { return tiers; }
        public void setTiers(List<TierEntry> v) { this.tiers = v; }
        public BigDecimal getBlendedPrice() { return blendedPrice; }
        public void setBlendedPrice(BigDecimal v) { this.blendedPrice = v; }
        public Integer getCustomersNeeded() { return customersNeeded; }
        public void setCustomersNeeded(Integer v) { this.customersNeeded = v; }
    }

    public static class TierEntry {
        private String name;
        private BigDecimal monthlyPrice;
        private BigDecimal annualPrice;
        private BigDecimal weight;   // mix percentage 0-1
        private Integer customersNeeded;
        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public BigDecimal getMonthlyPrice() { return monthlyPrice; }
        public void setMonthlyPrice(BigDecimal v) { this.monthlyPrice = v; }
        public BigDecimal getAnnualPrice() { return annualPrice; }
        public void setAnnualPrice(BigDecimal v) { this.annualPrice = v; }
        public BigDecimal getWeight() { return weight; }
        public void setWeight(BigDecimal v) { this.weight = v; }
        public Integer getCustomersNeeded() { return customersNeeded; }
        public void setCustomersNeeded(Integer v) { this.customersNeeded = v; }
    }

    public static class ProjectBreakdownEntry {
        private UUID projectId;
        private String projectName;
        private String status;
        private BigDecimal directBurn;
        private BigDecimal pctOfGroup;
        private BigDecimal currentMrr;
        private Integer currentCustomers;
        public UUID getProjectId() { return projectId; }
        public void setProjectId(UUID v) { this.projectId = v; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String v) { this.projectName = v; }
        public String getStatus() { return status; }
        public void setStatus(String v) { this.status = v; }
        public BigDecimal getDirectBurn() { return directBurn; }
        public void setDirectBurn(BigDecimal v) { this.directBurn = v; }
        public BigDecimal getPctOfGroup() { return pctOfGroup; }
        public void setPctOfGroup(BigDecimal v) { this.pctOfGroup = v; }
        public BigDecimal getCurrentMrr() { return currentMrr; }
        public void setCurrentMrr(BigDecimal v) { this.currentMrr = v; }
        public Integer getCurrentCustomers() { return currentCustomers; }
        public void setCurrentCustomers(Integer v) { this.currentCustomers = v; }
    }

    // ============ Outer Getters/Setters ============
    public String getMode() { return mode; }
    public void setMode(String v) { this.mode = v; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID v) { this.projectId = v; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String v) { this.projectName = v; }
    public BigDecimal getTargetMargin() { return targetMargin; }
    public void setTargetMargin(BigDecimal v) { this.targetMargin = v; }
    public BigDecimal getGrossMarginPct() { return grossMarginPct; }
    public void setGrossMarginPct(BigDecimal v) { this.grossMarginPct = v; }
    public BigDecimal getMonthlyChurnPct() { return monthlyChurnPct; }
    public void setMonthlyChurnPct(BigDecimal v) { this.monthlyChurnPct = v; }
    public BigDecimal getAnnualChurnPct() { return annualChurnPct; }
    public void setAnnualChurnPct(BigDecimal v) { this.annualChurnPct = v; }
    public BigDecimal getAnnualBillingPct() { return annualBillingPct; }
    public void setAnnualBillingPct(BigDecimal v) { this.annualBillingPct = v; }
    public BigDecimal getAnnualDiscountPct() { return annualDiscountPct; }
    public void setAnnualDiscountPct(BigDecimal v) { this.annualDiscountPct = v; }
    public BigDecimal getCacPerCustomer() { return cacPerCustomer; }
    public void setCacPerCustomer(BigDecimal v) { this.cacPerCustomer = v; }
    public BigDecimal getDirectBurn() { return directBurn; }
    public void setDirectBurn(BigDecimal v) { this.directBurn = v; }
    public BigDecimal getAllocatedOpex() { return allocatedOpex; }
    public void setAllocatedOpex(BigDecimal v) { this.allocatedOpex = v; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal v) { this.totalCost = v; }
    public BigDecimal getRequiredRevenue() { return requiredRevenue; }
    public void setRequiredRevenue(BigDecimal v) { this.requiredRevenue = v; }
    public BigDecimal getProfitAtMargin() { return profitAtMargin; }
    public void setProfitAtMargin(BigDecimal v) { this.profitAtMargin = v; }
    public BigDecimal getCurrentMrr() { return currentMrr; }
    public void setCurrentMrr(BigDecimal v) { this.currentMrr = v; }
    public BigDecimal getGap() { return gap; }
    public void setGap(BigDecimal v) { this.gap = v; }
    public BigDecimal getStickerAdjustment() { return stickerAdjustment; }
    public void setStickerAdjustment(BigDecimal v) { this.stickerAdjustment = v; }
    public BigDecimal getAdjustedTarget() { return adjustedTarget; }
    public void setAdjustedTarget(BigDecimal v) { this.adjustedTarget = v; }
    public BigDecimal getEffectiveBlendedChurn() { return effectiveBlendedChurn; }
    public void setEffectiveBlendedChurn(BigDecimal v) { this.effectiveBlendedChurn = v; }
    public BigDecimal getArpu() { return arpu; }
    public void setArpu(BigDecimal v) { this.arpu = v; }
    public BigDecimal getCacPaybackMonths() { return cacPaybackMonths; }
    public void setCacPaybackMonths(BigDecimal v) { this.cacPaybackMonths = v; }
    public BigDecimal getLtv() { return ltv; }
    public void setLtv(BigDecimal v) { this.ltv = v; }
    public BigDecimal getLtvCacRatio() { return ltvCacRatio; }
    public void setLtvCacRatio(BigDecimal v) { this.ltvCacRatio = v; }
    public Integer getChurnAdjustedTargetCustomers() { return churnAdjustedTargetCustomers; }
    public void setChurnAdjustedTargetCustomers(Integer v) { this.churnAdjustedTargetCustomers = v; }
    public Integer getNaiveTargetCustomers() { return naiveTargetCustomers; }
    public void setNaiveTargetCustomers(Integer v) { this.naiveTargetCustomers = v; }
    public BigDecimal getRuleOf40() { return ruleOf40; }
    public void setRuleOf40(BigDecimal v) { this.ruleOf40 = v; }
    public ScenarioA getScenarioA() { return scenarioA; }
    public void setScenarioA(ScenarioA v) { this.scenarioA = v; }
    public ScenarioB getScenarioB() { return scenarioB; }
    public void setScenarioB(ScenarioB v) { this.scenarioB = v; }
    public ScenarioC getScenarioC() { return scenarioC; }
    public void setScenarioC(ScenarioC v) { this.scenarioC = v; }
    public BigDecimal getCashUpfront() { return cashUpfront; }
    public void setCashUpfront(BigDecimal v) { this.cashUpfront = v; }
    public BigDecimal getMonthlyRecurring() { return monthlyRecurring; }
    public void setMonthlyRecurring(BigDecimal v) { this.monthlyRecurring = v; }
    public BigDecimal getTotalAnnualized() { return totalAnnualized; }
    public void setTotalAnnualized(BigDecimal v) { this.totalAnnualized = v; }
    public BigDecimal getRunwayBoostMonths() { return runwayBoostMonths; }
    public void setRunwayBoostMonths(BigDecimal v) { this.runwayBoostMonths = v; }
    public Integer getCurrentCustomers() { return currentCustomers; }
    public void setCurrentCustomers(Integer v) { this.currentCustomers = v; }
    public Integer getTargetCustomers() { return targetCustomers; }
    public void setTargetCustomers(Integer v) { this.targetCustomers = v; }
    public BigDecimal getMonthsToBreakEven() { return monthsToBreakEven; }
    public void setMonthsToBreakEven(BigDecimal v) { this.monthsToBreakEven = v; }
    public BigDecimal getMonthsToBreakEvenAllMonthly() { return monthsToBreakEvenAllMonthly; }
    public void setMonthsToBreakEvenAllMonthly(BigDecimal v) { this.monthsToBreakEvenAllMonthly = v; }
    public List<ProjectBreakdownEntry> getProjectBreakdown() { return projectBreakdown; }
    public void setProjectBreakdown(List<ProjectBreakdownEntry> v) { this.projectBreakdown = v; }
}
