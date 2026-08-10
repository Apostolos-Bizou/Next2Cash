package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.ReportDispatchCreateRequest;
import com.next2me.next2cash.model.ReportDispatch;
import com.next2me.next2cash.model.ReportDispatchItem;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ReportDispatchItemRepository;
import com.next2me.next2cash.repository.ReportDispatchRepository;
import com.next2me.next2cash.service.ReportDispatchService;
import com.next2me.next2cash.service.UserAccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ReportDispatchController — S105, Level 3.
 *
 * Six endpoints for the dispatch archive. INVARIANT #1: VIEWER (Σίμος) and
 * ACCOUNTANT are excluded from ALL of them, including the GETs — every mapping
 * is {@code hasAnyRole('ADMIN','USER')}, DELETE is ADMIN-only. No @PreAuthorize
 * here carries VIEWER or ACCOUNTANT.
 *
 * Every endpoint runs getCurrentUser() + an entity access check (either via the
 * service, or loadScoped() for the {id} routes). Cross-entity {id} returns 403,
 * not 404 — a caller must not be told whether a dispatch outside their scope
 * exists.
 *
 * PDF streaming (GET /{id}/pdf) is a stub until Level 4: while blob_path is null
 * it always returns 404.
 */
@RestController
@RequestMapping("/api/report-dispatches")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReportDispatchController {

    private final ReportDispatchService dispatchService;
    private final ReportDispatchRepository dispatchRepository;
    private final ReportDispatchItemRepository itemRepository;
    private final UserAccessService userAccessService;

    // ─── GET list ─────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> list(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q) {

        User user = userAccessService.getCurrentUser(authHeader);
        List<ReportDispatch> rows = dispatchService.list(user, entityId, from, to, q);

        List<Map<String, Object>> data = new ArrayList<>();
        for (ReportDispatch d : rows) data.add(toDto(d));

        return ResponseEntity.ok(Map.of("success", true, "data", data, "total", data.size()));
    }

    // ─── GET single ───────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> getOne(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id) {

        User user = userAccessService.getCurrentUser(authHeader);
        ReportDispatch d = loadScoped(user, id);
        return ResponseEntity.ok(Map.of("success", true, "data", toDetailDto(d)));
    }

    // ─── GET pdf (Level 4 will stream; 404 while blob_path is null) ─────────

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> getPdf(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id) {

        User user = userAccessService.getCurrentUser(authHeader);
        ReportDispatch d = loadScoped(user, id);

        if (d.getBlobPath() == null || d.getBlobPath().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false, "error", "pdf_not_generated"));
        }
        // Level 4: stream bytes from Azure Blob. Not reachable yet.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
            "success", false, "error", "pdf_streaming_not_implemented"));
    }

    // ─── GET recipients (autocomplete) ──────────────────────────────────────

    @GetMapping("/recipients")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> recipients(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {

        User user = userAccessService.getCurrentUser(authHeader);
        List<String> data = dispatchService.recipients(user, entityId);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    // ─── POST create ────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> create(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId,
            @RequestBody ReportDispatchCreateRequest body,
            HttpServletRequest request) {

        User user = userAccessService.getCurrentUser(authHeader);
        ReportDispatch d = dispatchService.create(user, entityId, body, clientIp(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true, "data", toDetailDto(d)));
    }

    // ─── DELETE (ADMIN only) ─────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            HttpServletRequest request) {

        User user = userAccessService.getCurrentUser(authHeader);
        ReportDispatch d = loadScoped(user, id);          // 404 if missing, 403 if cross-entity
        dispatchService.delete(user, d.getEntityId(), id, clientIp(request));
        return ResponseEntity.ok(Map.of("success", true, "message", "Dispatch deleted"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Load a dispatch by id and enforce entity access. Missing → 404; existing
     * but outside the caller's entity scope → 403 (never 404 — do not leak
     * whether a dispatch exists in another entity).
     */
    private ReportDispatch loadScoped(User user, UUID id) {
        ReportDispatch d = dispatchRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatch not found"));
        userAccessService.assertCanAccessEntity(user, d.getEntityId());
        return d;
    }

    private Map<String, Object> toDto(ReportDispatch d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",         d.getId());
        m.put("entityId",   d.getEntityId());
        m.put("title",      d.getTitle());
        m.put("recipient",  d.getRecipient());
        m.put("sentDate",   d.getSentDate());
        m.put("note",       d.getNote());
        m.put("hasPdf",     d.getBlobPath() != null && !d.getBlobPath().isBlank());
        m.put("createdBy",  d.getCreatedBy());
        m.put("createdAt",  d.getCreatedAt());
        return m;
    }

    private Map<String, Object> toDetailDto(ReportDispatch d) {
        Map<String, Object> m = toDto(d);
        List<Integer> txIds = new ArrayList<>();
        for (ReportDispatchItem it : itemRepository.findByIdDispatchId(d.getId())) {
            txIds.add(it.getId().getTransactionId());
        }
        m.put("transactionIds", txIds);
        return m;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
