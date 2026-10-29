package com.next2me.next2cash.service;

import com.next2me.next2cash.service.PolarisForecastMath.Point;
import com.next2me.next2cash.service.PolarisForecastMath.Raise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure math (no Spring). Every figure here is worked out by hand first and the
 * same cases are asserted in frontend/tests/polarisMath.test.mjs (parity).
 */
class PolarisForecastMathTest {

    private static final double EPS = 1e-6;
    private static final LocalDate BASE = LocalDate.of(2026, 10, 1);   // first of the current month (29 Oct 2026)

    // ─── interpolation: the member-milestone fixture (20k@2027-06, 30k@2028-03) ───

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
    }

    // ─── interpolation: the OPEX hand example (Consultants / Audit / Legal) ───

    @Test
    @DisplayName("OPEX: run-rate 8,857 x 365/300 = 10,776.02; 24,000 at +8; 36,000 at +28 -> 26,400 at 12, 33,600 at 24")
    void interpolate_opexHandExample() {
        double runRate = PolarisForecastMath.annualRunRate(8857, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 27));
        assertEquals(300, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 27)) + 1);
        assertEquals(8857 * 365.0 / 300, runRate, EPS);                 // 10,776.016667
        assertEquals(10776.016667, runRate, 1e-6);

        List<Point> pts = List.of(
            new Point(PolarisForecastMath.monthsBetween(BASE, LocalDate.of(2027, 6, 1)), 24000),   // +8
            new Point(PolarisForecastMath.monthsBetween(BASE, LocalDate.of(2029, 2, 1)), 36000));  // +28
        assertEquals(runRate, PolarisForecastMath.interpolate(runRate, 0, pts), EPS);
        // 12 months: 4/20 of the way from 24,000 to 36,000 -> 24,000 + 2,400 = 26,400
        assertEquals(26400.0, PolarisForecastMath.interpolate(runRate, 12, pts), EPS);
        // 24 months: 16/20 of the way -> 24,000 + 9,600 = 33,600
        assertEquals(33600.0, PolarisForecastMath.interpolate(runRate, 24, pts), EPS);
        // 48 months: past the last target -> 36,000 flat
        assertEquals(36000.0, PolarisForecastMath.interpolate(runRate, 48, pts), EPS);
        // between today and the first target: 4 months = halfway from 10,776.02 to 24,000
        assertEquals(runRate + (24000 - runRate) * 0.5, PolarisForecastMath.interpolate(runRate, 4, pts), EPS);
    }

    @Test
    @DisplayName("annualRunRate: empty/inverted period -> 0; full year -> change itself")
    void annualRunRate_edges() {
        assertEquals(0.0, PolarisForecastMath.annualRunRate(100, null, LocalDate.of(2026, 1, 1)), EPS);
        assertEquals(0.0, PolarisForecastMath.annualRunRate(100, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)), EPS);
        assertEquals(1200.0, PolarisForecastMath.annualRunRate(1200, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)), EPS);
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
