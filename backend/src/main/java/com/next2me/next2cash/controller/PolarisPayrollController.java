package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.PayrollMilestone;
import com.next2me.next2cash.model.PayrollPerson;
import com.next2me.next2cash.model.PayrollPersonRaise;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.PolarisPayrollService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Polaris P&L payroll endpoints (Phase 1, Oct 2026).
 *
 * Entity-scoped: every call takes ?entityId=<polaris id>; any other entity -> 400.
 *
 * Security model:
 *   1. @PreAuthorize (class level): ADMIN, USER, ACCOUNTANT. Exactly one
 *      method-level override: GET /summary (aggregate, no names) also admits
 *      the global VIEWER role. Every other endpoint, and every write, keeps the
 *      class-level gate (Invariant 1). This is only a coarse gate -- there is
 *      no global-ADMIN requirement for writes.
 *   2. entity_roles for the Polaris entity, checked per endpoint here:
 *
 *        resource     | GET                    | POST / PUT / DELETE
 *        -------------+------------------------+--------------------
 *        people       | admin, editor          | admin
 *        summary      | admin, editor, viewer  | (read-only aggregate)
 *        milestones   | admin                  | admin
 *        raises       | admin, editor          | admin   (payroll_person_raises, Oct 2026)
 *
 *      No entity_roles row -> 403, even for a global ADMIN.
 *
 * Endpoints:
 *   GET    /api/polaris/payroll/summary?entityId=X   (totals per office, no names)
 *   GET    /api/polaris/payroll/people?entityId=X
 *   GET    /api/polaris/payroll/people/{id}?entityId=X
 *   POST   /api/polaris/payroll/people?entityId=X
 *   PUT    /api/polaris/payroll/people/{id}?entityId=X
 *   DELETE /api/polaris/payroll/people/{id}?entityId=X
 *   (same five for /api/polaris/payroll/milestones)
 */
@RestController
@RequestMapping("/api/polaris/payroll")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT')")
public class PolarisPayrollController {

    private final PolarisPayrollService polarisPayrollService;
    private final UserAccessService userAccessService;

    // -------------------------------------------------------------------
    // payroll summary -- GET: admin+editor+viewer, aggregate only
    // -------------------------------------------------------------------

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT','VIEWER')")  // GET-only override; entity_roles still decides
    public ResponseEntity<?> payrollSummary(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisRead(user, entityId);
        return okList(polarisPayrollService.payrollSummary(entityId));
    }

    // -------------------------------------------------------------------
    // payroll_people -- GET: admin+editor, writes: admin
    // -------------------------------------------------------------------

    @GetMapping("/people")
    public ResponseEntity<?> listPeople(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        return okList(polarisPayrollService.listPeople(entityId));
    }

    @GetMapping("/people/{id}")
    public ResponseEntity<?> getPerson(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        return ok(polarisPayrollService.getPerson(entityId, id));
    }

    @PostMapping("/people")
    public ResponseEntity<?> createPerson(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestBody PayrollPerson body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisPayrollService.createPerson(user, entityId, body));
    }

    @PutMapping("/people/{id}")
    public ResponseEntity<?> updatePerson(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestBody PayrollPerson body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisPayrollService.updatePerson(user, entityId, id, body));
    }

    @DeleteMapping("/people/{id}")
    public ResponseEntity<?> deletePerson(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        polarisPayrollService.deletePerson(user, entityId, id);
        return deleted(id);
    }

    // -------------------------------------------------------------------
    // payroll_milestones -- GET: admin, writes: admin
    // -------------------------------------------------------------------

    @GetMapping("/milestones")
    public ResponseEntity<?> listMilestones(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return okList(polarisPayrollService.listMilestones(entityId));
    }

    @GetMapping("/milestones/{id}")
    public ResponseEntity<?> getMilestone(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisPayrollService.getMilestone(entityId, id));
    }

    @PostMapping("/milestones")
    public ResponseEntity<?> createMilestone(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestBody PayrollMilestone body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisPayrollService.createMilestone(user, entityId, body));
    }

    @PutMapping("/milestones/{id}")
    public ResponseEntity<?> updateMilestone(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestBody PayrollMilestone body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisPayrollService.updateMilestone(user, entityId, id, body));
    }

    @DeleteMapping("/milestones/{id}")
    public ResponseEntity<?> deleteMilestone(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        polarisPayrollService.deleteMilestone(user, entityId, id);
        return deleted(id);
    }

    // -------------------------------------------------------------------
    // payroll_person_raises -- GET: admin+editor (same tier as people), writes: admin
    // -------------------------------------------------------------------

    /** Every raise of every person of the entity. */
    @GetMapping("/raises")
    public ResponseEntity<?> listRaises(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOrEditor(user, entityId);
        return okList(polarisPayrollService.listRaises(entityId));
    }

    @PostMapping("/people/{personId}/raises")
    public ResponseEntity<?> createRaise(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID personId,
            @RequestParam UUID entityId,
            @RequestBody PayrollPersonRaise body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisPayrollService.createRaise(user, entityId, personId, body));
    }

    @PutMapping("/raises/{id}")
    public ResponseEntity<?> updateRaise(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestBody PayrollPersonRaise body) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        return ok(polarisPayrollService.updateRaise(user, entityId, id, body));
    }

    @DeleteMapping("/raises/{id}")
    public ResponseEntity<?> deleteRaise(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertPolarisAdminOnly(user, entityId);
        polarisPayrollService.deleteRaise(user, entityId, id);
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
