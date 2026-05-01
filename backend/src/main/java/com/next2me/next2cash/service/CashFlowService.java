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
import java.util.*;
import java.util.stream.Collectors;

/**
 * CashFlowService — produces the unified event stream used by /api/cashflow.
 *
 * Algorithm:
 *   1. Fetch all active transactions whose docDate falls within [from, to].
 *   2. Fetch all payments whose paymentDate falls within [from, to].
 *   3. SKIP payment events whose paymentDate equals the parent transaction's docDate
 *      (same-day payment — already represented by the transaction event itself).
 *   4. Merge into a single list, sorted by date DESC.
 *
 * This guarantees:
 *   - A transaction whose doc was issued 09/03 but paid 02/04 produces:
 *       * One transaction event on 09/03 (status=paid, paid amount visible)
 *       * One payment event on 02/04 (💳 Πληρωμή #N — ...)
 *   - When the user filters 01/04–30/04, only the payment event appears.
 *   - When the user filters 01/03–31/03, only the transaction event appears.
 */
@Service
@RequiredArgsConstructor
public class CashFlowService {

    private final TransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;

    public List<CashFlowEvent> getEvents(UUID entityId, LocalDate from, LocalDate to) {
        List<CashFlowEvent> events = new ArrayList<>();

        // ─── 1. Transaction events ──────────────────────────────────
        List<Transaction> txns = transactionRepository
            .findByEntityIdAndRecordStatusAndDocDateBetween(
                entityId, "active", from, to);

        for (Transaction t : txns) {
            events.add(buildTransactionEvent(t));
        }

        // ─── 2. Payment events ──────────────────────────────────────
        // Build a quick lookup: txnId → parent docDate (for de-dup check)
        Map<Integer, LocalDate> txnDocDates = txns.stream()
            .collect(Collectors.toMap(
                Transaction::getId, Transaction::getDocDate, (a, b) -> a));

        List<Payment> payments = paymentRepository
            .findByEntityIdAndPaymentDateBetween(entityId, from, to);

        for (Payment p : payments) {
            // Skip cancelled
            if (p.getStatus() != null && !"completed".equalsIgnoreCase(p.getStatus())) continue;

            Integer txnId = p.getTransactionId();
            LocalDate parentDocDate = txnId == null ? null : txnDocDates.get(txnId);

            // Same-day check requires fetching parent if not in current page
            if (parentDocDate == null && txnId != null) {
                Transaction parent = transactionRepository.findById(txnId).orElse(null);
                if (parent != null) parentDocDate = parent.getDocDate();
            }

            // De-dup: skip payment if it's same date as parent doc (already represented)
            if (parentDocDate != null && parentDocDate.equals(p.getPaymentDate())) continue;

            events.add(buildPaymentEvent(p, txnId));
        }

        // ─── 3. Sort: date DESC, then eventId DESC for stability ────
        events.sort((a, b) -> {
            int cmp = b.getDate().compareTo(a.getDate());
            if (cmp != 0) return cmp;
            return (b.getEventId() == null ? "" : b.getEventId())
                .compareTo(a.getEventId() == null ? "" : a.getEventId());
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

        // Build description: "💳 Πληρωμή #4742 — original description"
        String origDesc = p.getDescription() != null ? p.getDescription() : "";
        String prefix = "\uD83D\uDCB3 \u03A0\u03BB\u03B7\u03C1\u03C9\u03BC\u03AE #" + txnId + " \u2014 ";
        String desc = prefix + origDesc;

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