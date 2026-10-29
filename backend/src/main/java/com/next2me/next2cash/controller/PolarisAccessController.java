package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Polaris P&L -- the caller's own Polaris role (Phase 3, Oct 2026).
 *
 * Replaces the mockup's demo "ΠΡΟΒΟΛΗ ΩΣ" switcher: the frontend renders each
 * Polaris page from this role instead of a toggle.
 *
 * Security model (same as the other Polaris controllers):
 *   1. @PreAuthorize: class-level ADMIN, USER, ACCOUNTANT. GET /my-role carries a
 *      method-level gate that also admits the global VIEWER role (read-only;
 *      the Dashboard needs it to learn its entity_roles role). No write exists here.
 *   2. entity_roles: a row is required (admin / editor / viewer); no row -> 403.
 *      Same rules as UserAccessService#getPolarisRole (active user, existing
 *      entity scope, known role).
 *
 * Endpoint:
 *   GET /api/polaris/my-role?entityId=X  ->  { success, data: { entityId, role } }
 */
@RestController
@RequestMapping("/api/polaris")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT')")
public class PolarisAccessController {

    private final UserAccessService userAccessService;

    @GetMapping("/my-role")
    @PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT','VIEWER')")  // GET-only override; entity_roles still decides
    public ResponseEntity<?> myRole(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {
        User user = userAccessService.getCurrentUser(authHeader);
        if (!userAccessService.isPolarisEntity(entityId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This endpoint is only available for the Polaris entity");
        }
        String role = userAccessService.getPolarisRole(user, entityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Access denied for entity " + entityId));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entityId", entityId);
        data.put("role", role);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
