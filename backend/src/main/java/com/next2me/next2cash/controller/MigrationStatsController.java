package com.next2me.next2cash.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TEMPORARY ? Session #33 Priority 1 verification endpoint.
 * Returns classification counts for post-migration sanity check.
 * TO BE REMOVED once verification completes.
 *
 * Classification rules (match BlobMigrationService logic):
 *   AZURE_PATH_ONLY : blob_file_ids non-empty AND drive_file_ids empty/null
 *   DRIVE_ID_ONLY   : drive_file_ids non-empty AND blob_file_ids empty/null
 *   MIXED           : both non-empty
 *   EMPTY           : both empty/null
 */
@RestController
@RequestMapping("/api/admin/migration")
public class MigrationStatsController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/classification-stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> classificationStats() {
        // JSONB-based counts. COALESCE handles NULL safely.
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("AZURE_PATH_ONLY", ((Number) row[0]).longValue());
        result.put("DRIVE_ID_ONLY",   ((Number) row[1]).longValue());
        result.put("MIXED",           ((Number) row[2]).longValue());
        result.put("EMPTY",           ((Number) row[3]).longValue());
        result.put("TOTAL",           ((Number) row[4]).longValue());

        // Expected (per bootstrap estimates)
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("AZURE_PATH_ONLY", 2656);
        expected.put("DRIVE_ID_ONLY",   6);
        expected.put("MIXED",           1);
        expected.put("EMPTY",           2121);
        expected.put("TOTAL",           4784);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("actual", result);
        response.put("expected", expected);
        response.put("note", "Temporary S#33 endpoint. Remove after verification.");

        return ResponseEntity.ok(response);
    }
}
