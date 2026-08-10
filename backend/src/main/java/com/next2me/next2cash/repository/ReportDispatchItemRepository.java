package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.ReportDispatchItem;
import com.next2me.next2cash.model.ReportDispatchItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportDispatchItemRepository
        extends JpaRepository<ReportDispatchItem, ReportDispatchItemId> {

    /** All line items of a dispatch (transaction ids included in that report). */
    List<ReportDispatchItem> findByIdDispatchId(UUID dispatchId);

    /**
     * Batch lookup for dispatch-status badges: given a set of transaction ids,
     * returns the item rows that link any of them to a dispatch.
     * transaction ids are Integer (transactions.id is serial).
     */
    List<ReportDispatchItem> findByIdTransactionIdIn(List<Integer> transactionIds);
}
