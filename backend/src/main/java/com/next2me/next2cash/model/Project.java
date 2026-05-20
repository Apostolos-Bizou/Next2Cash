package com.next2me.next2cash.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Project entity representing a product/initiative of the Group.
 * Each transaction may be linked to a project via transactions.project_id.
 * NULL project_id = OpEx (general company expense).
 *
 * Spec ref: CashPlanning TechSpec v1.0 section 4.4
 * S86 extension: Pricing Calculator + AI CFO Advisor fields (10 new columns).
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_entity_id", nullable = false)
    private UUID ownerEntityId;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PLANNING";

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_completion_date")
    private LocalDate targetCompletionDate;

    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;

    @Column(name = "total_budget", precision = 15, scale = 2)
    private BigDecimal totalBudget = BigDecimal.ZERO;

    @Column(name = "expected_monthly_revenue", precision = 15, scale = 2)
    private BigDecimal expectedMonthlyRevenue = BigDecimal.ZERO;

    @Column(name = "color", length = 7)
    private String color = "#3B82F6";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    // ============================================================
    // S86 Pricing Calculator fields
    // ============================================================

    /** Manual override for monthly direct burn. If NULL, auto-computed from recurrence patterns. */
    @Column(name = "direct_burn_monthly", precision = 15, scale = 2)
    private BigDecimal directBurnMonthly;

    /** Manual % of total OpEx allocated to this project. 0-100. */
    @Column(name = "opex_allocation_pct", precision = 5, scale = 2)
    private BigDecimal opexAllocationPct = BigDecimal.ZERO;

    /** Current Monthly Recurring Revenue from this project (EUR). */
    @Column(name = "current_mrr", precision = 15, scale = 2)
    private BigDecimal currentMrr = BigDecimal.ZERO;

    /** Current paying customers count for this project. */
    @Column(name = "current_customers")
    private Integer currentCustomers = 0;

    /** Customer Acquisition Cost in EUR per customer. */
    @Column(name = "cac_per_customer", precision = 15, scale = 2)
    private BigDecimal cacPerCustomer = BigDecimal.ZERO;

    /** Gross margin % (revenue minus COGS / revenue). SaaS default 75%. */
    @Column(name = "gross_margin_pct", precision = 5, scale = 2)
    private BigDecimal grossMarginPct = new BigDecimal("75.00");

    /** Monthly customer churn % for monthly-billing customers. */
    @Column(name = "monthly_churn_pct", precision = 5, scale = 2)
    private BigDecimal monthlyChurnPct = new BigDecimal("3.00");

    // ============================================================
    // S86 Billing Mix fields (Annual vs Monthly contracts)
    // ============================================================

    /** % of customers on annual prepay contracts. Affects effective blended churn + cash flow. */
    @Column(name = "annual_billing_pct", precision = 5, scale = 2)
    private BigDecimal annualBillingPct = BigDecimal.ZERO;

    /** % discount given for annual prepay (vs monthly sticker). Default 15%. */
    @Column(name = "annual_discount_pct", precision = 5, scale = 2)
    private BigDecimal annualDiscountPct = new BigDecimal("15.00");

    /** Annual contract churn % (renewal failures). Default 0.5%. */
    @Column(name = "annual_churn_pct", precision = 5, scale = 2)
    private BigDecimal annualChurnPct = new BigDecimal("0.50");

    @PrePersist
    protected void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = "PLANNING";
        if (this.totalBudget == null) this.totalBudget = BigDecimal.ZERO;
        if (this.expectedMonthlyRevenue == null) this.expectedMonthlyRevenue = BigDecimal.ZERO;
        if (this.color == null) this.color = "#3B82F6";
        if (this.isActive == null) this.isActive = Boolean.TRUE;
        // S86 defaults
        if (this.opexAllocationPct == null) this.opexAllocationPct = BigDecimal.ZERO;
        if (this.currentMrr == null) this.currentMrr = BigDecimal.ZERO;
        if (this.currentCustomers == null) this.currentCustomers = 0;
        if (this.cacPerCustomer == null) this.cacPerCustomer = BigDecimal.ZERO;
        if (this.grossMarginPct == null) this.grossMarginPct = new BigDecimal("75.00");
        if (this.monthlyChurnPct == null) this.monthlyChurnPct = new BigDecimal("3.00");
        if (this.annualBillingPct == null) this.annualBillingPct = BigDecimal.ZERO;
        if (this.annualDiscountPct == null) this.annualDiscountPct = new BigDecimal("15.00");
        if (this.annualChurnPct == null) this.annualChurnPct = new BigDecimal("0.50");
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getOwnerEntityId() { return ownerEntityId; }
    public void setOwnerEntityId(UUID ownerEntityId) { this.ownerEntityId = ownerEntityId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getTargetCompletionDate() { return targetCompletionDate; }
    public void setTargetCompletionDate(LocalDate targetCompletionDate) { this.targetCompletionDate = targetCompletionDate; }
    public LocalDate getActualCompletionDate() { return actualCompletionDate; }
    public void setActualCompletionDate(LocalDate actualCompletionDate) { this.actualCompletionDate = actualCompletionDate; }
    public BigDecimal getTotalBudget() { return totalBudget; }
    public void setTotalBudget(BigDecimal totalBudget) { this.totalBudget = totalBudget; }
    public BigDecimal getExpectedMonthlyRevenue() { return expectedMonthlyRevenue; }
    public void setExpectedMonthlyRevenue(BigDecimal expectedMonthlyRevenue) { this.expectedMonthlyRevenue = expectedMonthlyRevenue; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { this.isActive = active; }

    // S86 Pricing Calculator getters/setters
    public BigDecimal getDirectBurnMonthly() { return directBurnMonthly; }
    public void setDirectBurnMonthly(BigDecimal directBurnMonthly) { this.directBurnMonthly = directBurnMonthly; }
    public BigDecimal getOpexAllocationPct() { return opexAllocationPct; }
    public void setOpexAllocationPct(BigDecimal opexAllocationPct) { this.opexAllocationPct = opexAllocationPct; }
    public BigDecimal getCurrentMrr() { return currentMrr; }
    public void setCurrentMrr(BigDecimal currentMrr) { this.currentMrr = currentMrr; }
    public Integer getCurrentCustomers() { return currentCustomers; }
    public void setCurrentCustomers(Integer currentCustomers) { this.currentCustomers = currentCustomers; }
    public BigDecimal getCacPerCustomer() { return cacPerCustomer; }
    public void setCacPerCustomer(BigDecimal cacPerCustomer) { this.cacPerCustomer = cacPerCustomer; }
    public BigDecimal getGrossMarginPct() { return grossMarginPct; }
    public void setGrossMarginPct(BigDecimal grossMarginPct) { this.grossMarginPct = grossMarginPct; }
    public BigDecimal getMonthlyChurnPct() { return monthlyChurnPct; }
    public void setMonthlyChurnPct(BigDecimal monthlyChurnPct) { this.monthlyChurnPct = monthlyChurnPct; }

    // S86 Billing Mix getters/setters
    public BigDecimal getAnnualBillingPct() { return annualBillingPct; }
    public void setAnnualBillingPct(BigDecimal annualBillingPct) { this.annualBillingPct = annualBillingPct; }
    public BigDecimal getAnnualDiscountPct() { return annualDiscountPct; }
    public void setAnnualDiscountPct(BigDecimal annualDiscountPct) { this.annualDiscountPct = annualDiscountPct; }
    public BigDecimal getAnnualChurnPct() { return annualChurnPct; }
    public void setAnnualChurnPct(BigDecimal annualChurnPct) { this.annualChurnPct = annualChurnPct; }
}
