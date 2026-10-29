package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.User;
import com.next2me.next2cash.model.UserNoticeDismissal;
import com.next2me.next2cash.repository.UserNoticeDismissalRepository;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Per-user dismissal of one-time in-app notices (Oct 2026).
 *
 *   GET  /api/notices/{key}          -> { success, data: { key, dismissed } }   any authenticated role
 *   POST /api/notices/{key}/dismiss  -> { success, data: { key, dismissed: true } }  ADMIN / USER / ACCOUNTANT
 *
 * A user can only read and dismiss their OWN notice (the user comes from the
 * JWT, never from the request). VIEWER never reaches the POST (Invariant 1: no
 * VIEWER on writes), which is fine: the banners that use this are shown only to
 * roles that can edit. The key is a short slug so the table cannot be used as
 * free-form storage.
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class UserNoticeController {

    private static final Pattern KEY = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,59}$");

    private final UserNoticeDismissalRepository repository;
    private final UserAccessService userAccessService;

    @GetMapping("/{key}")
    @PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT','VIEWER')")
    public ResponseEntity<?> status(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String key) {
        User user = userAccessService.getCurrentUser(authHeader);
        String k = validKey(key);
        return ok(k, repository.existsByUserIdAndNoticeKey(user.getId(), k));
    }

    @PostMapping("/{key}/dismiss")
    @PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT')")
    @Transactional
    public ResponseEntity<?> dismiss(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String key) {
        User user = userAccessService.getCurrentUser(authHeader);
        String k = validKey(key);
        if (!repository.existsByUserIdAndNoticeKey(user.getId(), k)) {   // idempotent
            UserNoticeDismissal d = new UserNoticeDismissal();
            d.setUserId(user.getId());
            d.setNoticeKey(k);
            repository.save(d);
        }
        return ok(k, true);
    }

    private static String validKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("notice key must be a short lowercase slug");
        }
        return key;
    }

    private static ResponseEntity<Map<String, Object>> ok(String key, boolean dismissed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("dismissed", dismissed);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
