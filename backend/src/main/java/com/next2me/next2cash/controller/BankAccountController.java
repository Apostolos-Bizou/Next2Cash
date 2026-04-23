package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.BankAccount;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.BankAccountRepository;
import com.next2me.next2cash.service.AuditLogService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
            if (updates.getBalanceDate() != null) {
                b.setBalanceDate(updates.getBalanceDate());
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
}