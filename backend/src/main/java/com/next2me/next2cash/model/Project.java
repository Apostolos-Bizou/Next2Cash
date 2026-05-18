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
}
