package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.Payment;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.PaymentRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.service.PaymentService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
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
}
