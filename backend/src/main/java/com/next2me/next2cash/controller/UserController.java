package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private static final Set<String> ALLOWED_ROLES = Set.of("admin", "user", "accountant", "viewer");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
    //  Body: { username, password, displayName?, email?, role }
    // ═══════════════════════════════════════════════════════════
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> request) {

        String username    = str(request.get("username"));
        String password    = str(request.get("password"));
        String displayName = str(request.get("displayName"));
        String email       = str(request.get("email"));
        String role        = strOrDefault(request.get("role"), "user").toLowerCase();

        // Validation
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

        // Create
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
    //  Body: { displayName?, email?, role?, isActive? }
    //  NOT allowed: password, username
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

        // Get the user performing the action
        String currentUsername = authentication != null ? authentication.getName() : null;
        boolean isSelf = currentUsername != null && currentUsername.equals(u.getUsername());

        // Explicitly reject password / username changes
        if (request.containsKey("password") || request.containsKey("passwordHash")) {
            return badRequest("Password cannot be changed here. Use /api/auth/change-password for self-service.");
        }
        if (request.containsKey("username")) {
            return badRequest("Username is immutable after creation");
        }

        // Apply allowed updates
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
            // Self-protection: can't downgrade your own admin/user privileges
            if (isSelf && ("admin".equals(u.getRole()) || "user".equals(u.getRole()))
                      && !("admin".equals(newRole) || "user".equals(newRole))) {
                return badRequest("Cannot downgrade your own admin/user role. Ask another admin.");
            }
            u.setRole(newRole);
        }
        if (request.containsKey("isActive")) {
            Object val = request.get("isActive");
            boolean newActive = Boolean.TRUE.equals(val) || "true".equalsIgnoreCase(String.valueOf(val));
            // Self-protection: can't deactivate yourself
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
    //  DELETE /api/admin/users/{id} — Soft delete (ADMIN only)
    //  Sets is_active = false (retains audit trail)
    // ═══════════════════════════════════════════════════════════
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable UUID id,
            Authentication authentication) {

        User u = userRepository.findById(id).orElse(null);
        if (u == null) {
            return notFound("User not found");
        }

        // Self-delete protection
        String currentUsername = authentication != null ? authentication.getName() : null;
        if (currentUsername != null && currentUsername.equals(u.getUsername())) {
            return badRequest("Cannot delete your own account");
        }

        // Already inactive?
        if (Boolean.FALSE.equals(u.getIsActive())) {
            return badRequest("User is already inactive");
        }

        // Soft delete
        u.setIsActive(false);
        u.setUpdatedAt(LocalDateTime.now());
        userRepository.save(u);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "User deactivated (soft delete) successfully"
        ));
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    /** Convert User entity to safe DTO (never includes passwordHash). */
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
