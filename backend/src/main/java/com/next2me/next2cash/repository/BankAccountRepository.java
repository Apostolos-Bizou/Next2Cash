package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByEntityIdAndIsActiveTrueOrderBySortOrderAsc(UUID entityId);

    List<BankAccount> findByIsActiveTrueOrderByEntityIdAscSortOrderAsc();

    // === Phase 2 (Session #49) — Auto-compute support ===

    /** All accounts for an entity (including virtual). */
    List<BankAccount> findByEntityIdOrderBySortOrderAsc(UUID entityId);

    /** Locate the system "Ανεκχώρητο" virtual account for an entity. */
    Optional<BankAccount> findFirstByEntityIdAndAccountLabel(UUID entityId, String accountLabel);

    /** Bank account that matches a transaction's paymentMethod (used by auto-compute). */
    Optional<BankAccount> findFirstByEntityIdAndAccountLabelAndIsActiveTrue(UUID entityId, String accountLabel);
}
