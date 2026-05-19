package com.next2me.next2cash.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Project status constants and validation helpers.
 *
 * <p>Single source of truth for valid project status values, mirroring the
 * PostgreSQL CHECK constraint defined in
 * {@code V2026_05_18_001__projects_foundation.sql} (line 18):
 * <pre>
 *   CHECK (status IN ('PLANNING','IN_DEVELOPMENT','TESTING','LIVE','PAUSED','CANCELLED'))
 * </pre>
 *
 * <p>Used by {@code ProjectController} for proactive whitelist validation so that
 * invalid values are rejected with a clear HTTP 400 BEFORE reaching the DB
 * (which would otherwise produce a generic 403 with empty body).
 *
 * <p>Note: kept as a constants holder rather than a Java enum because the
 * Project entity stores status as a String column (length 30), to remain
 * forward-compatible with future statuses added via DB migration without
 * requiring a backend redeploy.
 *
 * Spec ref: CashPlanning TechSpec v1.0 section 4.4
 * Session: S80 (May 2026)
 */
public final class ProjectStatus {

    public static final String PLANNING        = "PLANNING";
    public static final String IN_DEVELOPMENT  = "IN_DEVELOPMENT";
    public static final String TESTING         = "TESTING";
    public static final String LIVE            = "LIVE";
    public static final String PAUSED          = "PAUSED";
    public static final String CANCELLED       = "CANCELLED";

    /**
     * Immutable set of all valid status values, in canonical (TechSpec) order.
     * Order is preserved so that error messages list values predictably.
     */
    public static final Set<String> VALID_VALUES;

    static {
        Set<String> values = new LinkedHashSet<>();
        values.add(PLANNING);
        values.add(IN_DEVELOPMENT);
        values.add(TESTING);
        values.add(LIVE);
        values.add(PAUSED);
        values.add(CANCELLED);
        VALID_VALUES = Collections.unmodifiableSet(values);
    }

    /**
     * Returns true if the supplied status (case-sensitive, uppercase expected)
     * is one of the canonical valid values.
     *
     * @param status the status string to validate; may be null
     * @return true iff status is non-null and matches a valid value
     */
    public static boolean isValid(String status) {
        return status != null && VALID_VALUES.contains(status);
    }

    /**
     * Returns a comma-separated, ordered list of valid values, suitable for
     * inclusion in error messages.
     *
     * @return e.g. "PLANNING, IN_DEVELOPMENT, TESTING, LIVE, PAUSED, CANCELLED"
     */
    public static String validValuesAsString() {
        return String.join(", ", VALID_VALUES);
    }

    // Prevent instantiation
    private ProjectStatus() {
        throw new AssertionError("ProjectStatus is a constants holder; do not instantiate");
    }
}
