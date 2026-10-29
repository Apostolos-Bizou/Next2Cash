package com.next2me.next2cash.service;

import com.next2me.next2cash.model.CostMilestone;
import com.next2me.next2cash.model.ForecastAssumption;
import com.next2me.next2cash.model.PayrollMilestone;
import com.next2me.next2cash.model.PayrollPerson;
import com.next2me.next2cash.model.PayrollPersonRaise;
import com.next2me.next2cash.repository.CostMilestoneRepository;
import com.next2me.next2cash.repository.ForecastAssumptionRepository;
import com.next2me.next2cash.repository.PayrollMilestoneRepository;
import com.next2me.next2cash.repository.PayrollPersonRaiseRepository;
import com.next2me.next2cash.repository.PayrollPersonRepository;
import com.next2me.next2cash.service.PolarisForecastMath.Point;
import com.next2me.next2cash.service.PolarisForecastMath.Raise;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.next2me.next2cash.service.PolarisForecastMath.OPEX_CATEGORIES;

/**
 * Polaris P&L -- server-side forecast summary (Phase 4, Oct 2026).
 *
 * Computes the hero / KPI / chart aggregates of the "Προβλέψεις Κερδοφορίας"
 * block so that a role which may NOT read payroll_people, forecast_assumptions,
 * cost_milestones or payroll_person_raises (the Polaris viewer, e.g. Simos) can
 * still see the result. The response carries ONLY aggregates: no person rows,
 * names, salaries, raises, category names or milestone dates, and no raw
 * assumption values other than the current member count.
 *
 * The math is a 1:1 port of the frontend store (polarisPl.js / polarisMath.js),
 * which mirrors the Polaris xlsx model:
 *   - forecast year y = 1 is the current calendar year; year y is evaluated at
 *     1 January of that calendar year ((y-1)*12 months from the base month);
 *   - Athens salaries are USD (no FX), Manila salaries are PHP ÷ fx_php_per_usd;
 *   - raises: payroll_person_raises steps (compounding, PolarisForecastMath#salaryAt);
 *     a person with no rows falls back to the legacy one-time raise_pct from
 *     effective_from_year;
 *   - office-cost rows are part of the payroll total;
 *   - members: flat % growth compounded per year ("growth"), or the shared
 *     milestone interpolation over payroll_milestones ("milestones");
 *   - which of the two applies is the admin's choice on the Forecast page,
 *     persisted as the forecast_assumptions row `members_mode`
 *     (value 1 = milestones, anything else / absent = growth), so this summary
 *     matches what the admin sees. An explicit membersMode argument overrides it.
 *   - OPEX: for each PolarisForecastMath.OPEX_CATEGORIES label, the
 *     cost_milestones of that category interpolate with the same rule as members
 *     from a $0 baseline (decision 29 Oct 2026): a category without milestones
 *     contributes nothing, and the admin adds a first target near today at the
 *     current run-rate when the present cost should count too (the Forecast page
 *     shows that run-rate as a hint). EBITDA = contribution − payroll − OPEX.
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
    private final PayrollPersonRaiseRepository payrollPersonRaiseRepository;
    private final PayrollMilestoneRepository payrollMilestoneRepository;
    private final ForecastAssumptionRepository forecastAssumptionRepository;
    private final CostMilestoneRepository costMilestoneRepository;

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
        double opexToRevenuePct,
        double thirdPartyPerMember,
        List<SeriesPoint> series
    ) {}

    /** One forecast point (the store's pnlAtYear). Package-private for tests. */
    record Pnl(double members, double revenue, double thirdParty, double contribution, double payroll, double opex, double ebitda) {}

    /** Everything pnlAtYear needs, loaded once per request. Package-private for tests. */
    record Inputs(
        Map<String, Double> assumptions,
        List<PayrollPerson> people,
        Map<UUID, List<Raise>> raisesByPerson,
        List<Point> memberMilestones,
        String membersMode,
        int baseYear,
        Map<String, List<Point>> opexMilestones    // category -> milestones (months, annual USD); baseline $0
    ) {}

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
        Inputs in = loadInputs(entityId, membersMode, today);

        Pnl cur = pnlAtYear(years, in);
        double contribPerMember = cur.members() != 0 ? cur.contribution() / cur.members() : 0;
        // break-even: members needed to cover the fixed cost base (payroll + OPEX) at the horizon's per-member contribution
        Double breakEven = contribPerMember > 0 ? (cur.payroll() + cur.opex()) / contribPerMember : null;
        Double breakEvenDistance = breakEven == null ? null : cur.members() - breakEven;

        List<SeriesPoint> series = new ArrayList<>();
        for (int y = 1; y <= years; y++) {
            Pnl p = pnlAtYear(y, in);
            series.add(new SeriesPoint(y, in.baseYear() + y - 1, "Έτος " + y, p.ebitda(), p.members()));
        }

        return new ForecastSummary(
            years,
            in.baseYear(),
            in.baseYear() + years - 1,
            in.membersMode(),
            new Members(in.assumptions().getOrDefault(K_MEMBERS, 0.0), cur.members()),
            cur.ebitda(),
            pct(cur.ebitda(), cur.revenue()),
            perMember(cur.ebitda(), cur.members()),
            breakEven,
            breakEvenDistance,
            perMember(cur.revenue(), cur.members()),
            pct(cur.contribution(), cur.revenue()),
            pct(cur.payroll(), cur.revenue()),
            pct(cur.opex(), cur.revenue()),
            perMember(cur.thirdParty(), cur.members()),
            series
        );
    }

    /** Loads assumptions, people + raises, member milestones, OPEX run-rates + milestones. Package-private for tests. */
    Inputs loadInputs(UUID entityId, String membersMode, LocalDate today) {
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

        LocalDate milestoneBase = today.withDayOfMonth(1);

        List<PayrollPerson> people = payrollPersonRepository.findByEntityIdOrderByOfficeAscNameAsc(entityId);
        Map<UUID, List<Raise>> raises = new HashMap<>();
        for (PayrollPersonRaise r : payrollPersonRaiseRepository.findAllForEntity(entityId)) {
            raises.computeIfAbsent(r.getPayrollPersonId(), k -> new ArrayList<>())
                .add(new Raise(r.getEffectiveDate(), num(r.getRaisePct())));
        }

        List<Point> memberMilestones = MODE_MILESTONES.equals(mode)
            ? payrollMilestoneRepository.findByEntityIdOrderByTargetMonthAsc(entityId).stream()
                .filter(m -> m.getTargetMonth() != null)
                .map(m -> new Point(PolarisForecastMath.monthsBetween(milestoneBase, m.getTargetMonth()),
                                    m.getMembersTarget() == null ? 0 : m.getMembersTarget()))
                .toList()
            : List.of();

        Map<String, List<Point>> opexMilestones = new HashMap<>();
        for (CostMilestone c : costMilestoneRepository.findByEntityIdOrderByCategoryAscTargetDateAsc(entityId)) {
            if (c.getTargetDate() == null) continue;
            opexMilestones.computeIfAbsent(c.getCategory(), k -> new ArrayList<>())
                .add(new Point(PolarisForecastMath.monthsBetween(milestoneBase, c.getTargetDate()), num(c.getTargetValue())));
        }

        return new Inputs(a, people, raises, memberMilestones, mode, today.getYear(), opexMilestones);
    }

    /** The persisted members_mode assumption: 1 -> milestones, anything else or absent -> growth. */
    static String storedMembersMode(Map<String, Double> assumptions) {
        return assumptions.getOrDefault(K_MEMBERS_MODE, 0.0) >= 1 ? MODE_MILESTONES : MODE_GROWTH;
    }

    // -------------------------------------------------------------------
    // Core math (package-private so the unit test can check parity)
    // -------------------------------------------------------------------

    static Pnl pnlAtYear(int y, Inputs in) {
        Map<String, Double> a = in.assumptions();
        double members = membersAtYear(y, in);
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
        double payroll = payrollUsdInYear(in.people(), in.raisesByPerson(), calendarYear(in.baseYear(), y), phpPerUsd);
        double opex = opexAtYear(y, in);
        return new Pnl(members, revenue, thirdParty, contribution, payroll, opex, contribution - payroll - opex);
    }

    /** membersAtYear: compounded % growth, or the shared milestone interpolation at (y−1)·12 months. */
    static double membersAtYear(int y, Inputs in) {
        double members0 = in.assumptions().getOrDefault(K_MEMBERS, 0.0);
        if (MODE_MILESTONES.equals(in.membersMode())) {
            return PolarisForecastMath.interpolate(members0, (y - 1) * 12, in.memberMilestones());
        }
        double growth = in.assumptions().getOrDefault(K_GROWTH, 0.0);
        return members0 * Math.pow(1 + growth / 100, y - 1);
    }

    /** Sum over OPEX categories of the interpolated annual amount at (y−1)·12 months, from a $0 baseline. */
    static double opexAtYear(int y, Inputs in) {
        double total = 0;
        for (String cat : OPEX_CATEGORIES) {
            List<Point> pts = in.opexMilestones().getOrDefault(cat, List.of());
            total += PolarisForecastMath.interpolate(0, (y - 1) * 12, pts);
        }
        return total;
    }

    // ---- polarisMath.js ----

    static int calendarYear(int baseYear, int y) { return baseYear + (y - 1); }

    /** Legacy single raise: base, or base × (1 + raise%) from effective_from_year onward. */
    static double nativeSalaryInYear(PayrollPerson p, int year) {
        double base = num(p.getSalary());
        double raise = num(p.getRaisePct());
        Integer effFrom = p.getEffectiveFromYear();
        if (raise == 0 || effFrom == null || year < effFrom) return base;
        return base * (1 + raise / 100);
    }

    /** Native salary in a calendar year: raise steps at 1 January of that year, else the legacy single raise. */
    static double nativeSalaryInYear(PayrollPerson p, List<Raise> raises, int year) {
        if (raises == null || raises.isEmpty()) return nativeSalaryInYear(p, year);
        return PolarisForecastMath.salaryAt(num(p.getSalary()), raises, LocalDate.of(year, 1, 1));
    }

    static double nativeToUsd(PayrollPerson p, double nativeAmount, double phpPerUsd) {
        if ("MANILA".equalsIgnoreCase(p.getOffice())) return nativeAmount / (phpPerUsd != 0 ? phpPerUsd : DEFAULT_PHP_PER_USD);
        return nativeAmount;
    }

    static double usdSalaryInYear(PayrollPerson p, List<Raise> raises, int year, double phpPerUsd) {
        return nativeToUsd(p, nativeSalaryInYear(p, raises, year), phpPerUsd);
    }

    /** Total USD payroll (people AND office-cost rows) for a calendar year, with raise steps. */
    public static double payrollUsdInYear(List<PayrollPerson> people, Map<UUID, List<Raise>> raisesByPerson, int year, double phpPerUsd) {
        double s = 0;
        for (PayrollPerson p : people) s += usdSalaryInYear(p, raisesByPerson.getOrDefault(p.getId(), List.of()), year, phpPerUsd);
        return s;
    }

    /** Legacy-only variant (no raise rows): kept for the polarisMath parity tests. */
    public static double payrollUsdInYear(List<PayrollPerson> people, int year, double phpPerUsd) {
        return payrollUsdInYear(people, Map.of(), year, phpPerUsd);
    }

    // ---- helpers ----

    private static double num(BigDecimal v) { return v == null ? 0 : v.doubleValue(); }
    private static double pct(double part, double whole) { return whole != 0 ? part / whole * 100 : 0; }
    private static double perMember(double v, double members) { return members != 0 ? v / members : 0; }
}
