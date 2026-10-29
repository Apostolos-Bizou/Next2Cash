// polarisMath.js — pure forecast math for the Polaris P&L (no Vue, no store),
// so it can be unit-tested with plain node:test and reused by the store.
// MUST stay identical to backend PolarisForecastMath / PolarisForecastSummaryService
// (parity tests on both sides).
//
// Source of truth: the Polaris xlsx model (28 Oct 2026 review).
//   - Athens salaries are entered and paid in USD: NO FX conversion.
//   - Manila salaries are entered in PHP and converted at fx_php_per_usd.
//   - Raises (29 Oct 2026): a person's `raises` [{ date, pct }] are STEPS --
//     each applies once, in date order, compounding on the prior step, flat
//     before the first and between steps (salaryAt). A person with no raise
//     rows keeps the legacy single step: raisePct from effectiveFromYear onward.
//   - Office-cost rows (rent/misc, isOfficeCost) are part of the payroll
//     total: EBITDA = contribution − TOTAL offices cost − employer contributions − OPEX.
//   - Employer contributions (30 Oct 2026): employer_contrib_pct % of the STAFF
//     salaries of the year (office-cost rows carry none).
//   - Member milestones interpolate (interpolateMilestones): before the first
//     milestone the base value, between two milestones linear interpolation,
//     after the last one flat.
//   - OPEX milestones (30 Oct 2026) are a STEP function (stepMilestones), the
//     same semantics as payroll raises: the latest target dated <= the forecast
//     point is in force; $0 before the first target, flat after the last. No ramps.

export const DEFAULT_PHP_PER_USD = 56.5

/**
 * The 9 "Λοιπά OPEX Διοίκησης" forecast categories (30 Oct 2026 split): the
 * Astrolavos lines "Consultants / Audit / Legal" and "Ταξίδια / PR / λοιπά /
 * σύνδεσμοι" are forecast per sub-category. cost_milestones.category = one of these.
 */
export const OPEX_CATEGORIES = [
  'Consultants',
  'Audit',
  'Legal',
  'IT / Computer',
  'Marketing / Διαφήμιση',
  'Ταξίδια',
  'PR',
  'Λοιπά',
  'Σύνδεσμοι',
]
/**
 * Forecast category -> the Astrolavos actuals_rows label that carries its actuals.
 * Several categories share one label: Astrolavos only reports the combined total,
 * so the run-rate hint shown next to them is the SAME combined figure (no split
 * exists in the source data and none is fabricated).
 */
export const OPEX_ACTUALS_LABEL = {
  'Consultants': 'Consultants / Audit / Legal',
  'Audit': 'Consultants / Audit / Legal',
  'Legal': 'Consultants / Audit / Legal',
  'IT / Computer': 'IT / Computer',
  'Marketing / Διαφήμιση': 'Marketing / Διαφήμιση',
  'Ταξίδια': 'Ταξίδια / PR / λοιπά / σύνδεσμοι',
  'PR': 'Ταξίδια / PR / λοιπά / σύνδεσμοι',
  'Λοιπά': 'Ταξίδια / PR / λοιπά / σύνδεσμοι',
  'Σύνδεσμοι': 'Ταξίδια / PR / λοιπά / σύνδεσμοι',
}
export const OPEX_GROUP_KEY = 'adminOpexOther'

/** The categories that share `cat`'s actuals label (incl. itself); length > 1 = combined run-rate. */
export function opexSiblings(cat) {
  const label = OPEX_ACTUALS_LABEL[cat]
  return OPEX_CATEGORIES.filter(c => OPEX_ACTUALS_LABEL[c] === label)
}

/** Calendar year of forecast point y (y = 1 is the current year; quarters are fractional). */
export function calendarYear(baseYear, y) {
  return baseYear + Math.floor(y - 1)
}

// ---------------- milestones ----------------

/** Whole months from the first of `base`'s month to the month of 'YYYY-MM' / 'YYYY-MM-DD'. NaN if unparsable. */
export function monthsFromBase(base, monthStr) {
  if (!monthStr) return NaN
  const [y, m] = String(monthStr).split('-').map(Number)
  if (!y || !m) return NaN
  return (y - base.getFullYear()) * 12 + (m - 1 - base.getMonth())
}

/**
 * Piecewise-linear milestone interpolation (members).
 * points: [{ months, value }] in any order; months <= 0 (or NaN) are ignored --
 * the base value already stands for "now". Returns baseValue when targetMonths <= 0.
 */
export function interpolateMilestones(baseValue, targetMonths, points) {
  if (targetMonths <= 0) return baseValue
  const pts = [{ months: 0, value: baseValue }, ...points
    .filter(p => isFinite(p.months) && p.months > 0)
    .sort((a, b) => a.months - b.months)]
  const last = pts[pts.length - 1]
  if (targetMonths >= last.months) return last.value
  for (let i = 0; i < pts.length - 1; i++) {
    const a = pts[i], b = pts[i + 1]
    if (targetMonths >= a.months && targetMonths <= b.months) {
      const span = b.months - a.months
      const t = span > 0 ? (targetMonths - a.months) / span : 1
      return a.value + (b.value - a.value) * t
    }
  }
  return last.value
}

/**
 * Step function over milestones (OPEX): the value of the LATEST target whose
 * months <= targetMonths; baseValue when no target has been reached yet.
 * Targets in the past or in the current month (months <= 0) count like a raise
 * whose effective date has passed. Flat between targets and after the last one.
 * Points with a NaN month (unparsable date) are ignored.
 */
export function stepMilestones(baseValue, targetMonths, points) {
  let v = baseValue, hit = false, hitMonths = 0
  for (const p of points || []) {
    if (!isFinite(p.months) || p.months > targetMonths) continue
    if (!hit || p.months >= hitMonths) { v = p.value; hitMonths = p.months; hit = true }   // latest reached wins
  }
  return v
}

/** An actuals period's in-period change annualised by calendar days (change × 365 / days, days inclusive). */
export function annualRunRate(change, startIso, endIso) {
  if (!startIso || !endIso) return 0
  const start = new Date(startIso + 'T00:00:00Z'), end = new Date(endIso + 'T00:00:00Z')
  const days = Math.round((end - start) / 86400000) + 1
  if (!isFinite(days) || days <= 0) return 0
  return (Number(change) || 0) * 365 / days
}

// ---------------- payroll ----------------

/**
 * Salary in force at `atIso` ('YYYY-MM-DD'): base, then every raise with
 * date <= atIso applied once in date order, compounding on the previous step.
 * Example: 46,800 → +5% @2027-01-01 → 49,140 → +3% @2029-01-01 → 50,614.20.
 */
export function salaryAt(base, raises, atIso) {
  let s = Number(base) || 0
  const sorted = [...(raises || [])].filter(r => r && r.date).sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0))
  for (const r of sorted) {
    if (r.date > atIso) continue
    s = s * (1 + (Number(r.pct) || 0) / 100)
  }
  return s
}

/**
 * Native-currency salary of a person in a given calendar year, evaluated at 1 January.
 * p: { salary, raise, effFrom, raises? }  (raise in %, effFrom = effective_from_year)
 * With raise rows: the step function. Without: the legacy one-time step.
 */
export function nativeSalaryInYear(p, year) {
  const base = Number(p.salary) || 0
  if (p.raises && p.raises.length) return salaryAt(base, p.raises, `${year}-01-01`)
  const raise = Number(p.raise) || 0
  const effFrom = Number(p.effFrom)
  if (!raise || !Number.isFinite(effFrom) || year < effFrom) return base
  return base * (1 + raise / 100)
}

/** Convert a native amount to USD for the person's office. Athens = USD already. */
export function nativeToUsd(p, native, phpPerUsd = DEFAULT_PHP_PER_USD) {
  if (p.officeCode === 'MANILA') return native / (Number(phpPerUsd) || DEFAULT_PHP_PER_USD)
  return native
}

/** USD salary of a person in a calendar year. */
export function usdSalaryInYear(p, year, phpPerUsd) {
  return nativeToUsd(p, nativeSalaryInYear(p, year), phpPerUsd)
}

/** Total USD payroll (people AND office-cost rows) for a calendar year. */
export function payrollUsdInYear(people, year, phpPerUsd) {
  return people.reduce((s, p) => s + usdSalaryInYear(p, year, phpPerUsd), 0)
}

/** USD salaries of STAFF only (office-cost rows `rent` excluded): the base of the employer contributions. */
export function staffSalariesUsdInYear(people, year, phpPerUsd) {
  return people.filter(p => !p.rent).reduce((s, p) => s + usdSalaryInYear(p, year, phpPerUsd), 0)
}

/** Employer contributions of a calendar year: staff salaries × employer_contrib_pct / 100. */
export function employerContribUsdInYear(people, year, phpPerUsd, contribPct) {
  return staffSalariesUsdInYear(people, year, phpPerUsd) * (Number(contribPct) || 0) / 100
}
