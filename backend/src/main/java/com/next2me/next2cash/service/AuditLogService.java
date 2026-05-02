package com.next2me.next2cash.service;

import com.next2me.next2cash.model.AuditLog;
import com.next2me.next2cash.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void log(UUID entityId, UUID userId, String username,
                    String action, String targetTable, String targetId,
                    String details, String ipAddress) {
        try {
            AuditLog entry = new AuditLog();
            entry.setEntityId(entityId);
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setAction(action);
            entry.setTargetTable(targetTable);
            entry.setTargetId(targetId);
            entry.setDetails(wrapAsJson(details));
            entry.setIpAddress(ipAddress);
            entry.setCreatedAt(OffsetDateTime.now());
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    public void log(UUID entityId, UUID userId, String username,
                    String action, String targetTable, String targetId, String details) {
        log(entityId, userId, username, action, targetTable, targetId, details, null);
    }

    /**
     * Ensure the details payload is a valid JSON string before persisting,
     * so the JSONB column constraint is never violated. Plain text passed
     * by callers gets wrapped as {"message": "..."}; existing JSON is
     * validated and passed through unchanged. Returns null on null/empty
     * input or unrecoverable failures (column allows null).
     */
    private String wrapAsJson(String details) {
        if (details == null) return null;
        String s = details.trim();
        if (s.isEmpty()) return null;
        char first = s.charAt(0);
        if (first == '{' || first == '[' || first == '"') {
            try {
                objectMapper.readTree(s);
                return s;
            } catch (Exception ignore) {
                // looks like JSON but is malformed -- fall through and wrap.
            }
        }
        try {
            Map<String, String> wrapper = new HashMap<>();
            wrapper.put("message", details);
            return objectMapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            log.warn("wrapAsJson failed, dropping details: {}", e.getMessage());
            return null;
        }
    }
}