package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.Payment;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.PaymentRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.service.BankBalanceService;
import com.next2me.next2cash.service.PaymentService;
import com.next2me.next2cash.service.UserAccessService;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase L — Payment REST endpoints.
 *
 * Supports creating new payments against a transaction (full or partial),
 * listing payments by transaction, and voiding a payment (reverses parent recalc).
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final UserAccessService userAccessService;
    private final BankBalanceService bankBalanceService;

    @Value("${next2cash.azure.blob.connection-string:}")
    private String blobConnectionString;

    @Value("${next2cash.azure.blob.container:next2cash-documents}")
    private String blobContainerName;

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    // POST /api/payments
    // Body: { transactionId, entityId, paymentDate, amount, paymentMethod, description, notes }
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> createPayment(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        User user = userAccessService.getCurrentUser(authHeader);

        // Parse & validate inputs
        UUID entityId;
        Integer transactionId;
        BigDecimal amount;
        LocalDate paymentDate;
        try {
            entityId = UUID.fromString((String) body.get("entityId"));
            Object txnIdObj = body.get("transactionId");
            transactionId = (txnIdObj == null) ? null : Integer.valueOf(txnIdObj.toString());
            amount = new BigDecimal(body.get("amount").toString());
            Object pdObj = body.get("paymentDate");
            paymentDate = (pdObj == null || pdObj.toString().isBlank())
                ? LocalDate.now()
                : LocalDate.parse(pdObj.toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Invalid input: " + e.getMessage()));
        }

        // SECURITY GUARDS
        userAccessService.assertCanAccessEntity(user, entityId);
        if (transactionId != null) {
            Transaction txn = transactionRepository.findById(transactionId).orElse(null);
            if (txn == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Transaction not found"));
            }
            if (!txn.getEntityId().equals(entityId)) {
                return ResponseEntity.status(403)
                    .body(Map.of("success", false, "error", "Transaction belongs to a different entity"));
            }
            userAccessService.assertCanAccessEntity(user, txn.getEntityId());
        }

        try {
            Payment saved = paymentService.createPayment(
                entityId,
                transactionId,
                paymentDate,
                amount,
                (String) body.get("paymentMethod"),
                (String) body.get("description"),
                (String) body.get("notes"),
                user.getId()
            );

            // Also return the updated parent transaction so the UI can refresh in one round-trip
            Transaction updatedTxn = (transactionId != null)
                ? transactionRepository.findById(transactionId).orElse(null)
                : null;

            // Sync paymentMethod to parent transaction (Phase 3, Step 3.4).
            // Business rule: when a payment is registered via Mark-Paid, the bank account
            // chosen becomes the single source of truth on the parent transaction. This is
            // also what BankBalanceService reads (transactions.paymentMethod), so the sync
            // MUST happen before the recompute call below.
            if (updatedTxn != null && saved.getPaymentMethod() != null && !saved.getPaymentMethod().isBlank()) {
                if (!saved.getPaymentMethod().equals(updatedTxn.getPaymentMethod())) {
                    updatedTxn.setPaymentMethod(saved.getPaymentMethod());
                    updatedTxn.setUpdatedBy(user.getId());
                    updatedTxn = transactionRepository.save(updatedTxn);
                }
            }

            // Auto-recompute bank balance for the affected payment method (Phase 3, Step 3.4).
            // Runs OUTSIDE the service @Transactional block, so all data is committed
            // before the recompute reads from the DB. Failures are logged as WARN but never
            // block the response; the manual recompute button exists as a safety net.
            if (saved.getPaymentMethod() != null && !saved.getPaymentMethod().isBlank()) {
                try {
                    bankBalanceService.recomputeForPaymentMethod(saved.getEntityId(), saved.getPaymentMethod());
                } catch (Exception ex) {
                    log.warn("Auto-recompute failed for entity={} paymentMethod={} after PAYMENT-CREATE id={}: {}",
                            saved.getEntityId(), saved.getPaymentMethod(), saved.getId(), ex.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "payment", saved,
                "transaction", updatedTxn == null ? "" : updatedTxn
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // GET /api/payments/by-transaction/{id}
    @GetMapping("/by-transaction/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'VIEWER')")
    public ResponseEntity<?> getByTransaction(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer id) {

        User user = userAccessService.getCurrentUser(authHeader);

        Transaction txn = transactionRepository.findById(id).orElse(null);
        if (txn == null) {
            return ResponseEntity.notFound().build();
        }
        userAccessService.assertCanAccessEntity(user, txn.getEntityId());

        List<Payment> payments = paymentRepository
            .findByEntityIdAndTransactionIdIn(txn.getEntityId(), List.of(id));

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", payments,
            "total", payments.size()
        ));
    }

    // ============================================================
    // DELETE /api/payments/{id}  (Phase 57-D.1)
    // ------------------------------------------------------------
    // Removes a payment record. Recomputes parent transaction's
    // amountPaid/amountRemaining/paymentStatus/paymentDate via
    // PaymentService.recalcParentTransaction (idempotent).
    //
    // EDGE CASE: When the deleted payment was the LAST one for the
    // parent, recalc leaves status unchanged (service comment at
    // PaymentService line 143). We explicitly force status='unpaid'
    // and paymentDate=null in that case.
    //
    // Also deletes the blob (if any) and triggers bank balance
    // recompute for the affected paymentMethod.
    //
    // Auth: ADMIN + USER only.
    // ============================================================
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> deletePayment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer id) {

        User user = userAccessService.getCurrentUser(authHeader);

        Payment payment = paymentRepository.findById(id).orElse(null);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        userAccessService.assertCanAccessEntity(user, payment.getEntityId());

        // Capture details BEFORE delete (we need them for downstream actions)
        Integer transactionId = payment.getTransactionId();
        UUID entityId = payment.getEntityId();
        String paymentMethod = payment.getPaymentMethod();
        String blobFileId = payment.getBlobFileId();

        try {
            // 1. Delete the payment row
            paymentRepository.deleteById(id);
            log.info("Deleted Payment id={} txnId={} amount={} method={}",
                    id, transactionId, payment.getAmount(), paymentMethod);

            // 2. Recompute parent transaction (idempotent, reads remaining payments)
            if (transactionId != null) {
                paymentService.recalcParentTransaction(transactionId, user.getId());

                // 3. EDGE CASE: if no payments remain, force status='unpaid' + paymentDate=null
                Transaction txn = transactionRepository.findById(transactionId).orElse(null);
                if (txn != null) {
                    BigDecimal paid = txn.getAmountPaid() != null ? txn.getAmountPaid() : BigDecimal.ZERO;
                    if (paid.compareTo(new BigDecimal("0.01")) <= 0) {
                        txn.setPaymentStatus("unpaid");
                        txn.setPaymentDate(null);
                        txn.setUpdatedBy(user.getId());
                        transactionRepository.save(txn);
                        log.info("No payments remain on txn id={}, reset to unpaid", transactionId);
                    }
                }
            }

            // 4. Auto-recompute bank balance for the affected paymentMethod
            if (paymentMethod != null && !paymentMethod.isBlank()) {
                try {
                    bankBalanceService.recomputeForPaymentMethod(entityId, paymentMethod);
                } catch (Exception ex) {
                    log.warn("Auto-recompute failed for entity={} paymentMethod={} after PAYMENT-DELETE id={}: {}",
                            entityId, paymentMethod, id, ex.getMessage());
                }
            }

            // 5. Delete blob proof (if any)
            if (blobFileId != null && !blobFileId.isBlank()) {
                try {
                    BlobServiceClient client = new BlobServiceClientBuilder()
                        .connectionString(blobConnectionString)
                        .buildClient();
                    BlobContainerClient container = client.getBlobContainerClient(blobContainerName);
                    BlobClient blob = container.getBlobClient(blobFileId);
                    if (blob.exists()) {
                        blob.delete();
                        log.info("Deleted blob proof: {}", blobFileId);
                    }
                } catch (Exception ex) {
                    log.warn("Blob delete failed for blobFileId={}: {}", blobFileId, ex.getMessage());
                    // Non-fatal: payment row already gone, blob is now orphan
                }
            }

            // 6. Return updated transaction so frontend can refresh in one round-trip
            Transaction updatedTxn = (transactionId != null)
                ? transactionRepository.findById(transactionId).orElse(null)
                : null;

            return ResponseEntity.ok(Map.of(
                "success", true,
                "transaction", updatedTxn == null ? "" : updatedTxn
            ));

        } catch (Exception e) {
            log.error("deletePayment failed for id=" + id, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", "Σφάλμα διαγραφής: " + e.getMessage()));
        }
    }

    // ============================================================
    // GET/POST /api/payments/backfill  (Phase 57-E)
    // ------------------------------------------------------------
    // Backfills Payment records for legacy "paid" transactions that
    // have a paymentDate set but no corresponding Payment row.
    //
    // Use:
    //   GET  /api/payments/backfill?entityId=XXX&dryRun=true   (preview only)
    //   POST /api/payments/backfill?entityId=XXX               (execute)
    //
    // Idempotent: skips transactions that already have a Payment record.
    //
    // Auth: ADMIN only.
    // ============================================================
    @GetMapping("/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> backfillDryRun(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("entityId") String entityIdStr) {
        return runBackfill(authHeader, entityIdStr, true, true);
    }

    @PostMapping("/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> backfillExecute(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("entityId") String entityIdStr,
            @RequestParam(value = "splitOnly", defaultValue = "true") boolean splitOnly) {
        return runBackfill(authHeader, entityIdStr, false, splitOnly);
    }

    private ResponseEntity<?> runBackfill(String authHeader, String entityIdStr, boolean dryRun, boolean splitOnly) {
        User user = userAccessService.getCurrentUser(authHeader);
        UUID entityId;
        try {
            entityId = UUID.fromString(entityIdStr);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Invalid entityId: " + e.getMessage()));
        }
        userAccessService.assertCanAccessEntity(user, entityId);

        try {
            // 1. Fetch all active transactions for the entity
            List<Transaction> allTxns = transactionRepository
                .findByEntityIdAndRecordStatusOrderByDocDateDesc(entityId, "active");

            // 2. Filter to candidate orphans
            List<Transaction> candidates = new java.util.ArrayList<>();
            for (Transaction t : allTxns) {
                String ps = t.getPaymentStatus();
                if (ps == null) continue;
                boolean isPaid = "paid".equals(ps) || "received".equals(ps);
                if (!isPaid) continue;
                if (t.getPaymentDate() == null) continue;
                candidates.add(t);
            }

            // 3. Bulk-fetch existing Payment records for all candidate txn ids
            //    so we can detect which ones already have a Payment.
            List<Integer> candidateIds = new java.util.ArrayList<>();
            for (Transaction t : candidates) candidateIds.add(t.getId());

            java.util.Set<Integer> txnIdsWithPayments = new java.util.HashSet<>();
            if (!candidateIds.isEmpty()) {
                List<Payment> existing = paymentRepository
                    .findByEntityIdAndTransactionIdIn(entityId, candidateIds);
                for (Payment p : existing) {
                    if (p.getTransactionId() != null) {
                        txnIdsWithPayments.add(p.getTransactionId());
                    }
                }
            }

            // 4. Identify the actual orphans (paid + paymentDate + no Payment)
            List<Transaction> orphans = new java.util.ArrayList<>();
            for (Transaction t : candidates) {
                if (!txnIdsWithPayments.contains(t.getId())) {
                    orphans.add(t);
                }
            }

            // Step 57-E.2.1: filter orphans by split-only when executing
            if (!dryRun && splitOnly) {
                List<Transaction> filtered = new java.util.ArrayList<>();
                for (Transaction t : orphans) {
                    boolean isSplit = t.getPaymentDate() != null
                                   && t.getDocDate() != null
                                   && !t.getPaymentDate().equals(t.getDocDate());
                    if (isSplit) filtered.add(t);
                }
                orphans = filtered;
            }

            // 5. If dry-run, just return the count + sample (first 10)
            if (dryRun) {
                List<Map<String, Object>> samples = new java.util.ArrayList<>();
                int sampleLimit = Math.min(10, orphans.size());
                for (int i = 0; i < sampleLimit; i++) {
                    Transaction t = orphans.get(i);
                    Map<String, Object> sample = new java.util.LinkedHashMap<>();
                    sample.put("id", t.getId());
                    sample.put("entityNumber", t.getEntityNumber());
                    sample.put("docDate", t.getDocDate());
                    sample.put("paymentDate", t.getPaymentDate());
                    sample.put("amount", t.getAmount());
                    sample.put("paymentStatus", t.getPaymentStatus());
                    sample.put("paymentMethod", t.getPaymentMethod());
                    sample.put("description", t.getDescription());
                    samples.add(sample);
                }
                // Step 57-E.1.1: break down orphans by split vs same-day
                int splitOrphans = 0;
                int sameDayOrphans = 0;
                List<Map<String, Object>> splitSamples = new java.util.ArrayList<>();
                for (Transaction t : orphans) {
                    boolean isSplit = t.getPaymentDate() != null
                                   && t.getDocDate() != null
                                   && !t.getPaymentDate().equals(t.getDocDate());
                    if (isSplit) {
                        splitOrphans++;
                        if (splitSamples.size() < 20) {
                            Map<String, Object> s = new java.util.LinkedHashMap<>();
                            s.put("id", t.getId());
                            s.put("entityNumber", t.getEntityNumber());
                            s.put("docDate", t.getDocDate());
                            s.put("paymentDate", t.getPaymentDate());
                            s.put("amount", t.getAmount());
                            s.put("paymentMethod", t.getPaymentMethod());
                            s.put("description", t.getDescription());
                            splitSamples.add(s);
                        }
                    } else {
                        sameDayOrphans++;
                    }
                }

                Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("success", true);
                result.put("dryRun", true);
                result.put("totalActive", allTxns.size());
                result.put("candidatesPaidWithDate", candidates.size());
                result.put("alreadyHavePayment", txnIdsWithPayments.size());
                result.put("orphansToBackfill", orphans.size());
                result.put("splitOrphans", splitOrphans);
                result.put("sameDayOrphans", sameDayOrphans);
                result.put("samples", samples);
                result.put("splitSamples", splitSamples);
                return ResponseEntity.ok(result);
            }

            // 6. Execute: create Payment records for each orphan
            int created = 0;
            int skipped = 0;
            int failed = 0;
            List<Map<String, Object>> errors = new java.util.ArrayList<>();
            for (Transaction t : orphans) {
                try {
                    Payment p = new Payment();
                    p.setEntityId(t.getEntityId());
                    p.setTransactionId(t.getId());
                    p.setPaymentDate(t.getPaymentDate());
                    p.setAmount(t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO);
                    p.setPaymentMethod(t.getPaymentMethod());
                    p.setCounterparty(t.getCounterparty());
                    p.setDescription(t.getDescription());
                    p.setStatus("completed");
                    p.setNotes("Backfilled from legacy data (Phase 57-E)");
                    if ("income".equals(t.getType())) {
                        p.setPaymentType("incoming");
                    } else if ("expense".equals(t.getType())) {
                        p.setPaymentType("outgoing");
                    }
                    p.setCreatedBy(user.getId());
                    paymentRepository.save(p);
                    created++;
                } catch (Exception ex) {
                    failed++;
                    Map<String, Object> err = new java.util.LinkedHashMap<>();
                    err.put("txnId", t.getId());
                    err.put("entityNumber", t.getEntityNumber());
                    err.put("error", ex.getMessage());
                    errors.add(err);
                    log.warn("Backfill failed for txn id={}: {}", t.getId(), ex.getMessage());
                }
            }

            log.info("Backfill complete for entity {}: created={} skipped={} failed={}",
                    entityId, created, skipped, failed);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "dryRun", false,
                "entityId", entityId,
                "orphansFound", orphans.size(),
                "created", created,
                "skipped", skipped,
                "failed", failed,
                "errors", errors
            ));

        } catch (Exception e) {
            log.error("Backfill failed for entity " + entityIdStr, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", "Σφάλμα backfill: " + e.getMessage()));
        }
    }
}
