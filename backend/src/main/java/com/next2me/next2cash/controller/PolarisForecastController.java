package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.ActualsRow;
import com.next2me.next2cash.model.CostMilestone;
import com.next2me.next2cash.model.ForecastAssumption;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.PolarisForecastExportService;
import com.next2me.next2cash.service.PolarisForecastService;
import com.next2me.next2cash.service.PolarisForecastSummaryService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Polaris P&L forecast endpoints (Phase 1, Oct 2026): assumptions + actuals.
 *
 * Same security model as PolarisPayrollController:
 *   1. @PreAuthorize (class level): ADMIN, USER, ACCOUNTANT. Exactly two
 *      method-level overrides: GET /actuals and GET /summary (the Dashboard's
 *      data sources) also admit the global VIEWER role. Every write keeps the
 *      class-level gate, so VIEWER can never reach a POST/PUT/DELETE (Invariant 1).
 *   2. entity_roles for the Polaris entity, checked per endpoint here:
 *
 *        resource        | GET                    | POST / PUT / DELETE
 *        ----------------+------------------------+--------------------
 *        assumptions     | admin                  | admin
 *        actuals         | admin, editor, viewer  | admin, editor
 *        cost-milestones | admin, editor          | admin   (OPEX forecast, Oct 2026)
 *
 * Endpoints:
 *   GET    /api/polaris/forecast/assumptions?entityId=X
 *   GET    /api/polaris/forecast/assumptions/{id}?entityId=X
 *   POST   /api/polaris/forecast/assumptions?entityId=X
 *   PUT    /api/polaris/forecast/assumptions/{id}?entityId=X
 *   DELETE /api/polaris/forecast/assumptions/{id}?entityId=X
 *   (same five for /api/polaris/forecast/actuals)
 *   GET    /api/polaris/forecast/summary?entityId=X&years=1|3|5[&membersMode=growth|milestones]
 *          -> computed aggregates only (EBITDA, break-even, KPIs, per-year series);
 *             any Polaris role (admin / editor / viewer); never person rows,
 *             names, salaries or raw assumption values.
 *   GET    /api/polaris/forecast/export?entityId=X&years=1|3|5
 *          -> the full per-year P&L as an .xlsx (REVENUE / THIRD-PARTY /
 *             CONTRIBUTION / OPERATING COSTS by office / OPEX by category /
 *             EBITDA); admin + editor only, class-level Spring gate (never VIEWER).
 */
@RestController
@RequestMapping("/api/polaris/forecast")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT')")
public class PolarisForecastController {

    private final PolarisForecastService polarisForecastService;
    private final PolarisForecastSummaryService polarisForecastSummaryService;
    private final PolarisForecastExportService polarisForecastExportService;
    private final UserAccessService userAccessService;

    // -------------------------------------------------------------------
    // forecast Excel export -- GET: admin+editor (class gate: never VIEWER)
    // -------------------------------------------------------------------

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportForecast(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId,
            @RequestParam(defaultValue = "1") int years) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        byte[] bytes = polarisForecastExportService.generateExcel(entityId, years);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", PolarisForecastExportService.filename(years));
        headers.setContentLength(bytes.length);
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    // -------------------------------------------------------------------
    // forecast summary -- GET: admin+editor+viewer, computed aggregates only
    // -------------------------------------------------------------------

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT','VIEWER')")  // GET-only override; entity_roles still decides
    public ResponseEntity<?> forecastSummary(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId,
            @RequestParam(defaultValue = "1") int years,
            @RequestParam(required = false) String membersMode) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisRead(user, entityId);
        return ok(polarisForecastSummaryService.summary(entityId, years, membersMode));
    }

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
    // actuals_rows -- GET: admin+editor+viewer, writes: admin+editor
    // -------------------------------------------------------------------

    @GetMapping("/actuals")
    @PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT','VIEWER')")  // GET-only override; entity_roles still decides
    public ResponseEntity<?> listActuals(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisRead(user, entityId);
        return okList(polarisForecastService.listActuals(entityId));
    }

    @GetMapping("/actuals/{id}")
    public ResponseEntity<?> getActual(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisRead(user, entityId);
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
    // cost_milestones -- GET: admin+editor, writes: admin (never VIEWER: class gate)
    // -------------------------------------------------------------------

    @GetMapping("/cost-milestones")
    public ResponseEntity<?> listCostMilestones(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        return okList(polarisForecastService.listCostMilestones(entityId));
    }

    @GetMapping("/cost-milestones/{id}")
    public ResponseEntity<?> getCostMilestone(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        return ok(polarisForecastService.getCostMilestone(entityId, id));
    }

    @PostMapping("/cost-milestones")
    public ResponseEntity<?> createCostMilestone(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestBody CostMilestone body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisForecastService.createCostMilestone(user, entityId, body));
    }

    @PutMapping("/cost-milestones/{id}")
    public ResponseEntity<?> updateCostMilestone(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestBody CostMilestone body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisForecastService.updateCostMilestone(user, entityId, id, body));
    }

    @DeleteMapping("/cost-milestones/{id}")
    public ResponseEntity<?> deleteCostMilestone(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        polarisForecastService.deleteCostMilestone(user, entityId, id);
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
