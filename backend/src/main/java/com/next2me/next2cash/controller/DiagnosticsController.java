package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.Payment;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.repository.PaymentRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Session #59 - Diagnostic + repair endpoints (admin only).
 *
 * - GET  /transaction/{id}          read-only inspection of a single txn
 * - GET  /payment-date-mismatches   dry-run: list desync between
 *                                   Transaction.paymentDate and Payment.paymentDate
 * - POST /sync-payment-dates        execute the sync (backfill)
 *
 * Mounted under /api/admin/** so the SecurityConfig rule
 * "/api/admin/**".hasAnyRole("ADMIN", "USER") applies. A second manual
 * admin gate (adminGate()) provides defence in depth.
 *
 * The sync logic is intentionally CONSERVATIVE:
 *   - Only single-payment transactions are touched.
 *   - Split payments (>1 payments per txn) are reported and skipped.
 *   - Orphan payments (no transactionId) are reported and skipped.
 * Goal: repair trivial 1:1 cases without ever guessing for the rest.
 */
@RestController
@RequestMapping("/api/admin/diagnostics")
public class DiagnosticsController {

    @PersistenceContext
    private EntityManager em;

    private final TransactionRepository transactionRepository;
    private final PaymentRepository paymentRepository;

    public DiagnosticsController(TransactionRepository transactionRepository,
                                 PaymentRepository paymentRepository) {
        this.transactionRepository = transactionRepository;
        this.paymentRepository = paymentRepository;
    }

    // ================================================================
    // Shared admin gate
    // ================================================================
    private ResponseEntity<Map<String, Object>> adminGate() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "unauthenticated");
            return ResponseEntity.status(401).body(err);
        }
        boolean isAdmin = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equalsIgnoreCase("ROLE_ADMIN")
                        || a.equalsIgnoreCase("ADMIN")
                        || a.equalsIgnoreCase("admin"));
        if (!isAdmin) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "admin_role_required");
            err.put("authoritiesSeen", auth.getAuthorities().toString());
            return ResponseEntity.status(403).body(err);
        }
        return null; // OK
    }

    // ================================================================
    // GET /api/admin/diagnostics/transaction/{id}
    // ================================================================
    @GetMapping("/transaction/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> diagnoseTransaction(@PathVariable Integer id) {
        ResponseEntity<Map<String, Object>> gate = adminGate();
        if (gate != null) return gate;

        var txnOpt = transactionRepository.findById(id);
        if (txnOpt.isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "transaction_not_found");
            err.put("requestedId", id);
            return ResponseEntity.status(404).body(err);
        }
        Transaction txn = txnOpt.get();

        @SuppressWarnings("unchecked")
        List<Payment> payments = em.createQuery(
                "SELECT p FROM Payment p " +
                "WHERE p.transactionId = :tid " +
                "ORDER BY p.createdAt ASC, p.id ASC",
                Payment.class)
            .setParameter("tid", id)
            .getResultList();

        Map<String, Object> txnMap = transactionToMap(txn);

        List<Map<String, Object>> paymentsList = new ArrayList<>();
        BigDecimal sumPayments = BigDecimal.ZERO;
        for (Payment p : payments) {
            paymentsList.add(paymentToMap(p));
            if (p.getAmount() != null) {
                sumPayments = sumPayments.add(p.getAmount());
            }
        }

        List<String> blobs = splitBlobs(txn.getBlobFileIds());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("transactionAmount", txn.getAmount());
        summary.put("transactionAmountPaid", txn.getAmountPaid());
        summary.put("transactionAmountRemaining", txn.getAmountRemaining());
        summary.put("paymentCount", payments.size());
        summary.put("paymentsAmountSum", sumPayments);
        summary.put("attachmentCount", blobs.size());
        boolean amountsMatch = txn.getAmountPaid() != null
            && sumPayments.compareTo(txn.getAmountPaid()) == 0;
        summary.put("amountPaidEqualsSumPayments", amountsMatch);
        if (!amountsMatch) {
            BigDecimal diff = sumPayments.subtract(
                txn.getAmountPaid() == null ? BigDecimal.ZERO : txn.getAmountPaid());
            summary.put("amountDiff", diff);
        }
        // Date sync indicator (single-payment case)
        if (payments.size() == 1) {
            LocalDate pd = payments.get(0).getPaymentDate();
            boolean dateInSync = pd != null && pd.equals(txn.getPaymentDate());
            summary.put("paymentDateInSync", dateInSync);
            if (!dateInSync) {
                summary.put("transactionPaymentDate", txn.getPaymentDate());
                summary.put("paymentRecordPaymentDate", pd);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("summary", summary);
        response.put("transaction", txnMap);
        response.put("payments", paymentsList);
        response.put("attachments", blobs);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/admin/diagnostics/payment-date-mismatches?entityId=...
    // Dry-run: detect Transaction.paymentDate != Payment.paymentDate.
    // ================================================================
    @GetMapping("/payment-date-mismatches")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> findPaymentDateMismatches(
            @RequestParam(required = false) UUID entityId) {

        ResponseEntity<Map<String, Object>> gate = adminGate();
        if (gate != null) return gate;

        StringBuilder sql = new StringBuilder(
            "SELECT t.id AS txn_id, " +
            "       t.entity_id AS entity_id, " +
            "       t.entity_number AS entity_number, " +
            "       t.payment_date AS txn_payment_date, " +
            "       t.payment_status AS payment_status, " +
            "       t.amount AS amount, " +
            "       MIN(p.payment_date) AS pay_min, " +
            "       MAX(p.payment_date) AS pay_max, " +
            "       COUNT(p.id) AS pay_count " +
            "FROM transactions t " +
            "JOIN payments p ON p.transaction_id = t.id " +
            "WHERE t.record_status = 'active' "
        );
        if (entityId != null) {
            sql.append("  AND t.entity_id = :entityId ");
        }
        sql.append(
            "GROUP BY t.id, t.entity_id, t.entity_number, t.payment_date, t.payment_status, t.amount " +
            "HAVING COUNT(p.id) = 1 " +
            "   AND (t.payment_date IS DISTINCT FROM MAX(p.payment_date)) " +
            "ORDER BY t.id ASC"
        );

        var query = em.createNativeQuery(sql.toString());
        if (entityId != null) {
            query.setParameter("entityId", entityId);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<Map<String, Object>> mismatches = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("transactionId", ((Number) r[0]).intValue());
            row.put("entityId", r[1] != null ? r[1].toString() : null);
            row.put("entityNumber", r[2] == null ? null : ((Number) r[2]).intValue());
            row.put("transactionPaymentDate", r[3] != null ? r[3].toString() : null);
            row.put("paymentStatus", r[4]);
            row.put("amount", r[5]);
            row.put("paymentRecordPaymentDate", r[7] != null ? r[7].toString() : null);
            row.put("paymentCount", ((Number) r[8]).intValue());
            mismatches.add(row);
        }

        StringBuilder sqlSplit = new StringBuilder(
            "SELECT COUNT(*) FROM (" +
            "  SELECT t.id FROM transactions t " +
            "  JOIN payments p ON p.transaction_id = t.id " +
            "  WHERE t.record_status = 'active' "
        );
        if (entityId != null) {
            sqlSplit.append("    AND t.entity_id = :entityId ");
        }
        sqlSplit.append(
            "  GROUP BY t.id HAVING COUNT(p.id) > 1" +
            ") s"
        );
        var splitQuery = em.createNativeQuery(sqlSplit.toString());
        if (entityId != null) {
            splitQuery.setParameter("entityId", entityId);
        }
        long splitCount = ((Number) splitQuery.getSingleResult()).longValue();

        StringBuilder sqlOrphan = new StringBuilder(
            "SELECT COUNT(*) FROM payments p WHERE p.transaction_id IS NULL "
        );
        if (entityId != null) {
            sqlOrphan.append("  AND p.entity_id = :entityId ");
        }
        var orphanQuery = em.createNativeQuery(sqlOrphan.toString());
        if (entityId != null) {
            orphanQuery.setParameter("entityId", entityId);
        }
        long orphanCount = ((Number) orphanQuery.getSingleResult()).longValue();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("entityIdFilter", entityId);
        resp.put("mismatchCount", mismatches.size());
        resp.put("splitTransactionsSkipped", splitCount);
        resp.put("orphanPaymentsSkipped", orphanCount);
        resp.put("mismatches", mismatches);
        resp.put("note", "DRY-RUN. Call POST /sync-payment-dates?apply=true to fix.");
        return ResponseEntity.ok(resp);
    }

    // ================================================================
    // POST /api/admin/diagnostics/sync-payment-dates?entityId=X&apply=true
    // Applies the fix: Payment.paymentDate := Transaction.paymentDate
    // for all single-payment transactions where they differ.
    // ================================================================
    @PostMapping("/sync-payment-dates")
    @Transactional
    public ResponseEntity<Map<String, Object>> syncPaymentDates(
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "false") boolean apply) {

        ResponseEntity<Map<String, Object>> gate = adminGate();
        if (gate != null) return gate;

        if (!apply) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "apply_flag_required");
            err.put("hint", "Add ?apply=true to actually run the sync.");
            return ResponseEntity.badRequest().body(err);
        }

        StringBuilder sql = new StringBuilder(
            "WITH single_pay AS ( " +
            "  SELECT t.id AS txn_id, " +
            "         t.payment_date AS txn_pd, " +
            "         MAX(p.id) AS payment_id, " +
            "         MAX(p.payment_date) AS pay_pd " +
            "  FROM transactions t " +
            "  JOIN payments p ON p.transaction_id = t.id " +
            "  WHERE t.record_status = 'active' "
        );
        if (entityId != null) {
            sql.append("    AND t.entity_id = :entityId ");
        }
        sql.append(
            "  GROUP BY t.id, t.payment_date " +
            "  HAVING COUNT(p.id) = 1 " +
            "     AND (t.payment_date IS DISTINCT FROM MAX(p.payment_date)) " +
            ") " +
            "SELECT txn_id, txn_pd, payment_id, pay_pd FROM single_pay ORDER BY txn_id"
        );

        var query = em.createNativeQuery(sql.toString());
        if (entityId != null) {
            query.setParameter("entityId", entityId);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        int updated = 0;
        int skippedNullTxnDate = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (Object[] r : rows) {
            Integer txnId       = ((Number) r[0]).intValue();
            Object  txnPdRaw    = r[1];
            Integer paymentId   = ((Number) r[2]).intValue();
            Object  payPdRaw    = r[3];

            // Do not write null into a Payment record (that loses data)
            if (txnPdRaw == null) {
                skippedNullTxnDate++;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("transactionId", txnId);
                d.put("paymentId", paymentId);
                d.put("status", "skipped_null_txn_date");
                d.put("paymentRecordPaymentDate", payPdRaw == null ? null : payPdRaw.toString());
                details.add(d);
                continue;
            }

            LocalDate txnPd = LocalDate.parse(txnPdRaw.toString());

            Payment p = paymentRepository.findById(paymentId).orElse(null);
            if (p == null) continue;

            LocalDate before = p.getPaymentDate();
            p.setPaymentDate(txnPd);
            paymentRepository.save(p);

            updated++;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("transactionId", txnId);
            d.put("paymentId", paymentId);
            d.put("status", "updated");
            d.put("paymentDateBefore", before == null ? null : before.toString());
            d.put("paymentDateAfter", txnPd.toString());
            details.add(d);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("entityIdFilter", entityId);
        resp.put("candidatesFound", rows.size());
        resp.put("updated", updated);
        resp.put("skippedNullTxnDate", skippedNullTxnDate);
        resp.put("details", details);
        return ResponseEntity.ok(resp);
    }

    // ================================================================
    // Helpers
    // ================================================================
    private Map<String, Object> transactionToMap(Transaction txn) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", txn.getId());
        m.put("entityId", txn.getEntityId());
        m.put("entityNumber", txn.getEntityNumber());
        m.put("type", txn.getType());
        m.put("docDate", txn.getDocDate());
        m.put("accountingPeriod", txn.getAccountingPeriod());
        m.put("counterparty", txn.getCounterparty());
        m.put("account", txn.getAccount());
        m.put("category", txn.getCategory());
        m.put("subcategory", txn.getSubcategory());
        m.put("description", txn.getDescription());
        m.put("amount", txn.getAmount());
        m.put("amountPaid", txn.getAmountPaid());
        m.put("amountRemaining", txn.getAmountRemaining());
        m.put("paymentMethod", txn.getPaymentMethod());
        m.put("paymentStatus", txn.getPaymentStatus());
        m.put("paymentDate", txn.getPaymentDate());
        m.put("dueDate", txn.getDueDate());
        m.put("docStatus", txn.getDocStatus());
        m.put("blobFileIds", txn.getBlobFileIds());
        m.put("blobFolderPath", txn.getBlobFolderPath());
        m.put("recordStatus", txn.getRecordStatus());
        m.put("createdBy", txn.getCreatedBy());
        m.put("updatedBy", txn.getUpdatedBy());
        m.put("createdAt", txn.getCreatedAt());
        m.put("updatedAt", txn.getUpdatedAt());
        return m;
    }

    private Map<String, Object> paymentToMap(Payment p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("entityId", p.getEntityId());
        m.put("transactionId", p.getTransactionId());
        m.put("paymentDate", p.getPaymentDate());
        m.put("paymentPeriod", p.getPaymentPeriod());
        m.put("paymentType", p.getPaymentType());
        m.put("amount", p.getAmount());
        m.put("paymentMethod", p.getPaymentMethod());
        m.put("bankReference", p.getBankReference());
        m.put("bankAccountId", p.getBankAccountId());
        m.put("counterparty", p.getCounterparty());
        m.put("description", p.getDescription());
        m.put("status", p.getStatus());
        m.put("blobFileId", p.getBlobFileId());
        m.put("notes", p.getNotes());
        m.put("createdBy", p.getCreatedBy());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    private List<String> splitBlobs(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
