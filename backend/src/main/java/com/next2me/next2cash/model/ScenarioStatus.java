package com.next2me.next2cash.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Forecast scenario type constants and validation helpers.
 *
 * <p>Single source of truth for valid scenario_type values, mirroring the
 * PostgreSQL CHECK constraint defined in
 * {@code V2026_05_28_001__scenarios_persistence.sql}:
 * <pre>
 *   CHECK (scenario_type IN ('BASELINE','OPTIMISTIC','PESSIMISTIC','CUSTOM'))
 * </pre>
 *
 * <p>Used by {@code ScenarioController} for proactive whitelist validation so
 * that invalid values are rejected with a clear HTTP 400 BEFORE reaching the DB
 * (which would otherwise produce a generic 403 with empty body).
 *
 * <p>Note: kept as a constants holder rather than a Java enum because the
 * Scenario entity stores scenario_type as a String column, to remain
 * forward-compatible with future types added via DB migration without
 * requiring a backend redeploy. Same pattern as {@link ProjectStatus}.
 *
 * Spec ref: CashPlanning TechSpec v1.1 sections 3 (Principle 3) and 5.8.
 * Session: S97
 */
public final class ScenarioStatus {

    public static final String BASELINE     = "BASELINE";
    public static final String OPTIMISTIC   = "OPTIMISTIC";
    public static final String PESSIMISTIC  = "PESSIMISTIC";
    public static final String CUSTOM       = "CUSTOM";

    /**
     * Immutable set of all valid scenario types, in canonical (TechSpec) order.
     */
    public static final Set<String> VALID_VALUES;

    static {
        Set<String> values = new LinkedHashSet<>();
        values.add(BASELINE);
        values.add(OPTIMISTIC);
        values.add(PESSIMISTIC);
        values.add(CUSTOM);
        VALID_VALUES = Collections.unmodifiableSet(values);
    }

    /**
     * Returns true if the supplied scenario type (case-sensitive, uppercase
     * expected) is one of the canonical valid values.
     *
     * @param type the scenario type to validate; may be null
     * @return true iff type is non-null and matches a valid value
     */
    public static boolean isValid(String type) {
        return type != null && VALID_VALUES.contains(type);
    }

    /**
     * Returns a comma-separated, ordered list of valid values, suitable for
     * inclusion in error messages.
     *
     * @return "BASELINE, OPTIMISTIC, PESSIMISTIC, CUSTOM"
     */
    public static String validValuesAsString() {
        return String.join(", ", VALID_VALUES);
    }

    // Prevent instantiation
    private ScenarioStatus() {
        throw new AssertionError("ScenarioStatus is a constants holder; do not instantiate");
    }
}
