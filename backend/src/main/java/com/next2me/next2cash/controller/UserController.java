package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

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

    private Map<String, Object> toDto(User u) {
        java.util.LinkedHashMap<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("id",           u.getId());
        dto.put("username",     u.getUsername());
        dto.put("displayName",  u.getDisplayName());
        dto.put("email",        u.getEmail());
        dto.put("role",         u.getRole());
        dto.put("isActive",     u.getIsActive());
        dto.put("lastLogin",    u.getLastLogin());
        dto.put("createdAt",    u.getCreatedAt());
        return dto;
    }
}
