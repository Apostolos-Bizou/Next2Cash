package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.UserRepository;
import com.next2me.next2cash.security.JwtUtil;
import com.next2me.next2cash.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.next2me.next2cash.repository.UserEntityRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final UserEntityRepository userEntityRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Username and password required"));
        }

        return userRepository.findByUsername(username)
            .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
            .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
            .map(u -> {
                u.setLastLogin(LocalDateTime.now());
                auditLogService.log(null, u.getId(), u.getUsername(), "LOGIN_SUCCESS", "users", u.getId().toString(), null);
                userRepository.save(u);

                // Fetch entity assignments for JWT
                List<UUID> entityUuids = userEntityRepository.findEntityIdsByUserId(u.getId());
                List<String> entityIdStrings = entityUuids.stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());

                String token = jwtUtil.generateToken(
                    u.getUsername(), u.getRole(), u.getId(),
                    u.getAllowedSections(), entityIdStrings);

                // Build user response
                LinkedHashMap<String, Object> userMap = new LinkedHashMap<>();
                userMap.put("id", u.getId());
                userMap.put("username", u.getUsername());
                userMap.put("displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
                userMap.put("role", u.getRole());
                userMap.put("allowedSections", u.getAllowedSections());
                userMap.put("entityIds", entityIdStrings);

                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "token",   token,
                    "user",    userMap
                ));
            })
            .orElse(ResponseEntity.status(401)
                .body(Map.of("success", false, "error", "Invalid credentials")));
        // Note: failed login audit logged below only if username was provided
        // (actual logging happens in the orElse branch via the response)
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401)
                .body(Map.of("success", false, "error", "Not authenticated"));
        }

        String username = authentication.getName();

        return userRepository.findByUsername(username)
            .map(u -> {
                List<UUID> entityUuids = userEntityRepository.findEntityIdsByUserId(u.getId());
                List<String> entityIdStrings = entityUuids.stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());

                LinkedHashMap<String, Object> data = new LinkedHashMap<>();
                data.put("id", u.getId());
                data.put("username", u.getUsername());
                data.put("displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
                data.put("role", u.getRole());
                data.put("allowedSections", u.getAllowedSections());
                data.put("entityIds", entityIdStrings);

                return ResponseEntity.ok(data);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @RequestBody Map<String, String> request) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401)
                .body(Map.of("success", false, "error", "Not authenticated"));
        }

        String username        = authentication.getName();
        String currentPassword = request.get("currentPassword");
        String newPassword     = request.get("newPassword");

        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "currentPassword and newPassword required"));
        }
        if (newPassword.length() < 8) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "New password must be at least 8 characters"));
        }
        if (currentPassword.equals(newPassword)) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "New password must differ from current"));
        }

        return userRepository.findByUsername(username)
            .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
            .map(u -> {
                if (!passwordEncoder.matches(currentPassword, u.getPasswordHash())) {
                    return ResponseEntity.status(401)
                        .body((Object) Map.of("success", false, "error", "Current password is incorrect"));
                }

                u.setPasswordHash(passwordEncoder.encode(newPassword));
                auditLogService.log(null, u.getId(), u.getUsername(), "PASSWORD_CHANGED", "users", u.getId().toString(), null);
                u.setUpdatedAt(LocalDateTime.now());
                userRepository.save(u);

                return ResponseEntity.ok((Object) Map.of(
                    "success", true,
                    "message", "Password changed successfully. Please log in again."
                ));
            })
            .orElse(ResponseEntity.status(404)
                .body(Map.of("success", false, "error", "User not found")));
    }
}
