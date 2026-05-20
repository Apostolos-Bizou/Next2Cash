package com.next2me.next2cash.dto;

import java.math.BigDecimal;

/**
 * DTO for updating Pricing Calculator config on a project.
 * Used by PUT /api/projects/{id}/pricing-config (S86.5).
 *
 * All fields nullable -- service only updates non-null fields (partial update).
 */
public class PricingConfigDTO {

    private BigDecimal directBurnMonthly;
    private BigDecimal opexAllocationPct;
    private BigDecimal currentMrr;
    private Integer    currentCustomers;
    private BigDecimal cacPerCustomer;
    private BigDecimal grossMarginPct;
    private BigDecimal monthlyChurnPct;
    private BigDecimal annualBillingPct;
    private BigDecimal annualDiscountPct;
    private BigDecimal annualChurnPct;

    public PricingConfigDTO() {}

    public BigDecimal getDirectBurnMonthly() { return directBurnMonthly; }
    public void setDirectBurnMonthly(BigDecimal v) { this.directBurnMonthly = v; }

    public BigDecimal getOpexAllocationPct() { return opexAllocationPct; }
    public void setOpexAllocationPct(BigDecimal v) { this.opexAllocationPct = v; }

    public BigDecimal getCurrentMrr() { return currentMrr; }
    public void setCurrentMrr(BigDecimal v) { this.currentMrr = v; }

    public Integer getCurrentCustomers() { return currentCustomers; }
    public void setCurrentCustomers(Integer v) { this.currentCustomers = v; }

    public BigDecimal getCacPerCustomer() { return cacPerCustomer; }
    public void setCacPerCustomer(BigDecimal v) { this.cacPerCustomer = v; }

    public BigDecimal getGrossMarginPct() { return grossMarginPct; }
    public void setGrossMarginPct(BigDecimal v) { this.grossMarginPct = v; }

    public BigDecimal getMonthlyChurnPct() { return monthlyChurnPct; }
    public void setMonthlyChurnPct(BigDecimal v) { this.monthlyChurnPct = v; }

    public BigDecimal getAnnualBillingPct() { return annualBillingPct; }
    public void setAnnualBillingPct(BigDecimal v) { this.annualBillingPct = v; }

    public BigDecimal getAnnualDiscountPct() { return annualDiscountPct; }
    public void setAnnualDiscountPct(BigDecimal v) { this.annualDiscountPct = v; }

    public BigDecimal getAnnualChurnPct() { return annualChurnPct; }
    public void setAnnualChurnPct(BigDecimal v) { this.annualChurnPct = v; }
}
