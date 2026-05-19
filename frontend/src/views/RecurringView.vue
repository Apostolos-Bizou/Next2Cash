<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Bar } from 'vue-chartjs'
import {
  Chart as ChartJS,
  Title, Tooltip, Legend,
  BarElement, CategoryScale, LinearScale,
} from 'chart.js'
import api from '@/api'

ChartJS.register(Title, Tooltip, Legend, BarElement, CategoryScale, LinearScale)

const ENTITY_MAP_S82 = {
  next2me:      '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:        'dea1f32c-7b30-4981-b625-633da9dbe71e',
  next2megroup: '50317f44-9961-4fb4-add0-7a118e32dc14',
}
const ENTITY_LABELS = {
  next2me:      'Next2Me',
  house:        'House',
  next2megroup: 'Next2Me Group',
}

const PROJECT_COLORS = [
  '#a855f7', '#10b981', '#06b6d4', '#f97316', '#ef4444',
  '#84cc16', '#f59e0b', '#2e75b6', '#D4537E', '#534AB7',
]
const OPEX_COLOR = '#f59e0b'

// S83: Allocation rule "by Role not by Time" — active projects only
const ACTIVE_PROJECT_STATUSES = ['LIVE', 'IN_DEVELOPMENT']

const loading        = ref(false)
const error          = ref(null)
const recurringTxns  = ref([])
const patterns       = ref([])
const projects       = ref([])
const entityKey      = ref(localStorage.getItem('n2c_entity') || 'next2me')
const scope          = ref('group')

// S83: cost view mode — 'raw' = direct only, 'loaded' = direct + allocated OpEx share
const recurringMode  = ref(localStorage.getItem('n2c_recurring_mode') || 'raw')
watch(recurringMode, (v) => {
  localStorage.setItem('n2c_recurring_mode', v)
})

const cashOnHand = ref(Number(localStorage.getItem('n2c_cash_on_hand')) || 0)
watch(cashOnHand, (v) => {
  localStorage.setItem('n2c_cash_on_hand', String(v || 0))
})

function fmtMoney(amount) {
  const num = Number(amount) || 0
  return num.toLocaleString('el-GR', {
    style: 'currency', currency: 'EUR',
    minimumFractionDigits: 0, maximumFractionDigits: 0,
  })
}
function fmtMoneySigned(amount) {
  const num = Number(amount) || 0
  if (num === 0) return fmtMoney(0)
  const sign = num > 0 ? '+' : '−'
  const abs = Math.abs(num).toLocaleString('el-GR', {
    style: 'currency', currency: 'EUR',
    minimumFractionDigits: 0, maximumFractionDigits: 0,
  })
  return sign + abs.replace('-', '')
}

const FREQ_LABELS = {
  DAILY: 'Ημερήσια', WEEKLY: 'Εβδομαδιαία', MONTHLY: 'Μηνιαία',
  QUARTERLY: 'Τριμηνιαία', YEARLY: 'Ετήσια', CUSTOM: 'Custom',
}
function fmtFrequency(pattern) {
  if (!pattern) return '—'
  const base = FREQ_LABELS[pattern.frequency] || pattern.frequency
  const interval = Number(pattern.intervalCount) || 1
  if (interval === 1) return base
  if (pattern.frequency === 'MONTHLY')   return `Κάθε ${interval} μήνες`
  if (pattern.frequency === 'QUARTERLY') return `Κάθε ${interval} τρίμηνα`
  if (pattern.frequency === 'YEARLY')    return `Κάθε ${interval} έτη`
  if (pattern.frequency === 'WEEKLY')    return `Κάθε ${interval} εβδομάδες`
  return `${base} ×${interval}`
}

function monthlyEquivalent(txn, pattern) {
  const amt = Number(txn.amount) || 0
  if (!pattern) return amt
  const interval = Number(pattern.intervalCount) || 1
  switch (pattern.frequency) {
    case 'MONTHLY':   return amt / interval
    case 'QUARTERLY': return amt / (3 * interval)
    case 'YEARLY':    return amt / (12 * interval)
    case 'WEEKLY':    return (amt * 52) / (12 * interval)
    case 'DAILY':     return (amt * 365) / (12 * interval)
    default:          return amt
  }
}

const patternById = computed(() => {
  const m = {}
  for (const p of patterns.value) if (p && p.id) m[p.id] = p
  return m
})
const projectById = computed(() => {
  const m = {}
  for (const p of projects.value) if (p && p.id) m[p.id] = p
  return m
})

function colorForGroup(key, idx) {
  if (key === '__opex__') return OPEX_COLOR
  return PROJECT_COLORS[idx % PROJECT_COLORS.length]
}

const enrichedTxns = computed(() => {
  return recurringTxns.value.map((t) => {
    const pat = patternById.value[t.recurrencePatternId] || null
    const isOpEx = !t.projectId
    const projectName = t.projectId ? (projectById.value[t.projectId]?.name || 'Project') : 'OpEx'
    return {
      id: t.id,
      description: t.description,
      amount: Number(t.amount) || 0,
      currency: t.currency || 'EUR',
      type: t.type,
      pattern: pat,
      projectId: t.projectId || null,
      projectName,
      isOpEx,
      startDate: pat?.startDate || t.docDate,
      endDate: pat?.endDate || null,
      monthly: monthlyEquivalent(t, pat),
      dayOfMonth: pat?.dayOfMonth || null,
    }
  })
})

const filteredTxns = computed(() => {
  if (scope.value === 'group') return enrichedTxns.value
  if (scope.value === 'opex')  return enrichedTxns.value.filter(t => t.isOpEx)
  if (scope.value.startsWith('project:')) {
    const pid = scope.value.split(':')[1]
    return enrichedTxns.value.filter(t => t.projectId === pid)
  }
  return enrichedTxns.value
})

// S83: Allocation rule — list of projects eligible for OpEx share
// Active = status LIVE or IN_DEVELOPMENT. PLANNING/TESTING/PAUSED/CANCELLED excluded.
// IMPORTANT: a project must also appear in the current entity's recurring data
// (i.e. have at least one tracked txn) to receive a share. This keeps allocation
// scoped to projects this entity actually funds, not the whole portfolio.
const activeProjects = computed(() => {
  const projectIdsWithTxns = new Set(
    enrichedTxns.value.filter(t => !t.isOpEx && t.projectId).map(t => t.projectId)
  )
  return projects.value.filter(p =>
    p && p.id &&
    ACTIVE_PROJECT_STATUSES.includes(p.status) &&
    projectIdsWithTxns.has(p.id)
  )
})

// S83: Total OpEx burn (entity-wide, ignores current scope filter)
const totalOpExBurn = computed(() => {
  let total = 0
  for (const t of enrichedTxns.value) {
    if (t.isOpEx && t.type === 'expense') total += t.monthly
  }
  return total
})

// S83: Equal-split allocation. 0 active projects → 0 share (no division by zero).
const opExSharePerProject = computed(() => {
  const n = activeProjects.value.length
  if (n === 0) return 0
  return totalOpExBurn.value / n
})

// S83: Helper — does a given group (key) receive an OpEx share in loaded mode?
function groupReceivesShare(key, isOpEx) {
  if (recurringMode.value !== 'loaded') return false
  if (isOpEx) return false
  return activeProjects.value.some(p => p.id === key)
}

const kpis = computed(() => {
  let burn = 0, mrr = 0, burnCount = 0, mrrCount = 0
  for (const t of filteredTxns.value) {
    if (t.type === 'expense') { burn += t.monthly; burnCount++ }
    else if (t.type === 'income') { mrr += t.monthly; mrrCount++ }
  }
  const netBurn = mrr - burn
  const runwayMonths = (cashOnHand.value > 0 && netBurn < 0)
    ? Math.floor(cashOnHand.value / -netBurn)
    : null
  return { burn, mrr, netBurn, burnCount, mrrCount, runwayMonths }
})

const next30Days = computed(() => {
  const now = new Date()
  const horizon = new Date(now); horizon.setDate(horizon.getDate() + 30)
  let total = 0, count = 0
  for (const t of filteredTxns.value) {
    if (t.type !== 'expense' || !t.pattern) continue
    const dom = t.dayOfMonth
    if (!dom) { total += t.monthly; count++; continue }
    const thisMonth = new Date(now.getFullYear(), now.getMonth(), dom)
    const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, dom)
    for (const d of [thisMonth, nextMonth]) {
      if (d >= now && d <= horizon) { total += t.amount; count++ }
    }
  }
  return { total, count }
})

const runwayEndDate = computed(() => {
  if (!kpis.value.runwayMonths) return null
  const d = new Date()
  d.setMonth(d.getMonth() + kpis.value.runwayMonths)
  return d.toLocaleDateString('el-GR', { month: 'long', year: 'numeric' })
})

const perProject = computed(() => {
  const groups = new Map()
  const opExKey = '__opex__'
  groups.set(opExKey, { key: opExKey, label: 'OpEx (Γενικά Εταιρείας)', isOpEx: true, burn: 0, mrr: 0 })
  for (const t of enrichedTxns.value) {
    const key = t.projectId || opExKey
    if (!groups.has(key)) groups.set(key, { key, label: t.projectName, isOpEx: false, burn: 0, mrr: 0 })
    const g = groups.get(key)
    if (t.type === 'expense') g.burn += t.monthly
    else if (t.type === 'income') g.mrr += t.monthly
  }
  if (groups.get(opExKey).burn === 0 && groups.get(opExKey).mrr === 0) groups.delete(opExKey)
  let idx = 0
  // S83: precompute share once
  const share = opExSharePerProject.value
  const activeIds = new Set(activeProjects.value.map(p => p.id))
  const arr = Array.from(groups.values()).map(g => {
    // S83: directBurn is always the raw value; allocatedOpEx added only in loaded mode
    const directBurn = g.burn
    const receivesShare = recurringMode.value === 'loaded' && !g.isOpEx && activeIds.has(g.key)
    const allocatedOpEx = receivesShare ? share : 0
    const totalBurn = directBurn + allocatedOpEx
    const net = g.mrr - totalBurn
    let status, statusBg, statusFg
    if (g.isOpEx) { status = 'Πάγιο'; statusBg = '#2c2c2a'; statusFg = '#b4b2a9' }
    else if (g.mrr === 0) { status = 'Επένδυση'; statusBg = '#412402'; statusFg = '#FAC775' }
    else if (net >= 0) { status = 'Κερδοφόρο'; statusBg = '#04342C'; statusFg = '#5DCAA5' }
    else if (totalBurn > 0 && g.mrr / totalBurn >= 0.5) { status = 'Σε ανάπτυξη'; statusBg = '#1a1633'; statusFg = '#AFA9EC' }
    else { status = 'Επένδυση'; statusBg = '#412402'; statusFg = '#FAC775' }
    return { ...g, directBurn, allocatedOpEx, burn: totalBurn, receivesShare, net, status, statusBg, statusFg, color: colorForGroup(g.key, idx++) }
  })
  arr.sort((a, b) => {
    if (a.isOpEx && !b.isOpEx) return -1
    if (!a.isOpEx && b.isOpEx) return 1
    return b.burn - a.burn
  })
  return arr
})

const projectColorById = computed(() => {
  const m = { '__opex__': OPEX_COLOR }
  let i = 0
  for (const g of perProject.value) {
    if (!g.isOpEx) m[g.key] = PROJECT_COLORS[i++ % PROJECT_COLORS.length]
  }
  return m
})

const pareto = computed(() => {
  const expenses = filteredTxns.value
    .filter(t => t.type === 'expense')
    .map(t => ({
      id: t.id,
      description: t.description,
      monthly: t.monthly,
      isOpEx: t.isOpEx,
      projectName: t.projectName,
      projectColor: t.isOpEx ? OPEX_COLOR : (projectColorById.value[t.projectId] || PROJECT_COLORS[0]),
    }))
    .sort((a, b) => b.monthly - a.monthly)
    .slice(0, 10)
  const total = kpis.value.burn || 1
  const maxMonthly = expenses[0]?.monthly || 1
  return expenses.map((e, idx) => ({
    ...e,
    rank: idx + 1,
    pct: (e.monthly / total) * 100,
    barWidth: (e.monthly / maxMonthly) * 100,
  }))
})

const paretoSummary = computed(() => {
  if (pareto.value.length === 0) return null
  const top = pareto.value.reduce((s, p) => s + p.monthly, 0)
  const totalBurn = kpis.value.burn || 0
  const pct = totalBurn > 0 ? (top / totalBurn) * 100 : 0
  return { top, pct, count: pareto.value.length }
})

const trendData = computed(() => {
  const months = []
  const now = new Date()
  for (let i = 5; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    months.push({
      label: d.toLocaleDateString('el-GR', { month: 'short' }),
      key: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`,
      date: d,
    })
  }
  const burnByMonth = {}, mrrByMonth = {}
  for (const m of months) { burnByMonth[m.key] = 0; mrrByMonth[m.key] = 0 }
  for (const t of filteredTxns.value) {
    if (!t.pattern) continue
    const start = new Date(t.startDate)
    const end = t.endDate ? new Date(t.endDate) : null
    for (const m of months) {
      const monthStart = new Date(m.date.getFullYear(), m.date.getMonth(), 1)
      const monthEnd = new Date(m.date.getFullYear(), m.date.getMonth() + 1, 0)
      if (start > monthEnd) continue
      if (end && end < monthStart) continue
      if (t.type === 'expense') burnByMonth[m.key] += t.monthly
      else if (t.type === 'income') mrrByMonth[m.key] += t.monthly
    }
  }
  return {
    labels: months.map(m => m.label),
    burn: months.map(m => Math.round(burnByMonth[m.key])),
    mrr: months.map(m => Math.round(mrrByMonth[m.key])),
  }
})

const trendChartData = computed(() => ({
  labels: trendData.value.labels,
  datasets: [
    { label: 'Burn (έξοδα)', data: trendData.value.burn, backgroundColor: 'rgba(239,68,68,0.75)', borderRadius: 4 },
    { label: 'MRR (έσοδα)',  data: trendData.value.mrr,  backgroundColor: 'rgba(16,185,129,0.75)', borderRadius: 4 },
  ],
}))

const trendChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      labels: { color: '#8b949e', font: { size: 11 } },
      position: 'top', align: 'end',
    },
  },
  scales: {
    x: {
      ticks: { color: '#5f7d9a', font: { size: 10 } },
      grid: { color: '#21262d' },
    },
    y: {
      ticks: {
        color: '#5f7d9a', font: { size: 10 },
        callback: v => (v / 1000).toFixed(0) + 'K',
      },
      grid: { color: '#21262d' },
    },
  },
}

const trendSummary = computed(() => {
  const b = trendData.value.burn
  const m = trendData.value.mrr
  if (b.length < 2) return null
  const burnChange = b[0] > 0 ? ((b[b.length - 1] - b[0]) / b[0]) * 100 : 0
  const mrrChange  = m[0] > 0 ? ((m[m.length - 1] - m[0]) / m[0]) * 100 : (m[m.length - 1] > 0 ? 100 : 0)
  const netFirst = m[0] - b[0]
  const netLast  = m[m.length - 1] - b[b.length - 1]
  return { burnChange, mrrChange, netImprovement: netLast - netFirst }
})

const recurringList = computed(() => [...filteredTxns.value].sort((a, b) => b.monthly - a.monthly))

async function loadAll() {
  loading.value = true
  error.value = null
  recurringTxns.value = []
  patterns.value = []
  projects.value = []
  const entityId = ENTITY_MAP_S82[entityKey.value]
  if (!entityId) { error.value = 'Άκυρο entity.'; loading.value = false; return }
  try {
    const txnRes = await api.get('/api/transactions', { params: { entityId, perPage: 10000 } })
    const allTxns = Array.isArray(txnRes.data?.data) ? txnRes.data.data : (Array.isArray(txnRes.data) ? txnRes.data : [])
    recurringTxns.value = allTxns.filter(t => t.isRecurring === true)
    try {
      const patRes = await api.get('/api/recurrence-patterns', { params: { entityId } })
      patterns.value = Array.isArray(patRes.data?.data) ? patRes.data.data : (Array.isArray(patRes.data) ? patRes.data : [])
    } catch (pe) { patterns.value = [] }
    try {
      const prjRes = await api.get('/api/projects', { params: { entityId, activeOnly: false } })
      projects.value = Array.isArray(prjRes.data?.data) ? prjRes.data.data : []
    } catch (prje) { projects.value = [] }
  } catch (e) {
    if (e.response?.status === 403) error.value = 'Δεν έχετε δικαίωμα πρόσβασης σε αυτή τη σελίδα.'
    else error.value = 'Σφάλμα σύνδεσης με τον server.'
  } finally {
    loading.value = false
  }
}

watch(entityKey, () => {
  localStorage.setItem('n2c_entity', entityKey.value)
  scope.value = 'group'
  loadAll()
})

onMounted(loadAll)
</script>

<template>
  <div class="recurring-page">

    <div class="page-hdr">
      <div class="hdr-left">
        <i class="fas fa-sync-alt hdr-icon"></i>
        <h1>Πάγια &amp; Επαναλαμβανόμενα</h1>
        <span class="hdr-sub">Burn rate, MRR &amp; cash forecast · {{ recurringTxns.length }} εγγραφές</span>
      </div>
      <div class="hdr-right">
        <select v-model="entityKey" class="entity-sel">
          <option value="next2me">{{ ENTITY_LABELS.next2me }}</option>
          <option value="house">{{ ENTITY_LABELS.house }}</option>
          <option value="next2megroup">{{ ENTITY_LABELS.next2megroup }}</option>
        </select>
        <button class="refresh-btn" @click="loadAll" :disabled="loading">
          <i class="fas fa-redo"></i>
          {{ loading ? 'Φόρτωση…' : 'Ανανέωση' }}
        </button>
      </div>
    </div>

    <div class="scope-bar">
      <button class="scope-btn" :class="{ active: scope === 'group' }" @click="scope = 'group'">
        <i class="fas fa-globe"></i> Όλος ο όμιλος
      </button>
      <button class="scope-btn" :class="{ active: scope === 'opex' }" @click="scope = 'opex'">
        <i class="fas fa-building"></i> Μόνο OpEx
      </button>
      <div class="scope-divider" v-if="perProject.filter(p => !p.isOpEx).length > 0"></div>
      <button
        v-for="g in perProject.filter(p => !p.isOpEx)"
        :key="g.key"
        class="scope-btn"
        :class="{ active: scope === 'project:' + g.key }"
        @click="scope = 'project:' + g.key">
        <span class="scope-dot" :style="{ background: g.color }"></span>
        {{ g.label }}
      </button>

      <!-- S83: Cost view mode toggle (right-aligned) -->
      <div class="mode-toggle-wrap">
        <span class="mode-toggle-lbl">COST VIEW <span class="lbl-sub">(τρόπος προβολής)</span></span>
        <div class="mode-toggle">
          <button
            class="mode-btn"
            :class="{ active: recurringMode === 'raw' }"
            @click="recurringMode = 'raw'"
            title="Άμεσα κόστη μόνο — όπως καταχωρήθηκαν">
            <i class="fas fa-bolt"></i> Raw burn
          </button>
          <button
            class="mode-btn"
            :class="{ active: recurringMode === 'loaded' }"
            @click="recurringMode = 'loaded'"
            title="Άμεσα κόστη + κατανεμημένα OpEx (equal split σε active projects)">
            <i class="fas fa-layer-group"></i> Fully Loaded
          </button>
        </div>
      </div>
    </div>

    <!-- S83 fix: Allocation banner — always shown in Fully Loaded mode (regardless of data) -->
    <div v-if="recurringMode === 'loaded'" class="alloc-banner">
      <i class="fas fa-info-circle"></i>
      <span v-if="activeProjects.length === 0">
        <strong>Fully Loaded mode:</strong>
        Δεν υπάρχουν active projects (LIVE ή IN_DEVELOPMENT) με recurring transactions —
        η κατανομή OpEx δεν εφαρμόζεται.
      </span>
      <span v-else-if="totalOpExBurn === 0">
        <strong>Fully Loaded mode:</strong>
        OpEx pool = <strong>0 €/μήνα</strong> — όλα τα κόστη είναι ήδη project-specific,
        δεν υπάρχει κάτι για κατανομή.
        <span class="alloc-sub">({{ activeProjects.length }} active projects)</span>
      </span>
      <span v-else>
        <strong>Fully Loaded mode:</strong>
        OpEx pool <strong>{{ fmtMoney(totalOpExBurn) }}/μήνα</strong>
        ÷ <strong>{{ activeProjects.length }} active projects</strong>
        = <strong>{{ fmtMoney(opExSharePerProject) }}/μήνα ανά project</strong>
        <span class="alloc-sub">(equal split σε projects με status «Σε Παραγωγή» ή «Σε Ανάπτυξη»)</span>
      </span>
    </div>

    <div v-if="error" class="err-bar"><i class="fas fa-exclamation-triangle"></i> {{ error }}</div>

    <div v-else-if="loading" class="load-wrap">
      <div class="spinner"></div>
      <div>Φόρτωση δεδομένων…</div>
    </div>

    <div v-else-if="recurringTxns.length === 0" class="empty-state">
      <i class="fas fa-inbox empty-icon"></i>
      <h3>Καμία επαναλαμβανόμενη συναλλαγή</h3>
      <p>Δεν υπάρχουν πάγια για το <strong>{{ ENTITY_LABELS[entityKey] }}</strong>.</p>
      <p class="empty-hint">Πήγαινε στο <strong>Νέα Καταχώριση → Προγραμματισμένη</strong> και τσέκαρε <em>«Είναι επαναλαμβανόμενη»</em>.</p>
    </div>

    <template v-else>

      <div class="kpi-row">
        <div class="kpi-card">
          <div class="kpi-hdr">
            <i class="fas fa-fire kpi-ico" style="color:#ef4444"></i>
            <span class="kpi-title">MONTHLY BURN</span>
          </div>
          <div class="kpi-sub-gr">(ρυθμός εξόδων / μήνα)</div>
          <div class="kpi-val">{{ fmtMoney(kpis.burn) }}</div>
          <div class="kpi-foot">
            <i class="fas fa-arrow-trend-up" style="color:#ef4444"></i>
            <span style="color:#ef4444">{{ kpis.burnCount }} πάγια έξοδα</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-hdr">
            <i class="fas fa-chart-line kpi-ico" style="color:#10b981"></i>
            <span class="kpi-title" style="color:#10b981">MRR</span>
          </div>
          <div class="kpi-sub-gr">(μηνιαία επαναλ. έσοδα)</div>
          <div class="kpi-val">{{ fmtMoney(kpis.mrr) }}</div>
          <div class="kpi-foot">
            <i class="fas fa-users" style="color:#10b981"></i>
            <span style="color:#10b981">{{ kpis.mrrCount }} συμβόλαια</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-hdr">
            <i class="fas fa-balance-scale kpi-ico" style="color:#f59e0b"></i>
            <span class="kpi-title" style="color:#f59e0b">NET BURN</span>
          </div>
          <div class="kpi-sub-gr">(καθαρό μηνιαίο κόστος)</div>
          <div class="kpi-val" :style="{ color: kpis.netBurn < 0 ? '#f59e0b' : '#10b981' }">
            {{ fmtMoneySigned(kpis.netBurn) }}
          </div>
          <div class="kpi-foot">
            <i class="fas fa-hourglass-half" style="color:#8b949e"></i>
            <span v-if="kpis.runwayMonths !== null" style="color:#8b949e">Runway: {{ kpis.runwayMonths }} μήνες</span>
            <span v-else style="color:#8b949e">Runway: —</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-hdr">
            <i class="fas fa-calendar-alt kpi-ico" style="color:#06b6d4"></i>
            <span class="kpi-title" style="color:#06b6d4">NEXT 30 DAYS</span>
          </div>
          <div class="kpi-sub-gr">(πληρωμές επόμενου μήνα)</div>
          <div class="kpi-val">{{ fmtMoney(next30Days.total) }}</div>
          <div class="kpi-foot">
            <i class="fas fa-list-check" style="color:#06b6d4"></i>
            <span style="color:#06b6d4">{{ next30Days.count }} πληρωμές</span>
          </div>
        </div>
      </div>

      <div class="mid-row">
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle">
              <i class="fas fa-chart-column"></i>
              <span v-if="recurringMode === 'loaded'">Total Cost vs MRR ανά Project</span>
              <span v-else>Burn vs MRR ανά Project</span>
              <span class="ptitle-sub" v-if="recurringMode === 'loaded'">(direct + allocated OpEx vs έσοδα)</span>
              <span class="ptitle-sub" v-else>(πόσα κοστίζει vs πόσα φέρνει)</span>
            </span>
            <span class="pbadge">{{ perProject.length }} groups</span>
          </div>
          <table class="pp-table">
            <thead>
              <tr v-if="recurringMode === 'loaded'">
                <th>PROJECT</th>
                <th class="num">DIRECT</th>
                <th class="num">+ ALLOCATED</th>
                <th class="num">= TOTAL</th>
                <th class="num">MRR</th>
                <th class="num">NET</th>
                <th class="center">STATUS</th>
              </tr>
              <tr v-else>
                <th>PROJECT</th>
                <th class="num">BURN</th>
                <th class="num">MRR</th>
                <th class="num">NET</th>
                <th class="center">STATUS</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="g in perProject" :key="g.key">
                <td>
                  <span class="proj-dot" :style="{ background: g.color }"></span>
                  {{ g.label }}
                </td>
                <template v-if="recurringMode === 'loaded'">
                  <td class="num neg">{{ fmtMoney(g.directBurn) }}</td>
                  <td class="num" :class="g.receivesShare ? 'alloc' : 'muted'">
                    <span v-if="g.receivesShare">+ {{ fmtMoney(g.allocatedOpEx) }}</span>
                    <span v-else>—</span>
                  </td>
                  <td class="num neg strong">{{ fmtMoney(g.burn) }}</td>
                </template>
                <template v-else>
                  <td class="num neg">{{ fmtMoney(g.burn) }}</td>
                </template>
                <td class="num pos" v-if="g.mrr > 0">{{ fmtMoney(g.mrr) }}</td>
                <td class="num muted" v-else>—</td>
                <td class="num strong" :style="{ color: g.net >= 0 ? '#10b981' : '#ef4444' }">
                  {{ fmtMoneySigned(g.net) }}
                </td>
                <td class="center">
                  <span class="status-pill" :style="{ background: g.statusBg, color: g.statusFg }">
                    {{ g.status }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle">
              <i class="fas fa-wallet" style="color:#06b6d4"></i> Cash Runway
              <span class="ptitle-sub">(πόσους μήνες αντέχει)</span>
            </span>
          </div>
          <div class="runway-body">
            <div class="runway-input-wrap">
              <div class="runway-input-lbl">CASH ON HAND <span class="lbl-sub">(διαθέσιμο μετρητό)</span></div>
              <div class="runway-input-row">
                <input type="number" v-model.number="cashOnHand" placeholder="0" />
                <span class="runway-cur">€</span>
              </div>
            </div>
            <div class="runway-result" v-if="kpis.runwayMonths !== null">
              <div class="runway-lbl">RUNWAY <span class="lbl-sub">(οριζόντας επιβίωσης)</span></div>
              <div class="runway-months">
                {{ kpis.runwayMonths }}<span class="runway-unit">μήνες</span>
              </div>
              <div class="runway-note" v-if="runwayEndDate">
                Cash τελειώνει <span style="color:#e4e6eb">{{ runwayEndDate }}</span> με net burn
                <span style="color:#ef4444">{{ fmtMoneySigned(kpis.netBurn) }}/μήνα</span>.
              </div>
            </div>
            <div class="runway-result" v-else>
              <div class="runway-lbl">RUNWAY <span class="lbl-sub">(οριζόντας επιβίωσης)</span></div>
              <div class="runway-placeholder" v-if="kpis.netBurn >= 0">
                <i class="fas fa-check-circle" style="color:#10b981"></i>
                Καθαρά κερδοφόρο — άπειρο runway
              </div>
              <div class="runway-placeholder" v-else>
                <i class="fas fa-info-circle"></i>
                Βάλε cash on hand για υπολογισμό
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="panel-card pareto-panel" v-if="pareto.length > 0">
        <div class="panel-hdr">
          <span class="ptitle">
            <i class="fas fa-ranking-star" style="color:#f97316"></i> Top {{ pareto.length }} Recurring Expenses
            <span class="ptitle-sub">(τα μεγαλύτερα πάγια έξοδα)</span>
          </span>
          <span class="pbadge" v-if="paretoSummary">
            Pareto · {{ paretoSummary.pct.toFixed(0) }}% του burn
          </span>
        </div>
        <div class="pareto-list">
          <div v-for="p in pareto" :key="p.id" class="pareto-row">
            <span class="pareto-rank">{{ p.rank }}</span>
            <span class="pareto-desc">{{ p.description }}</span>
            <span class="pareto-pill" :style="{ background: p.projectColor + '33', color: p.projectColor }">
              {{ p.isOpEx ? 'OpEx' : p.projectName }}
            </span>
            <div class="pareto-bar-wrap">
              <div class="pareto-bar" :style="{ width: p.barWidth + '%', background: p.projectColor }"></div>
            </div>
            <span class="pareto-amount">{{ fmtMoney(p.monthly) }}</span>
            <span class="pareto-pct">{{ p.pct.toFixed(1) }}%</span>
          </div>
        </div>
        <div class="pareto-foot" v-if="paretoSummary">
          <span>Σύνολο top {{ paretoSummary.count }}: <strong>{{ fmtMoney(paretoSummary.top) }}</strong></span>
          <span style="color:#10b981">{{ paretoSummary.pct.toFixed(0) }}% του burn σε {{ paretoSummary.count }} γραμμές</span>
        </div>
      </div>

      <div class="bot-row">
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle">
              <i class="fas fa-chart-area" style="color:#06b6d4"></i> Burn vs MRR Trend
              <span class="ptitle-sub">(πορεία 6 μηνών)</span>
            </span>
          </div>
          <div class="trend-chart">
            <Bar :data="trendChartData" :options="trendChartOptions" />
          </div>
          <div class="trend-footer" v-if="trendSummary">
            <div class="trend-metric">
              <div class="tm-lbl">MRR growth <span class="lbl-sub">(αύξηση εσόδων)</span></div>
              <div class="tm-val" :style="{ color: trendSummary.mrrChange >= 0 ? '#10b981' : '#ef4444' }">
                {{ trendSummary.mrrChange >= 0 ? '+' : '' }}{{ trendSummary.mrrChange.toFixed(0) }}%
              </div>
            </div>
            <div class="trend-metric">
              <div class="tm-lbl">Burn change <span class="lbl-sub">(μεταβολή εξόδων)</span></div>
              <div class="tm-val" :style="{ color: trendSummary.burnChange <= 0 ? '#10b981' : '#f59e0b' }">
                {{ trendSummary.burnChange >= 0 ? '+' : '' }}{{ trendSummary.burnChange.toFixed(0) }}%
              </div>
            </div>
            <div class="trend-metric">
              <div class="tm-lbl">Net improvement <span class="lbl-sub">(βελτίωση καθαρού)</span></div>
              <div class="tm-val" :style="{ color: trendSummary.netImprovement >= 0 ? '#10b981' : '#ef4444' }">
                {{ fmtMoneySigned(trendSummary.netImprovement) }}
              </div>
            </div>
          </div>
        </div>

        <div class="panel-card list-panel">
          <div class="panel-hdr">
            <span class="ptitle">
              <i class="fas fa-list-ul" style="color:#a855f7"></i> Όλα τα Recurring
            </span>
            <span class="pbadge">{{ recurringList.length }}</span>
          </div>
          <div class="recur-list">
            <div v-for="r in recurringList" :key="r.id" class="recur-item">
              <span class="recur-bar" :style="{ background: r.isOpEx ? OPEX_COLOR : (projectColorById[r.projectId] || '#a855f7') }"></span>
              <div class="recur-info">
                <div class="recur-desc">{{ r.description }}</div>
                <div class="recur-meta">
                  {{ r.isOpEx ? 'OpEx' : r.projectName }} · {{ fmtFrequency(r.pattern) }}
                  <span v-if="r.dayOfMonth"> · {{ r.dayOfMonth }}η</span>
                </div>
              </div>
              <div class="recur-amount" :style="{ color: r.type === 'income' ? '#10b981' : '#ef4444' }">
                {{ r.type === 'income' ? '+' : '' }}{{ fmtMoney(r.monthly) }}
              </div>
            </div>
          </div>
        </div>
      </div>

    </template>

  </div>
</template>

<style scoped>
.recurring-page {
  padding: 20px 24px;
  color: #e4e6eb;
  background: var(--bg, #0d1117);
  min-height: 100vh;
}

.page-hdr {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  flex-wrap: wrap;
  gap: 12px;
}
.hdr-left { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.hdr-icon {
  font-size: 22px;
  color: #06b6d4;
  background: rgba(6,182,212,0.12);
  width: 38px; height: 38px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 8px;
}
.page-hdr h1 { margin: 0; font-size: 20px; font-weight: 600; color: #fff; }
.hdr-sub { font-size: 12px; color: #8b949e; margin-left: 4px; }
.hdr-right { display: flex; gap: 8px; align-items: center; }
.entity-sel {
  background: #161b22; color: #e4e6eb;
  border: 1px solid #30363d; border-radius: 6px;
  padding: 7px 12px; font-size: 13px; cursor: pointer;
}
.entity-sel:hover { border-color: #06b6d4; }
.refresh-btn {
  background: #21262d; color: #e4e6eb;
  border: 1px solid #30363d; border-radius: 6px;
  padding: 7px 14px; font-size: 13px; cursor: pointer;
  display: inline-flex; align-items: center; gap: 6px;
}
.refresh-btn:hover:not(:disabled) { background: #30363d; }
.refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.scope-bar {
  display: flex; align-items: center; gap: 6px;
  background: #161b22; border: 1px solid #30363d;
  border-radius: 8px; padding: 6px; margin-bottom: 14px;
  flex-wrap: wrap;
}
.scope-btn {
  background: transparent; color: #8b949e;
  border: 1px solid transparent;
  padding: 6px 12px; border-radius: 6px;
  font-size: 12px; cursor: pointer;
  display: inline-flex; align-items: center; gap: 6px;
  transition: all 0.15s;
}
.scope-btn:hover { color: #e4e6eb; background: #1c2128; }
.scope-btn.active {
  background: #1c2128; color: #e4e6eb;
  border-color: #30363d; font-weight: 500;
}
.scope-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
}
.scope-divider { width: 1px; height: 18px; background: #30363d; margin: 0 4px; }

/* S83: Cost view mode toggle */
.mode-toggle-wrap {
  margin-left: auto;
  display: flex; align-items: center; gap: 8px;
}
.mode-toggle-lbl {
  font-size: 10px; color: #8b949e; letter-spacing: 0.5px;
  text-transform: uppercase; font-weight: 500;
}
.mode-toggle {
  display: inline-flex; gap: 0;
  background: #0d1117; border: 1px solid #30363d; border-radius: 6px;
  padding: 2px;
}
.mode-btn {
  background: transparent; color: #8b949e;
  border: none; padding: 5px 10px; border-radius: 4px;
  font-size: 11px; cursor: pointer; font-weight: 500;
  display: inline-flex; align-items: center; gap: 5px;
  transition: all 0.15s;
}
.mode-btn:hover { color: #e4e6eb; }
.mode-btn.active {
  background: #06b6d4; color: #fff;
}
.mode-btn i { font-size: 10px; }

/* S83: Allocation banner (shown in Fully Loaded mode) */
.alloc-banner {
  background: rgba(6,182,212,0.08);
  border: 1px solid rgba(6,182,212,0.3);
  color: #79c0ff;
  border-radius: 8px; padding: 10px 14px; margin-bottom: 14px;
  font-size: 12px; display: flex; align-items: center; gap: 10px;
  line-height: 1.5;
}
.alloc-banner i { color: #06b6d4; font-size: 14px; flex-shrink: 0; }
.alloc-banner strong { color: #e4e6eb; font-weight: 600; }
.alloc-sub {
  display: block; font-size: 11px; color: #8b949e;
  font-style: italic; margin-top: 2px;
}

.err-bar {
  background: #3d1a1a; color: #ff8a80;
  border: 1px solid #5c2626; border-radius: 8px;
  padding: 12px 16px; margin-bottom: 14px;
}
.load-wrap { text-align: center; padding: 60px 20px; color: #8b949e; }
.spinner {
  width: 30px; height: 30px;
  border: 3px solid #30363d; border-top-color: #06b6d4;
  border-radius: 50%; margin: 0 auto 12px;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  text-align: center; padding: 60px 20px;
  background: #161b22; border: 1px solid #30363d; border-radius: 12px;
}
.empty-icon { font-size: 48px; color: #30363d; margin-bottom: 12px; }
.empty-state h3 { margin: 0 0 8px; color: #e4e6eb; font-weight: 600; }
.empty-state p { color: #8b949e; max-width: 480px; margin: 0 auto 8px; }
.empty-hint { font-size: 13px; color: #6e7681; }
.empty-hint em { color: #ffa657; font-style: normal; }
.empty-hint strong { color: #79c0ff; font-weight: 600; }

.kpi-row {
  display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 12px; margin-bottom: 14px;
}
.kpi-card {
  background: #161b22; border: 1px solid #30363d;
  border-radius: 10px; padding: 14px 16px;
}
.kpi-hdr { display: flex; align-items: center; gap: 6px; margin-bottom: 2px; }
.kpi-ico { font-size: 14px; }
.kpi-title { font-size: 12px; font-weight: 600; color: #ef4444; letter-spacing: 0.6px; }
.kpi-sub-gr { font-size: 10px; color: #8b949e; font-style: italic; margin-bottom: 10px; }
.kpi-val {
  font-size: 24px; font-weight: 600; color: #e4e6eb;
  letter-spacing: -0.5px; font-variant-numeric: tabular-nums;
}
.kpi-foot { display: flex; gap: 6px; align-items: center; margin-top: 8px; font-size: 11px; }

.mid-row {
  display: grid; grid-template-columns: 2fr 1fr;
  gap: 12px; margin-bottom: 14px;
}

.panel-card {
  background: #161b22; border: 1px solid #30363d;
  border-radius: 10px; padding: 14px 16px;
}
.panel-hdr {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}
.ptitle {
  font-size: 13px; font-weight: 600; color: #e4e6eb;
  display: inline-flex; align-items: center; gap: 8px;
}
.ptitle i { color: #06b6d4; }
.ptitle-sub {
  font-size: 11px; color: #8b949e; font-style: italic;
  font-weight: 400; margin-left: 4px;
}
.pbadge {
  font-size: 11px; padding: 2px 8px;
  background: #0d1117; border: 1px solid #30363d;
  border-radius: 4px; color: #8b949e;
}

.pp-table { width: 100%; font-size: 12px; border-collapse: collapse; }
.pp-table th {
  text-align: left; padding: 6px 0;
  font-weight: 500; font-size: 10px;
  color: #8b949e; letter-spacing: 0.5px;
}
.pp-table th.num { text-align: right; }
.pp-table th.center { text-align: center; }
.pp-table td { padding: 9px 0; border-top: 1px solid #21262d; }
.pp-table td.num { text-align: right; font-variant-numeric: tabular-nums; }
.pp-table td.center { text-align: center; }
.pp-table td.neg { color: #ef4444; }
.pp-table td.pos { color: #10b981; }
.pp-table td.muted { color: #5f7d9a; }
.pp-table td.strong { font-weight: 600; }
/* S83: allocated OpEx column */
.pp-table td.alloc { color: #f59e0b; font-weight: 500; }
.proj-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 8px; }
.status-pill { font-size: 10px; padding: 2px 8px; border-radius: 4px; }

.lbl-sub {
  font-size: 9px; color: #5f7d9a;
  font-style: italic; font-weight: 400; margin-left: 2px;
}

.runway-input-wrap { margin-bottom: 14px; }
.runway-input-lbl { font-size: 10px; color: #8b949e; margin-bottom: 4px; letter-spacing: 0.5px; }
.runway-input-row { display: flex; gap: 4px; align-items: center; }
.runway-input-row input {
  background: #0d1117; border: 1px solid #30363d;
  color: #e4e6eb; padding: 8px 10px; border-radius: 6px;
  font-size: 16px; font-weight: 600; flex: 1;
  font-variant-numeric: tabular-nums;
}
.runway-input-row input:focus { outline: none; border-color: #06b6d4; }
.runway-cur { color: #8b949e; font-size: 14px; }
.runway-result {
  background: #0d1117; border-radius: 8px;
  padding: 14px; text-align: center;
}
.runway-lbl { font-size: 10px; color: #8b949e; letter-spacing: 0.5px; margin-bottom: 6px; }
.runway-months {
  font-size: 32px; font-weight: 600;
  color: #10b981; font-variant-numeric: tabular-nums;
}
.runway-unit { font-size: 13px; color: #8b949e; margin-left: 6px; font-weight: 400; }
.runway-note { font-size: 11px; color: #8b949e; line-height: 1.6; margin-top: 8px; }
.runway-placeholder {
  font-size: 12px; color: #8b949e;
  padding: 10px; display: flex; align-items: center;
  justify-content: center; gap: 8px;
}

.pareto-panel { margin-bottom: 14px; }
.pareto-list { display: flex; flex-direction: column; gap: 6px; }
.pareto-row { display: flex; align-items: center; gap: 10px; }
.pareto-rank { font-size: 11px; width: 22px; text-align: center; color: #8b949e; font-weight: 600; }
.pareto-desc {
  font-size: 12px; width: 200px; flex-shrink: 0; color: #e4e6eb;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.pareto-pill {
  font-size: 10px; padding: 2px 8px; border-radius: 3px;
  min-width: 80px; text-align: center; font-weight: 500;
}
.pareto-bar-wrap { flex: 1; height: 22px; background: #0d1117; border-radius: 3px; overflow: hidden; }
.pareto-bar { height: 100%; border-radius: 3px; transition: width 0.4s ease; }
.pareto-amount {
  font-size: 12px; font-weight: 600; width: 90px;
  text-align: right; color: #ef4444; font-variant-numeric: tabular-nums;
}
.pareto-pct { font-size: 10px; width: 42px; text-align: right; color: #8b949e; }
.pareto-foot {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 14px; padding-top: 10px;
  border-top: 1px solid #30363d; font-size: 11px; color: #8b949e;
}
.pareto-foot strong { color: #e4e6eb; font-weight: 600; }

.bot-row { display: grid; grid-template-columns: 3fr 2fr; gap: 12px; margin-bottom: 14px; }

.trend-chart { height: 220px; margin-bottom: 12px; }
.trend-footer {
  display: flex; justify-content: space-around;
  padding-top: 10px; border-top: 1px solid #30363d; font-size: 11px;
}
.trend-metric { text-align: center; }
.tm-lbl { color: #8b949e; margin-bottom: 4px; }
.tm-val { font-weight: 600; font-size: 14px; }

.list-panel { max-height: 360px; display: flex; flex-direction: column; overflow: hidden; }
.recur-list { display: flex; flex-direction: column; gap: 6px; overflow-y: auto; max-height: 290px; }
.recur-item {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 10px; background: #0d1117; border-radius: 6px;
}
.recur-bar { width: 3px; height: 30px; border-radius: 2px; flex-shrink: 0; }
.recur-info { flex: 1; min-width: 0; }
.recur-desc {
  font-size: 12px; color: #e4e6eb;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.recur-meta { font-size: 10px; color: #8b949e; margin-top: 2px; }
.recur-amount {
  font-size: 13px; font-weight: 600; text-align: right;
  font-variant-numeric: tabular-nums; white-space: nowrap;
}

@media (max-width: 1100px) {
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
  .mid-row, .bot-row { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .kpi-row { grid-template-columns: 1fr; }
  .recurring-page { padding: 12px; }
}
</style>
