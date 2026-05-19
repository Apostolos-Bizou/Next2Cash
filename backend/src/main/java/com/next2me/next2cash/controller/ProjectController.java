package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.ProjectDTO;
import com.next2me.next2cash.dto.ProjectDetailDTO;
import com.next2me.next2cash.model.Project;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.ProjectStatus;
import com.next2me.next2cash.repository.ProjectRepository;
import com.next2me.next2cash.repository.TransactionRepository;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.UserAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
@PreAuthorize("isAuthenticated()")
public class ProjectController {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    // S77: Entity-scoped access control
    @Autowired
    private UserAccessService userAccessService;

    /**
     * S77: Entity-scoped project listing.
     *
     * Behavior:
     *  - If entityId param is given: assert user can access it, return projects of that entity.
     *  - Otherwise: return projects across user's accessible entities (intersection).
     *
     * Pattern mirrors BankAccountController.getBankAccounts() (Session #6 / Phase D).
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listProjects(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "entityId", required = false) UUID entityId,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly,
            @RequestParam(value = "status", required = false) String status) {

        User currentUser = userAccessService.getCurrentUser(authHeader);

        // Determine the set of entity IDs to query against
        Set<UUID> queryEntities;
        if (entityId != null) {
            // Single-entity request: guard, then use just this one
            userAccessService.assertCanAccessEntity(currentUser, entityId);
            queryEntities = new java.util.HashSet<>();
            queryEntities.add(entityId);
        } else {
            // Aggregate request: use everything the user can access
            queryEntities = userAccessService.getAccessibleEntityIds(currentUser);
        }

        List<Project> projects;
        if (queryEntities.isEmpty()) {
            // User has no accessible entities (e.g., new accountant with no assignments)
            projects = new ArrayList<>();
        } else if (status != null && !status.isBlank()) {
            projects = projectRepository.findByStatusAndOwnerEntityIdIn(status.toUpperCase(), queryEntities);
        } else if (activeOnly) {
            projects = projectRepository.findActiveByOwnerEntityIdIn(queryEntities);
        } else {
            projects = projectRepository.findByOwnerEntityIdIn(queryEntities);
        }

        List<ProjectDTO> dtos = projects.stream()
                .map(ProjectDTO::fromEntity)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", dtos);
        response.put("count", dtos.size());
        return ResponseEntity.ok(response);
    }


    /**
     * GET /api/projects/{id}/detail — Project Deep-Dive aggregations.
     *
     * Returns:
     *  - project: full project object
     *  - budgetBreakdown: per-category spent (only ACTUAL expenses)
     *  - totals: planned (project.totalBudget), spent, remaining, progressPct
     *  - linkedTransactions: 50 most recent linked transactions
     *  - revenueStreams: single entry from project.expectedMonthlyRevenue
     *  - weightedMonthlyRevenue
     *  - roi: derived calculations or null if expectedMonthlyRevenue is zero
     *
     * Spec ref: CashPlanning TechSpec v1.0 section 5.6
     */
    @GetMapping("/{id}/detail")
    public ResponseEntity<Map<String, Object>> getProjectDetail(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        Map<String, Object> response = new HashMap<>();
        Optional<Project> opt = projectRepository.findById(id);
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Project not found");
            return ResponseEntity.status(404).body(response);
        }
        Project project = opt.get();
        // S77: Enforce entity-level access
        userAccessService.assertCanAccessEntity(currentUser, project.getOwnerEntityId());

        // Budget breakdown per category (ACTUAL expenses only)
        List<Object[]> rawBreakdown = transactionRepository.sumActualExpenseByProjectGroupByCategory(id);
        List<ProjectDetailDTO.CategorySpent> breakdown = new ArrayList<>();
        for (Object[] row : rawBreakdown) {
            ProjectDetailDTO.CategorySpent cs = new ProjectDetailDTO.CategorySpent();
            cs.category = (String) row[0];
            cs.planned = null; // No per-category planned data until Phase 2-E
            cs.spent = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            cs.remaining = null;
            cs.progressPct = null;
            breakdown.add(cs);
        }

        // Totals
        BigDecimal totalSpent = transactionRepository.sumActualExpenseByProject(id);
        if (totalSpent == null) totalSpent = BigDecimal.ZERO;
        BigDecimal totalPlanned = project.getTotalBudget() != null ? project.getTotalBudget() : BigDecimal.ZERO;
        BigDecimal totalRemaining = totalPlanned.subtract(totalSpent);
        BigDecimal progressPct = null;
        if (totalPlanned.compareTo(BigDecimal.ZERO) > 0) {
            progressPct = totalSpent.multiply(new BigDecimal("100"))
                .divide(totalPlanned, 2, RoundingMode.HALF_UP);
        }

        ProjectDetailDTO.Totals totals = new ProjectDetailDTO.Totals();
        totals.planned = totalPlanned;
        totals.spent = totalSpent;
        totals.remaining = totalRemaining;
        totals.progressPct = progressPct;

        // Linked transactions (50 most recent)
        Pageable top50 = PageRequest.of(0, 50);
        List<Transaction> linked = transactionRepository.findLinkedToProject(id, top50);
        List<ProjectDetailDTO.LinkedTransaction> linkedDtos = new ArrayList<>();
        for (Transaction t : linked) {
            ProjectDetailDTO.LinkedTransaction lt = new ProjectDetailDTO.LinkedTransaction();
            lt.id = t.getId();
            lt.entityNumber = t.getEntityNumber();
            lt.docDate = t.getDocDate();
            lt.description = t.getDescription();
            lt.category = t.getCategory();
            lt.counterparty = t.getCounterparty();
            lt.type = t.getType();
            lt.amount = t.getAmount();
            lt.entryMode = t.getEntryMode() != null ? t.getEntryMode() : "ACTUAL";
            lt.paymentStatus = t.getPaymentStatus();
            linkedDtos.add(lt);
        }

        // Revenue streams (single entry until multi-row table added)
        List<ProjectDetailDTO.RevenueStream> revenueStreams = new ArrayList<>();
        BigDecimal expectedMonthly = project.getExpectedMonthlyRevenue() != null
            ? project.getExpectedMonthlyRevenue() : BigDecimal.ZERO;
        if (expectedMonthly.compareTo(BigDecimal.ZERO) > 0) {
            ProjectDetailDTO.RevenueStream rs = new ProjectDetailDTO.RevenueStream();
            rs.source = "Expected Monthly Revenue";
            rs.amount = expectedMonthly;
            rs.confidencePct = 100;
            revenueStreams.add(rs);
        }

        // ROI calculations (null if no expected revenue or no budget)
        ProjectDetailDTO.RoiAnalysis roi = null;
        if (expectedMonthly.compareTo(BigDecimal.ZERO) > 0 && totalPlanned.compareTo(BigDecimal.ZERO) > 0) {
            roi = new ProjectDetailDTO.RoiAnalysis();
            roi.totalInvestment = totalPlanned;
            roi.monthlyRevenueWeighted = expectedMonthly;
            roi.monthlyRevenueBestCase = expectedMonthly.multiply(new BigDecimal("1.37"))
                .setScale(2, RoundingMode.HALF_UP);
            roi.breakEvenMonthsWeighted = totalPlanned
                .divide(expectedMonthly, 2, RoundingMode.HALF_UP);
            roi.breakEvenMonthsBest = totalPlanned
                .divide(roi.monthlyRevenueBestCase, 2, RoundingMode.HALF_UP);
            BigDecimal twelveMonthRevWeighted = expectedMonthly.multiply(new BigDecimal("12"));
            BigDecimal twelveMonthRevBest = roi.monthlyRevenueBestCase.multiply(new BigDecimal("12"));
            roi.twelveMonthRoiWeightedPct = twelveMonthRevWeighted.subtract(totalPlanned)
                .multiply(new BigDecimal("100"))
                .divide(totalPlanned, 2, RoundingMode.HALF_UP);
            roi.twelveMonthRoiBestPct = twelveMonthRevBest.subtract(totalPlanned)
                .multiply(new BigDecimal("100"))
                .divide(totalPlanned, 2, RoundingMode.HALF_UP);
        }

        // Assemble response
        ProjectDetailDTO detail = new ProjectDetailDTO();
        detail.project = ProjectDTO.fromEntity(project);
        detail.budgetBreakdown = breakdown;
        detail.totals = totals;
        detail.linkedTransactions = new ProjectDetailDTO.LinkedTransactionsBlock();
        detail.linkedTransactions.count = linkedDtos.size();
        detail.linkedTransactions.transactions = linkedDtos;
        detail.revenueStreams = revenueStreams;
        detail.weightedMonthlyRevenue = expectedMonthly;
        detail.roi = roi;

        response.put("success", true);
        response.put("data", detail);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProject(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        Optional<Project> opt = projectRepository.findById(id);
        Map<String, Object> response = new HashMap<>();
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Project not found");
            return ResponseEntity.status(404).body(response);
        }
        // S77: Enforce entity-level access
        userAccessService.assertCanAccessEntity(currentUser, opt.get().getOwnerEntityId());
        response.put("success", true);
        response.put("data", ProjectDTO.fromEntity(opt.get()));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createProject(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ProjectDTO dto) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        Map<String, Object> response = new HashMap<>();
        if (dto.name == null || dto.name.isBlank()) {
            response.put("success", false);
            response.put("error", "Project name is required");
            return ResponseEntity.badRequest().body(response);
        }
        if (dto.ownerEntityId == null) {
            response.put("success", false);
            response.put("error", "ownerEntityId is required");
            return ResponseEntity.badRequest().body(response);
        }
        // S77: Admin must have access to the target entity to create a project there
        userAccessService.assertCanAccessEntity(currentUser, dto.ownerEntityId);
        if (projectRepository.findByName(dto.name).isPresent()) {
            response.put("success", false);
            response.put("error", "Project with this name already exists");
            return ResponseEntity.status(409).body(response);
        }
        // S80: proactive status whitelist check (before hitting DB CHECK constraint)
        if (dto.status != null && !dto.status.isBlank()) {
            String requested = dto.status.toUpperCase();
            if (!ProjectStatus.isValid(requested)) {
                throw new IllegalArgumentException(
                    "Invalid value '" + dto.status + "' for field 'status'. Allowed: "
                        + ProjectStatus.validValuesAsString());
            }
        }

        Project p = new Project();
        p.setName(dto.name);
        p.setDescription(dto.description);
        p.setOwnerEntityId(dto.ownerEntityId);
        p.setStatus(dto.status != null ? dto.status.toUpperCase() : "PLANNING");
        p.setStartDate(dto.startDate);
        p.setTargetCompletionDate(dto.targetCompletionDate);
        p.setActualCompletionDate(dto.actualCompletionDate);
        p.setTotalBudget(dto.totalBudget);
        p.setExpectedMonthlyRevenue(dto.expectedMonthlyRevenue);
        if (dto.color != null && !dto.color.isBlank()) p.setColor(dto.color);
        if (dto.isActive != null) p.setIsActive(dto.isActive);

        Project saved = projectRepository.save(p);
        response.put("success", true);
        response.put("data", ProjectDTO.fromEntity(saved));
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateProject(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestBody ProjectDTO dto) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        Map<String, Object> response = new HashMap<>();
        Optional<Project> opt = projectRepository.findById(id);
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Project not found");
            return ResponseEntity.status(404).body(response);
        }
        Project p = opt.get();
        // S77: Admin must have access to the project's current owner entity
        userAccessService.assertCanAccessEntity(currentUser, p.getOwnerEntityId());
        // S77: If reassigning, must also have access to the target entity
        if (dto.ownerEntityId != null && !dto.ownerEntityId.equals(p.getOwnerEntityId())) {
            userAccessService.assertCanAccessEntity(currentUser, dto.ownerEntityId);
        }
        if (dto.name != null && !dto.name.isBlank()) p.setName(dto.name);
        if (dto.description != null) p.setDescription(dto.description);
        if (dto.ownerEntityId != null) p.setOwnerEntityId(dto.ownerEntityId);
        // S80: proactive status whitelist check (before hitting DB CHECK constraint)
        if (dto.status != null && !dto.status.isBlank()) {
            String requested = dto.status.toUpperCase();
            if (!ProjectStatus.isValid(requested)) {
                throw new IllegalArgumentException(
                    "Invalid value '" + dto.status + "' for field 'status'. Allowed: "
                        + ProjectStatus.validValuesAsString());
            }
            p.setStatus(requested);
        }
        if (dto.startDate != null) p.setStartDate(dto.startDate);
        if (dto.targetCompletionDate != null) p.setTargetCompletionDate(dto.targetCompletionDate);
        if (dto.actualCompletionDate != null) p.setActualCompletionDate(dto.actualCompletionDate);
        if (dto.totalBudget != null) p.setTotalBudget(dto.totalBudget);
        if (dto.expectedMonthlyRevenue != null) p.setExpectedMonthlyRevenue(dto.expectedMonthlyRevenue);
        if (dto.color != null && !dto.color.isBlank()) p.setColor(dto.color);
        if (dto.isActive != null) p.setIsActive(dto.isActive);

        Project saved = projectRepository.save(p);
        response.put("success", true);
        response.put("data", ProjectDTO.fromEntity(saved));
        return ResponseEntity.ok(response);
    }
}
