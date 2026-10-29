// node --test — pure payroll math for the Polaris P&L (no Vue / Pinia needed).
// Targets come from the Polaris xlsx model (28 Oct 2026 review).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  calendarYear, nativeSalaryInYear, nativeToUsd, usdSalaryInYear, payrollUsdInYear,
  interpolateMilestones, monthsFromBase, annualRunRate, salaryAt, OPEX_CATEGORIES,
} from '../src/stores/polarisMath.js'

// ---- shared milestone rule + raise steps (29 Oct 2026) — same cases as backend PolarisForecastMathTest ----
const BASE = new Date(2026, 9, 1)   // 1 Oct 2026 (first of the current month)
const close = (a, b, eps = 1e-6) => assert.ok(Math.abs(a - b) < eps, `${a} != ${b}`)

test('monthsFromBase counts whole months from the first of the base month', () => {
  assert.equal(monthsFromBase(BASE, '2026-10'), 0)
  assert.equal(monthsFromBase(BASE, '2026-11-01'), 1)
  assert.equal(monthsFromBase(BASE, '2027-10'), 12)
  assert.equal(monthsFromBase(BASE, '2026-08'), -2)
  assert.ok(Number.isNaN(monthsFromBase(BASE, '')))
})

test('interpolateMilestones: members 16,800 -> 20k@+8 -> 30k@+17: 24,444.44 at 12, 30,000 at 24 (flat)', () => {
  const pts = [{ months: monthsFromBase(BASE, '2027-06'), value: 20000 }, { months: monthsFromBase(BASE, '2028-03'), value: 30000 }]
  assert.deepEqual(pts.map(p => p.months), [8, 17])
  assert.equal(interpolateMilestones(16800, 0, pts), 16800)                        // before first = base
  close(interpolateMilestones(16800, 4, pts), 16800 + (20000 - 16800) * 4 / 8)
  assert.equal(interpolateMilestones(16800, 8, pts), 20000)
  close(interpolateMilestones(16800, 12, pts), 20000 + 10000 * 4 / 9)              // 24,444.44
  assert.equal(interpolateMilestones(16800, 17, pts), 30000)
  assert.equal(interpolateMilestones(16800, 24, pts), 30000)                       // after last = flat
  assert.equal(interpolateMilestones(16800, 24, []), 16800)                        // no milestones = flat base
  assert.equal(interpolateMilestones(16800, 24, [{ months: NaN, value: 1 }, { months: -3, value: 2 }]), 16800)  // ignored
})

test('OPEX hand example: Consultants run-rate 8,857 x 365/300 = 10,776.02; 24k@+8, 36k@+28 -> 26,400 at 12, 33,600 at 24', () => {
  const runRate = annualRunRate(8857, '2026-01-01', '2026-10-27')
  close(runRate, 8857 * 365 / 300)
  close(runRate, 10776.016667, 1e-6)
  const pts = [{ months: monthsFromBase(BASE, '2027-06'), value: 24000 }, { months: monthsFromBase(BASE, '2029-02'), value: 36000 }]
  assert.deepEqual(pts.map(p => p.months), [8, 28])
  assert.equal(interpolateMilestones(runRate, 0, pts), runRate)
  close(interpolateMilestones(runRate, 12, pts), 26400)     // 24,000 + 12,000 x 4/20
  close(interpolateMilestones(runRate, 24, pts), 33600)     // 24,000 + 12,000 x 16/20
  assert.equal(interpolateMilestones(runRate, 48, pts), 36000)
  close(interpolateMilestones(runRate, 4, pts), runRate + (24000 - runRate) / 2)
  // edges
  assert.equal(annualRunRate(100, '', '2026-01-01'), 0)
  assert.equal(annualRunRate(100, '2026-02-01', '2026-01-01'), 0)
  assert.equal(annualRunRate(1200, '2025-01-01', '2025-12-31'), 1200)
  assert.deepEqual(OPEX_CATEGORIES, ['Consultants / Audit / Legal', 'IT / Computer', 'Marketing / Διαφήμιση', 'Ταξίδια / PR / λοιπά / σύνδεσμοι'])
})

test('salaryAt: 46,800 -> +5% @2027 = 49,140 flat -> +3% @2029 = 50,614.20 flat; before 2027 = 46,800', () => {
  const raises = [{ date: '2029-01-01', pct: 3 }, { date: '2027-01-01', pct: 5 }]   // out of order on purpose
  assert.equal(salaryAt(46800, raises, '2026-01-01'), 46800)
  assert.equal(salaryAt(46800, raises, '2026-12-31'), 46800)
  close(salaryAt(46800, raises, '2027-01-01'), 49140)
  close(salaryAt(46800, raises, '2028-06-15'), 49140)
  close(salaryAt(46800, raises, '2029-01-01'), 50614.20)
  close(salaryAt(46800, raises, '2035-01-01'), 50614.20)
  close(salaryAt(46800, raises, '2030-01-01'), 46800 * 1.05 * 1.03)   // each step once, never 1.05 x 1.05
  assert.equal(salaryAt(46800, [], '2040-01-01'), 46800)
  // nativeSalaryInYear uses the steps when present (evaluated at 1 Jan), else the legacy single raise
  const stepped = { salary: 46800, raise: 99, effFrom: 2027, raises }
  close(nativeSalaryInYear(stepped, 2026), 46800)
  close(nativeSalaryInYear(stepped, 2027), 49140)
  close(nativeSalaryInYear(stepped, 2029), 50614.20)
  const legacy = { salary: 10000, raise: 10, effFrom: 2028, raises: [] }
  assert.equal(nativeSalaryInYear(legacy, 2027), 10000)
  assert.equal(nativeSalaryInYear(legacy, 2028), 11000)
})

const PHP = 56.5
const athens = (name, salary, raise = 0, effFrom = 2027) => ({ name, officeCode: 'ATHENS', salary, raise, effFrom })
const manila = (name, salary, raise = 0, effFrom = 2027) => ({ name, officeCode: 'MANILA', salary, raise, effFrom })

const PEOPLE = [
  athens('Γραφείο Αθηνών', 10000), athens('General Manager', 62400), athens('Business Director', 62400),
  athens('Accounting Manager', 46800), athens('Marketing Manager', 23400), athens('IT & Developer', 46800),
  athens('Legal & Compliance Mgr', 20000),
  manila('Γραφείο Μανίλα', 1130000), manila('Pia Guirao', 1130000, 5), manila('Carmen Soriano', 2034000),
  manila('Margilyn Nalupano', 678000, 41.7), manila('Victoria Lawless', 678000, 41.7),
  manila('Stella Ducay', 678000), manila('Jinky', 678000),
]

test('Part B: Athens salary is USD — no EUR conversion', () => {
  assert.equal(usdSalaryInYear(athens('Accounting Manager', 46800), 2026, PHP), 46800)   // not 50544
  assert.equal(nativeToUsd({ officeCode: 'ATHENS' }, 271800, PHP), 271800)
  const athensTotal = payrollUsdInYear(PEOPLE.filter(p => p.officeCode === 'ATHENS'), 2026, PHP)
  assert.equal(athensTotal, 271800)
})

test('Manila converts PHP ÷ fx_php_per_usd', () => {
  assert.equal(usdSalaryInYear(manila('Carmen Soriano', 2034000), 2026, PHP), 36000)
  assert.equal(usdSalaryInYear(manila('Pia Guirao', 1130000, 5), 2026, PHP), 20000)
  assert.equal(usdSalaryInYear(manila('Jinky', 678000), 2026, PHP), 12000)
})

test('Part C: raise is a one-time step at effective_from_year, flat afterwards', () => {
  const pia = manila('Pia Guirao', 1130000, 5, 2027)
  assert.equal(nativeSalaryInYear(pia, 2026), 1130000)
  assert.equal(nativeSalaryInYear(pia, 2027), 1186500)
  assert.equal(nativeSalaryInYear(pia, 2028), 1186500)   // not 1245825 (compounded)
  assert.equal(nativeSalaryInYear(pia, 2030), 1186500)
  assert.equal(usdSalaryInYear(pia, 2027, PHP), 21000)

  const marg = manila('Margilyn Nalupano', 678000, 41.7, 2027)
  assert.equal(nativeSalaryInYear(marg, 2026), 678000)
  assert.ok(Math.abs(nativeSalaryInYear(marg, 2027) - 960726) < 1e-6)
  assert.ok(Math.abs(nativeSalaryInYear(marg, 2031) - 960726) < 1e-6)   // flat, never 2,733,437
  assert.ok(Math.abs(usdSalaryInYear(marg, 2027, PHP) - 17004) < 1e-6)

  for (const p of [manila('Carmen Soriano', 2034000), manila('Stella Ducay', 678000), manila('Jinky', 678000)]) {
    for (const y of [2026, 2027, 2028, 2031]) assert.equal(nativeSalaryInYear(p, y), p.salary)
  }
})

test('calendarYear maps forecast point y (1 = this year, quarters fractional)', () => {
  assert.equal(calendarYear(2026, 1), 2026)
  assert.equal(calendarYear(2026, 1.75), 2026)
  assert.equal(calendarYear(2026, 2), 2027)
  assert.equal(calendarYear(2026, 5), 2030)
})

test('Part C3: total offices cost includes office-cost rows; matches the xlsx', () => {
  const y2026 = payrollUsdInYear(PEOPLE, 2026, PHP)
  const y2027 = payrollUsdInYear(PEOPLE, 2027, PHP)
  assert.equal(y2026, 395800)                                  // 271,800 + 124,000
  assert.ok(Math.abs(y2027 - 406808) < 1e-6)                   // +1,000 Pia, +5,004 ×2 clerks
  const contribution = 410457.60
  assert.ok(Math.abs((contribution - y2026) - 14657.60) < 1e-6)
  assert.ok(Math.abs((contribution - y2027) - 3649.60) < 1e-6)  // xlsx says 3,657.60 — it rounds the clerks to $17,000
})
