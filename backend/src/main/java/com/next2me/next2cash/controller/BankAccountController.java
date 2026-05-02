package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.BankAccount;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.BankAccountRepository;
import com.next2me.next2cash.service.AuditLogService;
import com.next2me.next2cash.service.BankBalanceService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * BankAccount endpoints.
 *
 * Security model (Phase D, Session #6):
 *   - ADMIN    : full access (UserAccessService returns all entity IDs).
 *   - USER     : only bank accounts whose entityId is in assigned entities.
 *                Legacy rule: user with NO assignments is treated as having ALL
 *                (handled inside UserAccessService).
 *   - VIEWER   : read-only, strict entity filter (empty set if no assignments).
 *   - ACCOUNTANT: 403 (blocked at class level; ZIP export is their only endpoint).
 *
 * PUT endpoint (Session #39, April 2026):
 *   - ADMIN + USER only (VIEWER excluded at method level).
 *   - Updates currentBalance + balanceDate for manual bank reconciliation.
 *   - Audit: BANK_BALANCE_UPDATE, logged per update.
 */
@RestController
@RequestMapping("/api/bank-accounts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','USER','VIEWER')")
public class BankAccountController {

    private final BankAccountRepository bankAccountRepository;
    private final UserAccessService userAccessService;
    private final AuditLogService auditLogService;
    private final BankBalanceService bankBalanceService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getBankAccounts(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) UUID entityId) {

        User currentUser = userAccessService.getCurrentUser(authHeader);

        List<BankAccount> accounts;

        if (entityId != null) {
            // Single-entity request: assert access, then fetch.
            userAccessService.assertCanAccessEntity(currentUser, entityId);
            accounts = bankAccountRepository
                    .findByEntityIdAndIsActiveTrueOrderBySortOrderAsc(entityId);
        } else {
            // Aggregate request: fetch all active, then filter by accessible entities.
            // UserAccessService already handles: admin -> all, legacy user -> all,
            // viewer/accountant -> strict assignments only.
            Set<UUID> accessible = userAccessService.getAccessibleEntityIds(currentUser);

            List<BankAccount> all = bankAccountRepository
                    .findByIsActiveTrueOrderByEntityIdAscSortOrderAsc();

            accounts = new ArrayList<>();
            for (BankAccount a : all) {
                if (a.getEntityId() != null && accessible.contains(a.getEntityId())) {
                    accounts.add(a);
                }
            }
        }

        BigDecimal totalBalance = accounts.stream()
                .map(a -> a.getCurrentBalance() != null ? a.getCurrentBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_bank", totalBalance);
        summary.put("account_count", accounts.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("accounts", accounts);
        response.put("summary", summary);

        return ResponseEntity.ok(response);
    }

    // PUT /api/bank-accounts/{id}
    // Manual bank balance update (reconciliation). ADMIN + USER only.
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> updateBankAccount(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestBody BankAccount updates) {

        User user = userAccessService.getCurrentUser(authHeader);

        return bankAccountRepository.findById(id).map(b -> {
            // SECURITY GUARD: the existing bank account's entity must be accessible
            userAccessService.assertCanAccessEntity(user, b.getEntityId());

            // Business rule: updating a bank account without a balance is meaningless.
            if (updates.getCurrentBalance() == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("error", "currentBalance is required");
                return ResponseEntity.badRequest().body((Object) err);
            }

            // Apply updates: only the fields the UI is allowed to change.
            b.setCurrentBalance(updates.getCurrentBalance());
            // If no balanceDate provided, auto-stamp with today (balance was updated now).
            // Explicit balanceDate is still respected (for backfill/correction scenarios).
            if (updates.getBalanceDate() != null) {
                b.setBalanceDate(updates.getBalanceDate());
            } else {
                b.setBalanceDate(LocalDate.now());
            }

            BankAccount saved = bankAccountRepository.save(b);

            // Audit (same pattern as TransactionController: details=null).
            auditLogService.log(
                saved.getEntityId(),
                user.getId(),
                user.getUsername(),
                "BANK_BALANCE_UPDATE",
                "bank_accounts",
                saved.getId().toString(),
                null
            );

            return ResponseEntity.ok(Map.<String, Object>of("success", true, "data", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // === Phase 2 (Session #49) — Bank balance auto-compute endpoints ===

    /**
     * POST /api/bank-accounts/{id}/recompute
     *
     * Trigger a manual recompute of a single bank account's current_balance from
     * its opening anchor + paid transactions. Returns the updated account.
     *
     * ADMIN + USER only.
     */
    @PostMapping("/{id}/recompute")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> recomputeBankBalance(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {

        User user = userAccessService.getCurrentUser(authHeader);
        BankAccount existing = bankAccountRepository.findById(id)
            .orElse(null);
        if (existing == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "Bank account not found");
            return ResponseEntity.status(404).body(err);
        }

        userAccessService.assertCanAccessEntity(user, existing.getEntityId());

        BankAccount recomputed = bankBalanceService.recompute(id);

        auditLogService.log(
            existing.getEntityId(),
            user.getId(),
            user.getUsername(),
            "BANK_BALANCE_RECOMPUTE",
            "bank_accounts",
            id.toString(),
            "Recomputed " + existing.getAccountLabel() + " => " + recomputed.getCurrentBalance());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", recomputed);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/bank-accounts/recompute-all?entityId=X
     *
     * Recompute ALL bank accounts for a given entity. Used by the admin
     * "Επανυπολογισμός όλων" button and by the deploy-time auto-recompute.
     *
     * ADMIN + USER only.
     */
    @PostMapping("/recompute-all")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> recomputeAllForEntity(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId) {

        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(user, entityId);

        List<BankAccount> recomputed = bankBalanceService.recomputeForEntity(entityId);

        auditLogService.log(
            entityId,
            user.getId(),
            user.getUsername(),
            "BANK_BALANCE_RECOMPUTE_ALL",
            "bank_accounts",
            entityId.toString(),
            "Recomputed " + recomputed.size() + " bank accounts");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("count", recomputed.size());
        response.put("accounts", recomputed);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/bank-accounts/{id}/opening
     *
     * "Διόρθωση Σημείου Εκκίνησης" — sets a new opening_balance + opening_date
     * and triggers recompute. This is the user-facing manual override.
     *
     * Body: { "openingBalance": 1234.56, "openingDate": "2026-04-30" }
     *
     * ADMIN + USER only.
     */
    @PutMapping("/{id}/opening")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> updateOpeningAnchor(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        User user = userAccessService.getCurrentUser(authHeader);
        BankAccount existing = bankAccountRepository.findById(id)
            .orElse(null);
        if (existing == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "Bank account not found");
            return ResponseEntity.status(404).body(err);
        }

        userAccessService.assertCanAccessEntity(user, existing.getEntityId());

        BigDecimal newBalance = null;
        Object balObj = body.get("openingBalance");
        if (balObj != null) {
            newBalance = new BigDecimal(balObj.toString());
        }

        LocalDate newDate = null;
        Object dateObj = body.get("openingDate");
        if (dateObj != null && !dateObj.toString().isBlank()) {
            newDate = LocalDate.parse(dateObj.toString());
        } else {
            newDate = LocalDate.now();
        }

        try {
            BankAccount updated = bankBalanceService.updateOpeningAnchor(id, newBalance, newDate);

            auditLogService.log(
                existing.getEntityId(),
                user.getId(),
                user.getUsername(),
                "BANK_OPENING_UPDATE",
                "bank_accounts",
                id.toString(),
                "Opening anchor: balance=" + newBalance + ", date=" + newDate);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", e.getMessage());
            return ResponseEntity.status(400).body(err);
        }
    }

}