package com.next2me.next2cash.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pure forecast math shared by the Polaris P&L (Oct 2026). Mirrors
 * frontend/src/stores/polarisMath.js line for line -- client and server must
 * stay identical (parity tests on both sides).
 *
 *  - {@link #interpolate}: the MEMBER milestone rule: before the first
 *    milestone the base value (today's members), between two milestones linear
 *    interpolation, after the last one flat.
 *  - {@link #stepAt}: the OPEX milestone rule (30 Oct 2026 decision): a STEP
 *    function with the same semantics as payroll raises -- the value of the
 *    LATEST target whose month <= the forecast month, the base ($0) before the
 *    first target, flat between targets and after the last one. No ramps.
 *  - {@link #salaryAt}: the payroll raise STEP function: base, then each raise
 *    with effectiveDate <= the date applied once, in date order, compounding on
 *    the prior step; flat before the first and between steps.
 *  - {@link #annualRunRate}: an actuals period's in-period change annualised by
 *    calendar days (change x 365 / days).
 */
public final class PolarisForecastMath {

    private PolarisForecastMath() {}

    /**
     * The 9 "Λοιπά OPEX Διοίκησης" forecast categories (30 Oct 2026 split):
     * the Astrolavos lines "Consultants / Audit / Legal" and
     * "Ταξίδια / PR / λοιπά / σύνδεσμοι" are forecast per sub-category.
     * cost_milestones.category holds one of these strings.
     */
    public static final List<String> OPEX_CATEGORIES = List.of(
        "Consultants",
        "Audit",
        "Legal",
        "IT / Computer",
        "Marketing / Διαφήμιση",
        "Ταξίδια",
        "PR",
        "Λοιπά",
        "Σύνδεσμοι");

    /**
     * Forecast category -> the Astrolavos actuals_rows label that carries its
     * actuals. Several categories share one label because Astrolavos only
     * reports the combined total (no split exists in the source data).
     */
    public static final Map<String, String> OPEX_ACTUALS_LABEL = Map.of(
        "Consultants", "Consultants / Audit / Legal",
        "Audit", "Consultants / Audit / Legal",
        "Legal", "Consultants / Audit / Legal",
        "IT / Computer", "IT / Computer",
        "Marketing / Διαφήμιση", "Marketing / Διαφήμιση",
        "Ταξίδια", "Ταξίδια / PR / λοιπά / σύνδεσμοι",
        "PR", "Ταξίδια / PR / λοιπά / σύνδεσμοι",
        "Λοιπά", "Ταξίδια / PR / λοιπά / σύνδεσμοι",
        "Σύνδεσμοι", "Ταξίδια / PR / λοιπά / σύνδεσμοι");

    /** actuals_rows.group_key of those labels. */
    public static final String OPEX_GROUP_KEY = "adminOpexOther";

    /** A milestone as (months from the base month, value). */
    public record Point(double months, double value) {}

    /** A raise step. */
    public record Raise(LocalDate effectiveDate, double pct) {}

    /** Whole months between the first of `base`'s month and `target`'s month (monthsFromNow in the store). */
    public static int monthsBetween(LocalDate base, LocalDate target) {
        return (target.getYear() - base.getYear()) * 12 + (target.getMonthValue() - base.getMonthValue());
    }

    /**
     * Piecewise-linear milestone interpolation (members).
     * points: milestones in any order; those with months <= 0 are ignored (the
     * base value already stands for "now"). Returns baseValue when targetMonths <= 0.
     */
    public static double interpolate(double baseValue, double targetMonths, List<Point> milestones) {
        if (targetMonths <= 0) return baseValue;
        List<Point> pts = new ArrayList<>();
        pts.add(new Point(0, baseValue));
        milestones.stream()
            .filter(p -> p.months() > 0)
            .sorted(Comparator.comparingDouble(Point::months))
            .forEach(pts::add);
        Point last = pts.get(pts.size() - 1);
        if (targetMonths >= last.months()) return last.value();
        for (int i = 0; i < pts.size() - 1; i++) {
            Point a = pts.get(i), b = pts.get(i + 1);
            if (targetMonths >= a.months() && targetMonths <= b.months()) {
                double span = b.months() - a.months();
                double t = span > 0 ? (targetMonths - a.months()) / span : 1;
                return a.value() + (b.value() - a.value()) * t;
            }
        }
        return last.value();
    }

    /**
     * Step function over milestones (OPEX): the value of the LATEST target whose
     * months <= targetMonths; baseValue when no target has been reached yet.
     * Targets in the past or in the current month (months <= 0) count like a
     * raise whose effective date has passed: they are in force from "now".
     * Flat between targets, flat after the last one -- no interpolation.
     */
    public static double stepAt(double baseValue, double targetMonths, List<Point> milestones) {
        double v = baseValue;
        boolean hit = false;
        double hitMonths = 0;
        for (Point p : milestones) {
            if (p.months() > targetMonths) continue;
            if (!hit || p.months() >= hitMonths) {   // the latest reached target wins (ties: last listed)
                v = p.value();
                hitMonths = p.months();
                hit = true;
            }
        }
        return v;
    }

    /**
     * Salary in force at `at`: base, then every raise with effectiveDate <= at,
     * applied once in date order, each compounding on the previous step.
     * Example: 46,800 -> +5% @2027-01-01 -> 49,140 -> +3% @2029-01-01 -> 50,614.20.
     */
    public static double salaryAt(double base, List<Raise> raises, LocalDate at) {
        double s = base;
        List<Raise> sorted = new ArrayList<>(raises);
        sorted.sort(Comparator.comparing(Raise::effectiveDate));
        for (Raise r : sorted) {
            if (r.effectiveDate() == null || r.effectiveDate().isAfter(at)) continue;
            s = s * (1 + r.pct() / 100);
        }
        return s;
    }

    /** change x 365 / (end - start + 1 days); 0 when the period is empty or inverted. */
    public static double annualRunRate(double change, LocalDate start, LocalDate end) {
        if (start == null || end == null) return 0;
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days <= 0) return 0;
        return change * 365.0 / days;
    }
}
