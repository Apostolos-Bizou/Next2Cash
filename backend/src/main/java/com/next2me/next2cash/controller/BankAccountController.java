package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.BankAccount;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.BankAccountRepository;
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
 */
@RestController
@RequestMapping("/api/bank-accounts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','USER','VIEWER')")
public class BankAccountController {

    private final BankAccountRepository bankAccountRepository;
    private final UserAccessService userAccessService;

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
}