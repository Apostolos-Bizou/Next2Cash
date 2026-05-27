package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.CalendarResponse;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.CalendarService;
import com.next2me.next2cash.service.UserAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * CalendarController — daily-grouped cash flow events for the Calendar view.
 *
 * S96. Endpoint: GET /api/calendar?entityId={uuid|ALL}&year=2026&month=6
 * - entityId=ALL is ADMIN-only and returns aggregated (group) view.
 * - entityId=&lt;uuid&gt; returns analytical (per-transaction) view, subject
 *   to UserAccessService entity scope check.
 *
 * Auth pattern matches DashboardController: extract user from Authorization
 * header via UserAccessService.getCurrentUser(authHeader).
 */
@RestController
@RequestMapping("/api/calendar")
@PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT','VIEWER')")
public class CalendarController {

    private final CalendarService calendarService;
    private final UserAccessService userAccessService;

    @Autowired
    public CalendarController(CalendarService calendarService,
                              UserAccessService userAccessService) {
        this.calendarService = calendarService;
        this.userAccessService = userAccessService;
    }

    @GetMapping
    public ResponseEntity<CalendarResponse> getCalendar(
            @RequestParam("entityId") String entityIdParam,
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            @RequestHeader("Authorization") String authHeader) {

        if (year < 2000 || year > 2100) {
            return ResponseEntity.badRequest().build();
        }
        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest().build();
        }

        User user = userAccessService.getCurrentUser(authHeader);

        boolean isGroupScope = "ALL".equalsIgnoreCase(entityIdParam);
        if (isGroupScope) {
            // ADMIN-only for group view (role is stored as "admin" in DB)
            String role = user == null ? "" : (user.getRole() == null ? "" : user.getRole());
            if (!"admin".equalsIgnoreCase(role)) {
                return ResponseEntity.status(403).build();
            }
            CalendarResponse resp = calendarService.buildGroupCalendar(year, month);
            return ResponseEntity.ok(resp);
        }

        UUID entityId;
        try {
            entityId = UUID.fromString(entityIdParam);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }

        userAccessService.assertCanAccessEntity(user, entityId);

        CalendarResponse resp = calendarService.buildEntityCalendar(entityId, year, month);
        return ResponseEntity.ok(resp);
    }
}
