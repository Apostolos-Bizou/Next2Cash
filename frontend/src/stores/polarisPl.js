// polarisPl.js — shared state + P&L math for the 3 Polaris P&L views.
//
// Phase 2 (Oct 2026): 1:1 port of docs/polaris-mockup-reference.html. The
// mockup is one page with three "pages" sharing one state and one recalc();
// here that state lives in this store and every derived number is a computed,
// so the Dashboard / Payroll / Forecast views stay in sync exactly like the
// mockup does.
//
// DEMO DATA ON PURPOSE (Phase 2 = visual/functional fidelity, not wiring):
// people[], milestones[], actualsData and the "ΠΡΟΒΟΛΗ ΩΣ" role are the
// mockup's hardcoded values. Phase 3 replaces them with /api/polaris/* data
// and the real entity_roles role.

import { defineStore, storeToRefs } from 'pinia'
import { ref, reactive, computed } from 'vue'
import { usePolarisPlRoleStore } from './polarisPlRole'

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

// "today" reference point for the milestone timeline (mockup: MILESTONE_BASE)
const MILESTONE_BASE = new Date()
MILESTONE_BASE.setDate(1)

function monthsFromNow(monthStr) {
  if (!monthStr) return NaN
  const [y, m] = monthStr.split('-').map(Number)
  if (!y || !m) return NaN
  return (y - MILESTONE_BASE.getFullYear()) * 12 + (m - 1 - MILESTONE_BASE.getMonth())
}

function readLs(key) {
  try { return localStorage.getItem(key) } catch (e) { return null }
}
function writeLs(key, value) {
  try { localStorage.setItem(key, value) } catch (e) { /* storage blocked */ }
}

// mockup reads every input as parseFloat(el.value) || fallback
const num = (v, fallback = 0) => parseFloat(v) || fallback

export const usePolarisPlStore = defineStore('polarisPl', () => {
  // ---------------- state ----------------
  // demo role lives in its own data-free store (see polarisPlRole.js)
  const roleStore = usePolarisPlRoleStore()
  const { role } = storeToRefs(roleStore)
  const horizon = ref(parseInt(readLs('pl_horizon')) || 1)
  const memberForecastMode = ref(readLs('pl_memberMode') || 'growth') // 'growth' | 'milestones'
  const granularity = ref(readLs('pl_granularity') || 'year')        // 'year' | 'quarter'

  // Παραδοχές (assumption inputs) — same ids/defaults as the mockup
  const inputs = reactive({
    members: 16800,
    memberGrowth: 0,
    netFee: 24.5,
    monthlyFee: 0.625,
    auditRate: 0.15,
    dentalFee: 9.75,
    netPay: 12,
    auditPay: 0.09,
    dentalPay: 5,
    phpPerUsd: 56.5,
    usdPerEur: 1.08,
  })

  const actPeriod = ref('01/01/2026 – 27/10/2026')

  const people = ref([
    // Athens — fixed office roles, 0% raise (annual fees), not individually editable deletion target but still a row
    { office: 'Αθήνα', name: 'Γραφείο Αθηνών', role: 'Ενοίκιο & λειτουργικά', salary: 10000, raise: 0, effFrom: 2027, rent: true },
    { office: 'Αθήνα', name: 'General Manager',        role: 'Διοίκηση',   salary: 62400, raise: 0, effFrom: 2027 },
    { office: 'Αθήνα', name: 'Business Director',      role: 'Διοίκηση',   salary: 62400, raise: 0, effFrom: 2027 },
    { office: 'Αθήνα', name: 'Accounting Manager',     role: 'Λογιστήριο', salary: 46800, raise: 0, effFrom: 2027 },
    { office: 'Αθήνα', name: 'Marketing Manager',      role: 'Marketing',  salary: 23400, raise: 0, effFrom: 2027 },
    { office: 'Αθήνα', name: 'IT & Developer',         role: 'IT',         salary: 46800, raise: 0, effFrom: 2027 },
    { office: 'Αθήνα', name: 'Legal & Compliance Mgr', role: 'Νομικό',     salary: 20000, raise: 0, effFrom: 2027 },
    // Μανίλα: μισθός εισάγεται σε PHP (πέσος) — αυτόματη μετατροπή σε $ για το ενοποιημένο P&L
    { office: 'Μανίλα', name: 'Γραφείο Μανίλα',    role: 'Ενοίκιο & λειτουργικά', salary: 1130000, raise: 0, effFrom: 2027, rent: true },
    { office: 'Μανίλα', name: 'Pia Guirao',        role: 'COO',                  salary: 1130000, raise: 5, effFrom: 2027 },
    { office: 'Μανίλα', name: 'Carmen Soriano',    role: 'Network Co-ordinator', salary: 2034000, raise: 0, effFrom: 2027 },
    { office: 'Μανίλα', name: 'Margilyn Nalupano', role: 'Clerk 1',              salary: 678000,  raise: 41.7, effFrom: 2027 },
    { office: 'Μανίλα', name: 'Victoria Lawless',  role: 'Clerk 2',              salary: 678000,  raise: 41.7, effFrom: 2027 },
    { office: 'Μανίλα', name: 'Stella Ducay',      role: 'Clerk 3',              salary: 678000,  raise: 0, effFrom: 2027 },
    { office: 'Μανίλα', name: 'Jinky',             role: 'Clerk 3',              salary: 678000,  raise: 0, effFrom: 2027 },
  ])

  // manual member-count forecast milestones: { month: 'YYYY-MM', members: N }
  const milestones = ref([
    { month: '2027-06', members: 20000 },
    { month: '2028-03', members: 30000 },
  ])

  // actuals from the Αστρολάβος trial balance (01/01/2026–27/10/2026) —
  // Beginning + Period Change per row, End is always begin+change.
  const actualsData = reactive({
    revenue: {
      title: 'Έσοδα', code: 'κωδ. 91xxx', isRevenue: true,
      rows: [
        { label: 'Membership fees (όλα τα policy-years)', begin: 401606, change: 364913 },
        { label: 'Admin fees AMAPHIL', begin: 44278, change: 38773 },
        { label: 'Plan management fees', begin: 50219, change: 63360 },
        { label: 'Λοιπά έσοδα (τόκοι κ.ά.)', begin: 32, change: 14 },
      ]
    },
    opsCosts: {
      title: 'Κόστη λειτουργίας', code: 'κωδ. 92xxx', isRevenue: false,
      rows: [
        { label: 'Κόστη membership / admin / claims', begin: 208259, change: 179041 },
        { label: 'Τραπεζικά, ΦΠΑ, λοιπά λειτουργικά', begin: 33012, change: 31820 },
      ]
    },
    payroll: {
      title: 'Μισθοδοσία', code: 'κωδ. 96+97 · wages/bonus', isRevenue: false,
      rows: [
        { label: 'Μισθοί & bonus (Κύπρος + Φιλιππίνες)', begin: 209868, change: 169235 },
      ]
    },
    contributions: {
      title: 'Εισφορές', code: 'κωδ. 96+97 · wages contributions', isRevenue: false,
      rows: [
        { label: 'Εργοδοτικές εισφορές', begin: 28301, change: 16139 },
      ]
    },
    taxes: {
      title: 'Φόροι', code: 'κωδ. 96+97 · taxes/fees', isRevenue: false,
      rows: [
        { label: 'Φόροι / τέλη', begin: 7106, change: 59 },
      ]
    },
    adminOpexOther: {
      title: 'Λοιπά OPEX Διοίκησης', code: 'κωδ. 96+97 · υπόλοιπο', isRevenue: false,
      rows: [
        { label: 'Ενοίκιο / utilities', begin: 3263, change: 3040 },
        { label: 'IT / Computer', begin: 30868, change: 1571 },
        { label: 'Marketing / Διαφήμιση', begin: 1273, change: 5654 },
        { label: 'Consultants / Audit / Legal', begin: 11634, change: 8857 },
        { label: 'Ταξίδια / PR / λοιπά / σύνδεσμοι', begin: 19047, change: 1554 },
        { label: 'Medical / 3rd-party (Φιλιππίνες)', begin: 6956, change: 21928 },
      ]
    },
    other: {
      title: 'Λοιπά', code: 'κωδ. 99xxx', isRevenue: false,
      rows: [
        { label: 'Αναπροσαρμογές / συναλλαγματικές / rounding', begin: 3733, change: 37210 },
      ]
    }
  })

  // ---------------- role / horizon / granularity / mode ----------------
  const isAdmin = computed(() => role.value === 'admin')
  const canEdit = computed(() => role.value === 'admin' || role.value === 'editor')

  const setRole = roleStore.setRole
  function setHorizon(y) { horizon.value = y; writeLs('pl_horizon', y) }
  function setGranularity(g) { granularity.value = g; writeLs('pl_granularity', g) }
  function setMemberForecastMode(mode) { memberForecastMode.value = mode; writeLs('pl_memberMode', mode) }

  // ---------------- FX: every office pays in its own currency, the P&L is
  // consolidated in USD everywhere (same as Αστρολάβος + the original Excel) ----
  function phpToUsd(php) { return php / num(inputs.phpPerUsd, 56.5) }
  function eurToUsd(eur) { return eur * num(inputs.usdPerEur, 1.08) }
  function isManila(p) { return p.office === 'Μανίλα' }

  function projectedSalary(p, years) {
    // raise% compounds annually once the person has been on the raise schedule for N years within the horizon
    const yearsApplied = Math.max(0, years - 1)
    return p.salary * Math.pow(1 + (p.raise / 100), yearsApplied)
  }
  // $-equivalent of a person's salary at a given horizon (Αθήνα EUR, Μανίλα PHP)
  function salaryUSD(p, years) {
    const native = projectedSalary(p, years)
    return isManila(p) ? phpToUsd(native) : eurToUsd(native)
  }

  // ---------------- payroll edits ----------------
  function updatePerson(idx, field, val) {
    people.value[idx][field] = parseFloat(val) || 0
  }
  function removePerson(idx) {
    if (role.value !== 'admin') return
    people.value.splice(idx, 1)
  }
  function addPerson() {
    if (role.value !== 'admin') return
    people.value.push({ office: 'Αθήνα', name: 'Νέο μέλος προσωπικού', role: '—', salary: 0, raise: 0, effFrom: 2027 })
  }

  // ---------------- member milestones (manual forecast) ----------------
  function sortedMilestonePoints(members0) {
    const pts = milestones.value
      .map(m => ({ months: monthsFromNow(m.month), members: Number(m.members) || 0 }))
      .filter(p => isFinite(p.months) && p.months > 0)
      .sort((a, b) => a.months - b.months)
    return [{ months: 0, members: members0 }, ...pts]
  }
  // piecewise-linear interpolation across the milestones; flat after the last one
  function membersAtMonths(members0, targetMonths) {
    const pts = sortedMilestonePoints(members0)
    if (targetMonths <= 0) return members0
    const last = pts[pts.length - 1]
    if (targetMonths >= last.months) return last.members
    for (let i = 0; i < pts.length - 1; i++) {
      const a = pts[i], b = pts[i + 1]
      if (targetMonths >= a.months && targetMonths <= b.months) {
        const span = b.months - a.months
        const t = span > 0 ? (targetMonths - a.months) / span : 1
        return a.members + (b.members - a.members) * t
      }
    }
    return last.members
  }
  function updateMilestone(idx, field, val) {
    if (role.value !== 'admin') return
    milestones.value[idx][field] = field === 'members' ? (parseFloat(val) || 0) : val
  }
  function removeMilestone(idx) {
    if (role.value !== 'admin') return
    milestones.value.splice(idx, 1)
  }
  function addMilestone() {
    if (role.value !== 'admin') return
    milestones.value.push({ month: '', members: num(inputs.members) })
  }

  // ---------------- actuals (Αστρολάβος trial balance) ----------------
  // parses "Περίοδος" (π.χ. "01/01/2026 – 27/10/2026") into display dates
  const periodDates = computed(() => {
    const raw = actPeriod.value || ''
    const parts = raw.split(/\s*[–-]\s*/).map(s => s.trim()).filter(Boolean)
    if (parts.length >= 2) return { start: parts[0], end: parts[parts.length - 1] }
    return { start: '—', end: '—' }
  })
  function updateActualAmount(groupKey, idx, field, val) {
    actualsData[groupKey].rows[idx][field] = parseFloat(val) || 0
  }

  const revSum = computed(() => groupSum3(actualsData.revenue))
  const expSum = computed(() => EXPENSE_KEYS.reduce((s, k) => {
    const g = groupSum3(actualsData[k])
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
    // the whole P&L is consolidated in USD; payroll converted per person via salaryUSD()
    const revenue = members * netFee + members * monthlyFee * 12 + members * auditRate * claims + members * dentalFee
    const thirdParty = members * netPay * (1 + netVat) + members * claims * auditPay * (1 + auditVat) + members * dentalPay * (1 + dentalVat)
    const contribution = revenue - thirdParty
    const payroll = people.value.reduce((s, p) => s + salaryUSD(p, y), 0)
    const ebitda = contribution - payroll
    return { members, revenue, thirdParty, contribution, payroll, ebitda }
  }

  // break-even members at the selected horizon's cost base & per-member contribution
  const cur = computed(() => pnlAtYear(horizon.value))
  const lastYear1Ebitda = computed(() => pnlAtYear(1).ebitda)
  const breakEvenMembers = computed(() => {
    const c = cur.value
    const contribPerMember = c.members ? c.contribution / c.members : 0
    return contribPerMember > 0 ? c.payroll / contribPerMember : NaN
  })
  const breakEvenDistance = computed(() => cur.value.members - breakEvenMembers.value)
  const ebitdaMargin = computed(() => cur.value.revenue ? (cur.value.ebitda / cur.value.revenue * 100) : 0)

  // payroll aggregates (viewer card + table footer)
  const athensTotal = computed(() => people.value.filter(p => p.office === 'Αθήνα').reduce((s, p) => s + salaryUSD(p, horizon.value), 0))
  const manilaTotal = computed(() => people.value.filter(p => p.office === 'Μανίλα').reduce((s, p) => s + salaryUSD(p, horizon.value), 0))
  const staffCount = computed(() => people.value.filter(p => !p.rent).length)

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
    role, horizon, memberForecastMode, granularity, inputs, actPeriod, people, milestones, actualsData,
    isAdmin, canEdit, setRole, setHorizon, setGranularity, setMemberForecastMode,
    phpToUsd, eurToUsd, isManila, projectedSalary, salaryUSD,
    updatePerson, removePerson, addPerson,
    updateMilestone, removeMilestone, addMilestone,
    periodDates, updateActualAmount, revSum, expSum, netSum,
    pnlAtYear, cur, lastYear1Ebitda, breakEvenMembers, breakEvenDistance, ebitdaMargin,
    athensTotal, manilaTotal, staffCount, timePoints, actForecast,
  }
})
