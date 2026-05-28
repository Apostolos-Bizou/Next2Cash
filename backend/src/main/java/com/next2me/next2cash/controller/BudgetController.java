package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.BudgetLineDTO;
import com.next2me.next2cash.dto.BudgetSaveRequest;
import com.next2me.next2cash.dto.BudgetSeedLineDTO;
import com.next2me.next2cash.model.Budget;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.BudgetRepository;
import com.next2me.next2cash.service.BudgetService;
import com.next2me.next2cash.service.UserAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for budgets (editable per-category/subcategory monthly
 * targets, compared against ACTUAL to produce variance).
 *
 * Endpoints:
 *   GET  /api/budgets?entityId=&year=          list (entity-scoped)
 *   GET  /api/budgets/seed?entityId=&sourceYear=  auto-seed suggestion from ACTUAL
 *   POST /api/budgets                          bulk replace-on-save (ADMIN)
 *
 * Auth pattern mirrors ScenarioController exactly.
 *
 * Spec ref: CashPlanning TechSpec v1.1 section 6.3 (Budget vs Actual).
 * Session: S98.1
 */
@RestController
@RequestMapping("/api/budgets")
@PreAuthorize("isAuthenticated()")
public class BudgetController {

    @Autowired
    private BudgetRepository budgetRepository;
    @Autowired
    private BudgetService budgetService;
    @Autowired
    private UserAccessService userAccessService;

    /** Entity + year scoped budget listing. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listBudget(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("entityId") UUID entityId,
            @RequestParam("year") Integer year) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        List<Budget> lines = budgetRepository
                .findByOwnerEntityIdAndBudgetYearOrderByDirectionAscCategoryAscSubcategoryAscMonthAsc(entityId, year);
        List<BudgetLineDTO> dtos = lines.stream()
                .map(BudgetLineDTO::fromEntity)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", dtos);
        response.put("count", dtos.size());
        return ResponseEntity.ok(response);
    }

    /** Auto-seed suggestion: prior-year ACTUAL monthly averages per category. */
    @GetMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedBudget(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("entityId") UUID entityId,
            @RequestParam("sourceYear") int sourceYear) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        List<BudgetSeedLineDTO> seed = budgetService.seedFromActual(entityId, sourceYear);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", seed);
        response.put("count", seed.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Bulk replace-on-save: wipes the entity's budget for the year, then
     * reinserts all supplied lines. ADMIN only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> saveBudget(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody BudgetSaveRequest req) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        Map<String, Object> response = new HashMap<>();

        if (req.entityId == null || req.year == null) {
            response.put("success", false);
            response.put("error", "entityId and year are required");
            return ResponseEntity.badRequest().body(response);
        }
        userAccessService.assertCanAccessEntity(currentUser, req.entityId);

        budgetRepository.deleteByOwnerEntityIdAndBudgetYear(req.entityId, req.year);

        List<Budget> toSave = new ArrayList<>();
        if (req.lines != null) {
            for (BudgetLineDTO line : req.lines) {
                if (line.month == null || line.month < 1 || line.month > 12) continue;
                if (line.category == null || line.category.isBlank()) continue;
                BigDecimal amt = line.amount == null ? BigDecimal.ZERO : line.amount;
                // skip zero rows to keep the table lean
                if (amt.signum() == 0) continue;

                String dir = (line.direction == null) ? "expense" : line.direction.toLowerCase();
                if (!dir.equals("income") && !dir.equals("expense")) dir = "expense";

                Budget b = new Budget();
                b.setOwnerEntityId(req.entityId);
                b.setBudgetYear(req.year);
                b.setCategory(line.category);
                b.setSubcategory(line.subcategory == null ? "" : line.subcategory);
                b.setDirection(dir);
                b.setMonth(line.month);
                b.setAmount(amt);
                toSave.add(b);
            }
        }
        budgetRepository.saveAll(toSave);

        response.put("success", true);
        response.put("count", toSave.size());
        return ResponseEntity.ok(response);
    }
}
