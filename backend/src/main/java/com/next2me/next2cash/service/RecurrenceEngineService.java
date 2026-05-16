package com.next2me.next2cash.service;

import com.next2me.next2cash.model.RecurrencePattern;
import com.next2me.next2cash.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * RecurrenceEngineService.
 *
 * Created in Phase 1 (Session 3, May 2026) for the Cash Planning Module.
 *
 * Pure pull-based engine: generates future occurrence DATES on demand from a
 * RecurrencePattern. No cron jobs, no auto-persistence. The 90-day forecast
 * view (and any future report) calls this service to materialize virtual
 * child instances. Real child rows are persisted ONLY when the user clicks
 * "Convert" via TransactionController#convertPlannedToActual.
 *
 * Frequencies supported:
 *   - DAILY      : every interval_count days
 *   - WEEKLY     : every interval_count weeks, optionally on day_of_week (1=Mon..7=Sun)
 *   - MONTHLY    : every interval_count months, optionally on day_of_month (1-31)
 *                  with end-of-month clamping (31 -> 28/29/30 as appropriate)
 *   - QUARTERLY  : every interval_count*3 months
 *   - YEARLY     : every interval_count years (leap-year-aware for Feb 29)
 *   - CUSTOM     : NOT IMPLEMENTED in Phase 1 -- returns empty list with WARN log
 *
 * Stop conditions (any of the following ends iteration):
 *   - Pattern's end_date reached (exclusive: end_date itself IS included if it lands on an occurrence)
 *   - Pattern's max_occurrences reached
 *   - Caller's horizon (asOf + horizonDays) reached
 *   - Safety cap of 1000 iterations (protects against bad patterns)
 *
 * Timezone:
 *   Pattern.timezone defaults to Europe/Athens. All date arithmetic is performed
 *   in that zone; this matters at DST boundaries (Mar/Oct in Athens) where a
 *   "day" can be 23 or 25 hours long. We work with LocalDate (not Instant) so
 *   DST is mostly a labeling concern, but we still resolve the zone for any
 *   "today" comparisons.
 */
@Service
@RequiredArgsConstructor
public class RecurrenceEngineService {

    private static final Logger log = LoggerFactory.getLogger(RecurrenceEngineService.class);

    /**
     * Safety cap. No legitimate pattern should generate more than 1000 instances
     * for any reasonable horizon (DAILY for ~3 years = ~1095, but if anyone wants
     * that they should chunk the horizon). Protects against degenerate inputs.
     */
    private static final int MAX_ITERATIONS = 1000;

    /**
     * Generate a list of future occurrence dates for the given pattern, starting
     * from `asOf` (inclusive) and ending at `asOf + horizonDays` (inclusive).
     *
     * Returns dates only; the caller is responsible for materializing them into
     * virtual Transaction objects (see #materializeForecastInstances).
     *
     * @param pattern      the recurrence pattern (must not be null)
     * @param asOf         the start of the forecast window (inclusive)
     * @param horizonDays  how many days into the future to look (must be >= 0)
     * @return ordered list of occurrence dates within the window; never null
     */
    public List<LocalDate> generateFutureInstances(
            RecurrencePattern pattern,
            LocalDate asOf,
            int horizonDays) {

        if (pattern == null) {
            return List.of();
        }
        if (asOf == null) {
            throw new IllegalArgumentException("asOf cannot be null");
        }
        if (horizonDays < 0) {
            throw new IllegalArgumentException("horizonDays must be >= 0");
        }

        LocalDate windowEnd = asOf.plusDays(horizonDays);
        String freq = pattern.getFrequency();
        if (freq == null) {
            log.warn("Pattern {} has null frequency; returning empty list", pattern.getId());
            return List.of();
        }

        if ("CUSTOM".equalsIgnoreCase(freq)) {
            log.warn("Pattern {} uses CUSTOM frequency; not implemented in Phase 1", pattern.getId());
            return List.of();
        }

        // Start iterating from the pattern's start_date. We do NOT skip ahead to
        // `asOf` mathematically because for MONTHLY/YEARLY the math gets fragile
        // around end-of-month and leap years. Iterating from start_date with the
        // MAX_ITERATIONS cap is safer and still fast for any realistic horizon.
        LocalDate cursor = pattern.getStartDate();
        if (cursor == null) {
            log.warn("Pattern {} has null start_date; returning empty list", pattern.getId());
            return List.of();
        }

        int interval = pattern.getIntervalCount() == null ? 1 : pattern.getIntervalCount();
        if (interval < 1) {
            log.warn("Pattern {} has invalid interval_count={}; clamping to 1",
                pattern.getId(), interval);
            interval = 1;
        }

        Integer maxOccurrences = pattern.getMaxOccurrences();
        LocalDate endDate = pattern.getEndDate();

        List<LocalDate> result = new ArrayList<>();
        int occurrenceIndex = 0;
        int iterations = 0;

        while (iterations < MAX_ITERATIONS) {
            iterations++;

            // Stop if we passed the pattern's end_date.
            if (endDate != null && cursor.isAfter(endDate)) {
                break;
            }
            // Stop if we passed the caller's horizon.
            if (cursor.isAfter(windowEnd)) {
                break;
            }
            // Stop if we hit max_occurrences.
            if (maxOccurrences != null && occurrenceIndex >= maxOccurrences) {
                break;
            }

            // Only include dates that fall within [asOf, windowEnd].
            // (Dates before asOf are valid occurrences but outside the requested window.)
            if (!cursor.isBefore(asOf)) {
                result.add(cursor);
            }

            occurrenceIndex++;
            cursor = advance(cursor, freq, interval, pattern);
            if (cursor == null) {
                // Defensive: advance returned null (unsupported frequency caught earlier).
                break;
            }
        }

        if (iterations >= MAX_ITERATIONS) {
            log.warn("Pattern {} hit MAX_ITERATIONS cap ({}); list may be incomplete",
                pattern.getId(), MAX_ITERATIONS);
        }

        return result;
    }

    /**
     * Return the single next occurrence date strictly after `after`, or null if
     * the pattern has no more occurrences (passed endDate / maxOccurrences).
     *
     * Useful for "next execution date" badges in the Recurring Manager view.
     */
    public LocalDate nextOccurrence(RecurrencePattern pattern, LocalDate after) {
        if (pattern == null || after == null) {
            return null;
        }
        // Use a generous horizon -- 5 years is enough for any practical "next" query.
        // The +1 is because we want strictly after `after`.
        List<LocalDate> future = generateFutureInstances(pattern, after.plusDays(1), 365 * 5);
        return future.isEmpty() ? null : future.get(0);
    }

    /**
     * Materialize virtual child Transaction objects for forecast display.
     *
     * Given a "mother" transaction (entry_mode=PLANNED, is_recurring=true) and
     * its pattern, produce a list of in-memory Transactions representing each
     * future occurrence within the horizon. These are NOT persisted.
     *
     * Each virtual instance:
     *   - Inherits all business fields from the mother (amount, type, etc.)
     *   - Gets its own docDate (the occurrence date)
     *   - Sets parentRecurringId = mother.id
     *   - Sets isRecurring = false (children are not themselves mothers)
     *   - Has id=null (not persisted) and entityNumber=null
     *
     * @param mother       the "mother" planned recurring transaction
     * @param pattern      the recurrence pattern (mother.recurrencePatternId resolved by caller)
     * @param asOf         start of forecast window
     * @param horizonDays  forecast horizon in days
     * @return list of virtual Transaction objects, ordered by docDate ASC
     */
    public List<Transaction> materializeForecastInstances(
            Transaction mother,
            RecurrencePattern pattern,
            LocalDate asOf,
            int horizonDays) {

        if (mother == null || pattern == null) {
            return List.of();
        }

        List<LocalDate> dates = generateFutureInstances(pattern, asOf, horizonDays);
        List<Transaction> virtuals = new ArrayList<>(dates.size());

        for (LocalDate d : dates) {
            // Skip the mother's own startDate occurrence if it equals the mother's
            // docDate -- the mother itself already represents that instance.
            if (mother.getDocDate() != null && d.equals(mother.getDocDate())) {
                continue;
            }

            Transaction child = new Transaction();
            // Copy business fields
            child.setEntityId(mother.getEntityId());
            child.setType(mother.getType());
            child.setCounterparty(mother.getCounterparty());
            child.setAccount(mother.getAccount());
            child.setCategory(mother.getCategory());
            child.setSubcategory(mother.getSubcategory());
            child.setDescription(mother.getDescription());
            child.setAmount(mother.getAmount() == null ? BigDecimal.ZERO : mother.getAmount());
            child.setAmountPaid(BigDecimal.ZERO);
            child.setAmountRemaining(child.getAmount());
            child.setPaymentMethod(mother.getPaymentMethod());
            child.setPaymentStatus("unpaid"); // PLANNED instances are not yet paid
            child.setDocStatus(mother.getDocStatus());
            // docDate = the occurrence date
            child.setDocDate(d);
            // Accounting period mirrors docDate
            child.setAccountingPeriod(
                d.getYear() + "-" + String.format("%02d", d.getMonthValue()));
            // Phase 1 linkage
            child.setEntryMode("PLANNED");
            child.setIsRecurring(false);
            child.setParentRecurringId(mother.getId());
            child.setRecurrencePatternId(mother.getRecurrencePatternId());
            child.setProjectId(mother.getProjectId());
            child.setScenarioId(mother.getScenarioId());
            child.setConfidencePct(mother.getConfidencePct() == null ? 100 : mother.getConfidencePct());
            // System fields left null -- this is a virtual, not-persisted row.
            child.setRecordStatus("active");

            virtuals.add(child);
        }
        return virtuals;
    }

    // -------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------

    /**
     * Move the cursor forward by one interval according to frequency rules.
     * Handles end-of-month and leap-year clamping for MONTHLY/QUARTERLY/YEARLY.
     */
    private LocalDate advance(LocalDate cursor, String freq, int interval, RecurrencePattern pattern) {
        switch (freq.toUpperCase()) {
            case "DAILY":
                return cursor.plusDays(interval);

            case "WEEKLY":
                // If pattern specifies dayOfWeek, align to it on first advance.
                // Otherwise just add interval weeks.
                return advanceWeekly(cursor, interval, pattern.getDayOfWeek());

            case "MONTHLY":
                return advanceMonthly(cursor, interval, pattern.getDayOfMonth());

            case "QUARTERLY":
                // Quarterly = every 3 months. interval_count multiplies that.
                return advanceMonthly(cursor, interval * 3, pattern.getDayOfMonth());

            case "YEARLY":
                return advanceYearly(cursor, interval, pattern.getDayOfMonth());

            case "CUSTOM":
                // Already filtered out at the top of generateFutureInstances.
                return null;

            default:
                log.warn("Unknown frequency '{}' in pattern {}; stopping iteration",
                    freq, pattern.getId());
                return null;
        }
    }

    /**
     * WEEKLY advance. If dayOfWeek is null, simply add interval weeks (keeps
     * the cursor on the same weekday as startDate). If dayOfWeek is set and
     * cursor is not already on that weekday, snap forward first then advance.
     */
    private LocalDate advanceWeekly(LocalDate cursor, int interval, Integer dayOfWeek) {
        if (dayOfWeek == null) {
            return cursor.plusWeeks(interval);
        }
        // Defensive bounds
        int dow = Math.min(7, Math.max(1, dayOfWeek));
        DayOfWeek target = DayOfWeek.of(dow);
        // Add interval weeks first, then snap to the target weekday (if not already there).
        LocalDate next = cursor.plusWeeks(interval);
        if (next.getDayOfWeek() != target) {
            next = next.with(TemporalAdjusters.nextOrSame(target));
        }
        return next;
    }

    /**
     * MONTHLY (and QUARTERLY via months*3) advance with end-of-month clamping.
     *
     * Example: cursor=2026-01-31, monthsToAdd=1, dayOfMonth=31
     *   -> 2026-02-28 (Feb has no 31st; clamp to last day)
     *
     * Example: cursor=2026-01-15, monthsToAdd=2, dayOfMonth=null
     *   -> 2026-03-15 (just preserve cursor's day)
     */
    private LocalDate advanceMonthly(LocalDate cursor, int monthsToAdd, Integer dayOfMonth) {
        // Step 1: shift by months. LocalDate.plusMonths already clamps invalid
        // days down (e.g. Jan 31 + 1 month = Feb 28/29) which is exactly what we want
        // when dayOfMonth is unspecified.
        LocalDate shifted = cursor.plusMonths(monthsToAdd);

        if (dayOfMonth == null) {
            return shifted;
        }

        // Step 2: snap to the requested dayOfMonth, but clamp to the new month's
        // last day if dayOfMonth exceeds it (e.g. dayOfMonth=31 in February).
        int dom = Math.min(31, Math.max(1, dayOfMonth));
        int lastDayOfMonth = shifted.lengthOfMonth();
        int effectiveDom = Math.min(dom, lastDayOfMonth);
        return shifted.withDayOfMonth(effectiveDom);
    }

    /**
     * YEARLY advance. Identical to monthly but with year stepping. Leap-year
     * handling is implicit: LocalDate.plusYears(1) on Feb 29 yields Feb 28 in
     * non-leap years (Java's default behavior, which is exactly what we want).
     */
    private LocalDate advanceYearly(LocalDate cursor, int interval, Integer dayOfMonth) {
        LocalDate shifted = cursor.plusYears(interval);
        if (dayOfMonth == null) {
            return shifted;
        }
        int dom = Math.min(31, Math.max(1, dayOfMonth));
        int lastDayOfMonth = shifted.lengthOfMonth();
        int effectiveDom = Math.min(dom, lastDayOfMonth);
        return shifted.withDayOfMonth(effectiveDom);
    }

    /**
     * Convenience: resolve "today" in the pattern's timezone. Used by callers
     * that need an asOf reference but want to honour Europe/Athens semantics.
     */
    public LocalDate todayInPatternZone(RecurrencePattern pattern) {
        String tz = (pattern != null && pattern.getTimezone() != null && !pattern.getTimezone().isBlank())
            ? pattern.getTimezone()
            : "Europe/Athens";
        try {
            return LocalDate.now(ZoneId.of(tz));
        } catch (Exception ex) {
            log.warn("Invalid timezone '{}' in pattern; falling back to Europe/Athens", tz);
            return LocalDate.now(ZoneId.of("Europe/Athens"));
        }
    }
}
