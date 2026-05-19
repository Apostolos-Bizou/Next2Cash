package com.next2me.next2cash.controller;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized exception handler for the REST API.
 *
 * <p><strong>Scope (S80):</strong> Catches input-validation and data-integrity
 * exceptions that would otherwise bubble up to Spring's default error handler
 * and produce a confusing HTTP 403 with empty body. Returns HTTP 400 with the
 * existing {@code {"success": false, "error": "..."}} shape that the frontend
 * already understands.
 *
 * <p><strong>Out of scope (deliberately not caught):</strong>
 * <ul>
 *   <li>{@code AuthenticationException} / {@code AccessDeniedException} - left
 *       to Spring Security so existing 401/403 behavior is preserved.</li>
 *   <li>Generic {@code Exception} / {@code RuntimeException} - left uncaught so
 *       unexpected bugs surface in Application Insights / spring.log.</li>
 * </ul>
 *
 * <p>Response shape (consistent with AuthController, ConfigController, etc.):
 * <pre>
 *   { "success": false, "error": "human-readable message" }
 * </pre>
 *
 * Session: S80 (May 2026)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * DB CHECK / UNIQUE / FK constraint violations.
     *
     * <p>Typical trigger: {@code projectRepository.save()} with a status value
     * outside {@code ('PLANNING','IN_DEVELOPMENT','TESTING','LIVE','PAUSED','CANCELLED')}.
     * Before S80, this produced 403 with empty body (Spring default).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String rootMsg = rootCauseMessage(ex);
        String lower = rootMsg == null ? "" : rootMsg.toLowerCase();

        String userMessage;
        HttpStatus status;

        if (lower.contains("unique") || lower.contains("duplicate")) {
            userMessage = "Duplicate value violates a unique constraint";
            status = HttpStatus.CONFLICT; // 409
        } else if (lower.contains("foreign key") || lower.contains("violates foreign key")) {
            userMessage = "Referenced record does not exist";
            status = HttpStatus.BAD_REQUEST; // 400
        } else if (lower.contains("check constraint") || lower.contains("check_violation") || lower.contains("violates check")) {
            userMessage = "One or more field values are not allowed: " + extractCheckHint(rootMsg);
            status = HttpStatus.BAD_REQUEST; // 400
        } else if (lower.contains("not-null") || lower.contains("null value")) {
            userMessage = "A required field is missing";
            status = HttpStatus.BAD_REQUEST; // 400
        } else {
            userMessage = "Data integrity violation";
            status = HttpStatus.BAD_REQUEST; // 400
        }

        log.warn("DataIntegrityViolation -> HTTP {}: {}", status.value(), rootMsg);
        return ResponseEntity.status(status).body(errorBody(userMessage));
    }

    /**
     * Malformed JSON or Jackson cannot deserialize a value.
     *
     * <p>Includes {@link InvalidFormatException} which is thrown when a
     * future enum field receives an invalid value (e.g. {@code entry_mode = "FOO"}).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String userMessage;

        if (cause instanceof InvalidFormatException ife) {
            String field = extractFieldFromPath(ife);
            String value = String.valueOf(ife.getValue());
            String targetType = ife.getTargetType() != null ? ife.getTargetType().getSimpleName() : "value";
            userMessage = "Invalid value '" + value + "' for field '" + field + "' (expected " + targetType + ")";
        } else {
            userMessage = "Request body is malformed or has an invalid value";
        }

        log.warn("HttpMessageNotReadable: {}", cause != null ? cause.getMessage() : ex.getMessage());
        return ResponseEntity.badRequest().body(errorBody(userMessage));
    }

    /**
     * Invalid path/query parameter type (e.g. malformed UUID in {@code /api/projects/{id}}).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String userMessage = "Invalid value for parameter '" + ex.getName()
            + "'" + (ex.getRequiredType() != null ? " (expected " + ex.getRequiredType().getSimpleName() + ")" : "");
        log.warn("MethodArgumentTypeMismatch: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(errorBody(userMessage));
    }

    /**
     * Bean Validation (@Valid) failures - future-proof for when DTOs add @NotNull / @Pattern / etc.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String userMessage = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> "Field '" + fe.getField() + "': " + fe.getDefaultMessage())
            .orElse("Validation failed");
        log.warn("MethodArgumentNotValid: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(errorBody(userMessage));
    }

    /**
     * Plain {@link IllegalArgumentException} thrown by controllers/services for
     * proactive input validation (e.g. {@code ProjectController} status whitelist check).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Invalid argument";
        log.warn("IllegalArgument: {}", msg);
        return ResponseEntity.badRequest().body(errorBody(msg));
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", message);
        return body;
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cursor = t;
        Throwable last = t;
        int safety = 0;
        while (cursor != null && safety < 20) {
            last = cursor;
            cursor = cursor.getCause();
            safety++;
        }
        return last != null ? last.getMessage() : null;
    }

    /**
     * Extract a short hint from a Postgres check-violation message.
     * Postgres typically reports: "new row for relation \"projects\" violates check constraint \"projects_status_check\"".
     * H2 in PostgreSQL mode produces similar text. We just return the constraint name if recognizable.
     */
    private static String extractCheckHint(String msg) {
        if (msg == null) return "unknown constraint";
        int idx = msg.toLowerCase().indexOf("constraint");
        if (idx < 0) return "check constraint";
        String after = msg.substring(idx);
        // grab first quoted token
        int q1 = after.indexOf('"');
        int q2 = q1 >= 0 ? after.indexOf('"', q1 + 1) : -1;
        if (q1 >= 0 && q2 > q1) {
            return after.substring(q1 + 1, q2);
        }
        return "check constraint";
    }

    private static String extractFieldFromPath(InvalidFormatException ife) {
        if (ife.getPath() == null || ife.getPath().isEmpty()) return "(unknown)";
        return ife.getPath().stream()
            .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[?]")
            .reduce((a, b) -> a + "." + b)
            .orElse("(unknown)");
    }
}
