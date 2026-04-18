package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment entity — tracks individual payment/receipt events against a transaction.
 *
 * A payment always belongs to an entity (entity_id), and optionally links to a
 * parent Transaction (transaction_id). When linked, the payment represents a
 * partial or full settlement of the transaction's amount_remaining.
 *
 * Payments are displayed in card views alongside matching transactions (Phase K).
 * Rules are evaluated against the PARENT transaction (not the payment row itself),
 * so a payment is only shown if its parent transaction matches the card rule.
 *
 * Nullable fields (transaction_id, counterparty, description) allow orphan records
 * from data migrations — these are still rendered for visibility.
 */
@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /** Parent transaction this payment settles. Nullable to allow orphan records. */
    @Column(name = "transaction_id")
    private Integer transactionId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "payment_period", length = 20)
    private String paymentPeriod;

    /** incoming / outgoing */
    @Column(name = "payment_type", length = 20)
    private String paymentType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "bank_reference", length = 200)
    private String bankReference;

    @Column(name = "bank_account_id")
    private UUID bankAccountId;

    @Column(length = 200)
    private String counterparty;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** completed / pending / cancelled */
    @Column(length = 20)
    private String status;

    @Column(name = "blob_file_id", length = 500)
    private String blobFileId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}