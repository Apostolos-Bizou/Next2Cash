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

    Page<Transaction> findByEntityIdAndRecordStatusOrderByDocDateDesc(
        UUID entityId, String recordStatus, Pageable pageable);

    Page<Transaction> findByEntityIdAndRecordStatusAndTypeOrderByDocDateDesc(
        UUID entityId, String recordStatus, String type, Pageable pageable);

    Page<Transaction> findByEntityIdAndRecordStatusAndPaymentStatusOrderByDocDateDesc(
        UUID entityId, String recordStatus, String paymentStatus, Pageable pageable);

    Page<Transaction> findByEntityIdAndRecordStatusAndCategoryOrderByDocDateDesc(
        UUID entityId, String recordStatus, String category, Pageable pageable);

    Page<Transaction> findByEntityIdAndRecordStatusAndDocDateBetweenOrderByDocDateDesc(
        UUID entityId, String recordStatus, LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.type = 'income' AND t.docDate BETWEEN :from AND :to")
    BigDecimal sumIncomeByEntityAndPeriod(@Param("entityId") UUID entityId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.type = 'expense' AND t.docDate BETWEEN :from AND :to")
    BigDecimal sumExpenseByEntityAndPeriod(@Param("entityId") UUID entityId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(t.amountRemaining), 0) FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.paymentStatus = 'urgent'")
    BigDecimal sumUrgentRemaining(@Param("entityId") UUID entityId);

    @Query("SELECT COALESCE(SUM(t.amountRemaining), 0) FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.paymentStatus IN ('unpaid', 'urgent')")
    BigDecimal sumUnpaidRemaining(@Param("entityId") UUID entityId);

    @Query("SELECT t FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "ORDER BY t.docDate DESC, t.id DESC")
    List<Transaction> findRecentByEntity(@Param("entityId") UUID entityId, Pageable pageable);

    @Query("SELECT t FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY t.docDate DESC")
    List<Transaction> searchByDescription(@Param("entityId") UUID entityId,
                                          @Param("query") String query,
                                          Pageable pageable);

    @Query("SELECT t FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.blobFileIds IS NOT NULL AND t.blobFileIds != '' " +
           "AND t.docDate BETWEEN :from AND :to " +
           "ORDER BY t.docDate ASC")
    List<Transaction> findWithDocumentsByEntityAndDateRange(
        @Param("entityId") UUID entityId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to);

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

    // Balance trend: daily running balance for a period (for line chart)
    @Query(value = "SELECT t.doc_date, " +
           "SUM(CASE WHEN t.type = 'income' THEN t.amount ELSE -t.amount END) " +
           "OVER (ORDER BY t.doc_date, t.id) as running_balance " +
           "FROM transactions t " +
           "WHERE t.entity_id = :entityId AND t.record_status = 'active' " +
           "AND t.doc_date BETWEEN :from AND :to " +
           "ORDER BY t.doc_date, t.id",
           nativeQuery = true)
    List<Object[]> getBalanceTrend(@Param("entityId") UUID entityId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    // Yearly report by category (for Etisia Sygkrisi chart)
    @Query("SELECT EXTRACT(YEAR FROM t.docDate) as year, " +
           "t.category as category, " +
           "SUM(t.amount) as total " +
           "FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.type = 'expense' " +
           "GROUP BY EXTRACT(YEAR FROM t.docDate), t.category " +
           "ORDER BY year, total DESC")
    List<Object[]> getYearlyReport(@Param("entityId") UUID entityId);

    // Category breakdown with subcategory for period
    @Query("SELECT t.category, t.account, " +
           "SUM(CASE WHEN t.type = 'income' THEN t.amount ELSE 0 END) as income, " +
           "SUM(CASE WHEN t.type = 'expense' THEN t.amount ELSE 0 END) as expense " +
           "FROM Transaction t " +
           "WHERE t.entityId = :entityId AND t.recordStatus = 'active' " +
           "AND t.docDate BETWEEN :from AND :to " +
           "GROUP BY t.category, t.account " +
           "ORDER BY expense DESC")
    List<Object[]> getCategoryBreakdown(@Param("entityId") UUID entityId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);


    // Get max entity_number for a specific entity (for auto-assigning next entity_number)
    @Query("SELECT MAX(t.entityNumber) FROM Transaction t WHERE t.entityId = :entityId")
    Integer findMaxEntityNumberByEntityId(@Param("entityId") UUID entityId);

    // Phase H (Karteles) — all active transactions for entity, ordered by counterparty (for grouping in service layer)
    List<Transaction> findByEntityIdAndRecordStatusOrderByCounterpartyAscDocDateDesc(
        UUID entityId, String recordStatus);

    // Phase H v2 — all active transactions for entity, ordered by date DESC (for card rule engine).
    List<Transaction> findByEntityIdAndRecordStatusOrderByDocDateDesc(
        UUID entityId, String recordStatus);

    // Phase H (Karteles) — all active transactions of a specific counterparty, for detail view
    List<Transaction> findByEntityIdAndCounterpartyAndRecordStatusOrderByDocDateDesc(
        UUID entityId, String counterparty, String recordStatus);
}