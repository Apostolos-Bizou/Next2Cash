package com.next2me.next2cash.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ReportDispatch — S105.
 *
 * One row per dispatch: a free-form report (title + selected transactions)
 * generated as a PDF and sent to a recipient (accountant, investor, etc.).
 *
 * Architecture note (SPEC §2.1): we record dispatches, NOT a "sent" flag on
 * the transaction — the same transaction can be dispatched to multiple
 * recipients without losing the earlier dispatch (audit trail).
 *
 * Table maps to report_dispatches (migration s105_report_dispatches.sql).
 */
@Entity
@Table(name = "report_dispatches")
@Data
@NoArgsConstructor
public class ReportDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String recipient;

    @Column(name = "sent_date", nullable = false)
    private LocalDate sentDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    /** Azure Blob path of the generated PDF (null until archived). */
    @Column(name = "blob_path", columnDefinition = "TEXT")
    private String blobPath;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
