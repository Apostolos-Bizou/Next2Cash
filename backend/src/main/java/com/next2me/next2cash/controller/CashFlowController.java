package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.CashFlowEvent;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.CashFlowService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GET /api/cashflow?entityId=X&from=YYYY-MM-DD&to=YYYY-MM-DD
 *
 * Returns a unified event stream of transaction events + payment events
 * within the date range. This is the canonical endpoint for cash-flow views
 * (replaces ad-hoc client-side merging of /api/transactions + /api/payments).
 *
 * Response shape (matches project API convention):
 *   { "success": true, "total": N, "data": [ CashFlowEvent, ... ] }
 *
 * Role access: ADMIN, USER (entity-restricted).
 */
@RestController
@RequestMapping("/api/cashflow")
@RequiredArgsConstructor
public class CashFlowController {

    private final CashFlowService cashFlowService;
    private final UserAccessService userAccessService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<?> getCashFlow(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestParam String from,
            @RequestParam String to) {

        // Security guard
        User user = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(user, entityId);

        // Parse dates
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(from);
            toDate = LocalDate.parse(to);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Invalid date format — use YYYY-MM-DD"
            ));
        }

        if (fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "from date must be <= to date"
            ));
        }

        List<CashFlowEvent> events = cashFlowService.getEvents(entityId, fromDate, toDate);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "total", events.size(),
            "data", events
        ));
    }
}