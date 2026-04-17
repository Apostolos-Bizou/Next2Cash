package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.UserRepository;
import com.next2me.next2cash.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

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
                userRepository.save(u);

                String token = jwtUtil.generateToken(u.getUsername(), u.getRole(), u.getId());

                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "token",   token,
                    "user", Map.of(
                        "id",          u.getId(),
                        "username",    u.getUsername(),
                        "displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                        "role",        u.getRole()
                    )
                ));
            })
            .orElse(ResponseEntity.status(401)
                .body(Map.of("success", false, "error", "Invalid credentials")));
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
            .map(u -> ResponseEntity.ok(Map.of(
                "id",          u.getId(),
                "username",    u.getUsername(),
                "displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                "role",        u.getRole()
            )))
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
