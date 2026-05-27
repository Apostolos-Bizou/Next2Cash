package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.CalendarResponse;
import com.next2me.next2cash.service.CalendarService;
import com.next2me.next2cash.service.UserAccessService;
import com.next2me.next2cash.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * CalendarController — daily-grouped cash flow events for the Calendar view.
 *
 * S96. Endpoint: GET /api/calendar?entityId={uuid|ALL}&year=2026&month=6
 * - entityId=ALL is ADMIN-only and returns aggregated (group) view.
 * - entityId=&lt;uuid&gt; returns analytical (per-transaction) view, subject
 *   to UserAccessService entity scope check.
 */
@RestController
@RequestMapping("/api/calendar")
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
    @PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT','VIEWER')")
    public ResponseEntity<CalendarResponse> getCalendar(
            @RequestParam("entityId") String entityIdParam,
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            @AuthenticationPrincipal User user) {

        // Basic param validation
        if (year < 2000 || year > 2100) {
            return ResponseEntity.badRequest().build();
        }
        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest().build();
        }

        boolean isGroupScope = "ALL".equalsIgnoreCase(entityIdParam);
        if (isGroupScope) {
            // ADMIN-only for group view
            if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
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

        // Entity-scope guard
        userAccessService.assertCanAccessEntity(user, entityId);

        CalendarResponse resp = calendarService.buildEntityCalendar(entityId, year, month);
        return ResponseEntity.ok(resp);
    }
}
