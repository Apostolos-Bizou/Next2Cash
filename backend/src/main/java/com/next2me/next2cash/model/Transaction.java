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
}
