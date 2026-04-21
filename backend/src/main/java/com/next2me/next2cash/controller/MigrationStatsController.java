package com.next2me.next2cash.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TEMPORARY - Session #33 Priority 1 verification endpoint.
 * Manual role check (NOT @PreAuthorize).
 */
@RestController
@RequestMapping("/api/admin/migration")
public class MigrationStatsController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/classification-stats")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> classificationStats() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "Unauthenticated");
            return ResponseEntity.status(401).body(err);
        }
        boolean isAdmin = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equalsIgnoreCase("ROLE_ADMIN")
                        || a.equalsIgnoreCase("ADMIN")
                        || a.equalsIgnoreCase("admin"));
        if (!isAdmin) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "Admin role required");
            err.put("authoritiesSeen", auth.getAuthorities().toString());
            err.put("principalName", auth.getName());
            return ResponseEntity.status(403).body(err);
        }

        String sql =
            "SELECT " +
            "  COUNT(*) FILTER (WHERE " +
            "    jsonb_array_length(COALESCE(blob_file_ids, '[]'::jsonb)) > 0 " +
            "    AND jsonb_array_length(COALESCE(drive_file_ids, '[]'::jsonb)) = 0" +
            "  ) AS azure_path_only, " +
            "  COUNT(*) FILTER (WHERE " +
            "    jsonb_array_length(COALESCE(drive_file_ids, '[]'::jsonb)) > 0 " +
            "    AND jsonb_array_length(COALESCE(blob_file_ids, '[]'::jsonb)) = 0" +
            "  ) AS drive_id_only, " +
            "  COUNT(*) FILTER (WHERE " +
            "    jsonb_array_length(COALESCE(blob_file_ids, '[]'::jsonb)) > 0 " +
            "    AND jsonb_array_length(COALESCE(drive_file_ids, '[]'::jsonb)) > 0" +
            "  ) AS mixed, " +
            "  COUNT(*) FILTER (WHERE " +
            "    jsonb_array_length(COALESCE(blob_file_ids, '[]'::jsonb)) = 0 " +
            "    AND jsonb_array_length(COALESCE(drive_file_ids, '[]'::jsonb)) = 0" +
            "  ) AS empty_cls, " +
            "  COUNT(*) AS total " +
            "FROM transactions";

        Object[] row = (Object[]) em.createNativeQuery(sql).getSingleResult();

        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("AZURE_PATH_ONLY", ((Number) row[0]).longValue());
        actual.put("DRIVE_ID_ONLY",   ((Number) row[1]).longValue());
        actual.put("MIXED",           ((Number) row[2]).longValue());
        actual.put("EMPTY",           ((Number) row[3]).longValue());
        actual.put("TOTAL",           ((Number) row[4]).longValue());

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("AZURE_PATH_ONLY", 2656);
        expected.put("DRIVE_ID_ONLY",   6);
        expected.put("MIXED",           1);
        expected.put("EMPTY",           2121);
        expected.put("TOTAL",           4784);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("actual", actual);
        response.put("expected", expected);
        response.put("note", "Temporary S#33 endpoint v3");

        return ResponseEntity.ok(response);
    }
}
