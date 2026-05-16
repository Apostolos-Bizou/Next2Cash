package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Recurrence pattern definition for a recurring transaction.
 *
 * Created in Phase 1 (Session 3, May 2026) as part of the Cash Planning Module.
 * Backed by table `recurrence_patterns` (created by migration V2026_05_16_001).
 *
 * One pattern is linked to many transactions: the "mother" transaction holds
 * recurrence_pattern_id, and the engine generates child instances on demand.
 *
 * Note: this is master data (UUID primary key), unlike Transaction which is
 * INTEGER auto-increment.
 */
@Entity
@Table(name = "recurrence_patterns")
@Data
@NoArgsConstructor
public class RecurrencePattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * DAILY / WEEKLY / MONTHLY / QUARTERLY / YEARLY / CUSTOM.
     * Constrained by chk_recurrence_frequency in the database.
     */
    @Column(nullable = false, length = 20)
    private String frequency;

    /**
     * Number of frequency units between occurrences.
     * Example: frequency=MONTHLY, intervalCount=2 -> every 2 months.
     * Must be >= 1 (enforced by chk_recurrence_interval_count).
     */
    @Column(name = "interval_count", nullable = false)
    private Integer intervalCount = 1;

    /**
     * Day of month for MONTHLY/QUARTERLY/YEARLY frequencies. NULL otherwise.
     * Range 1-31, enforced by chk_recurrence_day_of_month.
     * Engine handles end-of-month edge cases (e.g. 31st in February).
     */
    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    /**
     * Day of week for WEEKLY frequency. NULL otherwise.
     * 1=Monday ... 7=Sunday (ISO-8601), enforced by chk_recurrence_day_of_week.
     */
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    /**
     * When the recurrence begins. The first generated instance has docDate >= startDate.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Optional end date. NULL means open-ended (use maxOccurrences instead, or both NULL = unlimited).
     * If both endDate and maxOccurrences are set, whichever triggers first wins.
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Optional cap on total occurrences. NULL means uncapped.
     * Example: monthly subscription for 12 months -> maxOccurrences=12.
     * Must be >= 1 if set (enforced by chk_recurrence_max_occurrences).
     */
    @Column(name = "max_occurrences")
    private Integer maxOccurrences;

    /**
     * IANA timezone for date computation, default Europe/Athens.
     * Affects DST boundaries when generating future instances.
     */
    @Column(length = 50)
    private String timezone = "Europe/Athens";

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
