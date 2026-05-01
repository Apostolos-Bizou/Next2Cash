package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Payment repository.
 *
 * Phase K — Karteles integration: payments are fetched by entity and then
 * filtered in Java alongside transactions via CardService's rule engine.
 * Rule matching happens on the parent transaction, not on the payment itself,
 * so we only need a simple "find all for entity" query here.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    /** All payments for an entity, ordered by most recent first. */
    List<Payment> findByEntityIdOrderByPaymentDateDescIdDesc(UUID entityId);

    /**
     * All payments for an entity that link to a transaction in the given id list.
     * Used by CardService to fetch only the payments relevant to the matched
     * transactions, avoiding a second full-table scan.
     */
    @Query("SELECT p FROM Payment p " +
           "WHERE p.entityId = :entityId " +
           "AND p.transactionId IN :transactionIds " +
           "ORDER BY p.paymentDate DESC, p.id DESC")
    List<Payment> findByEntityIdAndTransactionIdIn(
        @Param("entityId") UUID entityId,
        @Param("transactionIds") List<Integer> transactionIds);

    /**
     * Orphan payments — no transaction_id link.
     * Optional visibility feature; kept for data migration scenarios.
     */
    @Query("SELECT p FROM Payment p " +
           "WHERE p.entityId = :entityId AND p.transactionId IS NULL " +
           "ORDER BY p.paymentDate DESC, p.id DESC")
    List<Payment> findOrphansByEntityId(@Param("entityId") UUID entityId);
}