package com.next2me.next2cash.service;

import com.next2me.next2cash.service.PolarisForecastMath.Point;
import com.next2me.next2cash.service.PolarisForecastMath.Raise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure math (no Spring). Every figure here is worked out by hand first and the
 * same cases are asserted in frontend/tests/polarisMath.test.mjs (parity).
 */
class PolarisForecastMathTest {

    private static final double EPS = 1e-6;
    private static final LocalDate BASE = LocalDate.of(2026, 10, 1);   // first of the current month (29 Oct 2026)

    // ─── interpolation (members only): the member-milestone fixture (20k@2027-06, 30k@2028-03) ───

    @Test
    @DisplayName("members: 16,800 today; 20,000 at +8 months; 30,000 at +17 -> 24,444.44 at 12, 30,000 at 24 (flat)")
    void interpolate_memberFixture() {
        List<Point> pts = List.of(
            new Point(PolarisForecastMath.monthsBetween(BASE, LocalDate.of(2027, 6, 1)), 20000),
            new Point(PolarisForecastMath.monthsBetween(BASE, LocalDate.of(2028, 3, 1)), 30000));
        assertEquals(8, pts.get(0).months(), EPS);
        assertEquals(17, pts.get(1).months(), EPS);
        assertEquals(16800.0, PolarisForecastMath.interpolate(16800, 0, pts), EPS);          // before first = base
        assertEquals(16800 + (20000 - 16800) * 4 / 8.0, PolarisForecastMath.interpolate(16800, 4, pts), EPS);
        assertEquals(20000.0, PolarisForecastMath.interpolate(16800, 8, pts), EPS);
        assertEquals(20000 + 10000 * 4 / 9.0, PolarisForecastMath.interpolate(16800, 12, pts), EPS);  // 24,444.44
        assertEquals(30000.0, PolarisForecastMath.interpolate(16800, 17, pts), EPS);
        assertEquals(30000.0, PolarisForecastMath.interpolate(16800, 24, pts), EPS);         // after last = flat
        assertEquals(16800.0, PolarisForecastMath.interpolate(16800, 24, List.of()), EPS);   // no milestones = flat base
        assertEquals(16800.0, PolarisForecastMath.interpolate(16800, 24, List.of(new Point(-3, 2))), EPS);  // past ignored
    }

    // ─── STEP (OPEX): the hand example (Consultants, two targets) under the 30 Oct 2026 step rule ───
    //
    // Hand computation (base month Oct 2026, $0 baseline):
    //   targets: 24,000 @ 2027-06 (+8 months), 36,000 @ 2029-02 (+28 months)
    //   month  0 (Έτος 1, 1 Jan 2026 point): no target reached      ->      0
    //   month  4:                              no target reached      ->      0   (NOT the old ramp 10,776 -> 24,000)
    //   month  8:                              24,000 reached          -> 24,000
    //   month 12 (Έτος 2):                     latest reached = +8     -> 24,000   (old rule: 26,400)
    //   month 24 (Έτος 3):                     latest reached = +8     -> 24,000   (old rule: 33,600)
    //   month 27:                              still +8                -> 24,000
    //   month 28:                              36,000 reached          -> 36,000
    //   month 36 (Έτος 4), 48 (Έτος 5):        flat after the last     -> 36,000

    @Test
    @DisplayName("OPEX step: Consultants 24k@+8, 36k@+28 -> 0 at 0/4, 24,000 at 8/12/24/27, 36,000 at 28/36/48")
    void stepAt_opexHandExample() {
        List<Point> pts = List.of(
            new Point(PolarisForecastMath.monthsBetween(BASE, LocalDate.of(2029, 2, 1)), 36000),   // +28 (out of order on purpose)
            new Point(PolarisForecastMath.monthsBetween(BASE, LocalDate.of(2027, 6, 1)), 24000));  // +8
        assertEquals(28, pts.get(0).months(), EPS);
        assertEquals(8, pts.get(1).months(), EPS);
        assertEquals(0.0,     PolarisForecastMath.stepAt(0, 0, pts), EPS);
        assertEquals(0.0,     PolarisForecastMath.stepAt(0, 4, pts), EPS);
        assertEquals(0.0,     PolarisForecastMath.stepAt(0, 7, pts), EPS);
        assertEquals(24000.0, PolarisForecastMath.stepAt(0, 8, pts), EPS);
        assertEquals(24000.0, PolarisForecastMath.stepAt(0, 12, pts), EPS);
        assertEquals(24000.0, PolarisForecastMath.stepAt(0, 24, pts), EPS);
        assertEquals(24000.0, PolarisForecastMath.stepAt(0, 27, pts), EPS);
        assertEquals(36000.0, PolarisForecastMath.stepAt(0, 28, pts), EPS);
        assertEquals(36000.0, PolarisForecastMath.stepAt(0, 36, pts), EPS);
        assertEquals(36000.0, PolarisForecastMath.stepAt(0, 48, pts), EPS);
        // no targets -> baseline forever
        assertEquals(0.0, PolarisForecastMath.stepAt(0, 48, List.of()), EPS);
        // same shape as salaryAt: the value is never between two targets
        for (int m = 0; m <= 60; m++) {
            double v = PolarisForecastMath.stepAt(0, m, pts);
            assertTrue(v == 0.0 || v == 24000.0 || v == 36000.0, "month " + m + " ramped to " + v);
        }
    }

    @Test
    @DisplayName("OPEX step: a target dated this month or in the past is in force from month 0 (like a passed raise date)")
    void stepAt_pastAndCurrentTargetsCount() {
        List<Point> pts = List.of(new Point(0, 10000), new Point(-6, 7000), new Point(12, 12000));
        assertEquals(10000.0, PolarisForecastMath.stepAt(0, 0, pts), EPS);     // this month's target wins over the older one
        assertEquals(10000.0, PolarisForecastMath.stepAt(0, 11, pts), EPS);
        assertEquals(12000.0, PolarisForecastMath.stepAt(0, 12, pts), EPS);
        assertEquals(7000.0,  PolarisForecastMath.stepAt(0, 0, List.of(new Point(-6, 7000))), EPS);
        // the base value (run-rate) is returned until the first target when a non-zero base is given
        assertEquals(500.0, PolarisForecastMath.stepAt(500, 3, List.of(new Point(4, 9))), EPS);
    }

    @Test
    @DisplayName("annualRunRate: 8,857 over 300 days = 10,776.02; empty/inverted period -> 0; full year -> change itself")
    void annualRunRate_edges() {
        double runRate = PolarisForecastMath.annualRunRate(8857, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 27));
        assertEquals(300, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 27)) + 1);
        assertEquals(8857 * 365.0 / 300, runRate, EPS);                 // 10,776.016667
        assertEquals(10776.016667, runRate, 1e-6);
        assertEquals(0.0, PolarisForecastMath.annualRunRate(100, null, LocalDate.of(2026, 1, 1)), EPS);
        assertEquals(0.0, PolarisForecastMath.annualRunRate(100, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)), EPS);
        assertEquals(1200.0, PolarisForecastMath.annualRunRate(1200, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)), EPS);
    }

    @Test
    @DisplayName("9 OPEX categories (30 Oct 2026 split), each mapped to its Astrolavos actuals label")
    void opexCategories_andActualsLabels() {
        assertEquals(List.of("Consultants", "Audit", "Legal", "IT / Computer", "Marketing / Διαφήμιση",
            "Ταξίδια", "PR", "Λοιπά", "Σύνδεσμοι"), PolarisForecastMath.OPEX_CATEGORIES);
        for (String cat : PolarisForecastMath.OPEX_CATEGORIES) {
            assertTrue(PolarisForecastMath.OPEX_ACTUALS_LABEL.containsKey(cat), cat + " has no actuals label");
        }
        assertEquals(9, PolarisForecastMath.OPEX_ACTUALS_LABEL.size());
        for (String c : List.of("Consultants", "Audit", "Legal"))
            assertEquals("Consultants / Audit / Legal", PolarisForecastMath.OPEX_ACTUALS_LABEL.get(c));
        for (String c : List.of("Ταξίδια", "PR", "Λοιπά", "Σύνδεσμοι"))
            assertEquals("Ταξίδια / PR / λοιπά / σύνδεσμοι", PolarisForecastMath.OPEX_ACTUALS_LABEL.get(c));
        assertEquals("IT / Computer", PolarisForecastMath.OPEX_ACTUALS_LABEL.get("IT / Computer"));
        assertEquals("Marketing / Διαφήμιση", PolarisForecastMath.OPEX_ACTUALS_LABEL.get("Marketing / Διαφήμιση"));
    }

    // ─── payroll raise steps (hand example from the task) ───

    @Test
    @DisplayName("salaryAt: 46,800 -> +5% @2027 = 49,140 flat -> +3% @2029 = 50,614.20 flat; before 2027 = 46,800")
    void salaryAt_stepCompounding() {
        List<Raise> raises = List.of(
            new Raise(LocalDate.of(2029, 1, 1), 3),    // deliberately out of order: the helper sorts
            new Raise(LocalDate.of(2027, 1, 1), 5));
        assertEquals(46800.0,  PolarisForecastMath.salaryAt(46800, raises, LocalDate.of(2026, 1, 1)), EPS);
        assertEquals(46800.0,  PolarisForecastMath.salaryAt(46800, raises, LocalDate.of(2026, 12, 31)), EPS);
        assertEquals(49140.0,  PolarisForecastMath.salaryAt(46800, raises, LocalDate.of(2027, 1, 1)), EPS);   // 46,800 x 1.05
        assertEquals(49140.0,  PolarisForecastMath.salaryAt(46800, raises, LocalDate.of(2028, 6, 15)), EPS);  // flat until next step
        assertEquals(50614.20, PolarisForecastMath.salaryAt(46800, raises, LocalDate.of(2029, 1, 1)), EPS);   // 49,140 x 1.03
        assertEquals(50614.20, PolarisForecastMath.salaryAt(46800, raises, LocalDate.of(2035, 1, 1)), EPS);   // flat after the last
        // each raise applies ONCE: never 46,800 x 1.05 x 1.05
        assertEquals(46800 * 1.05 * 1.03, PolarisForecastMath.salaryAt(46800, raises, LocalDate.of(2030, 1, 1)), EPS);
        // no raises -> base
        assertEquals(46800.0, PolarisForecastMath.salaryAt(46800, List.of(), LocalDate.of(2040, 1, 1)), EPS);
    }

    @Test
    @DisplayName("monthsBetween counts whole months from the first of the base month")
    void monthsBetween_examples() {
        assertEquals(0, PolarisForecastMath.monthsBetween(BASE, LocalDate.of(2026, 10, 15)));
        assertEquals(1, PolarisForecastMath.monthsBetween(BASE, LocalDate.of(2026, 11, 1)));
        assertEquals(12, PolarisForecastMath.monthsBetween(BASE, LocalDate.of(2027, 10, 1)));
        assertEquals(-2, PolarisForecastMath.monthsBetween(BASE, LocalDate.of(2026, 8, 1)));
    }
}
