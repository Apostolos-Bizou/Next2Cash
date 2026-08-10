package com.next2me.next2cash.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReportDispatchItem — S105.
 *
 * Join row linking a dispatch to one included transaction. Deleting the parent
 * dispatch cascades (FK ON DELETE CASCADE at the DB level), which restores the
 * transactions to "μη απεσταλμένο" state for badge purposes (SPEC acceptance §7).
 *
 * Table maps to report_dispatch_items (migration s105_report_dispatches.sql).
 */
@Entity
@Table(name = "report_dispatch_items")
@Data
@NoArgsConstructor
public class ReportDispatchItem {

    @EmbeddedId
    private ReportDispatchItemId id;

    public ReportDispatchItem(java.util.UUID dispatchId, Integer transactionId) {
        this.id = new ReportDispatchItemId(dispatchId, transactionId);
    }
}
