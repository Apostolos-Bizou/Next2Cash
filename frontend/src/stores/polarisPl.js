// polarisPl.js — shared state + P&L math for the 3 Polaris P&L views.
//
// Phase 2 (Oct 2026) ported the math 1:1 from docs/polaris-mockup-reference.html.
// Phase 3 (Oct 2026) wires it to the backend: people / milestones /
// assumptions / actuals come from /api/polaris/*, and the role comes from
// GET /api/polaris/my-role (entity_roles) instead of the demo "ΠΡΟΒΟΛΗ ΩΣ"
// toggle. No staff names, salaries or trial-balance amounts live in this file
// any more -- only the Αστρολάβος category taxonomy (titles / codes / labels).
//
// What each role can load is decided by the backend (403 otherwise):
//   admin  -> people, milestones, assumptions, actuals (all writes)
//   editor -> people (read), actuals (read + write)
//   viewer -> actuals (read), payroll summary (totals per office, no names)

import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import {
  fetchMyRole, fetchPayrollSummary, peopleApi, milestonesApi, assumptionsApi, actualsApi, costMilestonesApi, raisesApi
} from '@/api/polaris'
import {
  calendarYear, nativeSalaryInYear, nativeToUsd, usdSalaryInYear, salaryAt,
  interpolateMilestones, monthsFromBase, annualRunRate, OPEX_CATEGORIES, OPEX_GROUP_KEY,
} from './polarisMath'
export { OPEX_CATEGORIES }

// ---------------- formatting (identical to the mockup) ----------------
export function fmtUSD(n) {
  const s = Math.round(n).toLocaleString('el-GR')
  return (n < 0 ? '-$' : '$') + s.replace('-', '')
}
export function fmtUSD2(n) {
  const s = n.toLocaleString('el-GR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return (n < 0 ? '-$' : '$') + s.replace('-', '')
}
export function fmtPct(n) { return n.toFixed(1).replace('.', ',') + '%' }
export function fmtInt(n) { return Math.round(n).toLocaleString('el-GR') }

export const EXPENSE_KEYS = ['opsCosts', 'payroll', 'contributions', 'taxes', 'adminOpexOther', 'other']

export function rowEnd(row) { return row.begin + row.change }

export function groupSum3(group) {
  return group.rows.reduce((s, r) => ({
    begin: s.begin + r.begin,
    change: s.change + r.change,
    end: s.end + rowEnd(r)
  }), { begin: 0, change: 0, end: 0 })
}

// ---------------- offices (backend stores codes, UI shows Greek labels) ----------------
export const OFFICES = [
  { code: 'ATHENS', label: 'Αθήνα' },
  { code: 'MANILA', label: 'Μανίλα' },
]
const OFFICE_LABEL = Object.fromEntries(OFFICES.map(o => [o.code, o.label]))

// ---------------- Αστρολάβος trial-balance taxonomy (no amounts) ----------------
// Same groups / codes / line labels as the mockup. Amounts come from
// actuals_rows (group_key + label + period); a taxonomy line without a row for
// the active period shows 0 and is created on its first edit.
const ACTUALS_TAXONOMY = [
  { key: 'revenue', title: 'Έσοδα', code: 'κωδ. 91xxx', isRevenue: true, labels: [
    'Membership fees (όλα τα policy-years)', 'Admin fees AMAPHIL', 'Plan management fees', 'Λοιπά έσοδα (τόκοι κ.ά.)'] },
  { key: 'opsCosts', title: 'Κόστη λειτουργίας', code: 'κωδ. 92xxx', isRevenue: false, labels: [
    'Κόστη membership / admin / claims', 'Τραπεζικά, ΦΠΑ, λοιπά λειτουργικά'] },
  { key: 'payroll', title: 'Μισθοδοσία', code: 'κωδ. 96+97 · wages/bonus', isRevenue: false, labels: [
    'Μισθοί & bonus (Κύπρος + Φιλιππίνες)'] },
  { key: 'contributions', title: 'Εισφορές', code: 'κωδ. 96+97 · wages contributions', isRevenue: false, labels: [
    'Εργοδοτικές εισφορές'] },
  { key: 'taxes', title: 'Φόροι', code: 'κωδ. 96+97 · taxes/fees', isRevenue: false, labels: [
    'Φόροι / τέλη'] },
  { key: 'adminOpexOther', title: 'Λοιπά OPEX Διοίκησης', code: 'κωδ. 96+97 · υπόλοιπο', isRevenue: false, labels: [
    'Ενοίκιο / utilities', 'IT / Computer', 'Marketing / Διαφήμιση', 'Consultants / Audit / Legal',
    'Ταξίδια / PR / λοιπά / σύνδεσμοι', 'Medical / 3rd-party (Φιλιππίνες)'] },
  { key: 'other', title: 'Λοιπά', code: 'κωδ. 99xxx', isRevenue: false, labels: [
    'Αναπροσαρμογές / συναλλαγματικές / rounding'] },
]

// ---------------- forecast_assumptions keys (UI field -> stored key) ----------------
export const ASSUMPTION_KEYS = {
  members: 'members',
  memberGrowth: 'member_growth_pct',
  netFee: 'network_fee',
  monthlyFee: 'monthly_fee',
  auditRate: 'audit_fee_rate',
  dentalFee: 'dental_fee',
  netPay: 'network_payable',
  auditPay: 'audit_payable_rate',
  dentalPay: 'dental_payable',
  phpPerUsd: 'fx_php_per_usd',
  usdPerEur: 'fx_usd_per_eur',   // stored, but no longer used: Athens payroll is in USD (28 Oct 2026)
  // member-forecast mode of the Forecast page, persisted so the server-side
  // Dashboard summary (GET /forecast/summary) matches the admin's view:
  // 1 = 'milestones' (Χειροκίνητοι στόχοι), 0 / absent = 'growth' (Ποσοστό %)
  membersMode: 'members_mode',
}
const MEMBERS_MODE_VALUE = { growth: 0, milestones: 1 }
const membersModeFromValue = v => Number(v) >= 1 ? 'milestones' : 'growth'

// ---------------- period helpers ("dd/mm/yyyy – dd/mm/yyyy" <-> ISO dates) ----------------
const pad = n => String(n).padStart(2, '0')
function isoToDmy(iso) { const [y, m, d] = iso.split('-'); return `${d}/${m}/${y}` }
function dmyToIso(dmy) {
  const m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec((dmy || '').trim())
  if (!m) return null
  const iso = `${m[3]}-${pad(m[2])}-${pad(m[1])}`
  return isNaN(new Date(iso).getTime()) ? null : iso
}
function parsePeriod(text) {
  const parts = (text || '').split(/\s*[–-]\s*/).map(s => s.trim()).filter(Boolean)
  if (parts.length < 2) return null
  const start = dmyToIso(parts[0]), end = dmyToIso(parts[parts.length - 1])
  return start && end && end >= start ? { start, end } : null
}
function periodText(p) { return `${isoToDmy(p.start)} – ${isoToDmy(p.end)}` }
function defaultPeriod() {
  const now = new Date()
  return { start: `${now.getFullYear()}-01-01`, end: `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}` }
}

// "today" reference point for the milestone timeline (mockup: MILESTONE_BASE)
const MILESTONE_BASE = new Date()
MILESTONE_BASE.setDate(1)

function monthsFromNow(monthStr) { return monthsFromBase(MILESTONE_BASE, monthStr) }

function readLs(key) {
  try { return localStorage.getItem(key) } catch (e) { return null }
}
function writeLs(key, value) {
  try { localStorage.setItem(key, value) } catch (e) { /* storage blocked */ }
}

// mockup reads every input as parseFloat(el.value) || fallback
const num = (v, fallback = 0) => parseFloat(v) || fallback

// ---------------- API <-> UI shapes ----------------
function personFromApi(p) {
  return {
    id: p.id,
    officeCode: p.office,
    office: OFFICE_LABEL[p.office] || p.office,
    name: p.name,
    role: p.roleTitle || '—',
    salary: Number(p.salary) || 0,
    raise: Number(p.raisePct) || 0,
    effFrom: p.effectiveFromYear,
    rent: !!p.isOfficeCost,
    raises: [],   // [{ id, date: 'YYYY-MM-DD', pct }] from payroll_person_raises, sorted by date
  }
}
const PERSON_FIELD_TO_API = { salary: 'salary', raise: 'raisePct', effFrom: 'effectiveFromYear', name: 'name', role: 'roleTitle', officeCode: 'office', rent: 'isOfficeCost' }

function raiseFromApi(r) {
  return { id: r.id, personId: r.payrollPersonId, date: (r.effectiveDate || '').slice(0, 10), pct: Number(r.raisePct) || 0 }
}
// the legacy ΑΥΞΗΣΗ / ΙΣΧΥΣ ΑΠΟ columns mirror the FIRST raise step when there is one
function syncLegacyFromRaises(p) {
  p.raises.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0))
  if (p.raises.length) { p.raise = p.raises[0].pct; p.effFrom = Number(p.raises[0].date.slice(0, 4)) }
}

function milestoneFromApi(m) {
  return { id: m.id, month: (m.targetMonth || '').slice(0, 7), members: Number(m.membersTarget) || 0 }
}
function costMilestoneFromApi(c) {
  return { id: c.id, category: c.category, month: (c.targetDate || '').slice(0, 7), value: Number(c.targetValue) || 0 }
}
function actualFromApi(r) {
  return {
    id: r.id, groupKey: r.groupKey, label: r.label,
    begin: Number(r.beginAmount) || 0, change: Number(r.changeAmount) || 0,
    periodStart: r.periodStart, periodEnd: r.periodEnd,
  }
}

function apiError(e) {
  const status = e?.response?.status
  const msg = e?.response?.data?.error || e?.response?.data?.message || e?.message || 'Άγνωστο σφάλμα'
  return status ? `HTTP ${status}: ${msg}` : msg
}

export const usePolarisPlStore = defineStore('polarisPl', () => {
  // ---------------- state ----------------
  const role = ref(null)          // 'admin' | 'editor' | 'viewer' | null (= no Polaris access)
  const loading = ref(false)
  const loaded = ref(false)
  const loadError = ref(null)
  const saveError = ref(null)

  const horizon = ref(parseInt(readLs('pl_horizon')) || 1)
  const memberForecastMode = ref(readLs('pl_memberMode') || 'growth') // 'growth' | 'milestones'
  const granularity = ref(readLs('pl_granularity') || 'year')        // 'year' | 'quarter'

  // Παραδοχές — '' until loaded from forecast_assumptions
  const inputs = reactive(Object.fromEntries(Object.keys(ASSUMPTION_KEYS).map(k => [k, ''])))
  const assumptionIds = reactive({})   // UI field -> forecast_assumptions.id

  const people = ref([])
  const payrollSummary = ref([])   // viewer only: [{ officeCode, office, salaryTotal, headcount }]
  const milestones = ref([])
  const costMilestones = ref([])   // admin/editor: [{ id, category, month: 'YYYY-MM', value (annual $) }]
  const actualsRows = ref([])
  const actPeriod = ref(periodText(defaultPeriod()))

  // ---------------- role ----------------
  const hasAccess = computed(() => role.value !== null)
  const isAdmin = computed(() => role.value === 'admin')
  const canEdit = computed(() => role.value === 'admin' || role.value === 'editor')

  function setHorizon(y) { horizon.value = y; writeLs('pl_horizon', y) }
  function setGranularity(g) { granularity.value = g; writeLs('pl_granularity', g) }
  // The server value is the source of truth (it drives the Dashboard summary for
  // every role); localStorage is only a cache for the first paint before load().
  function setMemberForecastMode(mode) {
    memberForecastMode.value = mode
    writeLs('pl_memberMode', mode)
    if (role.value === 'admin') {
      inputs.membersMode = MEMBERS_MODE_VALUE[mode] ?? 0
      return saveAssumption('membersMode')
    }
  }

  // ---------------- load ----------------
  // keepSaveError: the resync after a failed write keeps its error visible;
  // any other reload (navigation, entity change) clears it.
  async function load({ keepSaveError = false } = {}) {
    loading.value = true
    loadError.value = null
    if (!keepSaveError) saveError.value = null
    try {
      try {
        role.value = await fetchMyRole()
      } catch (e) {
        if (e?.response?.status === 403) { role.value = null; resetData(); return }
        throw e
      }
      const admin = role.value === 'admin'
      const editor = role.value === 'admin' || role.value === 'editor'
      const [ppl, ms, asm, act, sum, rs, cms] = await Promise.all([
        editor ? peopleApi.list() : [],
        admin ? milestonesApi.list() : [],
        admin ? assumptionsApi.list() : [],
        actualsApi.list(),                        // any Polaris role
        editor ? [] : fetchPayrollSummary(),      // viewer: aggregate instead of people
        editor ? raisesApi.list() : [],           // raise steps (admin+editor, same tier as people)
        editor ? costMilestonesApi.list() : [],   // OPEX milestones (admin+editor)
      ])
      people.value = ppl.map(personFromApi)
      for (const r of rs.map(raiseFromApi)) {
        const p = people.value.find(x => x.id === r.personId)
        if (p) p.raises.push(r)
      }
      people.value.forEach(syncLegacyFromRaises)
      costMilestones.value = cms.map(costMilestoneFromApi)
      payrollSummary.value = sum.map(s => ({
        officeCode: s.office, office: OFFICE_LABEL[s.office] || s.office,
        salaryTotal: Number(s.salaryTotal) || 0, headcount: Number(s.headcount) || 0,
      }))
      milestones.value = ms.map(milestoneFromApi)
      for (const k of Object.keys(ASSUMPTION_KEYS)) { inputs[k] = ''; delete assumptionIds[k] }
      for (const a of asm) {
        const field = Object.keys(ASSUMPTION_KEYS).find(k => ASSUMPTION_KEYS[k] === a.key)
        if (field) { inputs[field] = Number(a.value); assumptionIds[field] = a.id }
      }
      // persisted member-forecast mode wins over the localStorage cache (admin only
      // loads assumptions; other roles keep their cached/default value, which is
      // irrelevant to them since the Dashboard summary is computed server-side)
      if (inputs.membersMode !== '') {
        memberForecastMode.value = membersModeFromValue(inputs.membersMode)
        writeLs('pl_memberMode', memberForecastMode.value)
      }
      actualsRows.value = act.map(actualFromApi)
      // active period = the latest one that has rows, else 1 Jan – today
      const latest = actualsRows.value.reduce((best, r) => (!best || r.periodEnd > best.end) ? { start: r.periodStart, end: r.periodEnd } : best, null)
      actPeriod.value = periodText(latest || defaultPeriod())
      loaded.value = true
    } catch (e) {
      loadError.value = apiError(e)
    } finally {
      loading.value = false
    }
  }
  function resetData() {
    costMilestones.value = []
    people.value = []; payrollSummary.value = []; milestones.value = []; actualsRows.value = []
    for (const k of Object.keys(ASSUMPTION_KEYS)) { inputs[k] = ''; delete assumptionIds[k] }
    loaded.value = true
  }

  // every write goes through here: on failure show the error and resync from the server
  async function persist(fn) {
    saveError.value = null
    try { return await fn() } catch (e) { saveError.value = apiError(e); await load({ keepSaveError: true }); return null }
  }

  // ---------------- FX / raises (pure math in polarisMath.js) ----------------
  // Αθήνα is paid in USD (no conversion); Μανίλα in PHP ÷ fx_php_per_usd.
  // raise% is a one-time step from effective_from_year, flat afterwards.
  // Forecast year y = 1 is the current calendar year.
  const FORECAST_BASE_YEAR = new Date().getFullYear()
  function phpToUsd(php) { return php / num(inputs.phpPerUsd, 56.5) }
  function isManila(p) { return p.officeCode === 'MANILA' }

  // native-currency salary at forecast point y (₱ Μανίλα, $ Αθήνα)
  function projectedSalary(p, years) {
    return nativeSalaryInYear(p, calendarYear(FORECAST_BASE_YEAR, years))
  }
  // $-equivalent of a person's salary at forecast point y
  function salaryUSD(p, years) {
    return usdSalaryInYear(p, calendarYear(FORECAST_BASE_YEAR, years), num(inputs.phpPerUsd, 56.5))
  }
  // native-currency salary in force at an ISO date (raise steps table)
  function salaryAtDate(p, iso) { return salaryAt(p.salary, p.raises, iso) }

  // ---------------- payroll edits (admin) ----------------
  function updatePerson(idx, field, val) {
    const p = people.value[idx]
    if (!p) return
    let value
    if (field === 'name' || field === 'role') value = String(val).trim()
    else if (field === 'officeCode') value = val
    else if (field === 'rent') value = !!val
    else value = parseFloat(val) || 0
    if (field === 'name' && !value) return  // backend: name is required
    p[field] = value
    if (field === 'officeCode') p.office = OFFICE_LABEL[value] || value
    const apiValue = field === 'role' && value === '—' ? '' : value
    const legacy = persist(() => peopleApi.update(p.id, { [PERSON_FIELD_TO_API[field]]: apiValue }))
    // the quick-shortcut columns also write the first raise step (the calc's source of truth)
    if (field === 'raise' || field === 'effFrom') return legacy.then(() => syncFirstRaiseFromLegacy(p))
    return legacy
  }
  async function removePerson(idx) {
    if (role.value !== 'admin') return
    const p = people.value[idx]
    if (!p) return
    const ok = await persist(async () => { await peopleApi.remove(p.id); return true })
    if (ok) people.value.splice(people.value.indexOf(p), 1)
  }
  async function addPerson() {
    if (role.value !== 'admin') return
    const created = await persist(() => peopleApi.create({
      office: 'ATHENS', name: 'Νέο μέλος προσωπικού', roleTitle: '', salary: 0, raisePct: 0,
      effectiveFromYear: new Date().getFullYear() + 1, isOfficeCost: false,
    }))
    if (created) people.value.push(personFromApi(created))
  }

  // ---------------- member milestones (manual forecast, admin) ----------------
  // the ONE milestone rule (polarisMath.interpolateMilestones), shared with the OPEX categories
  function membersAtMonths(members0, targetMonths) {
    const pts = milestones.value.map(m => ({ months: monthsFromNow(m.month), value: Number(m.members) || 0 }))
    return interpolateMilestones(members0, targetMonths, pts)
  }

  // ---------------- OPEX milestones (Πρόβλεψη Λειτουργικών Εξόδων, admin) ----------------
  // Baseline is $0 (decision 29 Oct 2026): a category without targets adds nothing to
  // EBITDA until its first target. The current run-rate below is shown as a HINT and used
  // as the default value of a new target, so the admin can anchor "today" with one click.
  // run-rate = in-period change of the category's LATEST actuals period, annualised by days
  const opexRunRates = computed(() => Object.fromEntries(OPEX_CATEGORIES.map(cat => {
    const mine = actualsRows.value.filter(r => r.groupKey === OPEX_GROUP_KEY && r.label === cat && r.periodEnd)
    if (!mine.length) return [cat, 0]
    const latestEnd = mine.reduce((m, r) => (r.periodEnd > m ? r.periodEnd : m), mine[0].periodEnd)
    return [cat, mine.filter(r => r.periodEnd === latestEnd).reduce((s, r) => s + annualRunRate(r.change, r.periodStart, r.periodEnd), 0)]
  })))
  function costMilestonesOf(cat) { return costMilestones.value.filter(c => c.category === cat) }
  // annual $ of one category at forecast point y ((y−1)·12 months out), from the $0 baseline
  function opexCategoryAtYear(cat, y) {
    const pts = costMilestonesOf(cat).map(c => ({ months: monthsFromNow(c.month), value: Number(c.value) || 0 }))
    return interpolateMilestones(0, (y - 1) * 12, pts)
  }
  function opexAtYear(y) { return OPEX_CATEGORIES.reduce((s, cat) => s + opexCategoryAtYear(cat, y), 0) }

  function updateCostMilestone(cm, field, val) {
    if (role.value !== 'admin') return
    if (field === 'value') {
      cm.value = parseFloat(val) || 0
      return persist(() => costMilestonesApi.update(cm.id, { targetValue: cm.value }))
    }
    if (!val) return  // backend: targetDate is required -- keep the stored month
    cm.month = val
    return persist(() => costMilestonesApi.update(cm.id, { targetDate: `${val}-01` }))
  }
  async function removeCostMilestone(cm) {
    if (role.value !== 'admin') return
    const ok = await persist(async () => { await costMilestonesApi.remove(cm.id); return true })
    if (ok) costMilestones.value.splice(costMilestones.value.indexOf(cm), 1)
  }
  async function addCostMilestone(category) {
    if (role.value !== 'admin') return
    // default: next month at the category's current run-rate -- i.e. the "anchor today" target the intro banner describes
    const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() + 1)
    const created = await persist(() => costMilestonesApi.create({
      category, targetDate: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`,
      targetValue: Math.round(opexRunRates.value[category] || 0),
    }))
    if (created) costMilestones.value.push(costMilestoneFromApi(created))
  }

  // ---------------- payroll raise steps (admin) ----------------
  function updateRaise(p, r, field, val) {
    if (role.value !== 'admin') return
    if (field === 'pct') {
      r.pct = parseFloat(val) || 0
      syncLegacyFromRaises(p)
      return persist(() => raisesApi.update(r.id, { raisePct: r.pct }))
    }
    if (!val) return  // backend: effectiveDate is required -- keep the stored date
    r.date = val
    syncLegacyFromRaises(p)
    return persist(() => raisesApi.update(r.id, { effectiveDate: val }))
  }
  async function removeRaise(p, r) {
    if (role.value !== 'admin') return
    const ok = await persist(async () => { await raisesApi.remove(r.id); return true })
    if (ok) { p.raises.splice(p.raises.indexOf(r), 1); syncLegacyFromRaises(p) }
  }
  async function addRaise(p) {
    if (role.value !== 'admin') return
    // default: 1 January after the last step (or next year), 0% -- the admin fills in the rate
    const lastYear = p.raises.length ? Number(p.raises[p.raises.length - 1].date.slice(0, 4)) : new Date().getFullYear()
    const created = await persist(() => raisesApi.create(p.id, { effectiveDate: `${lastYear + 1}-01-01`, raisePct: 0 }))
    if (created) { p.raises.push(raiseFromApi(created)); syncLegacyFromRaises(p) }
  }
  // the legacy ΑΥΞΗΣΗ / ΙΣΧΥΣ ΑΠΟ columns are a shortcut that writes the FIRST raise step
  async function syncFirstRaiseFromLegacy(p) {
    if (!p.raise) return   // 0% legacy raise = no step to store
    const body = { effectiveDate: `${p.effFrom}-01-01`, raisePct: p.raise }
    if (p.raises.length) {
      const first = p.raises[0]
      first.date = body.effectiveDate; first.pct = body.raisePct
      await persist(() => raisesApi.update(first.id, body))
    } else {
      const created = await persist(() => raisesApi.create(p.id, body))
      if (created) p.raises.push(raiseFromApi(created))
    }
    syncLegacyFromRaises(p)
  }
  function updateMilestone(idx, field, val) {
    if (role.value !== 'admin') return
    const m = milestones.value[idx]
    if (!m) return
    if (field === 'members') {
      m.members = parseFloat(val) || 0
      return persist(() => milestonesApi.update(m.id, { membersTarget: Math.round(m.members) }))
    }
    if (!val) return  // backend: targetMonth is required -- keep the stored month
    m.month = val
    return persist(() => milestonesApi.update(m.id, { targetMonth: `${val}-01` }))
  }
  async function removeMilestone(idx) {
    if (role.value !== 'admin') return
    const m = milestones.value[idx]
    if (!m) return
    const ok = await persist(async () => { await milestonesApi.remove(m.id); return true })
    if (ok) milestones.value.splice(milestones.value.indexOf(m), 1)
  }
  async function addMilestone() {
    if (role.value !== 'admin') return
    // the mockup added an empty month; the table requires one, so default to next month
    const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() + 1)
    const created = await persist(() => milestonesApi.create({
      targetMonth: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`, membersTarget: Math.round(num(inputs.members)),
    }))
    if (created) milestones.value.push(milestoneFromApi(created))
  }

  // ---------------- assumptions (admin) ----------------
  // inputs update live through v-model (recalc on every keystroke, like the mockup);
  // this persists the field on change.
  function saveAssumption(field) {
    if (role.value !== 'admin') return
    const raw = inputs[field]
    if (raw === '' || raw === null || isNaN(Number(raw))) return
    const value = Number(raw)
    const id = assumptionIds[field]
    return persist(async () => {
      if (id) return assumptionsApi.update(id, { value })
      const created = await assumptionsApi.create({ key: ASSUMPTION_KEYS[field], value })
      if (created) assumptionIds[field] = created.id
      return created
    })
  }

  // ---------------- actuals (Αστρολάβος trial balance) ----------------
  // display dates come straight from the text field, like the mockup
  const periodDates = computed(() => {
    const raw = actPeriod.value || ''
    const parts = raw.split(/\s*[–-]\s*/).map(s => s.trim()).filter(Boolean)
    if (parts.length >= 2) return { start: parts[0], end: parts[parts.length - 1] }
    return { start: '—', end: '—' }
  })
  const activePeriod = computed(() => parsePeriod(actPeriod.value))

  function rowsFor(groupKey) {
    const p = activePeriod.value
    if (!p) return []
    return actualsRows.value.filter(r => r.groupKey === groupKey && r.periodStart === p.start && r.periodEnd === p.end)
  }

  // same shape the mockup's actualsData had: { key: { title, code, isRevenue, rows: [{label, begin, change}] } }
  const actualsData = computed(() => Object.fromEntries(ACTUALS_TAXONOMY.map(g => {
    const stored = rowsFor(g.key)
    const rows = g.labels.map(label => {
      const r = stored.find(s => s.label === label)
      return { label, begin: r ? r.begin : 0, change: r ? r.change : 0 }
    })
    // rows stored under this group with a label outside the taxonomy still count
    for (const r of stored) if (!g.labels.includes(r.label)) rows.push({ label: r.label, begin: r.begin, change: r.change })
    return [g.key, { title: g.title, code: g.code, isRevenue: g.isRevenue, rows }]
  })))

  function updateActualAmount(groupKey, idx, field, val) {
    if (!canEdit.value) return
    const p = activePeriod.value
    if (!p) { saveError.value = 'Μη έγκυρη περίοδος — χρησιμοποίησε τη μορφή ηη/μμ/εεεε – ηη/μμ/εεεε'; return }
    const row = actualsData.value[groupKey].rows[idx]
    if (!row) return
    const amount = parseFloat(val) || 0
    const existing = rowsFor(groupKey).find(r => r.label === row.label)
    if (existing) {
      existing[field] = amount
      return persist(() => actualsApi.update(existing.id, { [field === 'begin' ? 'beginAmount' : 'changeAmount']: amount }))
    }
    return persist(async () => {
      const created = await actualsApi.create({
        groupKey, label: row.label,
        beginAmount: field === 'begin' ? amount : row.begin,
        changeAmount: field === 'change' ? amount : row.change,
        periodStart: p.start, periodEnd: p.end,
      })
      if (created) actualsRows.value.push(actualFromApi(created))
      return created
    })
  }

  const revSum = computed(() => groupSum3(actualsData.value.revenue))
  const expSum = computed(() => EXPENSE_KEYS.reduce((s, k) => {
    const g = groupSum3(actualsData.value[k])
    return { begin: s.begin + g.begin, change: s.change + g.change, end: s.end + g.end }
  }, { begin: 0, change: 0, end: 0 }))
  const netSum = computed(() => ({
    begin: revSum.value.begin - expSum.value.begin,
    change: revSum.value.change - expSum.value.change,
    end: revSum.value.end - expSum.value.end
  }))

  // ---------------- core P&L math (mirrors the Excel model) ----------------
  const claims = 35, netVat = 0.12, auditVat = 0.12, dentalVat = 0.12

  // member count at forecast year y (y=1 is "now")
  function membersAtYear(y) {
    const members0 = num(inputs.members)
    if (memberForecastMode.value === 'milestones') return membersAtMonths(members0, (y - 1) * 12)
    return members0 * Math.pow(1 + num(inputs.memberGrowth) / 100, y - 1)
  }

  function pnlAtYear(y) {
    const members = membersAtYear(y)
    const netFee = num(inputs.netFee), monthlyFee = num(inputs.monthlyFee), auditRate = num(inputs.auditRate)
    const dentalFee = num(inputs.dentalFee), netPay = num(inputs.netPay), auditPay = num(inputs.auditPay)
    const dentalPay = num(inputs.dentalPay)
    // the whole P&L is consolidated in USD; payroll (people + office-cost rows)
    // converted per person via salaryUSD(), OPEX from the category milestones,
    // so EBITDA = contribution − TOTAL offices cost − OPEX (same as the server summary)
    const revenue = members * netFee + members * monthlyFee * 12 + members * auditRate * claims + members * dentalFee
    const thirdParty = members * netPay * (1 + netVat) + members * claims * auditPay * (1 + auditVat) + members * dentalPay * (1 + dentalVat)
    const contribution = revenue - thirdParty
    const payroll = people.value.reduce((s, p) => s + salaryUSD(p, y), 0)
    const opex = opexAtYear(y)
    const ebitda = contribution - payroll - opex
    return { members, revenue, thirdParty, contribution, payroll, opex, ebitda }
  }

  // break-even members at the selected horizon's fixed-cost base (payroll + OPEX) & per-member contribution
  const cur = computed(() => pnlAtYear(horizon.value))
  const lastYear1Ebitda = computed(() => pnlAtYear(1).ebitda)
  const breakEvenMembers = computed(() => {
    const c = cur.value
    const contribPerMember = c.members ? c.contribution / c.members : 0
    return contribPerMember > 0 ? (c.payroll + c.opex) / contribPerMember : NaN
  })
  const breakEvenDistance = computed(() => cur.value.members - breakEvenMembers.value)
  const ebitdaMargin = computed(() => cur.value.revenue ? (cur.value.ebitda / cur.value.revenue * 100) : 0)

  // payroll aggregates (table footer)
  const athensTotal = computed(() => people.value.filter(p => p.officeCode === 'ATHENS').reduce((s, p) => s + salaryUSD(p, horizon.value), 0))
  const manilaTotal = computed(() => people.value.filter(p => p.officeCode === 'MANILA').reduce((s, p) => s + salaryUSD(p, horizon.value), 0))
  const staffCount = computed(() => people.value.filter(p => !p.rent).length)

  // viewer aggregate card (payroll/summary): year-1 base salaries per office in $
  // (Αθήνα already USD, Μανίλα ÷ PHP rate). No raise projection (that needs
  // per-person rows) and the default FX rate (fx assumptions are admin-only).
  const summaryUsd = computed(() => {
    const usd = s => nativeToUsd(s, s.salaryTotal, num(inputs.phpPerUsd, 56.5))
    const byOffice = code => payrollSummary.value.filter(s => s.officeCode === code).reduce((t, s) => t + usd(s), 0)
    return {
      athens: byOffice('ATHENS'),
      manila: byOffice('MANILA'),
      total: payrollSummary.value.reduce((t, s) => t + usd(s), 0),
      headcount: payrollSummary.value.reduce((t, s) => t + s.headcount, 0),
    }
  })

  // chart time axis, in whole years or quarters
  const timePoints = computed(() => {
    const points = []
    if (granularity.value === 'quarter') {
      for (let y = 1; y <= horizon.value; y++) {
        for (let q = 1; q <= 4; q++) points.push({ y: y + (q - 1) * 0.25, label: `Q${q} · Έτος ${y}` })
      }
    } else {
      for (let y = 1; y <= horizon.value; y++) points.push({ y, label: `Έτος ${y}` })
    }
    return points
  })

  // rough pro-rated forecast EBITDA for the elapsed fraction of the year (indicative only)
  const actForecast = computed(() => {
    const now = new Date()
    const startOfYear = new Date(now.getFullYear(), 0, 1)
    const dayOfYear = Math.floor((now - startOfYear) / 86400000) + 1
    const fraction = Math.min(1, dayOfYear / 365)
    return lastYear1Ebitda.value * fraction
  })

  return {
    role, loading, loaded, loadError, saveError, load,
    horizon, memberForecastMode, granularity, inputs, actPeriod, people, milestones, actualsData,
    hasAccess, isAdmin, canEdit, setHorizon, setGranularity, setMemberForecastMode,
    phpToUsd, isManila, projectedSalary, salaryUSD, salaryAtDate,
    updatePerson, removePerson, addPerson,
    updateMilestone, removeMilestone, addMilestone,
    costMilestones, opexRunRates, costMilestonesOf, opexCategoryAtYear, opexAtYear,
    addCostMilestone, updateCostMilestone, removeCostMilestone,
    addRaise, updateRaise, removeRaise,
    saveAssumption,
    periodDates, activePeriod, updateActualAmount, revSum, expSum, netSum,
    pnlAtYear, cur, lastYear1Ebitda, breakEvenMembers, breakEvenDistance, ebitdaMargin,
    athensTotal, manilaTotal, staffCount, timePoints, actForecast,
    payrollSummary, summaryUsd,
  }
})
