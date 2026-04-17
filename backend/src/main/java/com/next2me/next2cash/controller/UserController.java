package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.CompanyEntityRepository;
import com.next2me.next2cash.repository.UserEntityRepository;
import com.next2me.next2cash.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User management endpoints (Admin Panel).
 * Access: ADMIN and USER roles (DELETE is ADMIN only — enforced in SecurityConfig).
 *
 * Rules:
 *  - Password is set ONLY on create (POST) or via self-service /api/auth/change-password
 *  - Self-delete is blocked
 *  - Self role downgrade is blocked (prevents admin losing their privileges accidentally)
 *  - Username is immutable after creation
 *  - For user_entities: ADMIN/USER allow empty set (= all entities), ACCOUNTANT/VIEWER require >=1
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private static final Set<String> ALLOWED_ROLES = Set.of("admin", "user", "accountant", "viewer");
    private static final Set<String> RESTRICTED_ROLES = Set.of("accountant", "viewer");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEntityRepository userEntityRepository;
    private final CompanyEntityRepository companyEntityRepository;

    // ═══════════════════════════════════════════════════════════
    //  GET /api/admin/users — List all users
    // ═══════════════════════════════════════════════════════════
    @GetMapping
    public ResponseEntity<?> listUsers() {
        List<Map<String, Object>> users = userRepository.findAllByOrderByUsernameAsc()
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data",    users,
            "count",   users.size()
        ));
    }

    // ═══════════════════════════════════════════════════════════
    //  POST /api/admin/users — Create new user
    // ═══════════════════════════════════════════════════════════
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> request) {

        String username    = str(request.get("username"));
        String password    = str(request.get("password"));
        String displayName = str(request.get("displayName"));
        String email       = str(request.get("email"));
        String role        = strOrDefault(request.get("role"), "user").toLowerCase();

        if (isBlank(username) || isBlank(password)) {
            return badRequest("username and password are required");
        }
        if (password.length() < 8) {
            return badRequest("Password must be at least 8 characters");
        }
        if (!ALLOWED_ROLES.contains(role)) {
            return badRequest("Invalid role. Allowed: admin, user, accountant, viewer");
        }
        if (userRepository.existsByUsername(username)) {
            return badRequest("Username already exists");
        }

        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setDisplayName(isBlank(displayName) ? username : displayName);
        u.setEmail(isBlank(email) ? null : email);
        u.setRole(role);
        u.setIsActive(true);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(u);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "User created successfully",
            "data",    toDto(saved)
        ));
    }

    // ═══════════════════════════════════════════════════════════
    //  PUT /api/admin/users/{id} — Update user info
    // ═══════════════════════════════════════════════════════════
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        User u = userRepository.findById(id).orElse(null);
        if (u == null) {
            return notFound("User not found");
        }

        String currentUsername = authentication != null ? authentication.getName() : null;
        boolean isSelf = currentUsername != null && currentUsername.equals(u.getUsername());

        if (request.containsKey("password") || request.containsKey("passwordHash")) {
            return badRequest("Password cannot be changed here. Use /api/auth/change-password for self-service.");
        }
        if (request.containsKey("username")) {
            return badRequest("Username is immutable after creation");
        }

        if (request.containsKey("displayName")) {
            String displayName = str(request.get("displayName"));
            u.setDisplayName(isBlank(displayName) ? null : displayName);
        }
        if (request.containsKey("email")) {
            String email = str(request.get("email"));
            u.setEmail(isBlank(email) ? null : email);
        }
        if (request.containsKey("role")) {
            String newRole = strOrDefault(request.get("role"), "").toLowerCase();
            if (!ALLOWED_ROLES.contains(newRole)) {
                return badRequest("Invalid role. Allowed: admin, user, accountant, viewer");
            }
            if (isSelf && ("admin".equals(u.getRole()) || "user".equals(u.getRole()))
                      && !("admin".equals(newRole) || "user".equals(newRole))) {
                return badRequest("Cannot downgrade your own admin/user role. Ask another admin.");
            }

            // If changing TO accountant/viewer, ensure user has entities assigned
            if (RESTRICTED_ROLES.contains(newRole) && !RESTRICTED_ROLES.contains(u.getRole())) {
                List<UUID> existingEntities = userEntityRepository.findEntityIdsByUserId(id);
                if (existingEntities.isEmpty()) {
                    return badRequest("Cannot change role to '" + newRole +
                        "' without first assigning at least one entity. " +
                        "Use PUT /api/admin/users/" + id + "/entities first.");
                }
            }

            u.setRole(newRole);
        }
        if (request.containsKey("isActive")) {
            Object val = request.get("isActive");
            boolean newActive = Boolean.TRUE.equals(val) || "true".equalsIgnoreCase(String.valueOf(val));
            if (isSelf && !newActive) {
                return badRequest("Cannot deactivate your own account");
            }
            u.setIsActive(newActive);
        }

        u.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(u);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "User updated successfully",
            "data",    toDto(saved)
        ));
    }

    // ═══════════════════════════════════════════════════════════
    //  DELETE /api/admin/users/{id} — Soft delete
    // ═══════════════════════════════════════════════════════════
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable UUID id,
            Authentication authentication) {

        User u = userRepository.findById(id).orElse(null);
        if (u == null) {
            return notFound("User not found");
        }

        String currentUsername = authentication != null ? authentication.getName() : null;
        if (currentUsername != null && currentUsername.equals(u.getUsername())) {
            return badRequest("Cannot delete your own account");
        }

        if (Boolean.FALSE.equals(u.getIsActive())) {
            return badRequest("User is already inactive");
        }

        u.setIsActive(false);
        u.setUpdatedAt(LocalDateTime.now());
        userRepository.save(u);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "User deactivated (soft delete) successfully"
        ));
    }

    // ═══════════════════════════════════════════════════════════
    //  GET /api/admin/users/{id}/entities — List user's assigned entities
    //  Returns [] for admin/user users with no explicit assignment (= all entities)
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/{id}/entities")
    public ResponseEntity<?> getUserEntities(@PathVariable UUID id) {
        User u = userRepository.findById(id).orElse(null);
        if (u == null) {
            return notFound("User not found");
        }

        List<CompanyEntity> entities = userEntityRepository.findEntitiesByUserId(id);
        List<Map<String, Object>> data = entities.stream()
            .map(this::entityToDto)
            .collect(Collectors.toList());

        // Helpful meta: explains what empty list means for this role
        String meta;
        boolean accessAll = data.isEmpty() && !RESTRICTED_ROLES.contains(u.getRole());
        if (accessAll) {
            meta = "No explicit assignment — this user has access to ALL entities (role: " + u.getRole() + ")";
        } else if (data.isEmpty()) {
            meta = "No entities assigned — this user has NO access (role: " + u.getRole() + " requires >=1)";
        } else {
            meta = "User has explicit access to " + data.size() + " entit" + (data.size() == 1 ? "y" : "ies");
        }

        return ResponseEntity.ok(Map.of(
            "success",   true,
            "userRole",  u.getRole(),
            "accessAll", accessAll,
            "data",      data,
            "count",     data.size(),
            "meta",      meta
        ));
    }

    // ═══════════════════════════════════════════════════════════
    //  PUT /api/admin/users/{id}/entities — Replace user's entity assignment
    //  Body: { "entityIds": ["uuid-1", "uuid-2", ...] }
    // ═══════════════════════════════════════════════════════════
    @PutMapping("/{id}/entities")
    @Transactional
    public ResponseEntity<?> setUserEntities(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {

        User u = userRepository.findById(id).orElse(null);
        if (u == null) {
            return notFound("User not found");
        }

        // Extract entityIds (accept array of strings or UUIDs)
        Object raw = request.get("entityIds");
        if (!(raw instanceof List<?> rawList)) {
            return badRequest("entityIds must be an array");
        }

        List<UUID> entityIds = new ArrayList<>();
        for (Object o : rawList) {
            if (o == null) continue;
            try {
                entityIds.add(UUID.fromString(String.valueOf(o).trim()));
            } catch (IllegalArgumentException ex) {
                return badRequest("Invalid UUID format: " + o);
            }
        }

        // Rule: ACCOUNTANT/VIEWER must have >=1 entity
        if (RESTRICTED_ROLES.contains(u.getRole()) && entityIds.isEmpty()) {
            return badRequest("User with role '" + u.getRole() +
                "' must have at least one entity assigned");
        }

        // Validate all entities exist and are active
        for (UUID entityId : entityIds) {
            CompanyEntity e = companyEntityRepository.findById(entityId).orElse(null);
            if (e == null) {
                return badRequest("Entity not found: " + entityId);
            }
            if (Boolean.FALSE.equals(e.getIsActive())) {
                return badRequest("Entity is inactive: " + e.getCode() + " (" + entityId + ")");
            }
        }

        // Replace-all: delete existing rows, insert new ones
        userEntityRepository.deleteAllForUser(id);
        for (UUID entityId : entityIds) {
            userEntityRepository.insertUserEntity(id, entityId);
        }

        // Return the new state
        List<CompanyEntity> current = userEntityRepository.findEntitiesByUserId(id);
        List<Map<String, Object>> data = current.stream()
            .map(this::entityToDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "User entities updated successfully",
            "data",    data,
            "count",   data.size()
        ));
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    private Map<String, Object> toDto(User u) {
        LinkedHashMap<String, Object> dto = new LinkedHashMap<>();
        dto.put("id",           u.getId());
        dto.put("username",     u.getUsername());
        dto.put("displayName",  u.getDisplayName());
        dto.put("email",        u.getEmail());
        dto.put("role",         u.getRole());
        dto.put("isActive",     u.getIsActive());
        dto.put("lastLogin",    u.getLastLogin());
        dto.put("createdAt",    u.getCreatedAt());
        dto.put("updatedAt",    u.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> entityToDto(CompanyEntity e) {
        LinkedHashMap<String, Object> dto = new LinkedHashMap<>();
        dto.put("id",       e.getId());
        dto.put("code",     e.getCode());
        dto.put("name",     e.getName());
        dto.put("currency", e.getCurrency());
        dto.put("country",  e.getCountry());
        dto.put("isActive", e.getIsActive());
        return dto;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static String strOrDefault(Object o, String def) {
        String s = str(o);
        return (s == null || s.isEmpty()) ? def : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static ResponseEntity<?> badRequest(String msg) {
        return ResponseEntity.badRequest()
            .body(Map.of("success", false, "error", msg));
    }

    private static ResponseEntity<?> notFound(String msg) {
        return ResponseEntity.status(404)
            .body(Map.of("success", false, "error", msg));
    }
}
