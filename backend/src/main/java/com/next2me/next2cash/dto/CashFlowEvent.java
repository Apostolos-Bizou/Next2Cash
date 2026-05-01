package com.next2me.next2cash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Unified event in the cash-flow stream returned by GET /api/cashflow.
 *
 * Two event kinds:
 *   - eventType = "transaction"  -- the transaction itself, dated at docDate
 *   - eventType = "payment"      -- a payment that closed (or partly closed)
 *                                   a transaction, dated at paymentDate
 *
 * The frontend renders payment events as separate rows under the same date
 * filter the user selected, so a March invoice paid in April surfaces in
 * the April view as the payment event (and only the payment event).
 *
 * All string fields are written to JSON via Jackson with UTF-8.
 * No Greek characters appear in this file or in the service that builds
 * the events: any Greek prefixes are constructed using backslash-u escape
 * sequences in the service layer (ASCII-safe at compile time, UTF-8 at runtime).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowEvent {

    /** Stable id for the event row. Format: "T-{txnId}" or "P-{paymentId}". */
    private String eventId;

    /** "transaction" or "payment". */
    private String eventType;

    /** Always the parent transaction id (for both event kinds). */
    private Integer transactionId;

    /** Set only when eventType = "payment". */
    private Integer paymentId;

    /** Date the event "happens" on the cash timeline (docDate or paymentDate). */
    private LocalDate date;

    /** Free-text description shown in the UI. */
    private String description;

    /** Absolute amount (always positive). Direction lives in inflow / outflow. */
    private BigDecimal amount;

    /** "income" or "expense" (mirrored from the parent transaction). */
    private String type;

    /** "paid", "received", "unpaid", "urgent", or null. */
    private String paymentStatus;

    private String category;
    private String subcategory;
    private String counterparty;
    private String paymentMethod;

    /** Business-level numbering of the parent transaction. */
    private Integer entityNumber;

    /** True for payment events; the frontend hides edit/delete on these rows. */
    private boolean isPaymentRow;

    /** Inflow side of the cash movement (>= 0). */
    private BigDecimal inflow;

    /** Outflow side of the cash movement (>= 0). */
    private BigDecimal outflow;

    /** Set on payment events; same value as transactionId, for clarity. */
    private Integer linkedTransactionId;
}
