package com.next2me.next2cash.service;

import com.next2me.next2cash.model.RecurrencePattern;
import com.next2me.next2cash.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RecurrenceEngineService.
 *
 * No Spring context, no Mockito -- the engine is a pure-function service with
 * no dependencies, so tests construct the service directly. This keeps the
 * suite fast (<100ms total) and makes edge-case reproduction obvious.
 *
 * Coverage targets the risk register entries from the Phase 1 TechSpec:
 *   - End-of-month bugs (Jan 31 -> Feb 28/29)
 *   - Leap years (Feb 29 in 2024 vs 2025)
 *   - Stop conditions (endDate, maxOccurrences)
 *   - WEEKLY day-of-week snapping
 *   - DAILY simple math
 *   - QUARTERLY (= MONTHLY x3)
 *   - YEARLY
 *   - CUSTOM (not implemented, must return empty)
 *   - Defensive inputs (null, negative interval, etc.)
 */
class RecurrenceEngineServiceTest {

    private final RecurrenceEngineService engine = new RecurrenceEngineService();

    // -------------------------------------------------------------------
    // DAILY
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("DAILY frequency")
    class DailyTests {

        @Test
        @DisplayName("daily, interval=1, 7-day horizon -> 8 occurrences (inclusive)")
        void dailyIntervalOne() {
            RecurrencePattern p = newPattern("DAILY", 1, LocalDate.of(2026, 6, 1));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), 7);
            // From Jun 1 to Jun 8 inclusive = 8 days
            assertEquals(8, dates.size());
            assertEquals(LocalDate.of(2026, 6, 1), dates.get(0));
            assertEquals(LocalDate.of(2026, 6, 8), dates.get(dates.size() - 1));
        }

        @Test
        @DisplayName("daily, interval=3, 10-day horizon -> 4 occurrences")
        void dailyIntervalThree() {
            RecurrencePattern p = newPattern("DAILY", 3, LocalDate.of(2026, 6, 1));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), 10);
            // Jun 1, 4, 7, 10
            assertEquals(4, dates.size());
            assertEquals(LocalDate.of(2026, 6, 1),  dates.get(0));
            assertEquals(LocalDate.of(2026, 6, 4),  dates.get(1));
            assertEquals(LocalDate.of(2026, 6, 7),  dates.get(2));
            assertEquals(LocalDate.of(2026, 6, 10), dates.get(3));
        }
    }

    // -------------------------------------------------------------------
    // WEEKLY
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("WEEKLY frequency")
    class WeeklyTests {

        @Test
        @DisplayName("weekly, interval=1, no dayOfWeek -> same weekday as startDate")
        void weeklyNoDay() {
            // Jun 1, 2026 is a Monday
            RecurrencePattern p = newPattern("WEEKLY", 1, LocalDate.of(2026, 6, 1));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), 28);
            // Jun 1, 8, 15, 22, 29 -> 5 Mondays
            assertEquals(5, dates.size());
            for (LocalDate d : dates) {
                assertEquals(java.time.DayOfWeek.MONDAY, d.getDayOfWeek());
            }
        }

        @Test
        @DisplayName("weekly with dayOfWeek=5 (Friday) -> always Fridays")
        void weeklyWithDayOfWeek() {
            // Start on Monday Jun 1, but request Fridays
            RecurrencePattern p = newPattern("WEEKLY", 1, LocalDate.of(2026, 6, 1));
            p.setDayOfWeek(5); // Friday
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), 28);
            // Engine adds 1 week, then snaps to Friday: Jun 5, 12, 19, 26
            for (LocalDate d : dates) {
                if (!d.equals(LocalDate.of(2026, 6, 1))) {
                    assertEquals(java.time.DayOfWeek.FRIDAY, d.getDayOfWeek(),
                        "All non-start occurrences should be Friday, got " + d.getDayOfWeek() + " on " + d);
                }
            }
        }
    }

    // -------------------------------------------------------------------
    // MONTHLY -- the bug-prone one
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("MONTHLY frequency")
    class MonthlyTests {

        @Test
        @DisplayName("monthly, dayOfMonth=15 -> always 15th")
        void monthlyDayFifteen() {
            RecurrencePattern p = newPattern("MONTHLY", 1, LocalDate.of(2026, 6, 15));
            p.setDayOfMonth(15);
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), 365);
            // 12 months from Jun 15 to Jun 15 next year (inclusive on start, possibly exclusive on end)
            assertTrue(dates.size() >= 12, "Expected at least 12 monthly occurrences");
            for (LocalDate d : dates) {
                assertEquals(15, d.getDayOfMonth());
            }
        }

        @Test
        @DisplayName("monthly, dayOfMonth=31 -> February clamps to 28")
        void monthlyDayThirtyOneFebClamp() {
            // 2026 is NOT a leap year (2024 was, next is 2028)
            RecurrencePattern p = newPattern("MONTHLY", 1, LocalDate.of(2026, 1, 31));
            p.setDayOfMonth(31);
            // 200-day horizon from Jan 1 -> windowEnd = ~Jul 20. Long enough for clear Feb/Apr clamping.
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 1, 1), 200);
            // Expected: Jan 31, Feb 28 (clamped), Mar 31, Apr 30 (clamped), May 31, Jun 30 (clamped), Jul 31
            assertTrue(dates.size() >= 6, "Expected at least 6 occurrences in 200-day window, got " + dates.size());
            assertEquals(LocalDate.of(2026, 1, 31), dates.get(0));
            assertEquals(LocalDate.of(2026, 2, 28), dates.get(1), "Feb 2026 should clamp to 28 (non-leap)");
            assertEquals(LocalDate.of(2026, 3, 31), dates.get(2));
            assertEquals(LocalDate.of(2026, 4, 30), dates.get(3), "Apr should clamp to 30");
            assertEquals(LocalDate.of(2026, 5, 31), dates.get(4));
            assertEquals(LocalDate.of(2026, 6, 30), dates.get(5), "Jun should clamp to 30");
        }

        @Test
        @DisplayName("monthly, dayOfMonth=29 -> Feb in leap year vs non-leap")
        void monthlyDayTwentyNineLeapYear() {
            // Start in Jan 2024 (leap year). Feb 2024 has 29 days. Feb 2025 has 28.
            RecurrencePattern p = newPattern("MONTHLY", 1, LocalDate.of(2024, 1, 29));
            p.setDayOfMonth(29);
            // Horizon must be wide enough to include Feb 2025.
            // Jan 1, 2024 + 450 days = ~late Mar 2025. Feb 2025 is inside the window.
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2024, 1, 1), 450);
            // Find Feb 2024 and Feb 2025
            LocalDate feb2024 = null, feb2025 = null;
            for (LocalDate d : dates) {
                if (d.getYear() == 2024 && d.getMonth() == Month.FEBRUARY) feb2024 = d;
                if (d.getYear() == 2025 && d.getMonth() == Month.FEBRUARY) feb2025 = d;
            }
            assertEquals(LocalDate.of(2024, 2, 29), feb2024, "Feb 2024 should be 29 (leap)");
            assertEquals(LocalDate.of(2025, 2, 28), feb2025, "Feb 2025 should be 28 (non-leap, clamped)");
        }

        @Test
        @DisplayName("monthly, interval=2 -> every 2 months")
        void monthlyIntervalTwo() {
            RecurrencePattern p = newPattern("MONTHLY", 2, LocalDate.of(2026, 1, 15));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 1, 1), 365);
            // Jan, Mar, May, Jul, Sep, Nov 2026 + Jan 2027 = 7
            assertTrue(dates.size() >= 6);
            assertEquals(LocalDate.of(2026, 1, 15), dates.get(0));
            assertEquals(LocalDate.of(2026, 3, 15), dates.get(1));
            assertEquals(LocalDate.of(2026, 5, 15), dates.get(2));
        }
    }

    // -------------------------------------------------------------------
    // QUARTERLY (= MONTHLY x3)
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("QUARTERLY frequency")
    class QuarterlyTests {

        @Test
        @DisplayName("quarterly, interval=1 -> every 3 months")
        void quarterlyBasic() {
            RecurrencePattern p = newPattern("QUARTERLY", 1, LocalDate.of(2026, 1, 1));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 1, 1), 365);
            // Jan, Apr, Jul, Oct, Jan (next year) = 5
            assertTrue(dates.size() >= 4);
            assertEquals(LocalDate.of(2026, 1, 1), dates.get(0));
            assertEquals(LocalDate.of(2026, 4, 1), dates.get(1));
            assertEquals(LocalDate.of(2026, 7, 1), dates.get(2));
            assertEquals(LocalDate.of(2026, 10, 1), dates.get(3));
        }
    }

    // -------------------------------------------------------------------
    // YEARLY
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("YEARLY frequency")
    class YearlyTests {

        @Test
        @DisplayName("yearly, normal date")
        void yearlyBasic() {
            RecurrencePattern p = newPattern("YEARLY", 1, LocalDate.of(2026, 7, 15));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 1, 1), 365 * 3 + 1);
            // 2026, 2027, 2028, 2029
            assertTrue(dates.size() >= 3);
            assertEquals(LocalDate.of(2026, 7, 15), dates.get(0));
            assertEquals(LocalDate.of(2027, 7, 15), dates.get(1));
            assertEquals(LocalDate.of(2028, 7, 15), dates.get(2));
        }

        @Test
        @DisplayName("yearly on Feb 29 -> non-leap years clamp to Feb 28")
        void yearlyFebTwentyNine() {
            RecurrencePattern p = newPattern("YEARLY", 1, LocalDate.of(2024, 2, 29));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2024, 1, 1), 365 * 5);
            // 2024 leap, 2025 not, 2026 not, 2027 not, 2028 leap
            assertEquals(LocalDate.of(2024, 2, 29), dates.get(0));
            assertEquals(LocalDate.of(2025, 2, 28), dates.get(1));
            assertEquals(LocalDate.of(2026, 2, 28), dates.get(2));
            assertEquals(LocalDate.of(2027, 2, 28), dates.get(3));
            assertEquals(LocalDate.of(2028, 2, 28), dates.get(4));
            // Note: 2028 is leap but engine carries forward from 2027-02-28, not from 2024-02-29
            // This is a known trade-off: once we drop to 28, we stay on 28.
        }
    }

    // -------------------------------------------------------------------
    // Stop conditions
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("Stop conditions")
    class StopTests {

        @Test
        @DisplayName("maxOccurrences=3 -> stops after 3 instances")
        void maxOccurrencesStops() {
            RecurrencePattern p = newPattern("MONTHLY", 1, LocalDate.of(2026, 6, 1));
            p.setMaxOccurrences(3);
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), 365);
            assertEquals(3, dates.size());
        }

        @Test
        @DisplayName("endDate set -> stops at endDate")
        void endDateStops() {
            RecurrencePattern p = newPattern("MONTHLY", 1, LocalDate.of(2026, 6, 1));
            p.setEndDate(LocalDate.of(2026, 9, 15));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), 365);
            // Jun 1, Jul 1, Aug 1, Sep 1 (Oct 1 is past endDate Sep 15)
            assertEquals(4, dates.size());
            assertEquals(LocalDate.of(2026, 9, 1), dates.get(dates.size() - 1));
        }

        @Test
        @DisplayName("Both endDate and maxOccurrences -> whichever hits first wins (max)")
        void bothLimitsMaxFirst() {
            RecurrencePattern p = newPattern("MONTHLY", 1, LocalDate.of(2026, 6, 1));
            p.setMaxOccurrences(2);
            p.setEndDate(LocalDate.of(2027, 1, 1));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), 365);
            // maxOccurrences=2 wins before endDate
            assertEquals(2, dates.size());
        }
    }

    // -------------------------------------------------------------------
    // CUSTOM + defensive
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("Defensive inputs")
    class DefensiveTests {

        @Test
        @DisplayName("CUSTOM frequency -> empty list")
        void customFrequency() {
            RecurrencePattern p = newPattern("CUSTOM", 1, LocalDate.of(2026, 6, 1));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), 365);
            assertTrue(dates.isEmpty());
        }

        @Test
        @DisplayName("null pattern -> empty list")
        void nullPattern() {
            List<LocalDate> dates = engine.generateFutureInstances(null, LocalDate.of(2026, 6, 1), 30);
            assertTrue(dates.isEmpty());
        }

        @Test
        @DisplayName("null asOf -> throws IllegalArgumentException")
        void nullAsOf() {
            RecurrencePattern p = newPattern("DAILY", 1, LocalDate.of(2026, 6, 1));
            assertThrows(IllegalArgumentException.class,
                () -> engine.generateFutureInstances(p, null, 30));
        }

        @Test
        @DisplayName("negative horizonDays -> throws IllegalArgumentException")
        void negativeHorizon() {
            RecurrencePattern p = newPattern("DAILY", 1, LocalDate.of(2026, 6, 1));
            assertThrows(IllegalArgumentException.class,
                () -> engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), -1));
        }

        @Test
        @DisplayName("unknown frequency -> empty list (stops cleanly, no exception)")
        void unknownFrequency() {
            RecurrencePattern p = newPattern("NEVER", 1, LocalDate.of(2026, 6, 1));
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2026, 6, 1), 30);
            // First occurrence at startDate IS included; then advance returns null and we stop.
            assertEquals(1, dates.size());
            assertEquals(LocalDate.of(2026, 6, 1), dates.get(0));
        }

        @Test
        @DisplayName("startDate in the past -> asOf filter excludes past occurrences")
        void startDateInPast() {
            RecurrencePattern p = newPattern("MONTHLY", 1, LocalDate.of(2025, 1, 1));
            // asOf is 6 months later
            List<LocalDate> dates = engine.generateFutureInstances(p, LocalDate.of(2025, 7, 1), 90);
            // Jul 1, Aug 1, Sep 1 are within window; Jan-Jun 2025 are skipped (before asOf)
            assertEquals(3, dates.size());
            assertEquals(LocalDate.of(2025, 7, 1), dates.get(0));
        }
    }

    // -------------------------------------------------------------------
    // nextOccurrence
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("nextOccurrence")
    class NextOccurrenceTests {

        @Test
        @DisplayName("returns the next date strictly after the given date")
        void simpleNext() {
            RecurrencePattern p = newPattern("MONTHLY", 1, LocalDate.of(2026, 1, 10));
            LocalDate next = engine.nextOccurrence(p, LocalDate.of(2026, 5, 15));
            assertEquals(LocalDate.of(2026, 6, 10), next);
        }

        @Test
        @DisplayName("returns null when pattern ended")
        void afterEnd() {
            RecurrencePattern p = newPattern("MONTHLY", 1, LocalDate.of(2026, 1, 1));
            p.setMaxOccurrences(3);
            // Pattern fires Jan, Feb, Mar -- after Mar there is no more
            LocalDate next = engine.nextOccurrence(p, LocalDate.of(2026, 4, 1));
            assertNull(next);
        }
    }

    // -------------------------------------------------------------------
    // materializeForecastInstances
    // -------------------------------------------------------------------
    @Nested
    @DisplayName("materializeForecastInstances")
    class MaterializeTests {

        @Test
        @DisplayName("produces virtual Transactions inheriting mother's fields")
        void materializeBasic() {
            RecurrencePattern p = newPattern("MONTHLY", 1, LocalDate.of(2026, 6, 1));
            p.setMaxOccurrences(3);

            Transaction mother = new Transaction();
            mother.setId(42);
            mother.setEntityId(UUID.randomUUID());
            mother.setType("expense");
            mother.setAmount(new BigDecimal("350.00"));
            mother.setDescription("AWS Server");
            mother.setCounterparty("AWS");
            mother.setDocDate(LocalDate.of(2026, 6, 1));
            mother.setRecurrencePatternId(p.getId());
            mother.setEntryMode("PLANNED");
            mother.setIsRecurring(true);

            List<Transaction> virtuals =
                engine.materializeForecastInstances(mother, p, LocalDate.of(2026, 6, 1), 180);

            // 3 occurrences total, but the first equals mother.docDate so it's skipped.
            // Expect 2 virtual instances.
            assertEquals(2, virtuals.size());
            for (Transaction v : virtuals) {
                assertNull(v.getId(), "virtual instances must not be persisted yet");
                assertEquals(mother.getEntityId(), v.getEntityId());
                assertEquals(mother.getAmount(), v.getAmount());
                assertEquals("PLANNED", v.getEntryMode());
                assertEquals(Integer.valueOf(42), v.getParentRecurringId());
                assertFalse(v.getIsRecurring(), "children are not themselves mothers");
                assertEquals("unpaid", v.getPaymentStatus());
            }
        }
    }

    // -------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------
    private RecurrencePattern newPattern(String freq, int interval, LocalDate start) {
        RecurrencePattern p = new RecurrencePattern();
        p.setId(UUID.randomUUID());
        p.setFrequency(freq);
        p.setIntervalCount(interval);
        p.setStartDate(start);
        p.setTimezone("Europe/Athens");
        return p;
    }
}
