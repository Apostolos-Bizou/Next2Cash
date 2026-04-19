package com.next2me.next2cash.service;

import com.next2me.next2cash.model.Payment;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.PaymentRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase L — Payment creation + parent transaction recalculation.
 *
 * Business rules:
 *   1. New payment row is persisted first.
 *   2. Parent transaction (if linked) is updated:
 *        amountPaid_new      = amountPaid_old + payment.amount
 *        amountRemaining_new = amount - amountPaid_new
 *   3. paymentStatus is recomputed:
 *        - if amountRemaining <= 0.01   → "paid"  (expense) / "received" (income)
 *        - else if amountPaid > 0       → "partial"
 *        - else                         → unchanged
 *   4. paymentDate is set to the most recent payment date.
 *
 * All operations run inside a single transaction so the payment row
 * and the parent transaction are committed atomically.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    // Threshold below which amountRemaining is treated as zero (rounding noise)
    private static final BigDecimal PAID_THRESHOLD = new BigDecimal("0.01");

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Create a new payment and update the parent transaction (if linked).
     *
     * @param entityId    the entity this payment belongs to
     * @param transactionId  parent transaction id (nullable — orphan payments allowed)
     * @param paymentDate    date of the payment (defaults to today if null)
     * @param amount      payment amount (must be > 0)
     * @param paymentMethod  bank / cash / card / etc.
     * @param description    free-text note shown in timelines
     * @param notes       internal notes
     * @param createdBy   user id from JWT
     * @return the persisted Payment
     */
    @Transactional
    public Payment createPayment(
            UUID entityId,
            Integer transactionId,
            LocalDate paymentDate,
            BigDecimal amount,
            String paymentMethod,
            String description,
            String notes,
            UUID createdBy) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        // 1. Build and persist the Payment row
        Payment payment = new Payment();
        payment.setEntityId(entityId);
        payment.setTransactionId(transactionId);
        payment.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now());
        payment.setAmount(amount);
        payment.setPaymentMethod(paymentMethod);
        payment.setDescription(description);
        payment.setNotes(notes);
        payment.setCreatedBy(createdBy);
        payment.setStatus("completed");

        // Infer payment_type from parent (if any) — incoming=income, outgoing=expense
        if (transactionId != null) {
            transactionRepository.findById(transactionId).ifPresent(parent -> {
                if ("income".equals(parent.getType())) {
                    payment.setPaymentType("incoming");
                } else if ("expense".equals(parent.getType())) {
                    payment.setPaymentType("outgoing");
                }
                if (payment.getCounterparty() == null) {
                    payment.setCounterparty(parent.getCounterparty());
                }
            });
        }

        Payment saved = paymentRepository.save(payment);

        // 2. Recalculate parent transaction if linked
        if (transactionId != null) {
            recalcParentTransaction(transactionId, createdBy);
        }

        return saved;
    }

    /**
     * Recompute a transaction's amountPaid / amountRemaining / paymentStatus
     * based on ALL linked payments. Safe to call multiple times (idempotent).
     */
    @Transactional
    public void recalcParentTransaction(Integer transactionId, UUID updatedBy) {
        Transaction txn = transactionRepository.findById(transactionId).orElse(null);
        if (txn == null) return;

        // Sum all payments linked to this transaction
        BigDecimal totalPaid = paymentRepository
            .findByEntityIdAndTransactionIdIn(txn.getEntityId(), java.util.List.of(transactionId))
            .stream()
            .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal amount = txn.getAmount() != null ? txn.getAmount() : BigDecimal.ZERO;
        BigDecimal remaining = amount.subtract(totalPaid);

        txn.setAmountPaid(totalPaid);
        txn.setAmountRemaining(remaining);

        // Recompute paymentStatus
        if (remaining.compareTo(PAID_THRESHOLD) <= 0) {
            // Fully paid — use "received" for income, "paid" for expense
            txn.setPaymentStatus("income".equals(txn.getType()) ? "received" : "paid");
            // Find the latest payment date to stamp as paymentDate
            paymentRepository
                .findByEntityIdAndTransactionIdIn(txn.getEntityId(), java.util.List.of(transactionId))
                .stream()
                .map(Payment::getPaymentDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .ifPresent(txn::setPaymentDate);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            txn.setPaymentStatus("partial");
        }
        // else: leave status unchanged (unpaid/urgent stay as-is)

        if (updatedBy != null) txn.setUpdatedBy(updatedBy);
        transactionRepository.save(txn);
    }
}
