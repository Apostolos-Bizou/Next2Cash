package com.next2me.next2cash.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link ReportDispatchItem}: (dispatch_id, transaction_id).
 *
 * transaction_id is Integer because transactions.id is a serial INTEGER
 * (NOT a UUID) — confirmed by codebase audit S105.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDispatchItemId implements Serializable {

    @Column(name = "dispatch_id", nullable = false)
    private UUID dispatchId;

    @Column(name = "transaction_id", nullable = false)
    private Integer transactionId;
}
