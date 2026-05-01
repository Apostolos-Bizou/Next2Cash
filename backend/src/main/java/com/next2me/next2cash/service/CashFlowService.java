package com.next2me.next2cash.service;

import com.next2me.next2cash.dto.CashFlowEvent;
import com.next2me.next2cash.model.Payment;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.PaymentRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CashFlowService produces the unified event stream used by /api/cashflow.
 *
 * Algorithm:
 *   1. Fetch all active transactions whose docDate falls within [from, to].
 *   2. Fetch all payments whose paymentDate falls within [from, to].
 *   3. SKIP payment events whose paymentDate equals the parent transaction's
 *      docDate (same-day payment is already represented by the transaction
 *      event itself, so we avoid double-counting).
 *   4. Merge into a single list, sorted by date DESC.
 *
 * Encoding notes (Session #48):
 *   - This file contains ONLY ASCII characters (no Greek in source).
 *   - Greek prefixes for payment-event descriptions are built using
 *     backslash-u escapes; this is compile-safe regardless of platform charset.
 *   - We do NOT use string concatenation with surrogate-pair emoji here:
 *     the original Session #47 code embedded U+1F4B3 (credit-card emoji)
 *     in the prefix, which combined with concat to produce mojibake
 *     under JVMs that defaulted file.encoding to a non-UTF-8 charset.
 *   - If the UI wants an icon, the frontend can prepend it on render.
 */
@Service
@RequiredArgsConstructor
public class CashFlowService {

    private final TransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;

    // Greek "Pi-lambda-eta-rho-omega-mu-eta-final" + " " + "gamma-iota-alpha" + " #"
    // Renders at runtime as: Payment-for-# (in Greek). Built from
    // backslash-u escapes so the source stays pure ASCII regardless of charset.
    private static final String PAYMENT_PREFIX =
        "\u03A0\u03BB\u03B7\u03C1\u03C9\u03BC\u03AE \u03B3\u03B9\u03B1 #";

    // " - " separator (en-dash for nicer typography on the row)
    private static final String SEP = " \u2014 ";

    public List<CashFlowEvent> getEvents(UUID entityId, LocalDate from, LocalDate to) {
        List<CashFlowEvent> events = new ArrayList<>();

        // 1. Transaction events
        List<Transaction> txns = transactionRepository
            .findByEntityIdAndRecordStatusAndDocDateBetween(
                entityId, "active", from, to);

        for (Transaction t : txns) {
            events.add(buildTransactionEvent(t));
        }

        // 2. Payment events
        // Quick lookup of parent docDate by txn id (used for same-day dedup)
        Map<Integer, LocalDate> txnDocDates = txns.stream()
            .collect(Collectors.toMap(
                Transaction::getId, Transaction::getDocDate, (a, b) -> a));

        List<Payment> payments = paymentRepository
            .findByEntityIdAndPaymentDateBetween(entityId, from, to);

        for (Payment p : payments) {
            // Skip cancelled / non-completed
            if (p.getStatus() != null
                    && !"completed".equalsIgnoreCase(p.getStatus())) {
                continue;
            }

            Integer txnId = p.getTransactionId();
            LocalDate parentDocDate = (txnId == null) ? null : txnDocDates.get(txnId);

            // If parent not in current window, fetch it (may live outside [from,to])
            if (parentDocDate == null && txnId != null) {
                Transaction parent = transactionRepository.findById(txnId).orElse(null);
                if (parent != null) {
                    parentDocDate = parent.getDocDate();
                }
            }

            // De-dup: skip payment if it's same date as parent doc
            if (parentDocDate != null && parentDocDate.equals(p.getPaymentDate())) {
                continue;
            }

            events.add(buildPaymentEvent(p, txnId));
        }

        // 3. Sort: date DESC, then eventId DESC for stable ordering
        events.sort((a, b) -> {
            int cmp = b.getDate().compareTo(a.getDate());
            if (cmp != 0) {
                return cmp;
            }
            String aId = a.getEventId() == null ? "" : a.getEventId();
            String bId = b.getEventId() == null ? "" : b.getEventId();
            return bId.compareTo(aId);
        });

        return events;
    }

    private CashFlowEvent buildTransactionEvent(Transaction t) {
        boolean isIncome = "income".equalsIgnoreCase(t.getType());
        BigDecimal amt = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
        return CashFlowEvent.builder()
            .eventId("T-" + t.getId())
            .eventType("transaction")
            .transactionId(t.getId())
            .date(t.getDocDate())
            .description(t.getDescription())
            .amount(amt)
            .type(t.getType())
            .paymentStatus(t.getPaymentStatus())
            .category(t.getCategory())
            .subcategory(t.getSubcategory())
            .counterparty(t.getCounterparty())
            .paymentMethod(t.getPaymentMethod())
            .entityNumber(t.getEntityNumber())
            .isPaymentRow(false)
            .inflow(isIncome ? amt : BigDecimal.ZERO)
            .outflow(isIncome ? BigDecimal.ZERO : amt)
            .build();
    }

    private CashFlowEvent buildPaymentEvent(Payment p, Integer txnId) {
        boolean isIncome = "incoming".equalsIgnoreCase(p.getPaymentType());
        BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;

        // Description format: "Payment for #N" + (optional) " - {original description}"
        // Built from constants and DB-loaded description; no surrogate pairs.
        String origDesc = p.getDescription();
        StringBuilder sb = new StringBuilder();
        sb.append(PAYMENT_PREFIX).append(txnId);
        if (origDesc != null && !origDesc.isEmpty()) {
            sb.append(SEP).append(origDesc);
        }
        String desc = sb.toString();

        return CashFlowEvent.builder()
            .eventId("P-" + p.getId())
            .eventType("payment")
            .transactionId(txnId)
            .paymentId(p.getId())
            .date(p.getPaymentDate())
            .description(desc)
            .amount(amt)
            .type(isIncome ? "income" : "expense")
            .paymentStatus(isIncome ? "received" : "paid")
            .counterparty(p.getCounterparty())
            .paymentMethod(p.getPaymentMethod())
            .isPaymentRow(true)
            .linkedTransactionId(txnId)
            .inflow(isIncome ? amt : BigDecimal.ZERO)
            .outflow(isIncome ? BigDecimal.ZERO : amt)
            .build();
    }
}
