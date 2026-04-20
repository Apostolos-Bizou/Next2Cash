package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.CompanyEntityRepository;
import com.next2me.next2cash.repository.UserEntityRepository;
import com.next2me.next2cash.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
 * Access: ADMIN and USER roles (DELETE + reset-password are ADMIN only - enforced in SecurityConfig).
 *
 * Privilege-escalation rules (Phase F):
 *  - USER role cannot create users with role=admin
 *  - USER role cannot change the role of any other user
 *  - USER role cannot edit admin accounts (role/isActive changes blocked)
 *  - Last-admin protection: cannot delete or demote the last active admin
 *
 * Other rules:
 *  - Password is set ONLY on create (POST), self-service /api/auth/change-password,
 *    or admin reset via POST /{id}/reset-password
 *  - Self-delete is blocked
 *  - Self-reset via admin endpoint is blocked (must use self-service)
 *  - Self role downgrade is blocked
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

    // ─────────────────────────────────────────────────────────────────
    //  GET /api/admin/users - List all users
    // ─────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────
    //  POST /api/admin/users - Create new user
    //  Phase F: non-admin callers cannot create admin users.
    // ─────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> createUser(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

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

        // Phase F: privilege-escalation guard
        if ("admin".equals(role) && !callerIsAdmin(authentication)) {
            return badRequest("Only admins can create users with role 'admin'");
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

        // M.6: allowed_sections (JSON array or null)
        if (request.containsKey("allowedSections")) {
            Object sectionsRaw = request.get("allowedSections");
            if (sectionsRaw == null) {
                u.setAllowedSections(null);
            } else {
                u.setAllowedSections(String.valueOf(sectionsRaw));
            }
        } else {
            // Set defaults based on role
            u.setAllowedSections(getDefaultSections(role));
        }

        User saved = userRepository.save(u);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "User created successfully",
            "data",    toDto(saved)
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    //  PUT /api/admin/users/{id} - Update user info (NOT password)
    //  Phase F:
    //    - Non-admin caller cannot change ANY user's role
    //    - Non-admin caller cannot edit admin accounts
    //    - Last-admin protection on role downgrade
    // ─────────────────────────────────────────────────────────────────
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
        boolean callerIsAdmin = callerIsAdmin(authentication);

        if (request.containsKey("password") || request.containsKey("passwordHash")) {
            return badRequest("Password cannot be changed here. Use POST /{id}/reset-password (admin) or POST /api/auth/change-password (self-service).");
        }
        if (request.containsKey("username")) {
            return badRequest("Username is immutable after creation");
        }

        // Phase F: non-admin cannot edit admin accounts (except themselves for display/email)
        if (!callerIsAdmin && "admin".equals(u.getRole()) && !isSelf) {
            return badRequest("Only admins can edit admin accounts");
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

            // Phase F: only admins can change roles
            if (!callerIsAdmin) {
                return badRequest("Only admins can change user roles");
            }

            if (isSelf && ("admin".equals(u.getRole()) || "user".equals(u.getRole()))
                      && !("admin".equals(newRole) || "user".equals(newRole))) {
                return badRequest("Cannot downgrade your own admin/user role. Ask another admin.");
            }

            // Phase F: last-admin protection on downgrade
            if ("admin".equals(u.getRole()) && !"admin".equals(newRole)) {
                long otherActiveAdmins = countOtherActiveAdmins(u.getId());
                if (otherActiveAdmins == 0) {
                    return badRequest("Cannot demote the last active admin. Promote another user to admin first.");
                }
            }

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
            // Phase F: non-admin cannot deactivate admin accounts
            if (!callerIsAdmin && "admin".equals(u.getRole()) && !newActive) {
                return badRequest("Only admins can deactivate admin accounts");
            }
            // Phase F: last-admin protection on deactivate
            if ("admin".equals(u.getRole()) && !newActive) {
                long otherActiveAdmins = countOtherActiveAdmins(u.getId());
                if (otherActiveAdmins == 0) {
                    return badRequest("Cannot deactivate the last active admin");
                }
            }
            u.setIsActive(newActive);
        }

        // M.6: allowed_sections update
        if (request.containsKey("allowedSections")) {
            Object sectionsRaw = request.get("allowedSections");
            if (sectionsRaw == null) {
                u.setAllowedSections(null);
            } else {
                u.setAllowedSections(String.valueOf(sectionsRaw));
            }
        }

        u.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(u);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "User updated successfully",
            "data",    toDto(saved)
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    //  DELETE /api/admin/users/{id} - Soft delete (ADMIN only)
    //  Phase F: last-admin protection
    // ─────────────────────────────────────────────────────────────────
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

        // Phase F: last-admin protection
        if ("admin".equals(u.getRole())) {
            long otherActiveAdmins = countOtherActiveAdmins(u.getId());
            if (otherActiveAdmins == 0) {
                return badRequest("Cannot delete the last active admin. Promote another user to admin first.");
            }
        }

        u.setIsActive(false);
        u.setUpdatedAt(LocalDateTime.now());
        userRepository.save(u);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "User deactivated (soft delete) successfully"
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    //  POST /api/admin/users/{id}/reset-password - Admin resets user's password
    //  Access: ADMIN only (enforced in SecurityConfig)
    // ─────────────────────────────────────────────────────────────────
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        User u = userRepository.findById(id).orElse(null);
        if (u == null) {
            return notFound("User not found");
        }

        String currentUsername = authentication != null ? authentication.getName() : null;

        if (currentUsername != null && currentUsername.equals(u.getUsername())) {
            return badRequest("Use POST /api/auth/change-password to change your own password");
        }

        if (Boolean.FALSE.equals(u.getIsActive())) {
            return badRequest("Cannot reset password for inactive user. Reactivate first.");
        }

        String newPassword = str(request.get("newPassword"));
        if (isBlank(newPassword)) {
            return badRequest("newPassword is required");
        }
        if (newPassword.length() < 8) {
            return badRequest("Password must be at least 8 characters");
        }

        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setUpdatedAt(LocalDateTime.now());
        userRepository.save(u);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Password reset successfully for user: " + u.getUsername()
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    //  GET /api/admin/users/{id}/entities - List user's assigned entities
    // ─────────────────────────────────────────────────────────────────
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

        String meta;
        boolean accessAll = data.isEmpty() && !RESTRICTED_ROLES.contains(u.getRole());
        if (accessAll) {
            meta = "No explicit assignment - this user has access to ALL entities (role: " + u.getRole() + ")";
        } else if (data.isEmpty()) {
            meta = "No entities assigned - this user has NO access (role: " + u.getRole() + " requires >=1)";
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

    // ─────────────────────────────────────────────────────────────────
    //  PUT /api/admin/users/{id}/entities - Replace user's entity assignment
    // ─────────────────────────────────────────────────────────────────
    @PutMapping("/{id}/entities")
    @Transactional
    public ResponseEntity<?> setUserEntities(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {

        User u = userRepository.findById(id).orElse(null);
        if (u == null) {
            return notFound("User not found");
        }

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

        if (RESTRICTED_ROLES.contains(u.getRole()) && entityIds.isEmpty()) {
            return badRequest("User with role '" + u.getRole() +
                "' must have at least one entity assigned");
        }

        for (UUID entityId : entityIds) {
            CompanyEntity e = companyEntityRepository.findById(entityId).orElse(null);
            if (e == null) {
                return badRequest("Entity not found: " + entityId);
            }
            if (Boolean.FALSE.equals(e.getIsActive())) {
                return badRequest("Entity is inactive: " + e.getCode() + " (" + entityId + ")");
            }
        }

        userEntityRepository.deleteAllForUser(id);
        for (UUID entityId : entityIds) {
            userEntityRepository.insertUserEntity(id, entityId);
        }

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

    // ─────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────

    /** Count active admin users OTHER than the given userId. */
    /** M.6: Default allowed sections per role. Returns JSON array string or null. */
    private static String getDefaultSections(String role) {
        if ("admin".equals(role) || "user".equals(role)) {
            return null;
        } else if ("accountant".equals(role)) {
            return "[\"zip-export\"]";
        } else if ("viewer".equals(role)) {
            return "[\"dashboard\",\"ai-analysis\"]";
        }
        return null;
    }


    private long countOtherActiveAdmins(UUID excludeUserId) {
        return userRepository.findAllByOrderByUsernameAsc().stream()
            .filter(usr -> "admin".equals(usr.getRole()))
            .filter(usr -> Boolean.TRUE.equals(usr.getIsActive()))
            .filter(usr -> !usr.getId().equals(excludeUserId))
            .count();
    }

    /** Check if authenticated caller has ROLE_ADMIN. */
    private boolean callerIsAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority a : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

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
        dto.put("allowedSections", u.getAllowedSections());
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
