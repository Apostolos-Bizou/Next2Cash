package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.CashFlowEvent;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.CashFlowService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GET /api/cashflow?entityId={uuid}&amp;from=YYYY-MM-DD&amp;to=YYYY-MM-DD
 *
 * Returns a unified, date-sorted stream of transaction and payment events
 * within the window. Payment events are dated by paymentDate (so a March
 * invoice paid in April surfaces under the April filter).
 *
 * Security: caller must have access to the entity. Admins bypass.
 */
@RestController
@RequestMapping("/api/cashflow")
@RequiredArgsConstructor
public class CashFlowController {

    private final CashFlowService cashFlowService;
    private final UserAccessService userAccessService;

    @GetMapping
    public ResponseEntity<?> getCashFlow(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("entityId") UUID entityId,
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to) {

        // Validate window
        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "from must be on or before to"));
        }

        // Resolve caller and authorize access to the entity
        User user = userAccessService.getCurrentUser(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body(
                Map.of("error", "unauthenticated"));
        }
        Set<UUID> allowed = userAccessService.getAccessibleEntityIds(user);
        if (!allowed.contains(entityId)) {
            return ResponseEntity.status(403).body(
                Map.of("error", "no access to entity"));
        }

        List<CashFlowEvent> events = cashFlowService.getEvents(entityId, from, to);

        Map<String, Object> body = new HashMap<>();
        body.put("entityId", entityId);
        body.put("from", from);
        body.put("to", to);
        body.put("count", events.size());
        body.put("events", events);
        return ResponseEntity.ok(body);
    }
}
