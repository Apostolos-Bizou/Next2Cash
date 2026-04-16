package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.UserRepository;
import com.next2me.next2cash.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
                // Update last login
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
        // JWT is stateless - client deletes token
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestAttribute("username") String username) {
        return userRepository.findByUsername(username)
            .map(u -> ResponseEntity.ok(Map.of(
                "id",          u.getId(),
                "username",    u.getUsername(),
                "displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                "role",        u.getRole()
            )))
            .orElse(ResponseEntity.notFound().build());
    }
}
