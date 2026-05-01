package com.next2me.next2cash.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CashFlowEvent — unified event in the cash-flow stream.
 *
 * Represents either:
 *   1. A transaction record (eventType = "transaction")
 *   2. A payment record    (eventType = "payment")
 *
 * Frontend renders both types in the same list, sorted by date.
 * Payment events have a negative eventId (so they don't collide with
 * transaction integer ids) and a "linkedTransactionId" pointing to
 * the parent transaction (for click-through, file viewing, etc).
 *
 * Notes:
 * - "amount" is always positive; the type field tells direction.
 * - "description" is pre-formatted for display (payments include "💳" prefix).
 * - "paymentMethod" carries through from either transaction or payment row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CashFlowEvent {

    /** Composite event id: "T-{txnId}" or "P-{paymentId}". */
    private String eventId;

    /** "transaction" or "payment". */
    private String eventType;

    /** Original transaction id (always present, even for payment events). */
    private Integer transactionId;

    /** Payment id — only present for eventType = "payment". */
    private Integer paymentId;

    /** Display date — docDate for transactions, paymentDate for payments. */
    private LocalDate date;

    /** Pre-formatted description (with "💳 Πληρωμή #N — " prefix for payment events). */
    private String description;

    /** Always positive amount. */
    private BigDecimal amount;

    /** "income" or "expense" (mirrors parent transaction type). */
    private String type;

    /** "paid", "received", "unpaid", "urgent", "partial". */
    private String paymentStatus;

    private String category;
    private String subcategory;
    private String counterparty;
    private String paymentMethod;
    private Integer entityNumber;

    /** True for payment events, false for transaction events. */
    private Boolean isPaymentRow;

    /** For payment events — id of parent transaction. */
    private Integer linkedTransactionId;

    /** Inflow amount (positive for income, 0 for expense). For frontend convenience. */
    private BigDecimal inflow;

    /** Outflow amount (positive for expense, 0 for income). For frontend convenience. */
    private BigDecimal outflow;
}