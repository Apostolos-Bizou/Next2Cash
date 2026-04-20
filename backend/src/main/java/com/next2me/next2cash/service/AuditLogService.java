package com.next2me.next2cash.service;

import com.next2me.next2cash.model.AuditLog;
import com.next2me.next2cash.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

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
            entry.setDetails(details);
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
}