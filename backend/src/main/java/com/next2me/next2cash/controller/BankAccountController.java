package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.BankAccount;
import com.next2me.next2cash.repository.BankAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/bank-accounts")
@CrossOrigin(origins = "*")
public class BankAccountController {

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT','VIEWER')")
    public ResponseEntity<Map<String, Object>> getBankAccounts(
            @RequestParam(required = false) UUID entityId) {

        List<BankAccount> accounts;

        if (entityId != null) {
            accounts = bankAccountRepository
                    .findByEntityIdAndIsActiveTrueOrderBySortOrderAsc(entityId);
        } else {
            accounts = bankAccountRepository
                    .findByIsActiveTrueOrderByEntityIdAscSortOrderAsc();
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
