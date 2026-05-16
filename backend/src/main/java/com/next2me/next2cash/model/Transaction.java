package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;
    @Column(name = "entity_number")
    private Integer entityNumber;

    @Column(nullable = false, length = 10)
    private String type; // income / expense

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Column(name = "accounting_period", length = 20)
    private String accountingPeriod;

    @Column(length = 200)
    private String counterparty;

    @Column(length = 200)
    private String account;

    @Column(length = 200)
    private String category;

    @Column(length = 200)
    private String subcategory;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "amount_paid", precision = 15, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "amount_remaining", precision = 15, scale = 2)
    private BigDecimal amountRemaining = BigDecimal.ZERO;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus = "unpaid"; // unpaid / urgent / paid / received / partial

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "doc_status", length = 20)
    private String docStatus; // bank / receipt / cash / none

    @Column(name = "blob_file_ids", columnDefinition = "TEXT")
    private String blobFileIds; // comma-separated blob paths

    @Column(name = "blob_folder_path", length = 500)
    private String blobFolderPath;

    @Column(name = "record_status", length = 10)
    private String recordStatus = "active"; // active / void

    @Column
    private Boolean approved = false;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===============================================================
    // Phase 1 -- Cash Planning Module (Session 3, May 2026)
    // Added by migration V2026_05_16_001__phase1_planning.sql
    // All fields nullable / defaulted for 100% backward compatibility.
    // ===============================================================

    /**
     * ACTUAL = historical/realized transaction (default for all existing rows).
     * PLANNED = future transaction that has not happened yet.
     * Drives the mode-aware form and the 90-day forecast view.
     */
    @Column(name = "entry_mode", length = 10)
    private String entryMode = "ACTUAL";

    /**
     * true = this row is the "mother" of a recurring series (the template).
     * Children (generated instances) have parentRecurringId pointing here.
     */
    @Column(name = "is_recurring")
    private Boolean isRecurring = false;

    /**
     * FK -> recurrence_patterns.id. NULL unless this row is recurring.
     * Defines frequency, day-of-month, end conditions, etc.
     */
    @Column(name = "recurrence_pattern_id")
    private UUID recurrencePatternId;

    /**
     * Self-FK -> transactions.id. NULL unless this row is a child instance
     * of a recurring "mother". Note: INTEGER, not UUID (transactions.id is INTEGER).
     */
    @Column(name = "parent_recurring_id")
    private Integer parentRecurringId;

    /**
     * FK -> projects.id (Phase 2). NULL = OpEx (general company expense).
     */
    @Column(name = "project_id")
    private UUID projectId;

    /**
     * 0-100. Probability that a PLANNED transaction will actually occur.
     * 100 = certain (default). Lower values shrink contribution to forecast.
     */
    @Column(name = "confidence_pct")
    private Integer confidencePct = 100;

    /**
     * FK -> forecast_scenarios.id (Phase 2). NULL = BASELINE scenario.
     */
    @Column(name = "scenario_id")
    private UUID scenarioId;

    /**
     * Audit trail: when a PLANNED transaction is realized, the new ACTUAL
     * transaction's id is stored here on the original PLANNED row.
     * Self-FK INTEGER (transactions.id type).
     */
    @Column(name = "converted_to_transaction_id")
    private Integer convertedToTransactionId;

    /**
     * Timestamp of when entryMode flipped PLANNED -> ACTUAL via the
     * convert action. NULL until conversion happens.
     */
    @Column(name = "converted_at")
    private LocalDateTime convertedAt;
}
