package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Date-based OPEX forecast milestone for one "Λοιπά OPEX Διοίκησης" category
 * (Polaris P&L, Oct 2026). Backed by table `cost_milestones` (manual migration
 * 29 Oct 2026). Entity-scoped via entity_id.
 *
 * targetValue is the ANNUAL run-rate (USD/year) the category should reach at
 * targetDate (stored as the first day of its month, like payroll_milestones).
 * Between milestones the forecast interpolates linearly; before the first one
 * it uses the current actuals run-rate; after the last one it stays flat --
 * the same rule as the member milestones (see PolarisForecastMath).
 */
@Entity
@Table(name = "cost_milestones")
@Data
@NoArgsConstructor
public class CostMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /** One of PolarisForecastMath.OPEX_CATEGORIES (an actuals_rows label). */
    @Column(nullable = false)
    private String category;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "target_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
