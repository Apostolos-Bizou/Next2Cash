package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One raise step of a Polaris payroll person (Oct 2026). Backed by table
 * `payroll_person_raises` (manual migration 29 Oct 2026; FK to payroll_people
 * with ON DELETE CASCADE).
 *
 * The salary in force at a date is the base salary with every raise whose
 * effectiveDate <= date applied ONCE, in date order, compounding on the prior
 * step (see PolarisForecastMath#salaryAt). A person with no rows falls back to
 * the legacy single raise (payroll_people.raise_pct / effective_from_year),
 * which the migration back-filled here as one row per person.
 */
@Entity
@Table(name = "payroll_person_raises")
@Data
@NoArgsConstructor
public class PayrollPersonRaise {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payroll_person_id", nullable = false)
    private UUID payrollPersonId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "raise_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal raisePct;

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
