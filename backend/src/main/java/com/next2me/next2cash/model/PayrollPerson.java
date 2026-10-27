package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One person on the Polaris payroll (Athens / Manila offices).
 *
 * Created in Polaris P&L Phase 1 (Oct 2026). Backed by table `payroll_people`
 * (Polaris Phase 0 migration). Entity-scoped via entity_id.
 *
 * salary is the base amount in the entity currency; raisePct is applied from
 * effectiveFromYear onwards. isOfficeCost marks rows that are office overhead
 * rather than an individual salary line.
 */
@Entity
@Table(name = "payroll_people")
@Data
@NoArgsConstructor
public class PayrollPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /** Office code, e.g. ATHENS / MANILA. */
    @Column(nullable = false, length = 20)
    private String office;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "role_title", length = 150)
    private String roleTitle;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal salary = BigDecimal.ZERO;

    @Column(name = "raise_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal raisePct = BigDecimal.ZERO;

    @Column(name = "effective_from_year", nullable = false)
    private Integer effectiveFromYear;

    @Column(name = "is_office_cost", nullable = false)
    private Boolean isOfficeCost = false;

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
