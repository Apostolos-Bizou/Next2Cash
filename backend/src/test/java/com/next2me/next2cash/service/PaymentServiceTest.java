package com.next2me.next2cash.service;

import com.next2me.next2cash.BaseIntegrationTest;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.Payment;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.support.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase L.7 β€” PaymentService integration tests.
 *
 * Exercises the core Payment business logic end-to-end against H2:
 *   - createPayment() persists a Payment row
 *   - parent Transaction is recalculated (amountPaid / amountRemaining / paymentStatus)
 *   - status transitions: unpaid β†’ paid, unpaid β†’ partial, partial β†’ paid, overpay β†’ paid
 *
 * Each @Test runs in its own transaction thanks to BaseIntegrationTest (rolled back after).
 */
class PaymentServiceTest extends BaseIntegrationTest {

    @Autowired private TestDataBuilder tdb;
    @Autowired private PaymentService paymentService;
    @Autowired private TransactionRepository transactionRepository;

    private CompanyEntity entity;
    private UUID adminUserId;

    @BeforeEach
    void setup() {
        entity = tdb.createEntity("PAYTEST", "Payment Test Entity");
        adminUserId = tdb.createAdmin("payment_test_admin").getId();
    }

    // β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€
    // Helper β€” build an unpaid expense Transaction of the given amount.
    // Uses reflection to set the @Id field (identity column doesn't allow manual set via setter).
    // β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€
    private Transaction createUnpaidExpense(String amount) {
        Transaction t = new Transaction();
        setField(t, "entityId",        entity.getId());
        setField(t, "type",            "expense");
        setField(t, "docDate",         LocalDate.of(2026, 1, 15));
        setField(t, "description",     "Test unpaid expense");
        setField(t, "amount",          new BigDecimal(amount));
        setField(t, "amountPaid",      BigDecimal.ZERO);
        setField(t, "amountRemaining", new BigDecimal(amount));
        setField(t, "paymentStatus",   "unpaid");
        setField(t, "recordStatus",    "active");
        return transactionRepository.save(t);
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + name, e);
        }
    }

    // β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€
    // 1. Full payment β€” unpaid β†’ paid
    // β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€
    @Test
    void fullPayment_unpaidToPaid() {
        Transaction txn = createUnpaidExpense("1000.00");

        Payment saved = paymentService.createPayment(
            entity.getId(),
            txn.getId(),
            LocalDate.of(2026, 2, 1),
            new BigDecimal("1000.00"),
            "bank",
            "Full payment",
            null,
            adminUserId
        );

        // Payment row persisted
        assertNotNull(saved.getId(), "Payment should have an id after save");
        assertEquals(0, new BigDecimal("1000.00").compareTo(saved.getAmount()));
        assertEquals("outgoing", saved.getPaymentType(), "payment_type inferred from expense parent");
        assertEquals("completed", saved.getStatus());

        // Parent transaction fully paid
        Transaction updated = transactionRepository.findById(txn.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(updated.getAmountPaid()),   "amountPaid = 1000");
        assertEquals(0, BigDecimal.ZERO.compareTo(updated.getAmountRemaining()),        "amountRemaining = 0");
        assertEquals("paid", updated.getPaymentStatus(),                                "expense fully paid β†’ status=paid");
        assertEquals(LocalDate.of(2026, 2, 1), updated.getPaymentDate(),                "paymentDate stamped from payment");
    }

    // β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€
    // 2. Partial payment β€” unpaid β†’ partial
    // β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€
    @Test
    void partialPayment_unpaidToPartial() {
        Transaction txn = createUnpaidExpense("1000.00");

        paymentService.createPayment(
            entity.getId(),
            txn.getId(),
            LocalDate.of(2026, 2, 1),
            new BigDecimal("400.00"),
            "bank",
            "Partial payment #1",
            null,
            adminUserId
        );

        Transaction updated = transactionRepository.findById(txn.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("400.00").compareTo(updated.getAmountPaid()),    "amountPaid = 400");
        assertEquals(0, new BigDecimal("600.00").compareTo(updated.getAmountRemaining()), "amountRemaining = 600");
        assertEquals("partial", updated.getPaymentStatus(),                             "partial payment β†’ status=partial");
    }

    // β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€
    // 3. Two partial payments β€” partial β†’ paid on 2nd settlement
    // β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€
    @Test
    void twoPartialPayments_partialToPaid() {
        Transaction txn = createUnpaidExpense("1000.00");

        // First payment β€” 400 β†’ partial
        paymentService.createPayment(
            entity.getId(), txn.getId(), LocalDate.of(2026, 2, 1),
            new BigDecimal("400.00"), "bank", "Partial 1", null, adminUserId
        );
        Transaction afterFirst = transactionRepository.findById(txn.getId()).orElseThrow();
        assertEquals("partial", afterFirst.getPaymentStatus(), "after 1st partial β†’ partial");

        // Second payment β€” 600 β†’ fully paid
        paymentService.createPayment(
            entity.getId(), txn.getId(), LocalDate.of(2026, 2, 15),
            new BigDecimal("600.00"), "cash", "Partial 2 settles remainder", null, adminUserId
        );

        Transaction afterSecond = transactionRepository.findById(txn.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(afterSecond.getAmountPaid()),  "total paid = 1000");
        assertEquals(0, BigDecimal.ZERO.compareTo(afterSecond.getAmountRemaining()),       "remaining = 0");
        assertEquals("paid", afterSecond.getPaymentStatus(),                               "2nd payment closes β†’ paid");
        assertEquals(LocalDate.of(2026, 2, 15), afterSecond.getPaymentDate(),              "paymentDate = latest payment");
    }

    // β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€
    // 4. Overpayment β€” status becomes paid, amountRemaining goes negative (noted, not blocked)
    // β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€β”€
    @Test
    void overpayment_remainingGoesNegative_statusPaid() {
        Transaction txn = createUnpaidExpense("500.00");

        paymentService.createPayment(
            entity.getId(),
            txn.getId(),
            LocalDate.of(2026, 2, 1),
            new BigDecimal("600.00"),
            "bank",
            "Overpayment scenario",
            null,
            adminUserId
        );

        Transaction updated = transactionRepository.findById(txn.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("600.00").compareTo(updated.getAmountPaid()),       "paid = 600");
        assertEquals(-1, updated.getAmountRemaining().compareTo(BigDecimal.ZERO),          "remaining < 0 (overpay)");
        assertEquals(0, new BigDecimal("-100.00").compareTo(updated.getAmountRemaining()), "remaining = -100");
        assertEquals("paid", updated.getPaymentStatus(),                                   "overpay β†’ still paid");
    }
}
