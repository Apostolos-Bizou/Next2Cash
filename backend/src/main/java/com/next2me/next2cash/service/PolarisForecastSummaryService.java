package com.next2me.next2cash.service;

import com.next2me.next2cash.model.ForecastAssumption;
import com.next2me.next2cash.model.PayrollMilestone;
import com.next2me.next2cash.model.PayrollPerson;
import com.next2me.next2cash.repository.ForecastAssumptionRepository;
import com.next2me.next2cash.repository.PayrollMilestoneRepository;
import com.next2me.next2cash.repository.PayrollPersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Polaris P&L -- server-side forecast summary (Phase 4, Oct 2026).
 *
 * Computes the hero / KPI / chart aggregates of the "Προβλέψεις Κερδοφορίας"
 * block so that a role which may NOT read payroll_people or
 * forecast_assumptions (the Polaris viewer, e.g. Simos) can still see the
 * result. The response carries ONLY aggregates: no person rows, names or
 * salaries, and no raw assumption values other than the current member count.
 *
 * The math is a 1:1 port of the frontend store (polarisPl.js / polarisMath.js),
 * which mirrors the Polaris xlsx model:
 *   - forecast year y = 1 is the current calendar year;
 *   - Athens salaries are USD (no FX), Manila salaries are PHP ÷ fx_php_per_usd;
 *   - raise_pct is a one-time step from effective_from_year, flat afterwards;
 *   - office-cost rows are part of the payroll total (EBITDA = contribution − payroll);
 *   - members: flat % growth compounded per year ("growth"), or piecewise-linear
 *     interpolation across payroll_milestones measured in months from the first
 *     day of the current month ("milestones"; flat after the last milestone).
 *   - which of the two applies is the admin's choice on the Forecast page,
 *     persisted as the forecast_assumptions row `members_mode`
 *     (value 1 = milestones, anything else / absent = growth), so this summary
 *     matches what the admin sees. An explicit membersMode argument overrides it.
 *
 * Access control is NOT done here: PolarisForecastController runs the
 * entity_roles guard first (any Polaris role may read this).
 */
@Service
@RequiredArgsConstructor
public class PolarisForecastSummaryService {

    public static final Set<Integer> ALLOWED_YEARS = Set.of(1, 3, 5);
    public static final String MODE_GROWTH = "growth";
    public static final String MODE_MILESTONES = "milestones";

    // same constants as the store's "core P&L math"
    static final double CLAIMS = 35;
    static final double NET_VAT = 0.12, AUDIT_VAT = 0.12, DENTAL_VAT = 0.12;
    static final double DEFAULT_PHP_PER_USD = 56.5;

    // forecast_assumptions keys (ASSUMPTION_KEYS in polarisPl.js)
    static final String K_MEMBERS = "members", K_GROWTH = "member_growth_pct", K_NET_FEE = "network_fee",
        K_MONTHLY_FEE = "monthly_fee", K_AUDIT_RATE = "audit_fee_rate", K_DENTAL_FEE = "dental_fee",
        K_NET_PAY = "network_payable", K_AUDIT_PAY = "audit_payable_rate", K_DENTAL_PAY = "dental_payable",
        K_PHP_PER_USD = "fx_php_per_usd";
    /** forecast_assumptions key holding the member-forecast mode: 1 = milestones, else growth. */
    public static final String K_MEMBERS_MODE = "members_mode";

    private final PayrollPersonRepository payrollPersonRepository;
    private final PayrollMilestoneRepository payrollMilestoneRepository;
    private final ForecastAssumptionRepository forecastAssumptionRepository;

    // -------------------------------------------------------------------
    // Response shape (aggregates only)
    // -------------------------------------------------------------------

    public record Members(double current, double horizon) {}

    public record SeriesPoint(int year, int calendarYear, String label, double ebitda, double members) {}

    public record ForecastSummary(
        int years,
        int baseYear,
        int horizonCalendarYear,
        String membersMode,
        Members members,
        double ebitda,
        double ebitdaMarginPct,
        double ebitdaPerMember,
        Double breakEvenMembers,      // null when contribution per member <= 0 (client shows "—")
        Double breakEvenDistance,     // members at horizon − break-even members
        double revenuePerMember,
        double contributionMarginPct,
        double payrollToRevenuePct,
        double thirdPartyPerMember,
        List<SeriesPoint> series
    ) {}

    /** One forecast point (the store's pnlAtYear). Package-private for tests. */
    record Pnl(double members, double revenue, double thirdParty, double contribution, double payroll, double ebitda) {}

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    public ForecastSummary summary(UUID entityId, int years, String membersMode) {
        return summary(entityId, years, membersMode, LocalDate.now());
    }

    /** `today` is injectable so tests are not tied to the wall clock. */
    public ForecastSummary summary(UUID entityId, int years, String membersMode, LocalDate today) {
        if (!ALLOWED_YEARS.contains(years)) {
            throw new IllegalArgumentException("years must be one of 1, 3, 5");
        }
        Map<String, Double> a = forecastAssumptionRepository.findByEntityIdOrderByKeyAsc(entityId).stream()
            .collect(Collectors.toMap(ForecastAssumption::getKey, x -> num(x.getValue()), (x, y) -> y));

        // mode: explicit argument wins; otherwise the admin's persisted choice; otherwise growth
        String mode;
        if (membersMode == null || membersMode.isBlank()) {
            mode = storedMembersMode(a);
        } else {
            mode = membersMode.trim().toLowerCase();
            if (!MODE_GROWTH.equals(mode) && !MODE_MILESTONES.equals(mode)) {
                throw new IllegalArgumentException("membersMode must be 'growth' or 'milestones'");
            }
        }

        List<PayrollPerson> people = payrollPersonRepository.findByEntityIdOrderByOfficeAscNameAsc(entityId);
        List<PayrollMilestone> milestones = MODE_MILESTONES.equals(mode)
            ? payrollMilestoneRepository.findByEntityIdOrderByTargetMonthAsc(entityId)
            : List.of();

        int baseYear = today.getYear();
        LocalDate milestoneBase = today.withDayOfMonth(1);
        Function<Integer, Pnl> pnlAt = y -> pnlAtYear(y, a, people, milestones, mode, baseYear, milestoneBase);

        Pnl cur = pnlAt.apply(years);
        double contribPerMember = cur.members() != 0 ? cur.contribution() / cur.members() : 0;
        Double breakEven = contribPerMember > 0 ? cur.payroll() / contribPerMember : null;
        Double breakEvenDistance = breakEven == null ? null : cur.members() - breakEven;

        List<SeriesPoint> series = new ArrayList<>();
        for (int y = 1; y <= years; y++) {
            Pnl p = pnlAt.apply(y);
            series.add(new SeriesPoint(y, baseYear + y - 1, "Έτος " + y, p.ebitda(), p.members()));
        }

        return new ForecastSummary(
            years,
            baseYear,
            baseYear + years - 1,
            mode,
            new Members(a.getOrDefault(K_MEMBERS, 0.0), cur.members()),
            cur.ebitda(),
            pct(cur.ebitda(), cur.revenue()),
            perMember(cur.ebitda(), cur.members()),
            breakEven,
            breakEvenDistance,
            perMember(cur.revenue(), cur.members()),
            pct(cur.contribution(), cur.revenue()),
            pct(cur.payroll(), cur.revenue()),
            perMember(cur.thirdParty(), cur.members()),
            series
        );
    }

    /** The persisted members_mode assumption: 1 -> milestones, anything else or absent -> growth. */
    static String storedMembersMode(Map<String, Double> assumptions) {
        return assumptions.getOrDefault(K_MEMBERS_MODE, 0.0) >= 1 ? MODE_MILESTONES : MODE_GROWTH;
    }

    // -------------------------------------------------------------------
    // Core math (package-private so the unit test can check parity)
    // -------------------------------------------------------------------

    static Pnl pnlAtYear(int y, Map<String, Double> a, List<PayrollPerson> people, List<PayrollMilestone> milestones,
                         String mode, int baseYear, LocalDate milestoneBase) {
        double members = membersAtYear(y, a, milestones, mode, milestoneBase);
        double netFee = a.getOrDefault(K_NET_FEE, 0.0), monthlyFee = a.getOrDefault(K_MONTHLY_FEE, 0.0);
        double auditRate = a.getOrDefault(K_AUDIT_RATE, 0.0), dentalFee = a.getOrDefault(K_DENTAL_FEE, 0.0);
        double netPay = a.getOrDefault(K_NET_PAY, 0.0), auditPay = a.getOrDefault(K_AUDIT_PAY, 0.0);
        double dentalPay = a.getOrDefault(K_DENTAL_PAY, 0.0);
        double phpPerUsd = a.getOrDefault(K_PHP_PER_USD, 0.0);
        if (phpPerUsd == 0) phpPerUsd = DEFAULT_PHP_PER_USD;   // store: num(inputs.phpPerUsd, 56.5)

        double revenue = members * netFee + members * monthlyFee * 12 + members * auditRate * CLAIMS + members * dentalFee;
        double thirdParty = members * netPay * (1 + NET_VAT) + members * CLAIMS * auditPay * (1 + AUDIT_VAT)
            + members * dentalPay * (1 + DENTAL_VAT);
        double contribution = revenue - thirdParty;
        double payroll = payrollUsdInYear(people, calendarYear(baseYear, y), phpPerUsd);
        return new Pnl(members, revenue, thirdParty, contribution, payroll, contribution - payroll);
    }

    /** membersAtYear: compounded % growth, or piecewise-linear milestones ((y−1)·12 months out). */
    static double membersAtYear(int y, Map<String, Double> a, List<PayrollMilestone> milestones, String mode,
                                LocalDate milestoneBase) {
        double members0 = a.getOrDefault(K_MEMBERS, 0.0);
        if (MODE_MILESTONES.equals(mode)) return membersAtMonths(members0, (y - 1) * 12, milestones, milestoneBase);
        double growth = a.getOrDefault(K_GROWTH, 0.0);
        return members0 * Math.pow(1 + growth / 100, y - 1);
    }

    static double membersAtMonths(double members0, int targetMonths, List<PayrollMilestone> milestones, LocalDate base) {
        if (targetMonths <= 0) return members0;
        // points: [{0, members0}, ...sorted future milestones (months > 0)]
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] { 0, members0 });
        milestones.stream()
            .filter(m -> m.getTargetMonth() != null)
            .map(m -> new double[] { monthsFromBase(m.getTargetMonth(), base), m.getMembersTarget() == null ? 0 : m.getMembersTarget() })
            .filter(p -> p[0] > 0)
            .sorted((p, q) -> Double.compare(p[0], q[0]))
            .forEach(pts::add);
        double[] last = pts.get(pts.size() - 1);
        if (targetMonths >= last[0]) return last[1];
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] p = pts.get(i), q = pts.get(i + 1);
            if (targetMonths >= p[0] && targetMonths <= q[0]) {
                double span = q[0] - p[0];
                double t = span > 0 ? (targetMonths - p[0]) / span : 1;
                return p[1] + (q[1] - p[1]) * t;
            }
        }
        return last[1];
    }

    /** monthsFromNow(): whole months between the first of the current month and the milestone month. */
    static int monthsFromBase(LocalDate target, LocalDate base) {
        return (target.getYear() - base.getYear()) * 12 + (target.getMonthValue() - base.getMonthValue());
    }

    // ---- polarisMath.js ----

    static int calendarYear(int baseYear, int y) { return baseYear + (y - 1); }

    static double nativeSalaryInYear(PayrollPerson p, int year) {
        double base = num(p.getSalary());
        double raise = num(p.getRaisePct());
        Integer effFrom = p.getEffectiveFromYear();
        if (raise == 0 || effFrom == null || year < effFrom) return base;
        return base * (1 + raise / 100);
    }

    static double nativeToUsd(PayrollPerson p, double nativeAmount, double phpPerUsd) {
        if ("MANILA".equalsIgnoreCase(p.getOffice())) return nativeAmount / (phpPerUsd != 0 ? phpPerUsd : DEFAULT_PHP_PER_USD);
        return nativeAmount;
    }

    static double usdSalaryInYear(PayrollPerson p, int year, double phpPerUsd) {
        return nativeToUsd(p, nativeSalaryInYear(p, year), phpPerUsd);
    }

    /** Total USD payroll (people AND office-cost rows) for a calendar year. Public for parity tests. */
    public static double payrollUsdInYear(List<PayrollPerson> people, int year, double phpPerUsd) {
        double s = 0;
        for (PayrollPerson p : people) s += usdSalaryInYear(p, year, phpPerUsd);
        return s;
    }

    // ---- helpers ----

    private static double num(BigDecimal v) { return v == null ? 0 : v.doubleValue(); }
    private static double pct(double part, double whole) { return whole != 0 ? part / whole * 100 : 0; }
    private static double perMember(double v, double members) { return members != 0 ? v / members : 0; }
}
