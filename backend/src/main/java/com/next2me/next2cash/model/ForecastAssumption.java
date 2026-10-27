package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Key/value forecast assumption (fees, FX rates, ...) per entity.
 *
 * Created in Polaris P&L Phase 1 (Oct 2026). Backed by table
 * `forecast_assumptions` (Polaris Phase 0 migration). One row per
 * (entity_id, key) -- enforced by a UNIQUE constraint in the database.
 *
 * Why the backtick-quoted column names:
 *   `key` and `value` are reserved words in H2 (test profile), so Hibernate's
 *   create-drop would fail on them unquoted -- same issue that already breaks
 *   the `month` column of budgets in H2. Backticks make Hibernate quote them
 *   for the active dialect; in PostgreSQL "key"/"value" resolve to the same
 *   lower-case columns the migration created.
 */
@Entity
@Table(name = "forecast_assumptions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"entity_id", "`key`"}))
@Data
@NoArgsConstructor
public class ForecastAssumption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "`key`", nullable = false, length = 50)
    private String key;

    @Column(name = "`value`", nullable = false, precision = 14, scale = 4)
    private BigDecimal value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
    }
}
