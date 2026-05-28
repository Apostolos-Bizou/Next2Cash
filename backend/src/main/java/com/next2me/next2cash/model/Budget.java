package com.next2me.next2cash.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Budget line entity ("planned" target amounts per category/subcategory,
 * mirroring the Reports philosophy but editable and persisted).
 *
 * <p>One row = (entity, year, category, subcategory, direction, month) -> amount.
 * Monthly granularity for input; quarter and year totals derive by summing
 * months client-side. The CEO enters a monthly figure (e.g. rent 650) and the
 * UI aggregates to Q1..Q4 and the full year automatically.
 *
 * <p>Budget figures are independent of PLANNED transactions: they are set by
 * the CEO (optionally auto-seeded from prior-year ACTUAL averages) and compared
 * against ACTUAL transactions to produce variance.
 *
 * Spec ref: CashPlanning TechSpec v1.1 section 6.3 (Budget vs Actual).
 * Session: S98.1
 */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_entity_id", nullable = false)
    private UUID ownerEntityId;

    @Column(name = "budget_year", nullable = false)
    private Integer budgetYear;

    @Column(name = "category", nullable = false, length = 200)
    private String category;

    /** May be empty string for category-level (no subcategory) lines. */
    @Column(name = "subcategory", length = 200)
    private String subcategory = "";

    /** income / expense -- mirrors Transaction.type */
    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    /** 1..12 */
    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOwnerEntityId() { return ownerEntityId; }
    public void setOwnerEntityId(UUID ownerEntityId) { this.ownerEntityId = ownerEntityId; }
    public Integer getBudgetYear() { return budgetYear; }
    public void setBudgetYear(Integer budgetYear) { this.budgetYear = budgetYear; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
