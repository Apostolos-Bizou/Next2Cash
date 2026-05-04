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
}
