package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.ActualsRow;
import com.next2me.next2cash.model.ForecastAssumption;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.PolarisForecastService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Polaris P&L forecast endpoints (Phase 1, Oct 2026): assumptions + actuals.
 *
 * Same security model as PolarisPayrollController:
 *   1. @PreAuthorize (class level, every method): ADMIN, USER, ACCOUNTANT --
 *      never VIEWER.
 *   2. entity_roles for the Polaris entity, checked per endpoint here:
 *
 *        resource     | GET            | POST / PUT / DELETE
 *        -------------+----------------+--------------------
 *        assumptions  | admin          | admin
 *        actuals      | admin, editor  | admin, editor
 *
 * Endpoints:
 *   GET    /api/polaris/forecast/assumptions?entityId=X
 *   GET    /api/polaris/forecast/assumptions/{id}?entityId=X
 *   POST   /api/polaris/forecast/assumptions?entityId=X
 *   PUT    /api/polaris/forecast/assumptions/{id}?entityId=X
 *   DELETE /api/polaris/forecast/assumptions/{id}?entityId=X
 *   (same five for /api/polaris/forecast/actuals)
 */
@RestController
@RequestMapping("/api/polaris/forecast")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT')")
public class PolarisForecastController {

    private final PolarisForecastService polarisForecastService;
    private final UserAccessService userAccessService;

    // -------------------------------------------------------------------
    // forecast_assumptions -- GET: admin, writes: admin
    // -------------------------------------------------------------------

    @GetMapping("/assumptions")
    public ResponseEntity<?> listAssumptions(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return okList(polarisForecastService.listAssumptions(entityId));
    }

    @GetMapping("/assumptions/{id}")
    public ResponseEntity<?> getAssumption(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisForecastService.getAssumption(entityId, id));
    }

    @PostMapping("/assumptions")
    public ResponseEntity<?> createAssumption(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestBody ForecastAssumption body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisForecastService.createAssumption(user, entityId, body));
    }

    @PutMapping("/assumptions/{id}")
    public ResponseEntity<?> updateAssumption(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestBody ForecastAssumption body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisForecastService.updateAssumption(user, entityId, id, body));
    }

    @DeleteMapping("/assumptions/{id}")
    public ResponseEntity<?> deleteAssumption(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        polarisForecastService.deleteAssumption(user, entityId, id);
        return deleted(id);
    }

    // -------------------------------------------------------------------
    // actuals_rows -- GET: admin+editor, writes: admin+editor
    // -------------------------------------------------------------------

    @GetMapping("/actuals")
    public ResponseEntity<?> listActuals(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        return okList(polarisForecastService.listActuals(entityId));
    }

    @GetMapping("/actuals/{id}")
    public ResponseEntity<?> getActual(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        return ok(polarisForecastService.getActual(entityId, id));
    }

    @PostMapping("/actuals")
    public ResponseEntity<?> createActual(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestBody ActualsRow body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        return ok(polarisForecastService.createActual(user, entityId, body));
    }

    @PutMapping("/actuals/{id}")
    public ResponseEntity<?> updateActual(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestBody ActualsRow body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        return ok(polarisForecastService.updateActual(user, entityId, id, body));
    }

    @DeleteMapping("/actuals/{id}")
    public ResponseEntity<?> deleteActual(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        polarisForecastService.deleteActual(user, entityId, id);
        return deleted(id);
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> okList(List<?> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("total", data.size());
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> deleted(UUID id) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("deleted", id);
        return ResponseEntity.ok(response);
    }
}
