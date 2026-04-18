package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.security.JwtUtil;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final JwtUtil jwtUtil;
    private final UserAccessService userAccessService;  // NEW

    // GET /api/transactions?entityId=X&page=0&perPage=25&type=expense&status=unpaid
    @GetMapping
    public ResponseEntity<?> getTransactions(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int perPage,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        // SECURITY GUARD: verify the user has access to this entity
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(user, entityId);

        PageRequest pageable = PageRequest.of(page, perPage,
            Sort.by(Sort.Direction.DESC, "docDate", "id"));

        Page<Transaction> result;

        if (from != null && to != null) {
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

            // Also block attempts to MOVE a transaction to an entity the user can't access
            if (updates.getEntityId() != null && !updates.getEntityId().equals(t.getEntityId())) {
                userAccessService.assertCanAccessEntity(user, updates.getEntityId());
            }

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

            Transaction saved = transactionRepository.save(t);
            return ResponseEntity.ok(Map.<String, Object>of("success", true, "data", saved));
        }).orElse(ResponseEntity.notFound().build());
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
            return ResponseEntity.ok(Map.<String, Object>of("success", true, "message", "Transaction voided"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // GET /api/transactions/next-number?entityId=X
    @GetMapping("/next-number")
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
}
