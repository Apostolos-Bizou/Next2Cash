package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    // All active transactions for entity (paginated)
    Page<Transaction> findByEntityIdAndRecordStatusOrderByDocDateDesc(
        UUID entityId, String recordStatus, Pageable pageable);

    // Filter by type
    Page<Transaction> findByEntityIdAndRecordStatusAndTypeOrderByDocDateDesc(
        UUID entityId, String recordStatus, String type, Pageable pageable);

    // Filter by payment status
    Page<Transaction> findByEntityIdAndRecordStatusAndPaymentStatusOrderByDocDateDesc(
        UUID entityId, String recordStatus, String paymentStatus, Pageable pageable);

    // Filter by category
    Page<Transaction> findByEntityIdAndRecordStatusAndCategoryOrderByDocDateDesc(
        UUID entityId, String recordStatus, String category, Pageable pageable);

    // Date range
    Page<Transaction> findByEntityIdAndRecordStatusAndDocDateBetweenOrderByDocDateDesc(
        UUID entityId, String recordStatus, LocalDate from, LocalDate to, Pageable pageable);

    // Dashboard KPIs - total income for period
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.type = 'income' AND t.docDate BETWEEN :from AND :to")
    BigDecimal sumIncomeByEntityAndPeriod(@Param("entityId") UUID entityId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    // Dashboard KPIs - total expense for period
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.type = 'expense' AND t.docDate BETWEEN :from AND :to")
    BigDecimal sumExpenseByEntityAndPeriod(@Param("entityId") UUID entityId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    // Urgent transactions (Εκκρεμείς) - used for dashboard cash calculation
    @Query("SELECT COALESCE(SUM(t.amountRemaining), 0) FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.paymentStatus = 'urgent'")
    BigDecimal sumUrgentRemaining(@Param("entityId") UUID entityId);

    // Unpaid + urgent
    @Query("SELECT COALESCE(SUM(t.amountRemaining), 0) FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.paymentStatus IN ('unpaid', 'urgent')")
    BigDecimal sumUnpaidRemaining(@Param("entityId") UUID entityId);

    // Recent transactions for dashboard
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "ORDER BY t.docDate DESC, t.id DESC")
    List<Transaction> findRecentByEntity(@Param("entityId") UUID entityId, Pageable pageable);

    // Search by description (for autocomplete)
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY t.docDate DESC")
    List<Transaction> searchByDescription(@Param("entityId") UUID entityId,
                                          @Param("query") String query,
                                          Pageable pageable);

    // ZIP export - all documents in date range
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.blobFileIds IS NOT NULL AND t.blobFileIds != '' " +
           "AND t.docDate BETWEEN :from AND :to " +
           "ORDER BY t.docDate ASC")
    List<Transaction> findWithDocumentsByEntityAndDateRange(
        @Param("entityId") UUID entityId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to);

    // Monthly report
    @Query("SELECT EXTRACT(MONTH FROM t.docDate) as month, " +
           "t.category as category, " +
           "SUM(t.amount) as total " +
           "FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND EXTRACT(YEAR FROM t.docDate) = :year " +
           "GROUP BY EXTRACT(MONTH FROM t.docDate), t.category " +
           "ORDER BY month")
    List<Object[]> getMonthlyReport(@Param("entityId") UUID entityId,
                                    @Param("year") int year);
}
