package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.Payment;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.PaymentRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.security.JwtUtil;
import com.next2me.next2cash.service.AuditLogService;
import com.next2me.next2cash.service.BankBalanceService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private final JwtUtil jwtUtil;
    private final UserAccessService userAccessService;
    private final AuditLogService auditLogService;
    private final BankBalanceService bankBalanceService;

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    // GET /api/transactions?entityId=X&page=0&perPage=25&type=expense&status=unpaid&mode=ACTUAL
    //
    // Role access:
    //   - ADMIN, USER: allowed (then entity-level check)
    //   - ACCOUNTANT, VIEWER: 403 (use /api/documents/export or /api/dashboard instead)
    //
    // Phase 1 (Session 3, May 2026): added optional `mode` filter for Cash Planning.
    //   mode=ACTUAL   -> only historical/realized transactions (default behaviour pre-Phase-1)
    //   mode=PLANNED  -> only future planned transactions
    //   mode=all      -> both modes (or simply omit the param)
    // Filter is applied AFTER the repository query so existing query methods stay untouched.
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'VIEWER')")
    public ResponseEntity<?> getTransactions(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int perPage,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String mode) {

        // SECURITY GUARD: verify the user has access to this entity
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(user, entityId);

        PageRequest pageable = PageRequest.of(page, perPage,
            Sort.by(Sort.Direction.DESC, "docDate", "id"));

        Page<Transaction> result;

        // UNIVERSAL SEARCH: when search param is present, search ALL fields
        if (search != null && !search.isBlank()) {
            result = transactionRepository.universalSearch(entityId, search.trim(), pageable);
        } else if (from != null && to != null) {
            result = transactionRepository
                .findByEntityIdAndRecordStatusAndDocDateBetweenOrderByDocDateDesc(
                    entityId, "active",
                    LocalDate.parse(from), LocalDate.parse(to), pageable);
        } else if (type != null) {
            result = transactionRepository
                .findByEntityIdAndRecordStatusAndTypeOrderByDocDateDesc(
                    entityId, "active", type, pageable);
        } else if (status != null) {
            result = transactionRepository
                .findByEntityIdAndRecordStatusAndPaymentStatusOrderByDocDateDesc(
                    entityId, "active", status, pageable);
        } else if (category != null) {
            result = transactionRepository
                .findByEntityIdAndRecordStatusAndCategoryOrderByDocDateDesc(
                    entityId, "active", category, pageable);
        } else {
            result = transactionRepository
                .findByEntityIdAndRecordStatusOrderByDocDateDesc(
                    entityId, "active", pageable);
        }

        // Phase 1: optional mode filter applied AFTER paging (rare in practice, see note below).
        //
        // Trade-off: filtering after paging means the page count and total may include rows
        // that get dropped here. For the Cash Planning UI (90-day forecast), the frontend
        // typically requests perPage>=1000 so the filter is effectively complete. The
        // dedicated /planned endpoint below is the precise tool for forecast views.
        if (mode != null && !mode.isBlank() && !"all".equalsIgnoreCase(mode)) {
            String wantedMode = mode.toUpperCase();
            List<Transaction> filtered = result.getContent().stream()
                .filter(t -> {
                    String tm = t.getEntryMode();
                    if (tm == null) tm = "ACTUAL"; // safety: pre-Phase-1 rows
                    return wantedMode.equals(tm);
                })
                .toList();
            result = new PageImpl<>(filtered, pageable, filtered.size());
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data",    result.getContent(),
            "total",   result.getTotalElements(),
            "page",    result.getNumber(),
            "pages",   result.getTotalPages()
        ));
    }

    // GET /api/transactions/{id}
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'VIEWER')")
    public ResponseEntity<?> getTransaction(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer id) {

        User user = userAccessService.getCurrentUser(authHeader);

        return transactionRepository.findById(id)
            .map(t -> {
                // SECURITY GUARD: this transaction must belong to an accessible entity
                userAccessService.assertCanAccessEntity(user, t.getEntityId());
                return ResponseEntity.ok(Map.<String, Object>of("success", true, "data", t));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/transactions
    //
    // Phase 1 note: Jackson deserializes ALL @Column fields including the 9 new
    // Phase 1 fields (entryMode, isRecurring, recurrencePatternId, parentRecurringId,
    // projectId, confidencePct, scenarioId, convertedToTransactionId, convertedAt).
    // No code change needed here: the frontend can already send them and they will
    // be persisted automatically. Database CHECK constraints validate values.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> createTransaction(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Transaction transaction) {

        User user = userAccessService.getCurrentUser(authHeader);

        // SECURITY GUARD: user must have access to the target entity
        userAccessService.assertCanAccessEntity(user, transaction.getEntityId());

        // Set created_by from the authenticated user
        transaction.setCreatedBy(user.getId());
        transaction.setRecordStatus("active");

        // Auto-assign entity_number (next sequential number per entity)
        Integer maxEntityNumber = transactionRepository.findMaxEntityNumberByEntityId(transaction.getEntityId());
        transaction.setEntityNumber(maxEntityNumber == null ? 1 : maxEntityNumber + 1);

        // Calculate amount_remaining
        if (transaction.getAmountPaid() == null) {
            transaction.setAmountPaid(java.math.BigDecimal.ZERO);
        }
        transaction.setAmountRemaining(
            transaction.getAmount().subtract(transaction.getAmountPaid()));

        // Set accounting_period (YYYY-MM)
        if (transaction.getDocDate() != null && transaction.getAccountingPeriod() == null) {
            transaction.setAccountingPeriod(
                transaction.getDocDate().getYear() + "-" +
                String.format("%02d", transaction.getDocDate().getMonthValue()));
        }

        Transaction saved = transactionRepository.save(transaction);
        auditLogService.log(saved.getEntityId(), user.getId(), user.getUsername(), "TRANSACTION_CREATE", "transactions", saved.getId().toString(), "{\"type\":\"" + saved.getType() + "\",\"amount\":" + saved.getAmount() + "}");

        // Auto-recompute bank balance for the affected payment method (Phase 3).
        tryRecomputeBank(saved.getEntityId(), saved.getPaymentMethod(), saved.getId(), "CREATE");

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data",    saved,
            "id",      saved.getId()
        ));
    }

    // PUT /api/transactions/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> updateTransaction(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer id,
            @RequestBody Transaction updates) {

        User user = userAccessService.getCurrentUser(authHeader);

        return transactionRepository.findById(id).map(t -> {
            // SECURITY GUARD: the existing transaction's entity must be accessible
            userAccessService.assertCanAccessEntity(user, t.getEntityId());

              // [Step 59-C-2] Block paymentDate edit when split payments exist.
              // If the parent has >1 Payment records, only the Payment-level edit UI
              // (per virtual row) is the safe place to change dates. Editing here would
              // be silently overridden by recalcParentTransaction (max payment date).
              if (updates.getPaymentDate() != null
                      && (t.getPaymentDate() == null
                          || !updates.getPaymentDate().equals(t.getPaymentDate()))) {
                  long payCount = ((Number) entityManager.createNativeQuery(
                          "SELECT COUNT(*) FROM payments WHERE transaction_id = :tid")
                      .setParameter("tid", t.getId())
                      .getSingleResult()).longValue();
                  if (payCount > 1L) {
                      return ResponseEntity.badRequest().body(Map.<String, Object>of(
                          "success", false,
                          "error",   "split_payments_present",
                          "message", "Has multiple payments \u2014 edit Payment records individually.",
                          "paymentCount", payCount
                      ));
                  }
              }

            // Also block attempts to MOVE a transaction to an entity the user can't access
            if (updates.getEntityId() != null && !updates.getEntityId().equals(t.getEntityId())) {
                userAccessService.assertCanAccessEntity(user, updates.getEntityId());
            }

            // ===== S94-EDIT-PAID-NO-PAYMENT-GUARD =====
            // Block AMOUNT edits when the transaction is marked paid/received (with
            // amount_paid > 0) but has NO Payment record. These are legacy imports
            // and S92 DB-fixed rows where status was set without creating a Payment.
            // Without this guard, the S93 guard does not fire (no Payment row to find)
            // and the subsequent S93-RECALC can produce a negative amount_remaining,
            // reintroducing exactly the broken-record class of bug that S92 fixed.
            // Rule: status must first move to unpaid before amount can be edited.
            // Greek string uses backslash-u escapes (file-encoding rule).
            if (updates.getAmount() != null
                    && t.getAmount() != null
                    && updates.getAmount().compareTo(t.getAmount()) != 0) {
                String ps = t.getPaymentStatus();
                boolean isPaidStatus = ps != null &&
                        ("paid".equalsIgnoreCase(ps) || "received".equalsIgnoreCase(ps));
                boolean hasAmountPaid = t.getAmountPaid() != null &&
                        t.getAmountPaid().compareTo(java.math.BigDecimal.ZERO) > 0;
                if (isPaidStatus && hasAmountPaid) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Payment> _s94Payments = entityManager.createQuery(
                            "SELECT p FROM Payment p WHERE p.transactionId = :tid",
                            Payment.class)
                        .setParameter("tid", t.getId())
                        .getResultList();
                    if (_s94Payments.isEmpty()) {
                        return ResponseEntity.badRequest().body(Map.<String, Object>of(
                            "success", false,
                            "error",   "paid_no_payment_record",
                            "message", "\u0397 \u03b5\u03b3\u03b3\u03c1\u03b1\u03c6\u03ae \u03b5\u03af\u03bd\u03b1\u03b9 \u03ae\u03b4\u03b7 \u03b5\u03be\u03bf\u03c6\u03bb\u03b7\u03bc\u03ad\u03bd\u03b7 \u03c7\u03c9\u03c1\u03af\u03c2 \u03b5\u03b3\u03b3\u03c1\u03b1\u03c6\u03ae \u03c0\u03bb\u03b7\u03c1\u03c9\u03bc\u03ae\u03c2 (legacy). \u0393\u03b9\u03b1 \u03b1\u03bb\u03bb\u03b1\u03b3\u03ae \u03c0\u03bf\u03c3\u03bf\u03cd, \u03ac\u03bb\u03bb\u03b1\u03be\u03b5 \u03c0\u03c1\u03ce\u03c4\u03b1 \u03c4\u03b7\u03bd \u03ba\u03b1\u03c4\u03ac\u03c3\u03c4\u03b1\u03c3\u03b7 \u03c3\u03b5 unpaid.",
                            "paymentStatus", ps,
                            "amountPaid",    t.getAmountPaid()
                        ));
                    }
                }
            }

            // ===== S93-EDIT-PAID-GUARD =====
            // Block AMOUNT edits when the transaction already has >=1 Payment record.
            // Root cause of stale derived fields: changing amount on a paid txn left
            // amount_paid/amount_remaining out of sync. Rule: paid txn => amount is
            // locked. User must delete the payment first, then edit the amount.
            // Non-amount fields (description, category, etc.) are still allowed.
            if (updates.getAmount() != null
                    && t.getAmount() != null
                    && updates.getAmount().compareTo(t.getAmount()) != 0) {
                @SuppressWarnings("unchecked")
                java.util.List<Payment> existingPayments = entityManager.createQuery(
                        "SELECT p FROM Payment p WHERE p.transactionId = :tid",
                        Payment.class)
                    .setParameter("tid", t.getId())
                    .getResultList();
                if (!existingPayments.isEmpty()) {
                    Payment first = existingPayments.get(0);
                    // Greek message below uses backslash-u escapes (file-encoding rule).
                    return ResponseEntity.badRequest().body(Map.<String, Object>of(
                        "success", false,
                        "error",   "payment_present",
                        "message", "Η εγγραφή έχει εξόφληση. Σβήσε πρώτα την πληρωμή για να αλλάξεις το ποσό.",
                        "paymentCount", existingPayments.size(),
                        "paymentId", first.getId(),
                        "paymentAmount", first.getAmount(),
                        "paymentDate", first.getPaymentDate() != null ? first.getPaymentDate().toString() : ""
                    ));
                }
            }

            // Capture OLD state BEFORE setters run (Phase 3, Step 3.2).
            // Needed so we can recompute the previous bank account if paymentMethod or entity change.
            final UUID   oldEntityId      = t.getEntityId();
            final String oldPaymentMethod = t.getPaymentMethod();

            t.setUpdatedBy(user.getId());

            if (updates.getDocDate()        != null) t.setDocDate(updates.getDocDate());
            if (updates.getType()           != null) t.setType(updates.getType());
            if (updates.getCounterparty()   != null) t.setCounterparty(updates.getCounterparty());
            if (updates.getAccount()        != null) t.setAccount(updates.getAccount());
            if (updates.getCategory()       != null) t.setCategory(updates.getCategory());
            if (updates.getSubcategory()    != null) t.setSubcategory(updates.getSubcategory());
            if (updates.getDescription()    != null) t.setDescription(updates.getDescription());
            if (updates.getAmount()         != null) t.setAmount(updates.getAmount());
            if (updates.getPaymentMethod()  != null) t.setPaymentMethod(updates.getPaymentMethod());
            if (updates.getPaymentStatus()  != null) t.setPaymentStatus(updates.getPaymentStatus());
            if (updates.getPaymentDate()    != null) t.setPaymentDate(updates.getPaymentDate());
            if (updates.getDocStatus()      != null) t.setDocStatus(updates.getDocStatus());

            // -------------------------------------------------------------------
            // Phase 1 (Session 3, May 2026): partial updates for Cash Planning.
            //
            // Frontend can edit any of these on an existing transaction without
            // wiping the others. The conversion fields (convertedToTransactionId,
            // convertedAt) are intentionally NOT exposed here -- they are managed
            // exclusively by POST /api/transactions/{id}/convert below.
            // -------------------------------------------------------------------
            if (updates.getEntryMode()            != null) t.setEntryMode(updates.getEntryMode());
            if (updates.getIsRecurring()          != null) t.setIsRecurring(updates.getIsRecurring());
            if (updates.getRecurrencePatternId()  != null) t.setRecurrencePatternId(updates.getRecurrencePatternId());
            if (updates.getParentRecurringId()    != null) t.setParentRecurringId(updates.getParentRecurringId());
            if (updates.getProjectId()            != null) t.setProjectId(updates.getProjectId());
            if (updates.getConfidencePct()        != null) t.setConfidencePct(updates.getConfidencePct());
            if (updates.getScenarioId()           != null) t.setScenarioId(updates.getScenarioId());

            // ===== S93-RECALC ===== (paired with S93-EDIT-PAID-GUARD)
            // For UNPAID txns (no Payment record blocked above), if amount changed,
            // recompute amount_remaining = amount - amount_paid. Fixes the original
            // root cause where PUT changed amount but never recalculated remaining.
            if (updates.getAmount() != null && t.getAmount() != null) {
                java.math.BigDecimal paid = t.getAmountPaid() != null
                    ? t.getAmountPaid() : java.math.BigDecimal.ZERO;
                t.setAmountRemaining(t.getAmount().subtract(paid));
            }

            Transaction saved = transactionRepository.save(t);
            auditLogService.log(saved.getEntityId(), user.getId(), user.getUsername(), "TRANSACTION_UPDATE", "transactions", saved.getId().toString(), null);

              // [Step 59-C-2] Sync Payment.paymentDate to Transaction.paymentDate.
              // Single-payment case: keep the Payment record aligned with the parent
              // so virtual rows + cashflow + bank balances all use the same date.
              // Split-payment case is already blocked above by the pre-guard.
              if (updates.getPaymentDate() != null && saved.getPaymentDate() != null) {
                  @SuppressWarnings("unchecked")
                  java.util.List<Payment> linkedPayments = entityManager.createQuery(
                      "SELECT p FROM Payment p WHERE p.transactionId = :tid",
                      Payment.class)
                    .setParameter("tid", saved.getId())
                    .getResultList();
                  if (linkedPayments.size() == 1) {
                      Payment p = linkedPayments.get(0);
                      if (!saved.getPaymentDate().equals(p.getPaymentDate())) {
                          p.setPaymentDate(saved.getPaymentDate());
                          paymentRepository.save(p);
                      }
                  }
              }

            // Auto-recompute bank balances (Phase 3, Step 3.2).
            // Always recompute the current (post-update) bucket; if the paymentMethod or entity
            // changed, ALSO recompute the OLD bucket so the previous bank account is corrected.
            tryRecomputeBank(saved.getEntityId(), saved.getPaymentMethod(), saved.getId(), "UPDATE-new");
            if (!Objects.equals(oldPaymentMethod, saved.getPaymentMethod())
                    || !Objects.equals(oldEntityId, saved.getEntityId())) {
                tryRecomputeBank(oldEntityId, oldPaymentMethod, saved.getId(), "UPDATE-old");
            }

            return ResponseEntity.ok(Map.<String, Object>of("success", true, "data", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ----------------------------------------------------------------
    // Phase 3 helper: bank balance recompute with null-guard + try/catch.
    // Failures are logged as WARN but never block the parent operation.
    // Used by CREATE (3.1), UPDATE (3.2), DELETE (3.3) and Mark-Paid (3.4).
    // ----------------------------------------------------------------
    private void tryRecomputeBank(UUID entityId, String paymentMethod, Object txnId, String action) {
        if (entityId == null) return;
        if (paymentMethod == null || paymentMethod.isBlank()) return;
        try {
            bankBalanceService.recomputeForPaymentMethod(entityId, paymentMethod);
        } catch (Exception ex) {
            log.warn("Auto-recompute failed for entity={} paymentMethod={} after {} txn id={}: {}",
                    entityId, paymentMethod, action, txnId, ex.getMessage());
        }
    }

    // DELETE /api/transactions/{id}  -->  soft delete (void)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> voidTransaction(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer id) {

        User user = userAccessService.getCurrentUser(authHeader);

        return transactionRepository.findById(id).map(t -> {
            // SECURITY GUARD: cannot void a transaction in an inaccessible entity
            userAccessService.assertCanAccessEntity(user, t.getEntityId());

            t.setRecordStatus("void");
            t.setUpdatedBy(user.getId());
            transactionRepository.save(t);
            auditLogService.log(t.getEntityId(), user.getId(), user.getUsername(), "TRANSACTION_VOID", "transactions", t.getId().toString(), null);

            // Auto-recompute bank balance after soft delete (Phase 3, Step 3.3).
            // The voided transaction is now excluded from the paid-only sum, so the affected
            // bank account balance must be refreshed. Helper handles null-guard + try/catch.
            tryRecomputeBank(t.getEntityId(), t.getPaymentMethod(), t.getId(), "DELETE");

            return ResponseEntity.ok(Map.<String, Object>of("success", true, "message", "Transaction voided"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // PATCH /api/transactions/{id}/blob-file-ids
    // Admin utility: directly set blob_file_ids on a transaction.
    // Used for test setups + future blob management. ADMIN only.
    // Body: { "blobFileIds": "path/to/blob1,path/to/blob2" }
    @org.springframework.web.bind.annotation.PatchMapping("/{id}/blob-file-ids")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateBlobFileIds(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer id,
            @RequestBody Map<String, String> body) {

        User user = userAccessService.getCurrentUser(authHeader);

        return transactionRepository.findById(id).map(t -> {
            userAccessService.assertCanAccessEntity(user, t.getEntityId());

            String newValue = body.get("blobFileIds");
            t.setBlobFileIds(newValue);
            t.setUpdatedBy(user.getId());
            Transaction saved = transactionRepository.save(t);

            return ResponseEntity.ok(Map.<String, Object>of(
                "success",      true,
                "id",           saved.getId(),
                "entityNumber", saved.getEntityNumber() != null ? saved.getEntityNumber() : 0,
                "blobFileIds",  saved.getBlobFileIds() != null ? saved.getBlobFileIds() : ""
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    // GET /api/transactions/next-number?entityId=X
    @GetMapping("/next-number")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> getNextNumber(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId) {

        // SECURITY GUARD
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(user, entityId);

        Integer maxNumber = transactionRepository.findMaxEntityNumberByEntityId(entityId);
        int nextNumber = (maxNumber == null) ? 1 : maxNumber + 1;
        return ResponseEntity.ok(Map.of("success", true, "nextNumber", nextNumber));
    }

    // GET /api/transactions/search?entityId=X&q=...
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'VIEWER')")
    public ResponseEntity<?> searchTransactions(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestParam String q) {

        // SECURITY GUARD
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(user, entityId);

        var results = transactionRepository.searchByDescription(
            entityId, q, PageRequest.of(0, 20));

        return ResponseEntity.ok(Map.of("success", true, "data", results));
    }

    // ===================================================================
    // PHASE H -- KARTELES ENDPOINTS
    // ===================================================================

    // GET /api/transactions/counterparties?entityId=X
    // Returns list of counterparties with aggregated metrics (count, total, paid, balance).
    // Service-layer aggregation in Java -- NO SQL GROUP BY (Postgres-safe).
    @GetMapping("/counterparties")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'VIEWER')")
    public ResponseEntity<?> getCounterparties(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId) {

        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(user, entityId);

        List<Transaction> all = transactionRepository
            .findByEntityIdAndRecordStatusOrderByCounterpartyAscDocDateDesc(entityId, "active");

        // Group by counterparty in Java (null-safe)
        Map<String, List<Transaction>> grouped = new java.util.LinkedHashMap<>();
        for (Transaction t : all) {
            String cp = (t.getCounterparty() == null || t.getCounterparty().isBlank())
                ? "(no counterparty)"
                : t.getCounterparty().trim();
            grouped.computeIfAbsent(cp, k -> new java.util.ArrayList<>()).add(t);
        }

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map.Entry<String, List<Transaction>> e : grouped.entrySet()) {
            List<Transaction> txns = e.getValue();
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            java.math.BigDecimal paid = java.math.BigDecimal.ZERO;
            java.math.BigDecimal remaining = java.math.BigDecimal.ZERO;
            int income = 0, expense = 0;
            for (Transaction t : txns) {
                if (t.getAmount() != null) total = total.add(t.getAmount());
                if (t.getAmountPaid() != null) paid = paid.add(t.getAmountPaid());
                if (t.getAmountRemaining() != null) remaining = remaining.add(t.getAmountRemaining());
                if ("income".equals(t.getType())) income++;
                else if ("expense".equals(t.getType())) expense++;
            }
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("counterparty", e.getKey());
            row.put("count", txns.size());
            row.put("incomeCount", income);
            row.put("expenseCount", expense);
            row.put("total", total);
            row.put("paid", paid);
            row.put("remaining", remaining);
            result.add(row);
        }

        result.sort((a, b) -> Integer.compare((Integer) b.get("count"), (Integer) a.get("count")));

        return ResponseEntity.ok(Map.of("success", true, "data", result, "total", result.size()));
    }

    // GET /api/transactions/by-counterparty?entityId=X&counterparty=NAME
    // Returns all active transactions for a specific counterparty.
    @GetMapping("/by-counterparty")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'VIEWER')")
    public ResponseEntity<?> getByCounterparty(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestParam String counterparty) {

        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(user, entityId);

        List<Transaction> results;
        if ("(no counterparty)".equals(counterparty)) {
            results = transactionRepository
                .findByEntityIdAndRecordStatusOrderByCounterpartyAscDocDateDesc(entityId, "active")
                .stream()
                .filter(t -> t.getCounterparty() == null || t.getCounterparty().isBlank())
                .toList();
        } else {
            results = transactionRepository
                .findByEntityIdAndCounterpartyAndRecordStatusOrderByDocDateDesc(
                    entityId, counterparty, "active");
        }

        return ResponseEntity.ok(Map.of("success", true, "data", results, "total", results.size()));
    }

    // ===================================================================
    // PHASE 1 (Session 3, May 2026) -- CASH PLANNING ENDPOINTS
    // ===================================================================

    /**
     * POST /api/transactions/{id}/convert
     *
     * Convert a PLANNED transaction into an ACTUAL one. This is the moment
     * when a forecasted entry becomes realized. The semantics:
     *
     *   1. The original PLANNED row is preserved (audit trail).
     *   2. A NEW ACTUAL row is created with copied fields, entryMode=ACTUAL,
     *      and confidencePct=100. Its entity_number gets the next sequential
     *      value (so it appears in the standard ledger).
     *   3. The original is marked: convertedToTransactionId = newId, convertedAt = now.
     *
     * Optional body (all fields optional, used to override the new ACTUAL row):
     *   {
     *     "docDate":       "2026-06-12",
     *     "amount":        347.50,
     *     "paymentDate":   "2026-06-12",
     *     "paymentMethod": "Eurobank",
     *     "paymentStatus": "paid",
     *     "description":   "AWS June (actual)"
     *   }
     *
     * Body fields not provided are copied as-is from the original PLANNED row.
     *
     * Returns the newly-created ACTUAL transaction.
     *
     * ADMIN + USER only. Idempotency: if the original already has
     * convertedToTransactionId set, returns 409 with the existing target.
     */
    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional
    public ResponseEntity<?> convertPlannedToActual(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer id,
            @RequestBody(required = false) Transaction overrides) {

        User user = userAccessService.getCurrentUser(authHeader);

        return transactionRepository.findById(id).map(original -> {
            // SECURITY GUARD: original must belong to an accessible entity
            userAccessService.assertCanAccessEntity(user, original.getEntityId());

            // Validation: only PLANNED rows can be converted.
            String mode = original.getEntryMode() == null ? "ACTUAL" : original.getEntryMode();
            if (!"PLANNED".equals(mode)) {
                return ResponseEntity.badRequest().body(Map.<String, Object>of(
                    "success", false,
                    "error",   "not_planned",
                    "message", "Only PLANNED transactions can be converted to ACTUAL."
                ));
            }

            // Idempotency: if already converted, do not create a second ACTUAL.
            if (original.getConvertedToTransactionId() != null) {
                return ResponseEntity.status(409).body(Map.<String, Object>of(
                    "success", false,
                    "error",   "already_converted",
                    "message", "This planned transaction has already been converted.",
                    "convertedToTransactionId", original.getConvertedToTransactionId(),
                    "convertedAt", original.getConvertedAt()
                ));
            }

            // Build the new ACTUAL row by copying the original.
            Transaction actual = new Transaction();
            actual.setEntityId(original.getEntityId());
            actual.setType(original.getType());
            actual.setCounterparty(original.getCounterparty());
            actual.setAccount(original.getAccount());
            actual.setCategory(original.getCategory());
            actual.setSubcategory(original.getSubcategory());
            actual.setDescription(original.getDescription());
            actual.setAmount(original.getAmount());
            actual.setAmountPaid(original.getAmountPaid());
            actual.setPaymentMethod(original.getPaymentMethod());
            actual.setPaymentStatus(original.getPaymentStatus());
            actual.setPaymentDate(original.getPaymentDate());
            actual.setDueDate(original.getDueDate());
            actual.setDocStatus(original.getDocStatus());
            // docDate defaults to today if not overridden -- the conversion moment.
            actual.setDocDate(LocalDate.now());
            // Project / scenario / recurrence linkage stays the same.
            actual.setProjectId(original.getProjectId());
            actual.setScenarioId(original.getScenarioId());
            actual.setRecurrencePatternId(original.getRecurrencePatternId());
            actual.setParentRecurringId(original.getParentRecurringId());

            // Apply optional overrides from the body.
            if (overrides != null) {
                if (overrides.getDocDate()       != null) actual.setDocDate(overrides.getDocDate());
                if (overrides.getAmount()        != null) actual.setAmount(overrides.getAmount());
                if (overrides.getAmountPaid()    != null) actual.setAmountPaid(overrides.getAmountPaid());
                if (overrides.getPaymentMethod() != null) actual.setPaymentMethod(overrides.getPaymentMethod());
                if (overrides.getPaymentStatus() != null) actual.setPaymentStatus(overrides.getPaymentStatus());
                if (overrides.getPaymentDate()   != null) actual.setPaymentDate(overrides.getPaymentDate());
                if (overrides.getDescription()   != null) actual.setDescription(overrides.getDescription());
                if (overrides.getDocStatus()     != null) actual.setDocStatus(overrides.getDocStatus());
                if (overrides.getCounterparty()  != null) actual.setCounterparty(overrides.getCounterparty());
                if (overrides.getCategory()      != null) actual.setCategory(overrides.getCategory());
                if (overrides.getSubcategory()   != null) actual.setSubcategory(overrides.getSubcategory());
            }

            // Mark as ACTUAL with full confidence.
            actual.setEntryMode("ACTUAL");
            actual.setIsRecurring(false); // the child is never itself the "mother"
            actual.setConfidencePct(100);

            // System-managed fields
            actual.setCreatedBy(user.getId());
            actual.setRecordStatus("active");

            // Auto-assign entity_number
            Integer maxEntityNumber = transactionRepository.findMaxEntityNumberByEntityId(actual.getEntityId());
            actual.setEntityNumber(maxEntityNumber == null ? 1 : maxEntityNumber + 1);

            // amount_remaining = amount - amountPaid (guard against null)
            if (actual.getAmountPaid() == null) {
                actual.setAmountPaid(java.math.BigDecimal.ZERO);
            }
            if (actual.getAmount() != null) {
                actual.setAmountRemaining(
                    actual.getAmount().subtract(actual.getAmountPaid()));
            }

            // accounting_period from docDate
            if (actual.getDocDate() != null) {
                actual.setAccountingPeriod(
                    actual.getDocDate().getYear() + "-" +
                    String.format("%02d", actual.getDocDate().getMonthValue()));
            }

            Transaction savedActual = transactionRepository.save(actual);

            // Update the original PLANNED row with audit trail.
            original.setConvertedToTransactionId(savedActual.getId());
            original.setConvertedAt(LocalDateTime.now());
            original.setUpdatedBy(user.getId());
            transactionRepository.save(original);

            // Audit logs: one for the new ACTUAL, one for the conversion on the PLANNED.
            auditLogService.log(
                savedActual.getEntityId(), user.getId(), user.getUsername(),
                "TRANSACTION_CREATE_FROM_PLANNED", "transactions",
                savedActual.getId().toString(),
                "{\"convertedFrom\":" + original.getId() + "}");

            auditLogService.log(
                original.getEntityId(), user.getId(), user.getUsername(),
                "TRANSACTION_CONVERT_PLANNED", "transactions",
                original.getId().toString(),
                "{\"convertedTo\":" + savedActual.getId() + "}");

            // Recompute bank balance for the new ACTUAL's payment method if it's paid.
            tryRecomputeBank(savedActual.getEntityId(), savedActual.getPaymentMethod(),
                savedActual.getId(), "CONVERT");

            return ResponseEntity.ok(Map.<String, Object>of(
                "success", true,
                "data",    savedActual,
                "original", original
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}
