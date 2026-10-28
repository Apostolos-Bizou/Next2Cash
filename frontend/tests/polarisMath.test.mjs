// node --test — pure payroll math for the Polaris P&L (no Vue / Pinia needed).
// Targets come from the Polaris xlsx model (28 Oct 2026 review).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  calendarYear, nativeSalaryInYear, nativeToUsd, usdSalaryInYear, payrollUsdInYear,
} from '../src/stores/polarisMath.js'

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
