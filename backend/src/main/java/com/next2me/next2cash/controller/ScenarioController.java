package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.ScenarioDTO;
import com.next2me.next2cash.model.Scenario;
import com.next2me.next2cash.model.ScenarioStatus;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ScenarioRepository;
import com.next2me.next2cash.service.UserAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for forecast scenarios (Cash Planning "what-if" levers).
 *
 * Endpoints:
 *   GET    /api/scenarios?entityId=...        list (entity-scoped)
 *   GET    /api/scenarios/{id}                single
 *   POST   /api/scenarios                     create (ADMIN)
 *   PUT    /api/scenarios/{id}                update (ADMIN)
 *   DELETE /api/scenarios/{id}                soft-delete -> is_active=false (ADMIN)
 *
 * Auth pattern mirrors ProjectController exactly: custom JWT extraction via
 * UserAccessService.getCurrentUser(authHeader) + entity-scoped guards.
 *
 * Spec ref: CashPlanning TechSpec v1.1 sections 3 (Principle 3) and 5.8.
 * Session: S97
 */
@RestController
@RequestMapping("/api/scenarios")
@PreAuthorize("isAuthenticated()")
public class ScenarioController {

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private UserAccessService userAccessService;

    /**
     * Entity-scoped scenario listing.
     * - entityId given: assert access, return that entity's scenarios.
     * - otherwise: return scenarios across all accessible entities.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listScenarios(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "entityId", required = false) UUID entityId,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly) {

        User currentUser = userAccessService.getCurrentUser(authHeader);

        Set<UUID> queryEntities;
        if (entityId != null) {
            userAccessService.assertCanAccessEntity(currentUser, entityId);
            queryEntities = new HashSet<>();
            queryEntities.add(entityId);
        } else {
            queryEntities = userAccessService.getAccessibleEntityIds(currentUser);
        }

        List<Scenario> scenarios;
        if (queryEntities.isEmpty()) {
            scenarios = new ArrayList<>();
        } else if (activeOnly) {
            scenarios = scenarioRepository.findActiveByOwnerEntityIdIn(queryEntities);
        } else {
            scenarios = scenarioRepository.findByOwnerEntityIdIn(queryEntities);
        }

        List<ScenarioDTO> dtos = scenarios.stream()
                .map(ScenarioDTO::fromEntity)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", dtos);
        response.put("count", dtos.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getScenario(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        Map<String, Object> response = new HashMap<>();
        Optional<Scenario> opt = scenarioRepository.findById(id);
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Scenario not found");
            return ResponseEntity.status(404).body(response);
        }
        userAccessService.assertCanAccessEntity(currentUser, opt.get().getOwnerEntityId());
        response.put("success", true);
        response.put("data", ScenarioDTO.fromEntity(opt.get()));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createScenario(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ScenarioDTO dto) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        Map<String, Object> response = new HashMap<>();

        if (dto.name == null || dto.name.isBlank()) {
            response.put("success", false);
            response.put("error", "Scenario name is required");
            return ResponseEntity.badRequest().body(response);
        }
        if (dto.ownerEntityId == null) {
            response.put("success", false);
            response.put("error", "ownerEntityId is required");
            return ResponseEntity.badRequest().body(response);
        }
        userAccessService.assertCanAccessEntity(currentUser, dto.ownerEntityId);

        // Proactive scenario_type whitelist check (before DB CHECK constraint)
        String type = (dto.scenarioType != null && !dto.scenarioType.isBlank())
                ? dto.scenarioType.toUpperCase() : ScenarioStatus.CUSTOM;
        if (!ScenarioStatus.isValid(type)) {
            throw new IllegalArgumentException(
                "Invalid value '" + dto.scenarioType + "' for field 'scenarioType'. Allowed: "
                    + ScenarioStatus.validValuesAsString());
        }

        // Name uniqueness within the entity
        if (scenarioRepository.findByOwnerEntityIdAndName(dto.ownerEntityId, dto.name).isPresent()) {
            response.put("success", false);
            response.put("error", "Scenario with this name already exists for this entity");
            return ResponseEntity.status(409).body(response);
        }

        Scenario s = new Scenario();
        s.setName(dto.name);
        s.setScenarioType(type);
        s.setOwnerEntityId(dto.ownerEntityId);
        s.setDescription(dto.description);
        if (dto.revenueAdjustPct != null) s.setRevenueAdjustPct(dto.revenueAdjustPct);
        if (dto.expenseAdjustPct != null) s.setExpenseAdjustPct(dto.expenseAdjustPct);
        if (dto.color != null && !dto.color.isBlank()) s.setColor(dto.color);
        // New scenarios are never the default; baseline defaults are seeded.
        s.setIsDefault(Boolean.FALSE);
        if (dto.isActive != null) s.setIsActive(dto.isActive);

        Scenario saved = scenarioRepository.save(s);
        response.put("success", true);
        response.put("data", ScenarioDTO.fromEntity(saved));
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateScenario(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestBody ScenarioDTO dto) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        Map<String, Object> response = new HashMap<>();
        Optional<Scenario> opt = scenarioRepository.findById(id);
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Scenario not found");
            return ResponseEntity.status(404).body(response);
        }
        Scenario s = opt.get();
        userAccessService.assertCanAccessEntity(currentUser, s.getOwnerEntityId());

        // BASELINE adjustments are locked at 0/0 by definition; guard against edits.
        boolean isBaseline = ScenarioStatus.BASELINE.equalsIgnoreCase(s.getScenarioType());

        if (dto.name != null && !dto.name.isBlank()) s.setName(dto.name);
        if (dto.description != null) s.setDescription(dto.description);

        // scenario_type may be changed only for non-baseline scenarios
        if (!isBaseline && dto.scenarioType != null && !dto.scenarioType.isBlank()) {
            String requested = dto.scenarioType.toUpperCase();
            if (!ScenarioStatus.isValid(requested)) {
                throw new IllegalArgumentException(
                    "Invalid value '" + dto.scenarioType + "' for field 'scenarioType'. Allowed: "
                        + ScenarioStatus.validValuesAsString());
            }
            // Cannot promote a scenario to BASELINE via update (only one seeded baseline).
            if (!ScenarioStatus.BASELINE.equals(requested)) {
                s.setScenarioType(requested);
            }
        }

        if (!isBaseline) {
            if (dto.revenueAdjustPct != null) s.setRevenueAdjustPct(dto.revenueAdjustPct);
            if (dto.expenseAdjustPct != null) s.setExpenseAdjustPct(dto.expenseAdjustPct);
        } else {
            // Keep baseline neutral no matter what is sent.
            s.setRevenueAdjustPct(BigDecimal.ZERO);
            s.setExpenseAdjustPct(BigDecimal.ZERO);
        }

        if (dto.color != null && !dto.color.isBlank()) s.setColor(dto.color);
        if (dto.isActive != null) s.setIsActive(dto.isActive);

        Scenario saved = scenarioRepository.save(s);
        response.put("success", true);
        response.put("data", ScenarioDTO.fromEntity(saved));
        return ResponseEntity.ok(response);
    }

    /**
     * Soft-delete: marks the scenario inactive. Baseline cannot be deleted.
     * The FK transactions.scenario_id is ON DELETE SET NULL, but we soft-delete
     * to preserve history and avoid orphaning tagged PLANNED transactions.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteScenario(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id) {
        User currentUser = userAccessService.getCurrentUser(authHeader);
        Map<String, Object> response = new HashMap<>();
        Optional<Scenario> opt = scenarioRepository.findById(id);
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Scenario not found");
            return ResponseEntity.status(404).body(response);
        }
        Scenario s = opt.get();
        userAccessService.assertCanAccessEntity(currentUser, s.getOwnerEntityId());

        if (Boolean.TRUE.equals(s.getIsDefault())
                || ScenarioStatus.BASELINE.equalsIgnoreCase(s.getScenarioType())) {
            response.put("success", false);
            response.put("error", "The Baseline scenario cannot be deleted");
            return ResponseEntity.status(409).body(response);
        }

        s.setIsActive(Boolean.FALSE);
        scenarioRepository.save(s);
        response.put("success", true);
        response.put("data", ScenarioDTO.fromEntity(s));
        return ResponseEntity.ok(response);
    }
}
