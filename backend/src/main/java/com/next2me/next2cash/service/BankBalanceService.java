package com.next2me.next2cash.service;

import com.next2me.next2cash.model.BankAccount;
import com.next2me.next2cash.repository.BankAccountRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2 (Session #49) — Bank balance auto-compute.
 *
 * Hybrid model (Option C):
 *   - Each BankAccount has an opening_balance + opening_date "anchor".
 *   - Cached current_balance = opening_balance + SUM(paid transactions
 *       where paymentMethod = accountLabel AND docDate >= opening_date).
 *   - Recompute is triggered by:
 *       (a) explicit calls from CRUD operations on Transaction/Payment
 *       (b) manual button "Recompute all" in admin UI
 *       (c) startup (if last_recomputed_at is NULL or stale)
 *
 * Orphan handling:
 *   - Paid transactions whose paymentMethod doesn't match any active bank account
 *     contribute to the "Ανεκχώρητο" virtual account.
 *
 * NOTE: This service does NOT mutate transactions. Original payment_method strings
 * are preserved in the audit history. Recompute is read-only over transactions.
 */
@Service
@RequiredArgsConstructor
public class BankBalanceService {

    private static final Logger log = LoggerFactory.getLogger(BankBalanceService.class);

    /** Label of the system-managed virtual account. */
    public static final String UNALLOCATED_LABEL = "Ανεκχώρητο";

    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Recompute current_balance for a single bank account from its opening anchor.
     *
     * For real accounts:
     *   current_balance = opening_balance + sumPaidNet(paymentMethod = accountLabel, docDate >= opening_date)
     *
     * For the virtual "Ανεκχώρητο":
     *   current_balance = sum of all paid transactions whose paymentMethod doesn't match
     *                     any other active bank account in the same entity.
     *   (Note: orphan transactions can be either income (positive) or expense (negative).)
     *
     * @return the recomputed BankAccount (with current_balance + last_recomputed_at updated)
     */
    @Transactional
    public BankAccount recompute(UUID bankAccountId) {
        BankAccount ba = bankAccountRepository.findById(bankAccountId)
            .orElseThrow(() -> new IllegalArgumentException("Bank account not found: " + bankAccountId));

        BigDecimal newBalance = computeBalance(ba);
        ba.setCurrentBalance(newBalance);
        ba.setLastRecomputedAt(LocalDateTime.now());
        BankAccount saved = bankAccountRepository.save(ba);

        log.info("Recomputed balance for account [{}/{}]: {} EUR-equivalent",
            ba.getAccountLabel(), ba.getCurrency(), newBalance);

        return saved;
    }

    /**
     * Recompute all bank accounts for a given entity.
     *
     * @return list of recomputed accounts
     */
    @Transactional
    public List<BankAccount> recomputeForEntity(UUID entityId) {
        List<BankAccount> accounts = bankAccountRepository.findByEntityIdOrderBySortOrderAsc(entityId);
        List<BankAccount> result = new ArrayList<>();
        for (BankAccount ba : accounts) {
            result.add(recompute(ba.getId()));
        }
        log.info("Recomputed {} bank accounts for entity {}", result.size(), entityId);
        return result;
    }

    /**
     * Recompute only the bank accounts affected by a given paymentMethod string.
     * Used by Transaction CRUD triggers (Phase 3).
     *
     * Affected accounts:
     *   1. The account whose label matches paymentMethod (if any), OR the Ανεκχώρητο.
     *   2. Always: the Ανεκχώρητο account itself (might gain/lose orphans).
     *
     * If paymentMethod is null/empty, only Ανεκχώρητο is recomputed.
     */
    @Transactional
    public void recomputeForPaymentMethod(UUID entityId, String paymentMethod) {
        // 1. Match-based account
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            Optional<BankAccount> matched = bankAccountRepository
                .findFirstByEntityIdAndAccountLabelAndIsActiveTrue(entityId, paymentMethod);
            matched.ifPresent(ba -> recompute(ba.getId()));
        }

        // 2. Always recompute Ανεκχώρητο (orphans might have changed)
        Optional<BankAccount> unallocated = bankAccountRepository
            .findFirstByEntityIdAndAccountLabel(entityId, UNALLOCATED_LABEL);
        unallocated.ifPresent(ba -> recompute(ba.getId()));
    }

    /**
     * Update the opening anchor of a bank account.
     *
     * This is what "Διόρθωση Σημείου Εκκίνησης" does:
     *   - Set new opening_balance + opening_date
     *   - Recompute current_balance (which now reflects the new anchor)
     *
     * Use case: User has reconciled with ebanking and wants to declare
     * "as of YYYY-MM-DD, the balance is exactly X EUR — start counting from there".
     */
    @Transactional
    public BankAccount updateOpeningAnchor(UUID bankAccountId, BigDecimal newOpeningBalance, LocalDate newOpeningDate) {
        BankAccount ba = bankAccountRepository.findById(bankAccountId)
            .orElseThrow(() -> new IllegalArgumentException("Bank account not found: " + bankAccountId));

        if (Boolean.TRUE.equals(ba.getIsVirtual())) {
            throw new IllegalArgumentException("Cannot manually set opening anchor for virtual account: " + ba.getAccountLabel());
        }

        ba.setOpeningBalance(newOpeningBalance != null ? newOpeningBalance : BigDecimal.ZERO);
        ba.setOpeningDate(newOpeningDate != null ? newOpeningDate : LocalDate.now());
        bankAccountRepository.save(ba);

        log.info("Updated opening anchor for [{}]: balance={}, date={}",
            ba.getAccountLabel(), newOpeningBalance, newOpeningDate);

        // Recompute with new anchor
        return recompute(ba.getId());
    }

    // ──────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────

    private BigDecimal computeBalance(BankAccount ba) {
        if (Boolean.TRUE.equals(ba.getIsVirtual())) {
            return computeVirtualBalance(ba);
        }
        return computeRealBalance(ba);
    }

    /**
     * Real account: opening + net paid transactions matching this account's label.
     */
    private BigDecimal computeRealBalance(BankAccount ba) {
        BigDecimal opening = ba.getOpeningBalance() != null ? ba.getOpeningBalance() : BigDecimal.ZERO;
        LocalDate openingDate = ba.getOpeningDate() != null ? ba.getOpeningDate() : LocalDate.of(2000, 1, 1);

        BigDecimal net = transactionRepository.sumPaidNetByEntityAndPaymentMethodSince(
            ba.getEntityId(), ba.getAccountLabel(), openingDate);

        if (net == null) net = BigDecimal.ZERO;

        return opening.add(net);
    }

    /**
     * Virtual "Ανεκχώρητο" account: net of paid transactions whose paymentMethod
     * doesn't match any other active (non-virtual) account in this entity.
     */
    private BigDecimal computeVirtualBalance(BankAccount virtualBa) {
        // Collect labels of all OTHER active non-virtual accounts in this entity
        List<BankAccount> all = bankAccountRepository.findByEntityIdOrderBySortOrderAsc(virtualBa.getEntityId());
        List<String> validLabels = new ArrayList<>();
        for (BankAccount other : all) {
            if (other.getId().equals(virtualBa.getId())) continue;
            if (!Boolean.TRUE.equals(other.getIsActive())) continue;
            if (Boolean.TRUE.equals(other.getIsVirtual())) continue;
            validLabels.add(other.getAccountLabel());
        }

        // Edge case: no other accounts → no orphans (every label is invalid by definition)
        // We still query so we get all paid transactions
        if (validLabels.isEmpty()) {
            validLabels.add("__NEVER_MATCH_SENTINEL__");
        }

        LocalDate openingDate = virtualBa.getOpeningDate() != null
            ? virtualBa.getOpeningDate()
            : LocalDate.of(2000, 1, 1);

        var orphans = transactionRepository.findOrphanPaidTransactions(
            virtualBa.getEntityId(), validLabels, openingDate);

        BigDecimal net = BigDecimal.ZERO;
        for (var t : orphans) {
            BigDecimal amount = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            if ("income".equals(t.getType())) {
                net = net.add(amount);
            } else {
                net = net.subtract(amount);
            }
        }
        return net;
    }
}
