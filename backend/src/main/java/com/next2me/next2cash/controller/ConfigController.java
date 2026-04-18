package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.Config;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ConfigRepository;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ConfigController
 *
 * Security model (Phase E):
 *   - Class-level @PreAuthorize: only ADMIN and USER may hit any endpoint.
 *     ACCOUNTANT and VIEWER get 403 automatically.
 *   - Per-request: user must have access to the requested entityId
 *     (admin bypass; user-with-no-assignments legacy rule applies).
 *
 * Config records are per-entity (Config.entityId is NOT NULL).
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyRole('ADMIN','USER')")
public class ConfigController {

    private final ConfigRepository configRepository;
    private final UserAccessService userAccessService;

    /**
     * GET /api/config?entityId=X
     * Returns all active config for the given entity, grouped by type:
     *   categories, subcategories, accounts, paymentMethods.
     *
     * Access control:
     *   - Throws 401 if no/invalid JWT (via UserAccessService.getCurrentUser).
     *   - Throws 400 if entityId is null (handled by @RequestParam).
     *   - Throws 403 if user is not assigned to the requested entity
     *     (admin bypass; user with zero assignments = legacy "see all").
     */
    @GetMapping
    public ResponseEntity<?> getConfig(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        List<Config> all = configRepository
            .findByEntityIdAndIsActiveTrueOrderBySortOrderAsc(entityId);

        List<Map<String, Object>> categories      = new ArrayList<>();
        List<Map<String, Object>> subcategories   = new ArrayList<>();
        List<Map<String, Object>> accounts        = new ArrayList<>();
        List<Map<String, Object>> paymentMethods  = new ArrayList<>();

        for (Config c : all) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key",       c.getConfigKey());
            item.put("value",     c.getConfigValue());
            item.put("parentKey", c.getParentKey());
            item.put("icon",      c.getIcon());

            switch (c.getConfigType()) {
                case "category"       -> categories.add(item);
                case "subcategory"    -> subcategories.add(item);
                case "account"        -> accounts.add(item);
                case "payment_method" -> paymentMethods.add(item);
            }
        }

        return ResponseEntity.ok(Map.of(
            "success",        true,
            "categories",     categories,
            "subcategories",  subcategories,
            "accounts",       accounts,
            "paymentMethods", paymentMethods
        ));
    }
}