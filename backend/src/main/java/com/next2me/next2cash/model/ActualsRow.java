package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One actuals line (Astrolabe actuals) for a period, grouped by category.
 *
 * Created in Polaris P&L Phase 1 (Oct 2026). Backed by table `actuals_rows`
 * (Polaris Phase 0 migration). Entity-scoped via entity_id.
 *
 * beginAmount = balance at periodStart, changeAmount = movement during the
 * period. periodEnd must be on or after periodStart (validated in
 * PolarisForecastService; the table has no CHECK for it).
 */
@Entity
@Table(name = "actuals_rows")
@Data
@NoArgsConstructor
public class ActualsRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "group_key", nullable = false, length = 50)
    private String groupKey;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "begin_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal beginAmount = BigDecimal.ZERO;

    @Column(name = "change_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal changeAmount = BigDecimal.ZERO;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
