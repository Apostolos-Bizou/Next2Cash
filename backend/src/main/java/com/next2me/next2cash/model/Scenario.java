package com.next2me.next2cash.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Forecast scenario entity ("what-if" lever set) of the Group.
 *
 * <p>Each entity owns a set of scenarios (Baseline / Optimistic / Pessimistic /
 * Custom). A scenario shifts PLANNED revenue and expenses by simple
 * percentages relative to baseline, letting the CEO see best/worst-case cash
 * projections side-by-side.
 *
 * <p>PLANNED transactions may be tagged to a scenario via
 * {@code transactions.scenario_id}. NULL scenario = baseline.
 *
 * Spec ref: CashPlanning TechSpec v1.1 sections 3 (Principle 3) and 5.8.
 * Session: S97
 */
@Entity
@Table(name = "forecast_scenarios")
public class Scenario {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "scenario_type", nullable = false, length = 20)
    private String scenarioType = "CUSTOM";

    @Column(name = "owner_entity_id", nullable = false)
    private UUID ownerEntityId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Percentage shift applied to PLANNED revenue vs baseline. Baseline = 0. */
    @Column(name = "revenue_adjust_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal revenueAdjustPct = BigDecimal.ZERO;

    /** Percentage shift applied to PLANNED expenses vs baseline. Baseline = 0. */
    @Column(name = "expense_adjust_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal expenseAdjustPct = BigDecimal.ZERO;

    @Column(name = "color", nullable = false, length = 7)
    private String color = "#6B7280";

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.scenarioType == null) this.scenarioType = "CUSTOM";
        if (this.revenueAdjustPct == null) this.revenueAdjustPct = BigDecimal.ZERO;
        if (this.expenseAdjustPct == null) this.expenseAdjustPct = BigDecimal.ZERO;
        if (this.color == null) this.color = "#6B7280";
        if (this.isDefault == null) this.isDefault = Boolean.FALSE;
        if (this.isActive == null) this.isActive = Boolean.TRUE;
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
    public String getScenarioType() { return scenarioType; }
    public void setScenarioType(String scenarioType) { this.scenarioType = scenarioType; }
    public UUID getOwnerEntityId() { return ownerEntityId; }
    public void setOwnerEntityId(UUID ownerEntityId) { this.ownerEntityId = ownerEntityId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getRevenueAdjustPct() { return revenueAdjustPct; }
    public void setRevenueAdjustPct(BigDecimal revenueAdjustPct) { this.revenueAdjustPct = revenueAdjustPct; }
    public BigDecimal getExpenseAdjustPct() { return expenseAdjustPct; }
    public void setExpenseAdjustPct(BigDecimal expenseAdjustPct) { this.expenseAdjustPct = expenseAdjustPct; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
