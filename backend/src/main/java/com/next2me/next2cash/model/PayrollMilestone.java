package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Manual membership target for a given month (Polaris member forecast).
 *
 * Created in Polaris P&L Phase 1 (Oct 2026). Backed by table `payroll_milestones`
 * (Polaris Phase 0 migration). Entity-scoped via entity_id.
 *
 * targetMonth is always stored as the first day of the month (normalized by
 * PolarisPayrollService). The table has no updated_at column.
 */
@Entity
@Table(name = "payroll_milestones")
@Data
@NoArgsConstructor
public class PayrollMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "target_month", nullable = false)
    private LocalDate targetMonth;

    @Column(name = "members_target", nullable = false)
    private Integer membersTarget;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
