<template>
  <div class="calendar-view">
    <!-- Top filter row -->
    <div class="filter-row">
      <h1 class="title">Ημερολόγιο</h1>

      <div class="controls">
        <select v-model="entryModeFilter" class="mode-select" :disabled="loading">
          <option value="ACTUAL">🔥 Πραγματικές</option>
          <option value="PLANNED">📅 Προγραμματισμένες</option>
          <option value="ALL">📊 Όλες</option>
        </select>

        <select v-model="selectedEntity" class="entity-select" :disabled="loading">
          <option v-if="isAdmin" value="ALL">Όλος ο Όμιλος</option>
          <option v-for="ent in entities" :key="ent.id" :value="ent.id">{{ ent.name }}</option>
        </select>

        <button class="nav-btn" @click="prevMonth" :disabled="loading" aria-label="Previous month">‹</button>
        <span class="month-label">{{ monthLabel }}</span>
        <button class="nav-btn" @click="nextMonth" :disabled="loading" aria-label="Next month">›</button>
        <button class="today-btn" @click="goToToday" :disabled="loading">Σήμερα</button>
      </div>
    </div>

    <!-- KPI cards -->
    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-value kpi-income">{{ fmtCurrency(filteredKpis.totalIncome, true) }}</div>
        <div class="kpi-label">Έσοδα μήνα</div>
        <div class="kpi-sub">{{ scope === 'group' ? 'σύνολο εσόδων ομίλου' : 'σύνολο εσόδων' }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value kpi-expense">{{ fmtCurrency(filteredKpis.totalExpenses, false, true) }}</div>
        <div class="kpi-label">Έξοδα μήνα</div>
        <div class="kpi-sub">{{ scope === 'group' ? 'σύνολο εξόδων ομίλου' : 'σύνολο εξόδων' }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value">{{ fmtCurrency(filteredKpis.netFlow, true) }}</div>
        <div class="kpi-label">Net flow</div>
        <div class="kpi-sub">έσοδα − έξοδα</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-value">{{ fmtCurrency(filteredKpis.endOfMonthCash) }}</div>
        <div class="kpi-label">Cash τέλους μήνα</div>
        <div class="kpi-sub">εκτίμηση balance</div>
      </div>
    </div>

    <!-- Group view banner -->
    <div v-if="scope === 'group'" class="info-banner">
      <strong>Group view</strong> — Group view — κάθε μέρα δείχνει το άθροισμα όλων των εταιρειών. Για ανάλυση ανά συναλλαγή, διάλεξε συγκεκριμένη εταιρεία.
    </div>

    <!-- Error -->
    <div v-if="errorMsg" class="error-banner">Σφάλμα κατά την φόρτωση: {{ errorMsg }}</div>

    <!-- Calendar card -->
    <div class="calendar-card">
      <div class="dow-row">
        <div v-for="d in dowLabels" :key="d" class="dow-cell">{{ d }}</div>
      </div>

      <div v-for="(week, wIdx) in calendarWeeks" :key="wIdx" class="week-row">
        <div
          v-for="(cell, cIdx) in week"
          :key="cIdx"
          class="day-cell"
          :class="dayCellClass(cell)"
        >
          <template v-if="cell">
            <div class="day-header">
              <span class="day-num">{{ cell.dayOfMonth }}</span>
              <span v-if="cell.isToday" class="today-badge">ΣΉΜΕΡΑ</span>
            </div>

            <!-- Per-transaction view (single entity) -->
            <template v-if="scope === 'single' && cell.transactions && cell.transactions.length">
              <div
                v-for="tx in filteredTransactions(cell.transactions)"
                :key="tx.id"
                class="tx-box"
                :class="txBoxClass(tx, cell.isWeekend)"
                :title="txTooltip(tx)"
              >
                <div class="tx-amount">{{ tx.type === 'income' ? '+' : '−' }}{{ fmtAmount(tx.amount) }} €</div>
                <div class="tx-meta">
                  <span v-if="cell.isWeekend && tx.entryMode === 'PLANNED'" class="weekend-flag" :title="'Πέφτει σαββατοκύριακο — πιθανώς θα γίνει επόμενη εργάσιμη'">⚠ </span>{{ tx.category }}{{ tx.projectName ? ' · ' + tx.projectName : '' }}
                </div>
              </div>
            </template>

            <!-- Group view aggregates -->
            <template v-else-if="scope === 'group' && cell.aggregates">
              <div
                v-if="showIncome(cell.aggregates)"
                class="tx-box agg-box"
                :class="aggIncomeClass(cell.aggregates, cell.isWeekend)"
              >
                <div class="tx-amount">+{{ fmtAmount(aggIncomeTotal(cell.aggregates)) }} €</div>
                <div class="tx-meta">
                  <span v-if="cell.isWeekend && cell.aggregates.plannedIncomeTotal > 0" class="weekend-flag">⚠ </span>{{ aggIncomeCount(cell.aggregates) }} {{ aggIncomeCount(cell.aggregates) === 1 ? 'έσοδο' : 'έσοδα' }}
                </div>
              </div>
              <div
                v-if="showExpense(cell.aggregates)"
                class="tx-box agg-box"
                :class="aggExpenseClass(cell.aggregates, cell.isWeekend)"
              >
                <div class="tx-amount">−{{ fmtAmount(aggExpenseTotal(cell.aggregates)) }} €</div>
                <div class="tx-meta">
                  <span v-if="cell.isWeekend && cell.aggregates.plannedExpenseTotal > 0" class="weekend-flag">⚠ </span>{{ aggExpenseCount(cell.aggregates) }} {{ aggExpenseCount(cell.aggregates) === 1 ? 'έξοδο' : 'έξοδα' }}
                </div>
              </div>
            </template>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import api from '../api'

let userObj = {}
try { userObj = JSON.parse(localStorage.getItem('n2c_user') || '{}') } catch (e) { userObj = {} }
const isAdmin = computed(() => (userObj.role || '').toUpperCase() === 'ADMIN')

const entities = ref([])
const selectedEntity = ref('')
const entryModeFilter = ref(localStorage.getItem('n2c_calendar_mode') || 'ACTUAL')

const today = new Date()
const viewYear  = ref(today.getFullYear())
const viewMonth = ref(today.getMonth() + 1)

const loading  = ref(false)
const errorMsg = ref('')
const calData  = ref({ year: 0, month: 0, entityScope: 'single', kpis: {}, days: [] })

const dowLabels = ["ΔΕΥΤΕΡΑ","ΤΡΙΤΗ","ΤΕΤΑΡΤΗ","ΠΕΜΠΤΗ","ΠΑΡΑΣΚΕΥΗ","ΣΑΒΒΑΤΟ","ΚΥΡΙΑΚΗ"]
const MONTH_NAMES = ["Ιανουάριος","Φεβρουάριος","Μάρτιος","Απρίλιος","Μάιος","Ιούνιος","Ιούλιος","Αύγουστος","Σεπτέμβριος","Οκτώβριος","Νοέμβριος","Δεκέμβριος"]

const monthLabel = computed(() => MONTH_NAMES[viewMonth.value - 1] + ' ' + viewYear.value)
const scope = computed(() => calData.value.entityScope || 'single')

const calendarWeeks = computed(() => {
  const days = calData.value.days || []
  if (!days.length) return []
  const first = days[0]
  const leadingBlanks = (first.dayOfWeek || 1) - 1
  const cells = []
  for (let i = 0; i < leadingBlanks; i++) cells.push(null)
  for (const d of days) cells.push(d)
  while (cells.length % 7 !== 0) cells.push(null)
  const weeks = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))
  return weeks
})

// ── Filtered KPIs based on entry-mode filter ─────────────────────────
const filteredKpis = computed(() => {
  const days = calData.value.days || []
  let inc = 0, exp = 0
  const mode = entryModeFilter.value
  for (const d of days) {
    if (scope.value === 'single' && d.transactions) {
      for (const t of d.transactions) {
        if (mode !== 'ALL' && (t.entryMode || 'ACTUAL') !== mode) continue
        const amt = Number(t.amount || 0)
        if (t.type === 'income') inc += amt
        else if (t.type === 'expense') exp += amt
      }
    } else if (scope.value === 'group' && d.aggregates) {
      const a = d.aggregates
      if (mode === 'ACTUAL') {
        inc += Number(a.actualIncomeTotal || 0)
        exp += Number(a.actualExpenseTotal || 0)
      } else if (mode === 'PLANNED') {
        inc += Number(a.plannedIncomeTotal || 0)
        exp += Number(a.plannedExpenseTotal || 0)
      } else {
        inc += Number(a.incomeTotal || 0)
        exp += Number(a.expenseTotal || 0)
      }
    }
  }
  const endCash = (calData.value.kpis && calData.value.kpis.endOfMonthCash) || 0
  return {
    totalIncome: inc,
    totalExpenses: exp,
    netFlow: inc - exp,
    endOfMonthCash: endCash
  }
})

// ── Filter transactions per cell ─────────────────────────────────────
function filteredTransactions(arr) {
  const mode = entryModeFilter.value
  if (mode === 'ALL') return arr
  return arr.filter(t => (t.entryMode || 'ACTUAL') === mode)
}

// ── Aggregate filtered totals (group view) ───────────────────────────
function aggIncomeTotal(a) {
  if (entryModeFilter.value === 'ACTUAL')  return Number(a.actualIncomeTotal || 0)
  if (entryModeFilter.value === 'PLANNED') return Number(a.plannedIncomeTotal || 0)
  return Number(a.incomeTotal || 0)
}
function aggExpenseTotal(a) {
  if (entryModeFilter.value === 'ACTUAL')  return Number(a.actualExpenseTotal || 0)
  if (entryModeFilter.value === 'PLANNED') return Number(a.plannedExpenseTotal || 0)
  return Number(a.expenseTotal || 0)
}
function aggIncomeCount(a) {
  // we only have plannedIncomeTotal/actualIncomeTotal — derive count proportionally
  // for simplicity, just show the total count when ALL, or estimate when filtered
  const total = Number(a.incomeTotal || 0)
  if (total === 0) return 0
  if (entryModeFilter.value === 'ALL') return a.incomeCount || 0
  // estimate: split count proportionally to amount
  const filteredAmt = aggIncomeTotal(a)
  return Math.round((filteredAmt / total) * (a.incomeCount || 0))
}
function aggExpenseCount(a) {
  const total = Number(a.expenseTotal || 0)
  if (total === 0) return 0
  if (entryModeFilter.value === 'ALL') return a.expenseCount || 0
  const filteredAmt = aggExpenseTotal(a)
  return Math.round((filteredAmt / total) * (a.expenseCount || 0))
}
function showIncome(a) { return aggIncomeTotal(a) > 0 }
function showExpense(a) { return aggExpenseTotal(a) > 0 }

// ── Fetchers ─────────────────────────────────────────────────────────
async function fetchEntities() {
  try {
    const res = await api.get('/api/config/entities')
    let list = []
    if (Array.isArray(res.data)) list = res.data
    else if (res.data && Array.isArray(res.data.data)) list = res.data.data
    else if (res.data && Array.isArray(res.data.entities)) list = res.data.entities
    entities.value = list
    if (!selectedEntity.value) {
      const saved = localStorage.getItem('n2c_calendar_entity')
      if (saved && (saved === 'ALL' || entities.value.some(e => e.id === saved))) {
        selectedEntity.value = saved
      } else if (isAdmin.value) {
        selectedEntity.value = 'ALL'
      } else if (entities.value.length > 0) {
        selectedEntity.value = entities.value[0].id
      }
    }
  } catch (e) {
    errorMsg.value = 'entities load failed: ' + ((e.response && e.response.status) || e.message)
  }
}

async function fetchCalendar() {
  if (!selectedEntity.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    const params = { entityId: selectedEntity.value, year: viewYear.value, month: viewMonth.value }
    const r = await api.get('/api/calendar', { params })
    calData.value = r.data || { kpis: {}, days: [] }
  } catch (e) {
    errorMsg.value = (e.response && e.response.data && e.response.data.message) || e.message || 'unknown'
    calData.value = { year: viewYear.value, month: viewMonth.value, entityScope: 'single', kpis: {}, days: [] }
  } finally {
    loading.value = false
  }
}

function prevMonth() {
  if (viewMonth.value === 1) { viewMonth.value = 12; viewYear.value -= 1 }
  else { viewMonth.value -= 1 }
}
function nextMonth() {
  if (viewMonth.value === 12) { viewMonth.value = 1; viewYear.value += 1 }
  else { viewMonth.value += 1 }
}
function goToToday() {
  const t = new Date()
  viewYear.value  = t.getFullYear()
  viewMonth.value = t.getMonth() + 1
}

function fmtCurrency(v, signed = false, forceMinus = false) {
  const n = Number(v || 0)
  const abs = Math.abs(n)
  const s = abs.toLocaleString('el-GR', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
  let prefix = ''
  if (forceMinus && abs > 0) prefix = '−'
  else if (signed && n > 0) prefix = '+'
  else if (signed && n < 0) prefix = '−'
  return prefix + s + ' €'
}
function fmtAmount(v) {
  return Math.abs(Number(v || 0)).toLocaleString('el-GR', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}
function dayCellClass(cell) {
  if (!cell) return ['empty']
  const cls = []
  if (cell.isWeekend) cls.push('weekend')
  if (cell.isToday) cls.push('today')
  return cls
}
function txBoxClass(tx, isWeekend) {
  const cls = []
  cls.push(tx.type === 'income' ? 'income' : 'expense')
  cls.push(tx.entryMode === 'PLANNED' ? 'planned' : 'actual')
  if (isWeekend && tx.entryMode === 'PLANNED') cls.push('weekend-warning')
  return cls
}
function aggIncomeClass(ag, isWeekend) {
  const cls = ['income']
  if (entryModeFilter.value === 'PLANNED') cls.push('planned')
  else if (entryModeFilter.value === 'ACTUAL') cls.push('actual')
  else {
    if (ag.plannedIncomeTotal > 0 && ag.actualIncomeTotal === 0) cls.push('planned')
    else if (ag.actualIncomeTotal > 0 && ag.plannedIncomeTotal === 0) cls.push('actual')
    else cls.push('mixed')
  }
  if (isWeekend && ag.plannedIncomeTotal > 0) cls.push('weekend-warning')
  return cls
}
function aggExpenseClass(ag, isWeekend) {
  const cls = ['expense']
  if (entryModeFilter.value === 'PLANNED') cls.push('planned')
  else if (entryModeFilter.value === 'ACTUAL') cls.push('actual')
  else {
    if (ag.plannedExpenseTotal > 0 && ag.actualExpenseTotal === 0) cls.push('planned')
    else if (ag.actualExpenseTotal > 0 && ag.plannedExpenseTotal === 0) cls.push('actual')
    else cls.push('mixed')
  }
  if (isWeekend && ag.plannedExpenseTotal > 0) cls.push('weekend-warning')
  return cls
}
function txTooltip(tx) {
  let s = (tx.entryMode || 'ACTUAL') + ' · ' + (tx.category || '')
  if (tx.description) s += ' · ' + tx.description
  if (tx.projectName) s += ' · ' + tx.projectName
  return s
}

watch(selectedEntity, (v) => {
  if (v) localStorage.setItem('n2c_calendar_entity', v)
  fetchCalendar()
})
watch(entryModeFilter, (v) => {
  localStorage.setItem('n2c_calendar_mode', v)
})
watch([viewYear, viewMonth], () => fetchCalendar())

function onEntityChanged(e) {
  if (e && e.detail && e.detail.entityId) selectedEntity.value = e.detail.entityId
}

onMounted(async () => {
  await fetchEntities()
  await fetchCalendar()
  window.addEventListener('entity-changed', onEntityChanged)
})
</script>

<style scoped>
.calendar-view {
  padding: 24px 28px;
  color: #e8eef5;
}

/* Filter row */
.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 22px;
}
.title { font-size: 28px; font-weight: 700; margin: 0; color: #fff; }
.controls { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.entity-select, .mode-select {
  background: #1f2a3f;
  border: 1px solid #2f3d57;
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 15px;
  color: #fff;
  min-width: 180px;
  cursor: pointer;
  font-weight: 500;
}
.entity-select { min-width: 220px; }
.nav-btn, .today-btn {
  background: #1f2a3f;
  border: 1px solid #2f3d57;
  border-radius: 6px;
  padding: 10px 16px;
  font-size: 17px;
  color: #fff;
  cursor: pointer;
  min-width: 42px;
  font-weight: 600;
}
.nav-btn:hover, .today-btn:hover { background: #2a3855; }
.nav-btn:disabled, .today-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.month-label { font-size: 18px; font-weight: 700; min-width: 180px; text-align: center; color: #fff; }

/* KPI cards */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.kpi-card {
  background: #fff;
  border: 1px solid #d8dde6;
  border-radius: 8px;
  padding: 20px 22px;
  text-align: center;
}
.kpi-value { font-size: 30px; font-weight: 700; color: #0a1628; margin-bottom: 8px; line-height: 1.15; }
.kpi-value.kpi-income { color: #0f6e56; }
.kpi-value.kpi-expense { color: #a32d2d; }
.kpi-label { font-size: 13px; color: #2c3e50; letter-spacing: 1px; text-transform: uppercase; margin-bottom: 4px; font-weight: 700; }
.kpi-sub { font-size: 12px; color: #5a6a85; font-style: italic; }

/* Banners */
.info-banner {
  background: rgba(29,158,117,0.15);
  border-left: 4px solid #1d9e75;
  border-radius: 4px;
  padding: 14px 18px;
  margin-bottom: 16px;
  font-size: 14px;
  color: #5dcaa5;
  font-weight: 500;
}
.info-banner strong { color: #1d9e75; font-weight: 700; }
.error-banner {
  background: rgba(226,75,74,0.15);
  border-left: 4px solid #e24b4a;
  border-radius: 4px;
  padding: 14px 18px;
  margin-bottom: 16px;
  font-size: 14px;
  color: #f09595;
  font-weight: 500;
}

/* Calendar card */
.calendar-card {
  background: #fff;
  border: 1px solid #d8dde6;
  border-radius: 8px;
  padding: 18px;
}

.dow-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
  margin-bottom: 10px;
}
.dow-cell {
  text-align: center;
  font-size: 14px;
  color: #0a1628;
  padding: 10px 0;
  font-weight: 800;
  letter-spacing: 0.8px;
  text-transform: uppercase;
  background: #eef1f5;
  border-radius: 4px;
}

.week-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
  margin-bottom: 8px;
  align-items: stretch;
}

.day-cell {
  background: #fff;
  border: 2px solid #c4cad6;
  border-radius: 6px;
  padding: 10px;
  min-height: 110px;
  display: flex;
  flex-direction: column;
}
.day-cell.empty { background: transparent; border: none; }
.day-cell.weekend { background: #e8ecf2; border-color: #b0b8c7; }
.day-cell.today { background: #fff5b8; border: 3px solid #d9a300; padding: 9px; }

.day-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.day-num { font-size: 22px; color: #0a1628; font-weight: 800; line-height: 1; }
.day-cell.weekend .day-num { color: #2c3e50; }
.day-cell.today .day-num { font-size: 26px; color: #5a4100; font-weight: 900; }
.today-badge {
  background: #d9a300;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  padding: 4px 9px;
  border-radius: 4px;
  letter-spacing: 0.8px;
}

/* Transaction boxes */
.tx-box {
  border-radius: 0 4px 4px 0;
  padding: 6px 9px;
  margin-top: 5px;
  border-left: 5px solid #ccc;
}
.tx-box.income { background: #d8ecc4; border-left-color: #0f6e56; }
.tx-box.expense { background: #f8d4d4; border-left-color: #a32d2d; }
.tx-box.planned { border-left-style: dashed; }
.tx-box.actual { border-left-style: solid; }
.tx-box.mixed { border-left-style: solid; }

.tx-amount {
  font-size: 16px;
  font-weight: 800;
  line-height: 1.25;
}
.tx-box.income .tx-amount { color: #08533f; }
.tx-box.expense .tx-amount { color: #7a1818; }
.tx-meta {
  font-size: 12px;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.tx-box.income .tx-meta { color: #08533f; }
.tx-box.expense .tx-meta { color: #7a1818; }

.weekend-flag { color: #8b6508; font-weight: 800; margin-right: 2px; }

.agg-box .tx-amount { font-size: 18px; }
.agg-box .tx-meta { font-size: 13px; font-weight: 700; }

@media (max-width: 960px) {
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
}
</style>
