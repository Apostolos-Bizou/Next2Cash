// polarisMath.js — pure payroll math for the Polaris P&L (no Vue, no store),
// so it can be unit-tested with plain node:test and reused by the store.
//
// Source of truth: the Polaris xlsx model (28 Oct 2026 review).
//   - Athens salaries are entered and paid in USD: NO FX conversion.
//   - Manila salaries are entered in PHP and converted at fx_php_per_usd.
//   - raisePct is a ONE-TIME step that applies from effectiveFromYear onward
//     (flat afterwards) — it does not compound year after year.
//   - Office-cost rows (rent/misc, isOfficeCost) are part of the payroll
//     total: EBITDA = contribution − TOTAL offices cost.

export const DEFAULT_PHP_PER_USD = 56.5

/** Calendar year of forecast point y (y = 1 is the current year; quarters are fractional). */
export function calendarYear(baseYear, y) {
  return baseYear + Math.floor(y - 1)
}

/**
 * Native-currency salary of a person in a given calendar year.
 * p: { salary, raise, effFrom }  (raise in %, effFrom = effective_from_year)
 */
export function nativeSalaryInYear(p, year) {
  const base = Number(p.salary) || 0
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
