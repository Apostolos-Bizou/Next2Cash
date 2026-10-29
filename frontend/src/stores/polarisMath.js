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
//     total: EBITDA = contribution − TOTAL offices cost − OPEX.
//   - Milestones (members AND every OPEX category) share ONE rule
//     (interpolateMilestones): before the first milestone the base value,
//     between two milestones linear interpolation, after the last one flat.

export const DEFAULT_PHP_PER_USD = 56.5

/** The "Λοιπά OPEX Διοίκησης" actuals labels that get date-based milestones (29 Oct 2026 decision). */
export const OPEX_CATEGORIES = [
  'Consultants / Audit / Legal',
  'IT / Computer',
  'Marketing / Διαφήμιση',
  'Ταξίδια / PR / λοιπά / σύνδεσμοι',
]
export const OPEX_GROUP_KEY = 'adminOpexOther'

/** Calendar year of forecast point y (y = 1 is the current year; quarters are fractional). */
export function calendarYear(baseYear, y) {
  return baseYear + Math.floor(y - 1)
}

// ---------------- milestones (shared rule) ----------------

/** Whole months from the first of `base`'s month to the month of 'YYYY-MM' / 'YYYY-MM-DD'. NaN if unparsable. */
export function monthsFromBase(base, monthStr) {
  if (!monthStr) return NaN
  const [y, m] = String(monthStr).split('-').map(Number)
  if (!y || !m) return NaN
  return (y - base.getFullYear()) * 12 + (m - 1 - base.getMonth())
}

/**
 * Piecewise-linear milestone interpolation.
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
