package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.RecurrencePattern;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.RecurrencePatternRepository;
import com.next2me.next2cash.service.AuditLogService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * RecurrencePattern endpoints.
 *
 * Created in Phase 1 (Session 3, May 2026) for the Cash Planning Module.
 *
 * Recurrence patterns are master data (UUID-keyed) shared across entities:
 * the same pattern can be referenced by transactions of any entity. Therefore
 * there is no entityId on the pattern itself, and no per-entity access filter.
 * Class-level @PreAuthorize gates role access; mutating endpoints require an
 * entityId query param so audit logs can be attributed to a business context.
 *
 * Security model:
 *   - ADMIN    : full access
 *   - USER     : full access (recurrence patterns are not entity-scoped)
 *   - VIEWER   : read-only (excluded from POST/PUT/DELETE at method level)
 *   - ACCOUNTANT: 403 (blocked at class level)
 *
 * Frequency must be one of: DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM
 * (enforced by chk_recurrence_frequency in the database).
 */
@RestController
@RequestMapping("/api/recurrence-patterns")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','USER','VIEWER')")
public class RecurrencePatternController {

    private static final Set<String> ALLOWED_FREQUENCIES = Set.of(
        "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "CUSTOM"
    );

    private final RecurrencePatternRepository recurrencePatternRepository;
    private final UserAccessService userAccessService;
    private final AuditLogService auditLogService;

    /**
     * GET /api/recurrence-patterns
     *
     * List all recurrence patterns (paginated server-side later if needed).
     * Returns rows ordered by start_date DESC so newest definitions surface first.
     */
    @GetMapping
    public ResponseEntity<?> listPatterns(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // Authenticate -- no entity filter (master data).
        User currentUser = userAccessService.getCurrentUser(authHeader);
        if (currentUser == null) {
            return unauthorized();
        }

        List<RecurrencePattern> all = recurrencePatternRepository.findAll();
        // Sort by start_date DESC, then by id (stable order across calls).
        all.sort((a, b) -> {
            int dateCmp = b.getStartDate().compareTo(a.getStartDate());
            if (dateCmp != 0) return dateCmp;
            return b.getId().compareTo(a.getId());
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", all);
        response.put("total", all.size());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/recurrence-patterns/{id}
     *
     * Fetch a single pattern by id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPattern(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        if (currentUser == null) {
            return unauthorized();
        }

        Optional<RecurrencePattern> pattern = recurrencePatternRepository.findById(id);
        if (pattern.isEmpty()) {
            return notFound("Recurrence pattern not found");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", pattern.get());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/recurrence-patterns?entityId=X
     *
     * Create a new recurrence pattern. The entityId query param is for audit
     * attribution only -- the pattern itself is not bound to any entity.
     *
     * Body:
     *   {
     *     "frequency": "MONTHLY",      // required
     *     "intervalCount": 1,           // default 1
     *     "dayOfMonth": 10,             // for MONTHLY/QUARTERLY/YEARLY
     *     "dayOfWeek": 1,               // for WEEKLY (1=Mon..7=Sun)
     *     "startDate": "2026-06-01",   // required
     *     "endDate": "2027-06-01",     // optional
     *     "maxOccurrences": 12,         // optional cap
     *     "timezone": "Europe/Athens"  // default Europe/Athens
     *   }
     *
     * ADMIN + USER only.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> createPattern(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam UUID entityId,
            @RequestBody RecurrencePattern body) {

        User user = userAccessService.getCurrentUser(authHeader);
        if (user == null) {
            return unauthorized();
        }
        // entityId is for audit attribution -- verify user can act on it.
        userAccessService.assertCanAccessEntity(user, entityId);

        // Validate the payload before persisting.
        ResponseEntity<?> validationError = validatePayload(body);
        if (validationError != null) {
            return validationError;
        }

        // Defensive defaults (the @Column defaults are applied at INSERT, but
        // we set them here so the response echoes the effective values).
        if (body.getIntervalCount() == null) body.setIntervalCount(1);
        if (body.getTimezone() == null || body.getTimezone().isBlank()) {
            body.setTimezone("Europe/Athens");
        }

        RecurrencePattern saved = recurrencePatternRepository.save(body);

        auditLogService.log(
            entityId,
            user.getId(),
            user.getUsername(),
            "RECURRENCE_PATTERN_CREATE",
            "recurrence_patterns",
            saved.getId().toString(),
            "frequency=" + saved.getFrequency()
                + ", interval=" + saved.getIntervalCount()
                + ", startDate=" + saved.getStartDate()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", saved);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/recurrence-patterns/{id}?entityId=X
     *
     * Update an existing recurrence pattern. Only mutable fields are applied
     * from the body; createdAt is preserved, updatedAt is bumped via @PreUpdate.
     *
     * ADMIN + USER only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> updatePattern(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestBody RecurrencePattern body) {

        User user = userAccessService.getCurrentUser(authHeader);
        if (user == null) {
            return unauthorized();
        }
        userAccessService.assertCanAccessEntity(user, entityId);

        Optional<RecurrencePattern> existingOpt = recurrencePatternRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return notFound("Recurrence pattern not found");
        }
        RecurrencePattern existing = existingOpt.get();

        // Apply only the fields the API allows to change. Defensive nulls
        // mean a partial update does not wipe a previously-set field.
        if (body.getFrequency() != null) {
            String freq = body.getFrequency().toUpperCase();
            if (!ALLOWED_FREQUENCIES.contains(freq)) {
                return badRequest("Invalid frequency: " + body.getFrequency());
            }
            existing.setFrequency(freq);
        }
        if (body.getIntervalCount() != null) {
            if (body.getIntervalCount() < 1) {
                return badRequest("intervalCount must be >= 1");
            }
            existing.setIntervalCount(body.getIntervalCount());
        }
        if (body.getDayOfMonth() != null) {
            if (body.getDayOfMonth() < 1 || body.getDayOfMonth() > 31) {
                return badRequest("dayOfMonth must be between 1 and 31");
            }
            existing.setDayOfMonth(body.getDayOfMonth());
        }
        if (body.getDayOfWeek() != null) {
            if (body.getDayOfWeek() < 1 || body.getDayOfWeek() > 7) {
                return badRequest("dayOfWeek must be between 1 and 7");
            }
            existing.setDayOfWeek(body.getDayOfWeek());
        }
        if (body.getStartDate() != null) {
            existing.setStartDate(body.getStartDate());
        }
        if (body.getEndDate() != null) {
            existing.setEndDate(body.getEndDate());
        }
        if (body.getMaxOccurrences() != null) {
            if (body.getMaxOccurrences() < 1) {
                return badRequest("maxOccurrences must be >= 1");
            }
            existing.setMaxOccurrences(body.getMaxOccurrences());
        }
        if (body.getTimezone() != null && !body.getTimezone().isBlank()) {
            existing.setTimezone(body.getTimezone());
        }

        // Cross-field consistency: endDate, if present, must be >= startDate.
        if (existing.getEndDate() != null
                && existing.getEndDate().isBefore(existing.getStartDate())) {
            return badRequest("endDate must be on or after startDate");
        }

        RecurrencePattern saved = recurrencePatternRepository.save(existing);

        auditLogService.log(
            entityId,
            user.getId(),
            user.getUsername(),
            "RECURRENCE_PATTERN_UPDATE",
            "recurrence_patterns",
            saved.getId().toString(),
            null
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", saved);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/recurrence-patterns/{id}?entityId=X
     *
     * Hard delete. NOTE: the database FK from transactions.recurrence_pattern_id
     * is ON DELETE SET NULL -- so deleting a pattern detaches it from any
     * "mother" recurring transactions but leaves the rows themselves intact.
     *
     * ADMIN + USER only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> deletePattern(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {

        User user = userAccessService.getCurrentUser(authHeader);
        if (user == null) {
            return unauthorized();
        }
        userAccessService.assertCanAccessEntity(user, entityId);

        Optional<RecurrencePattern> existingOpt = recurrencePatternRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return notFound("Recurrence pattern not found");
        }

        recurrencePatternRepository.deleteById(id);

        auditLogService.log(
            entityId,
            user.getId(),
            user.getUsername(),
            "RECURRENCE_PATTERN_DELETE",
            "recurrence_patterns",
            id.toString(),
            "Hard delete; FK ON DELETE SET NULL detaches referencing transactions"
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("deleted", id);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /**
     * Validate a brand-new pattern payload. Returns null on success, or an
     * error ResponseEntity that the caller should return as-is.
     */
    private ResponseEntity<?> validatePayload(RecurrencePattern body) {
        if (body.getFrequency() == null || body.getFrequency().isBlank()) {
            return badRequest("frequency is required");
        }
        String freq = body.getFrequency().toUpperCase();
        if (!ALLOWED_FREQUENCIES.contains(freq)) {
            return badRequest("Invalid frequency: " + body.getFrequency());
        }
        body.setFrequency(freq); // normalize to upper-case

        if (body.getStartDate() == null) {
            return badRequest("startDate is required");
        }

        if (body.getIntervalCount() != null && body.getIntervalCount() < 1) {
            return badRequest("intervalCount must be >= 1");
        }
        if (body.getDayOfMonth() != null
                && (body.getDayOfMonth() < 1 || body.getDayOfMonth() > 31)) {
            return badRequest("dayOfMonth must be between 1 and 31");
        }
        if (body.getDayOfWeek() != null
                && (body.getDayOfWeek() < 1 || body.getDayOfWeek() > 7)) {
            return badRequest("dayOfWeek must be between 1 and 7");
        }
        if (body.getMaxOccurrences() != null && body.getMaxOccurrences() < 1) {
            return badRequest("maxOccurrences must be >= 1");
        }
        // Cross-field: endDate >= startDate when both present.
        LocalDate end = body.getEndDate();
        if (end != null && end.isBefore(body.getStartDate())) {
            return badRequest("endDate must be on or after startDate");
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("error", "Unauthorized");
        return ResponseEntity.status(401).body(err);
    }

    private ResponseEntity<Map<String, Object>> notFound(String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("error", message);
        return ResponseEntity.status(404).body(err);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("error", message);
        return ResponseEntity.status(400).body(err);
    }
}
