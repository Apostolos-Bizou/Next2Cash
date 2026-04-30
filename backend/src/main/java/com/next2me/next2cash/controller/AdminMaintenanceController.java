package com.next2me.next2cash.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Temporary admin endpoint for one-time database maintenance tasks.
 * TO BE REMOVED after sequence fix is verified.
 */
@RestController
@RequestMapping("/api/admin/maintenance")
public class AdminMaintenanceController {

    private static final Logger log = LoggerFactory.getLogger(AdminMaintenanceController.class);

    @PersistenceContext
    private EntityManager em;

    @PostMapping("/fix-payments-sequence")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> fixPaymentsSequence() {
        Map<String, Object> result = new HashMap<>();

        try {
            Object maxIdBefore = em.createNativeQuery(
                "SELECT COALESCE(MAX(id), 0) FROM payments"
            ).getSingleResult();

            Object seqBefore = em.createNativeQuery(
                "SELECT last_value FROM payments_id_seq"
            ).getSingleResult();

            log.info("[SEQ-FIX] Before: max(id)={}, seq.last_value={}", maxIdBefore, seqBefore);

            Object newSeqValue = em.createNativeQuery(
                "SELECT setval('payments_id_seq', (SELECT COALESCE(MAX(id), 1) FROM payments))"
            ).getSingleResult();

            Object seqAfter = em.createNativeQuery(
                "SELECT last_value FROM payments_id_seq"
            ).getSingleResult();

            log.info("[SEQ-FIX] After: setval returned={}, seq.last_value={}", newSeqValue, seqAfter);

            result.put("success", true);
            result.put("maxIdBefore", maxIdBefore.toString());
            result.put("seqBefore", seqBefore.toString());
            result.put("setvalReturned", newSeqValue.toString());
            result.put("seqAfter", seqAfter.toString());
            result.put("message", "Sequence successfully reset");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[SEQ-FIX] Failed", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}