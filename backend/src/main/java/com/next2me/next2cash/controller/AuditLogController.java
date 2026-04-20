package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.AuditLog;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.AuditLogRepository;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api/activity-log")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final UserAccessService userAccessService;

    @GetMapping("/debug")
    public ResponseEntity<?> debug(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("authHeaderPresent", authHeader != null);
        info.put("authHeaderLength", authHeader != null ? authHeader.length() : 0);
        try {
            User user = userAccessService.getCurrentUser(authHeader);
            info.put("userId", user.getId());
            info.put("username", user.getUsername());
            info.put("role", user.getRole());
            info.put("step", "user resolved OK");
            UUID testEntity = UUID.fromString("58202b71-4ddb-45c9-8e3c-39e816bde972");
            boolean canAccess = userAccessService.canAccessEntity(user, testEntity);
            info.put("canAccessNext2me", canAccess);
        } catch (Exception e) {
            info.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return ResponseEntity.ok(info);
    }
    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok(Map.of("success", true, "message", "audit controller alive"));
    }

    @GetMapping("/{entityId}")
    public ResponseEntity<?> getAuditLog(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        OffsetDateTime fromDt = null;
        OffsetDateTime toDt = null;
        if (from != null && !from.isBlank()) {
            fromDt = LocalDate.parse(from).atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        if (to != null && !to.isBlank()) {
            toDt = LocalDate.parse(to).plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        }

        Page<AuditLog> result = auditLogRepository.findFiltered(
            entityId, action, username, fromDt, toDt,
            PageRequest.of(page, size));

        List<Map<String, Object>> data = new ArrayList<>();
        for (AuditLog a : result.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("entityId", a.getEntityId());
            m.put("userId", a.getUserId());
            m.put("username", a.getUsername());
            m.put("action", a.getAction());
            m.put("targetTable", a.getTargetTable());
            m.put("targetId", a.getTargetId());
            m.put("details", a.getDetails());
            m.put("ipAddress", a.getIpAddress());
            m.put("createdAt", a.getCreatedAt());
            data.add(m);
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data,
            "total", result.getTotalElements(),
            "page", result.getNumber(),
            "totalPages", result.getTotalPages()
        ));
    }
}