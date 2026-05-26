<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import api from '@/api'

// ── Filters ────────────────────────────────────────────────────────
const selectedYear   = ref('all')
const selectedReport = ref('subcategory')
const selectedType   = ref('all')
// S68: PLANNED mode toggle — values: 'actual' | 'planned' | 'all'
const selectedMode = ref(localStorage.getItem('reportsViewMode') || 'actual')

// S76: Project filter — UUID of project, 'all' (default), or 'opex' (projectId IS NULL)
const selectedProject = ref(localStorage.getItem('reportsViewProject') || 'all')
const projectsList = ref([])

async function loadProjects() {
  try {
    // S77-PATCH-APPLIED: entity-scoped — pass current entity so backend filters properly
    const entityKey = localStorage.getItem('n2c_entity') || 'next2me'
    const entityId = ENTITY_MAP[entityKey] || null
    const params = entityId ? { entityId } : {}
    const res = await api.get('/api/projects', { params })
    const data = res.data?.data || res.data || []
    projectsList.value = (Array.isArray(data) ? data : [])
      .filter(p => p.isActive !== false)
      .sort((a, b) => (a.name || '').localeCompare(b.name || '', 'el'))
  } catch (err) {
    console.error('Reports: failed to load projects', err)
    projectsList.value = []
  }
}

const allYears = ['2017','2018','2019','2020','2021','2022','2023','2024','2025','2026']
const years = allYears  // used in yearly/budget tables
const reports = [
  { value: 'monthly',    label: 'Μηνιαία Ανάλυση' },
  { value: 'yearly',     label: 'Ετήσια Σύγκριση' },
  { value: 'budget',     label: 'Ανάλυση Προϋπολογισμού' },
  { value: 'category',   label: 'Ανά Κατηγορία' },
  { value: 'subcategory',label: 'Ανά Υποκατηγορία' },
]
const types = [
  { value: 'all',      label: 'Όλα' },
  { value: 'payments', label: 'Πληρωμές' },
  { value: 'income',   label: 'Εισπράξεις' },
]

// ── State ─────────────────────────────────────────────────────────
const allTransactions = ref([])
const loading = ref(false)

const ENTITY_MAP = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  next2megroup: '50317f44-9961-4fb4-add0-7a118e32dc14',
}

const MONTH_NAMES = [
  'Ιανουάριος',
  'Φεβρουάριος',
  'Μάρτιος',
  'Απρίλιος',
  'Μάιος',
  'Ιούνιος',
  'Ιούλιος',
  'Αύγουστος',
  'Σεπτέμβριος',
  'Οκτώβριος',
  'Νοέμβριος',
  'Δεκέμβριος',
]

const BUDGET_COLORS = ['#29b6f6','#ab47bc','#4FC3A1','#ff9800','#ef5350','#ff5252','#e91e63','#8bc34a','#ff9800']

// ── Fetch transactions ──────────────────────────────────────────
async function loadTransactions() {
  const entityKey = localStorage.getItem('n2c_entity') || 'next2me'
  const entityId = ENTITY_MAP[entityKey]
  if (!entityId) return

  loading.value = true
  try {
    const res = await api.get('/api/transactions', {
      params: { entityId, page: 0, perPage: 9999 }
    })
    const data = res.data?.data || res.data || []
    // Only active transactions
    allTransactions.value = (Array.isArray(data) ? data : []).filter(
      t => (t.recordStatus || 'active') === 'active'
    )
  } catch (err) {
    console.error('Reports: failed to load transactions', err)
    allTransactions.value = []
  } finally {
    loading.value = false
  }
}

// ── Active transactions for selected year ─────────────────────────
// S68: mode-aware base — single source of truth for all downstream reports
// 'actual' (default) excludes PLANNED, 'planned' shows only PLANNED, 'all' shows both
const modeFilteredTransactions = computed(() => {
  // S76: First mode filter, then project filter
  let result = allTransactions.value
  const mode = selectedMode.value
  if (mode === 'planned') {
    result = result.filter(t => (t.entryMode || 'ACTUAL').toUpperCase() === 'PLANNED')
  } else if (mode !== 'all') {
    // default: actual
    result = result.filter(t => (t.entryMode || 'ACTUAL').toUpperCase() === 'ACTUAL')
  }
  // S76: Apply project filter
  const proj = selectedProject.value
  if (proj === 'all') return result
  if (proj === 'opex') return result.filter(t => !t.projectId)
  return result.filter(t => t.projectId === proj)
})

const yearTransactions = computed(() => {
  if (selectedYear.value === 'all') return modeFilteredTransactions.value.filter(t => !!t.docDate)
  return modeFilteredTransactions.value.filter(t => {
    if (!t.docDate) return false
    return t.docDate.substring(0, 4) === selectedYear.value
  })
})

// ── REPORT 1: Subcategory Data ────────────────────────────────
const subcategoryData = computed(() => {
  const map = {}
  yearTransactions.value.forEach(t => {
    const sub = t.subcategory || t.category || 'N/A'
    if (!map[sub]) map[sub] = { name: sub, income: 0, payments: 0, moves: 0 }
    map[sub].moves++
    if (t.type === 'income') {
      map[sub].income += Number(t.amount) || 0
    } else {
      map[sub].payments += Number(t.amount) || 0
    }
  })
  return Object.values(map).sort((a, b) => (b.income + b.payments) - (a.income + a.payments))
})

// ── REPORT 2: Category Data ───────────────────────────────────
const categoryData = computed(() => {
  const map = {}
  yearTransactions.value.forEach(t => {
    const cat = t.category || 'N/A'
    if (!map[cat]) map[cat] = { name: cat, amount: 0, moves: 0, incomeAmount: 0 }
    map[cat].moves++
    const amt = Number(t.amount) || 0
    if (t.type === 'expense') {
      map[cat].amount += amt
    } else {
      map[cat].incomeAmount += amt
    }
  })
  const rows = Object.values(map)
  const totalExpense = rows.reduce((s, r) => s + r.amount, 0)
  return rows.map(r => ({
    name: r.name,
    amount: r.amount,
    pct: totalExpense > 0 ? (r.amount / totalExpense) * 100 : 0,
    moves: r.moves,
    avg: r.moves > 0 ? r.amount / r.moves : 0,
  })).sort((a, b) => b.amount - a.amount)
})

// ── REPORT 3: Yearly Category Data (all years) ─────────────────
const yearlyCategoryData = computed(() => {
  const map = {}
  modeFilteredTransactions.value.forEach(t => {
    if (!t.docDate || t.type !== 'expense') return
    const cat = t.category || 'N/A'
    const yr = t.docDate.substring(0, 4)
    if (!map[cat]) {
      map[cat] = { name: cat, years: {}, total: 0 }
      years.forEach(y => { map[cat].years[y] = 0 })
    }
    const amt = Number(t.amount) || 0
    if (map[cat].years[yr] !== undefined) {
      map[cat].years[yr] += amt
    }
    map[cat].total += amt
  })
  return Object.values(map).sort((a, b) => b.total - a.total)
})

const yearlyTotals = computed(() => {
  const t = {}
  years.forEach(y => { t[y] = yearlyCategoryData.value.reduce((s, r) => s + (r.years[y] || 0), 0) })
  return t
})
const yearlyGrandTotal = computed(() => yearlyCategoryData.value.reduce((s, r) => s + r.total, 0))

// ── REPORT 4: Monthly Data (for selected year) ─────────────────
const monthlyData = computed(() => {
  const months = Array.from({ length: 12 }, (_, i) => ({
    month: MONTH_NAMES[i], income: 0, payments: 0, net: 0, moves: 0
  }))
  yearTransactions.value.forEach(t => {
    if (!t.docDate) return
    const m = parseInt(t.docDate.substring(5, 7), 10) - 1
    if (m < 0 || m > 11) return
    const amt = Number(t.amount) || 0
    months[m].moves++
    if (t.type === 'income') {
      months[m].income += amt
    } else {
      months[m].payments += amt
    }
  })
  months.forEach(m => { m.net = m.income - m.payments })
  return months
})

// ── S86.18: Monthly grouped by year (only when 'all' years) ────
const expandedYears = ref({})  // {2021: true, ...} — all closed by default
function toggleYear(y) { expandedYears.value[y] = !expandedYears.value[y] }

const monthlyByYear = computed(() => {
  // group transactions: year -> 12 months
  const map = {}
  modeFilteredTransactions.value.forEach(t => {
    if (!t.docDate) return
    const yr = t.docDate.substring(0, 4)
    const m = parseInt(t.docDate.substring(5, 7), 10) - 1
    if (m < 0 || m > 11) return
    if (!map[yr]) {
      map[yr] = {
        year: yr,
        months: Array.from({ length: 12 }, (_, i) => ({
          month: MONTH_NAMES[i], income: 0, payments: 0, net: 0, moves: 0
        })),
        income: 0, payments: 0, net: 0, moves: 0
      }
    }
    const amt = Number(t.amount) || 0
    const g = map[yr]
    g.months[m].moves++
    g.moves++
    if (t.type === 'income') { g.months[m].income += amt; g.income += amt }
    else { g.months[m].payments += amt; g.payments += amt }
  })
  // finalize net + keep only months with activity inside each year
  const arr = Object.values(map)
  arr.forEach(g => {
    g.net = g.income - g.payments
    g.months.forEach(mo => { mo.net = mo.income - mo.payments })
    g.activeMonths = g.months.filter(mo => mo.moves > 0)
  })
  return arr.sort((a, b) => a.year.localeCompare(b.year))
})

const monthlyByYearTotals = computed(() => {
  const inc = monthlyByYear.value.reduce((s, g) => s + g.income, 0)
  const pay = monthlyByYear.value.reduce((s, g) => s + g.payments, 0)
  const mov = monthlyByYear.value.reduce((s, g) => s + g.moves, 0)
  return { income: inc, payments: pay, net: inc - pay, moves: mov }
})
const budgetCategories = computed(() => {
  const map = {}
  let colorIdx = 0
  modeFilteredTransactions.value.forEach(t => {
    if (!t.docDate || t.type !== 'expense') return
    const cat = t.category || 'N/A'
    const yr = t.docDate.substring(0, 4)
    if (!map[cat]) {
      map[cat] = { name: cat, color: BUDGET_COLORS[colorIdx++ % BUDGET_COLORS.length], years: {}, total: 0 }
      years.forEach(y => { map[cat].years[y] = 0 })
    }
    const amt = Number(t.amount) || 0
    if (map[cat].years[yr] !== undefined) {
      map[cat].years[yr] += amt
    }
    map[cat].total += amt
  })
  return Object.values(map).sort((a, b) => b.total - a.total)
})

const budgetIncome = computed(() => {
  const result = { years: {}, total: 0 }
  years.forEach(y => { result.years[y] = 0 })
  modeFilteredTransactions.value.forEach(t => {
    if (!t.docDate || t.type !== 'income') return
    const yr = t.docDate.substring(0, 4)
    const amt = Number(t.amount) || 0
    if (result.years[yr] !== undefined) {
      result.years[yr] += amt
    }
    result.total += amt
  })
  return result
})

const budgetTotalExpenses = computed(() => {
  const t = {}
  years.forEach(y => { t[y] = budgetCategories.value.reduce((s, c) => s + (c.years[y] || 0), 0) })
  return t
})
const budgetNet = computed(() => {
  const t = {}
  years.forEach(y => { t[y] = (budgetIncome.value.years[y] || 0) - (budgetTotalExpenses.value[y] || 0) })
  return t
})

// ── KPI Computed ───────────────────────────────────────────────────
const kpis = computed(() => {
  if (selectedReport.value === 'subcategory') {
    const filtered = filteredSubcategory.value
    const inc  = filtered.reduce((s, r) => s + r.income, 0)
    const pay  = filtered.reduce((s, r) => s + r.payments, 0)
    const subs = filtered.length
    return [
      { label: 'Εισπράξεις',    value: fmt(inc),      color: 'green' },
      { label: 'Πληρωμές',      value: fmt(pay),      color: 'red' },
      { label: 'Υποκατηγορίες', value: subs.toString(), color: 'neutral' },
      { label: 'Καθαρό',        value: fmt(inc - pay), color: inc - pay >= 0 ? 'green' : 'red' },
    ]
  }
  if (selectedReport.value === 'category') {
    const filtered = filteredCategory.value
    const total = filtered.reduce((s, r) => s + r.amount, 0)
    const moves = filtered.reduce((s, r) => s + r.moves, 0)
    const avg   = filtered.length ? total / filtered.length : 0
    return [
      { label: 'Σύνολο',      value: fmt(total),                color: 'red' },
      { label: 'Κατηγορίες',  value: filtered.length.toString(), color: 'neutral' },
      { label: 'Κινήσεις',    value: moves.toString(),          color: 'neutral' },
      { label: 'Μ.Ο./Κατηγορία', value: fmt(avg),       color: 'neutral' },
    ]
  }
  if (selectedReport.value === 'yearly') {
    const total  = yearlyGrandTotal.value
    const yCount = years.length
    const cats   = yearlyCategoryData.value.length
    const avg    = yCount ? total / yCount : 0
    return [
      { label: 'Γενικό Σύνολο', value: fmt(total),       color: 'neutral' },
      { label: 'Έτη',           value: yCount.toString(), color: 'neutral' },
      { label: 'Κατηγορίες',    value: cats.toString(),   color: 'neutral' },
      { label: 'Μ.Ο./Έτος',     value: fmt(avg),          color: 'neutral' },
    ]
  }
  if (selectedReport.value === 'budget') {
    const totalExp = Object.values(budgetTotalExpenses.value).reduce((s, v) => s + v, 0)
    const totalInc = Object.values(budgetIncome.value.years).reduce((s, v) => s + v, 0)
    const cats     = budgetCategories.value.length
    return [
      { label: 'Σύν. Εξόδων',  value: fmt(totalExp), color: 'red' },
      { label: 'Σύν. Εσόδων',  value: fmt(totalInc), color: 'green' },
      { label: 'Κατηγορίες',   value: cats.toString(), color: 'neutral' },
      { label: 'Έτη (2017–2026)', value: '10',          color: 'neutral' },
    ]
  }
  if (selectedReport.value === 'monthly') {
    const active = monthlyData.value.filter(m => m.moves > 0)
    const inc    = monthlyData.value.reduce((s, m) => s + m.income, 0)
    const pay    = monthlyData.value.reduce((s, m) => s + m.payments, 0)
    const moves  = monthlyData.value.reduce((s, m) => s + m.moves, 0)
    return [
      { label: 'Εισπράξεις', value: fmt(inc),              color: 'green' },
      { label: 'Πληρωμές',   value: fmt(pay),              color: 'red' },
      { label: 'Καθαρό',     value: fmt(inc - pay),        color: inc - pay >= 0 ? 'green' : 'red' },
      { label: 'Ενεργοί Μήνες', value: active.length.toString(), color: 'neutral' },
    ]
  }
  return []
})

// ── Filtered Data ──────────────────────────────────────────────────
const filteredSubcategory = computed(() => {
  if (selectedType.value === 'income')   return subcategoryData.value.filter(r => r.income > 0)
  if (selectedType.value === 'payments') return subcategoryData.value.filter(r => r.payments > 0)
  return subcategoryData.value
})

const filteredCategory = computed(() => {
  if (selectedType.value === 'income')   return categoryData.value.filter(r => r.amount === 0)
  if (selectedType.value === 'payments') return categoryData.value.filter(r => r.amount > 0)
  return categoryData.value
})

// ── Helpers ────────────────────────────────────────────────────────
const fmt = (n) => {
  if (n === 0) return '0,00 €'
  return new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n) + ' €'
}

const fmtShort = (n) => {
  if (!n) return '—'
  if (n >= 1000) return (n / 1000).toFixed(0) + 'K'
  return n.toFixed(0)
}

// Chart: compute bar heights for subcategory/category
const chartBars = computed(() => {
  let data = []
  if (selectedReport.value === 'subcategory') {
    data = filteredSubcategory.value.map(r => ({
      label: r.name,
      value: Math.max(r.income, r.payments),
      raw: Math.max(r.income, r.payments),
      isIncome: r.income > r.payments,
    }))
  } else if (selectedReport.value === 'category') {
    data = filteredCategory.value.map((r, i) => ({
      label: r.name,
      value: r.amount,
      raw: r.amount,
      isIncome: false,
    }))
  } else if (selectedReport.value === 'monthly') {
    data = monthlyData.value.map(m => ({
      label: m.month.substring(0, 3),
      value: Math.abs(m.net),
      raw: m.net,
      isIncome: m.net >= 0,
    }))
  }
  const max = Math.max(...data.map(d => d.value), 1)
  const colors = ['#29b6f6','#4FC3A1','#ef5350','#ab47bc','#ff9800','#e91e63','#8bc34a','#ff5252','#26c6da','#ffd54f','#78909c']
  return data.map((d, i) => ({
    ...d,
    height: Math.max((d.value / max) * 220, d.value > 0 ? 4 : 0),
    color: d.isIncome ? '#4FC3A1' : colors[i % colors.length],
    displayValue: fmtShort(d.value),
  }))
})

// Yearly chart bars (one bar per year = total)
const yearlyChartBars = computed(() => {
  const max = Math.max(...years.map(y => yearlyTotals.value[y] || 0), 1)
  const colors = ['#29b6f6','#4FC3A1','#ef5350','#ab47bc','#ff9800','#e91e63','#8bc34a','#ff5252','#26c6da','#ffd54f']
  return years.map((y, i) => ({
    label: y,
    value: yearlyTotals.value[y] || 0,
    height: Math.max(((yearlyTotals.value[y] || 0) / max) * 220, (yearlyTotals.value[y] || 0) > 0 ? 4 : 0),
    color: colors[i % colors.length],
    displayValue: fmtShort(yearlyTotals.value[y] || 0),
  }))
})

// Budget chart (paired bars per year)
const budgetChartBars = computed(() => {
  const max = Math.max(...years.map(y => Math.max(budgetTotalExpenses.value[y] || 0, budgetIncome.value.years[y] || 0)), 1)
  return years.map((y, i) => ({
    label: y,
    expHeight: Math.max(((budgetTotalExpenses.value[y] || 0) / max) * 220, (budgetTotalExpenses.value[y] || 0) > 0 ? 4 : 0),
    incHeight: Math.max(((budgetIncome.value.years[y] || 0) / max) * 220, (budgetIncome.value.years[y] || 0) > 0 ? 4 : 0),
    expVal: fmtShort(budgetTotalExpenses.value[y] || 0),
    incVal: fmtShort(budgetIncome.value.years[y] || 0),
  }))
})

// ── Export (stub) ──────────────────────────────────────────────────
const exportExcel = () => {
  alert('Export Excel — θα συνδεθεί με το API στη φάση 2')
}

// ── Current date label ─────────────────────────────────────────────
const currentMonthLabel = new Intl.DateTimeFormat('el-GR', { month: 'long', year: 'numeric' }).format(new Date())

// ── Lifecycle ─────────────────────────────────────────────────────
onMounted(() => {
  loadProjects()
  loadTransactions()
  // Listen for entity changes (same pattern as AdminView)
  window.addEventListener('storage', (e) => {
    if (e.key === 'n2c_entity') loadTransactions()
  })
})

// S68: persist mode choice across reloads
watch(selectedMode, (val) => {
  try { localStorage.setItem('reportsViewMode', val) } catch (e) { /* ignore */ }
})

// S76: Persist project selection across sessions
watch(selectedProject, (val) => {
  try { localStorage.setItem('reportsViewProject', val) } catch (e) { /* ignore */ }
})

// Also reload when year changes (not strictly needed since yearTransactions is computed, but good UX)
// Entity switch within same tab uses custom event
window.addEventListener('entity-changed', () => { loadTransactions() })
</script>

<template>
  <div class="reports-page" :class="{ 'mode-planned-active': selectedMode === 'planned' }">

    <!-- ── Top Bar ── -->
    <div class="top-bar">
      <h1 class="page-title">
        Αναφορές
        <!-- S68: PLANNED mode badge -->
        <span v-if="selectedMode === 'planned'" class="mode-pill mode-pill-planned" title="Προβλεπόμενα δεδομένα">📋 Προγραμματισμένες</span>
      </h1>

      <div class="filters-row">
        <!-- Year -->
        <div class="select-wrap">
          <select v-model="selectedYear" class="filter-select">
            <option value="all">Όλα τα έτη</option>
            <option v-for="y in allYears" :key="y" :value="y">{{ y }}</option>
          </select>
          <span class="select-arrow">▾</span>
        </div>

        <!-- Report type -->
        <div class="select-wrap">
          <select v-model="selectedReport" class="filter-select wide">
            <option v-for="r in reports" :key="r.value" :value="r.value">{{ r.label }}</option>
          </select>
          <span class="select-arrow">▾</span>
        </div>

        <!-- Income/Payments/All -->
        <div class="select-wrap">
          <select v-model="selectedType" class="filter-select">
            <option v-for="t in types" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
          <span class="select-arrow">▾</span>
        </div>

        <!-- S68: Mode toggle (Actual / Planned / All) -->
        <div class="select-wrap">
          <select v-model="selectedMode" class="filter-select" :class="{ 'mode-planned': selectedMode === 'planned' }">
            <option value="actual">💰 Πραγματικές</option>
            <option value="planned">📋 Προγραμματισμένες</option>
            <option value="all">📊 Όλες</option>
          </select>
          <span class="select-arrow">▾</span>
        </div>

        <!-- S76: Project filter — S77-PATCH-APPLIED: hidden when entity has no projects -->
        <div class="select-wrap" v-if="projectsList.length > 0">
          <select v-model="selectedProject" class="filter-select">
            <option value="all">🎯 Όλα τα projects</option>
            <option value="opex">📊 Γενικό OpEx</option>
            <option v-for="p in projectsList" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
          <span class="select-arrow">▾</span>
        </div>
      </div>

      <div class="top-right">
        <span class="date-label">{{ currentMonthLabel }}</span>
        <button class="btn-export" @click="exportExcel">
          <span class="export-icon">⊞</span> Export Excel
        </button>
      </div>
    </div>

    <!-- ── KPI Cards ── -->
    <div class="kpi-row">
      <div
        v-for="kpi in kpis"
        :key="kpi.label"
        class="kpi-card"
        :class="'kpi-' + kpi.color"
      >
        <div class="kpi-value">{{ kpi.value }}</div>
        <div class="kpi-label">{{ kpi.label }}</div>
      </div>
    </div>

    <!-- ════════════════════════════════════════════════════════════
         REPORT: ΑΝΑ ΥΠΟΚΑΤΗΓΟΡΙΑ
    ═════════════════════════════════════════════════════════════ -->
    <template v-if="selectedReport === 'subcategory'">
      <p class="report-intro">Δείχνει <strong>κάθε υποκατηγορία</strong> ξεχωριστά — πόσα μπήκαν (εισπράξεις), πόσα βγήκαν (πληρωμές) και το <strong>καθαρό</strong> της καθεμίας.</p>
      <div class="data-table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-name">ΥΠΟΚΑΤΗΓΟΡΙΑ</th>
              <th class="col-num">ΕΙΣΠΡΑΞΕΙΣ</th>
              <th class="col-num">ΠΛΗΡΩΜΕΣ</th>
              <th class="col-num">NET <span class="col-hint" title="Εισπράξεις μείον Πληρωμές: τι έμεινε καθαρό">?</span></th>
              <th class="col-moves">ΚΙΝΗΣΕΙΣ</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in filteredSubcategory" :key="row.name">
              <td class="col-name">{{ row.name }}</td>
              <td class="col-num income-val">
                <span v-if="row.income > 0">{{ fmt(row.income) }}</span>
                <span v-else class="dash">—</span>
              </td>
              <td class="col-num payment-val">
                <span v-if="row.payments > 0">{{ fmt(row.payments) }}</span>
                <span v-else class="dash">—</span>
              </td>
              <td class="col-num" :class="row.income - row.payments >= 0 ? 'income-val' : 'payment-val'">
                {{ fmt(row.income - row.payments) }}
              </td>
              <td class="col-moves">{{ row.moves }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td>ΣΥΝΟΛΟ</td>
              <td class="col-num income-val">{{ fmt(filteredSubcategory.reduce((s,r)=>s+r.income,0)) }}</td>
              <td class="col-num payment-val">{{ fmt(filteredSubcategory.reduce((s,r)=>s+r.payments,0)) }}</td>
              <td class="col-num" :class="filteredSubcategory.reduce((s,r)=>s+r.income-r.payments,0)>=0?'income-val':'payment-val'">
                {{ fmt(filteredSubcategory.reduce((s,r)=>s+r.income-r.payments,0)) }}
              </td>
              <td class="col-moves">{{ filteredSubcategory.reduce((s,r)=>s+r.moves,0) }}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </template>

    <!-- ════════════════════════════════════════════════════════════
         REPORT: ΑΝΑ ΚΑΤΗΓΟΡΙΑ
    ═════════════════════════════════════════════════════════════ -->
    <template v-if="selectedReport === 'category'">
      <p class="report-intro">Δείχνει <strong>πόσα πήγαν σε κάθε κατηγορία</strong> συνολικά, το <strong>ποσοστό</strong> της καθεμίας στο σύνολο, και πόσες κινήσεις είχε.</p>
      <div class="data-table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-name">ΚΑΤΗΓΟΡΙΑ</th>
              <th class="col-num">ΠΟΣΟ</th>
              <th class="col-pct">% <span class="col-hint" title="Τι ποσοστό των συνολικών εξόδων είναι αυτή η κατηγορία">?</span></th>
              <th class="col-moves">ΚΙΝΗΣΕΙΣ</th>
              <th class="col-num">Μ.Ο./ΚΙΝΗΣΗ <span class="col-hint" title="Ποσό διά πλήθος κινήσεων: μέσο κόστος ανά συναλλαγή">?</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in filteredCategory" :key="row.name">
              <td class="col-name">{{ row.name }}</td>
              <td class="col-num payment-val">{{ fmt(row.amount) }}</td>
              <td class="col-pct pct-val">{{ row.pct.toFixed(1) }}%</td>
              <td class="col-moves">{{ row.moves }}</td>
              <td class="col-num neutral-val">{{ fmt(row.avg) }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td>ΣΥΝΟΛΟ</td>
              <td class="col-num payment-val">{{ fmt(filteredCategory.reduce((s,r)=>s+r.amount,0)) }}</td>
              <td class="col-pct income-val">100%</td>
              <td class="col-moves">{{ filteredCategory.reduce((s,r)=>s+r.moves,0) }}</td>
              <td class="col-num">—</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </template>

    <!-- ════════════════════════════════════════════════════════════
         REPORT: ΜΗΝΙΑΙΑ ΑΝΑΛΥΣΗ
    ═════════════════════════════════════════════════════════════ -->
    <template v-if="selectedReport === 'monthly'">
      <p class="report-intro" v-if="selectedYear === 'all'">Με <strong>«Όλα τα έτη»</strong>, οι μήνες ομαδοποιούνται <strong>ανά έτος</strong>. Πάτησε σε ένα έτος για να ανοίξει/κλείσει τους μήνες του.</p>
      <p class="report-intro" v-else>Δείχνει <strong>μήνα-μήνα</strong> πόσα μπήκαν (εισπράξεις) και πόσα βγήκαν (πληρωμές), και το <strong>καθαρό</strong> κάθε μήνα.</p>

      <!-- ΟΛΑ ΤΑ ΕΤΗ: grouped ανα ετος (expandable) -->
      <div class="data-table-wrap" v-if="selectedYear === 'all'">
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-name">ΠΕΡΙΟΔΟΣ</th>
              <th class="col-num">ΕΙΣΠΡΑΞΕΙΣ</th>
              <th class="col-num">ΠΛΗΡΩΜΕΣ</th>
              <th class="col-num">NET <span class="col-hint" title="Εισπράξεις μείον Πληρωμές: τι έμεινε καθαρό">?</span></th>
              <th class="col-moves">ΚΙΝΗΣΕΙΣ</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="g in monthlyByYear" :key="g.year">
              <tr class="year-group-row" @click="toggleYear(g.year)">
                <td class="col-name year-group-name">
                  <span class="year-caret">{{ expandedYears[g.year] ? '▾' : '▸' }}</span> {{ g.year }}
                </td>
                <td class="col-num income-val">
                  <span v-if="g.income > 0">{{ fmt(g.income) }}</span><span v-else class="dash">—</span>
                </td>
                <td class="col-num payment-val">
                  <span v-if="g.payments > 0">{{ fmt(g.payments) }}</span><span v-else class="dash">—</span>
                </td>
                <td class="col-num" :class="g.net >= 0 ? 'income-val' : 'payment-val'">{{ fmt(g.net) }}</td>
                <td class="col-moves">{{ g.moves || '—' }}</td>
              </tr>
              <tr v-for="mo in g.activeMonths" v-show="expandedYears[g.year]" :key="g.year + mo.month" class="month-sub-row">
                <td class="col-name month-sub-name">{{ mo.month }}</td>
                <td class="col-num income-val">
                  <span v-if="mo.income > 0">{{ fmt(mo.income) }}</span><span v-else class="dash">—</span>
                </td>
                <td class="col-num payment-val">
                  <span v-if="mo.payments > 0">{{ fmt(mo.payments) }}</span><span v-else class="dash">—</span>
                </td>
                <td class="col-num" :class="mo.net >= 0 ? 'income-val' : 'payment-val'">
                  <span v-if="mo.moves > 0">{{ fmt(mo.net) }}</span><span v-else class="dash">—</span>
                </td>
                <td class="col-moves">{{ mo.moves || '—' }}</td>
              </tr>
            </template>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td>ΓΕΝΙΚΟ ΣΥΝΟΛΟ</td>
              <td class="col-num income-val">{{ fmt(monthlyByYearTotals.income) }}</td>
              <td class="col-num payment-val">{{ fmt(monthlyByYearTotals.payments) }}</td>
              <td class="col-num" :class="monthlyByYearTotals.net >= 0 ? 'income-val' : 'payment-val'">{{ fmt(monthlyByYearTotals.net) }}</td>
              <td class="col-moves">{{ monthlyByYearTotals.moves }}</td>
            </tr>
          </tfoot>
        </table>
      </div>

      <!-- ΣΥΓΚΕΚΡΙΜΕΝΟ ΕΤΟΣ: flat 12 μηνες (οπως πριν) -->
      <div class="data-table-wrap" v-else>
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-name">ΜΗΝΑΣ</th>
              <th class="col-num">ΕΙΣΠΡΑΞΕΙΣ</th>
              <th class="col-num">ΠΛΗΡΩΜΕΣ</th>
              <th class="col-num">NET <span class="col-hint" title="Εισπράξεις μείον Πληρωμές: τι έμεινε καθαρό">?</span></th>
              <th class="col-moves">ΚΙΝΗΣΕΙΣ</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in monthlyData" :key="row.month">
              <td class="col-name">{{ row.month }}</td>
              <td class="col-num income-val">
                <span v-if="row.income > 0">{{ fmt(row.income) }}</span>
                <span v-else class="dash">—</span>
              </td>
              <td class="col-num payment-val">
                <span v-if="row.payments > 0">{{ fmt(row.payments) }}</span>
                <span v-else class="dash">—</span>
              </td>
              <td class="col-num" :class="row.net >= 0 ? 'income-val' : 'payment-val'">
                <span v-if="row.moves > 0">{{ fmt(row.net) }}</span>
                <span v-else class="dash">—</span>
              </td>
              <td class="col-moves">{{ row.moves || '—' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td>ΣΥΝΟΛΟ</td>
              <td class="col-num income-val">{{ fmt(monthlyData.reduce((s,m)=>s+m.income,0)) }}</td>
              <td class="col-num payment-val">{{ fmt(monthlyData.reduce((s,m)=>s+m.payments,0)) }}</td>
              <td class="col-num" :class="monthlyData.reduce((s,m)=>s+m.net,0)>=0?'income-val':'payment-val'">
                {{ fmt(monthlyData.reduce((s,m)=>s+m.net,0)) }}
              </td>
              <td class="col-moves">{{ monthlyData.reduce((s,m)=>s+m.moves,0) }}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </template>

    <!-- ════════════════════════════════════════════════════════════
         REPORT: ΕΤΗΣΙΑ ΣΥΓΚΡΙΣΗ
    ═════════════════════════════════════════════════════════════ -->
    <template v-if="selectedReport === 'yearly'">
      <p class="report-intro">Δείχνει <strong>πόσα ξόδεψες σε κάθε κατηγορία</strong> κάθε χρόνο, και στη δεξιά στήλη το <strong>σύνολο</strong> κάθε κατηγορίας για όλα τα έτη.</p>
      <div class="data-table-wrap scrollable-x">
        <table class="data-table pivot-table">
          <thead>
            <tr>
              <th class="col-name">ΚΑΤΗΓΟΡΙΑ</th>
              <th v-for="y in years" :key="y" class="col-year">{{ y }}</th>
              <th class="col-num">ΣΥΝΟΛΟ</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in yearlyCategoryData" :key="row.name">
              <td class="col-name">{{ row.name }}</td>
              <td v-for="y in years" :key="y" class="col-year num-cell">
                <span v-if="row.years[y]">{{ fmt(row.years[y]) }}</span>
                <span v-else class="dash">—</span>
              </td>
              <td class="col-num payment-val bold-val">{{ fmt(row.total) }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td>ΣΥΝΟΛΟ</td>
              <td v-for="y in years" :key="y" class="col-year num-cell payment-val">
                {{ yearlyTotals[y] ? fmt(yearlyTotals[y]) : '—' }}
              </td>
              <td class="col-num payment-val bold-val">{{ fmt(yearlyGrandTotal) }}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </template>

    <!-- ════════════════════════════════════════════════════════════
         REPORT: ΑΝΑΛΥΣΗ ΠΡΟΫΠΟΛΟΓΙΣΜΟΥ
    ═════════════════════════════════════════════════════════════ -->
    <template v-if="selectedReport === 'budget'">
      <p class="report-intro">Δείχνει <strong>πόσα ξόδεψες σε κάθε κατηγορία</strong> κάθε χρόνο, και στο κάτω μέρος τα <strong>συνολικά έξοδα</strong>, τα <strong>έσοδα</strong> και το <strong>καθαρό αποτέλεσμα</strong> (κέρδος ή ζημιά) ανά έτος.</p>
      <div class="data-table-wrap scrollable-x">
        <table class="data-table pivot-table budget-table">
          <thead>
            <tr>
              <th class="col-budget-name">Ανάλυση Προϋπολογισμού</th>
              <th v-for="y in years" :key="y" class="col-year">{{ y }}</th>
              <th class="col-num">Σύνολο</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cat in budgetCategories" :key="cat.name" class="budget-cat-row">
              <td class="col-budget-name">
                <span class="cat-dot" :style="{ background: cat.color }"></span>
                {{ cat.name }}
              </td>
              <td v-for="y in years" :key="y" class="col-year num-cell payment-val">
                <span v-if="cat.years[y]">{{ fmt(cat.years[y]) }}</span>
                <span v-else class="dash">—</span>
              </td>
              <td class="col-num payment-val bold-val">{{ cat.total ? fmt(cat.total) : '— €' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="budget-expenses-total">
              <td class="col-budget-name income-val">ΣΥΝΟΛΟ ΕΞΟΔΩΝ</td>
              <td v-for="y in years" :key="y" class="col-year num-cell payment-val">
                {{ budgetTotalExpenses[y] ? fmt(budgetTotalExpenses[y]) : '—' }}
              </td>
              <td class="col-num payment-val bold-val">{{ fmt(Object.values(budgetTotalExpenses).reduce((s,v)=>s+v,0)) }}</td>
            </tr>
            <tr class="budget-income-row">
              <td class="col-budget-name income-val">↓ Έσοδα</td>
              <td v-for="y in years" :key="y" class="col-year num-cell income-val">
                {{ budgetIncome.years[y] ? fmt(budgetIncome.years[y]) : '—' }}
              </td>
              <td class="col-num income-val bold-val">{{ fmt(budgetIncome.total) }}</td>
            </tr>
            <tr class="budget-net-row">
              <td class="col-budget-name">
                <span class="net-icon">⚡</span> Καθαρό (Έσοδα − Έξοδα)
              </td>
              <td v-for="y in years" :key="y" class="col-year num-cell" :class="budgetNet[y]>=0?'income-val':'payment-val'">
                {{ fmt(budgetNet[y]) }}
                <span v-if="budgetNet[y]<0" class="arrow-down">▼</span>
                <span v-else class="arrow-up">▲</span>
              </td>
              <td class="col-num bold-val" :class="Object.values(budgetNet).reduce((s,v)=>s+v,0)>=0?'income-val':'payment-val'">
                {{ fmt(Object.values(budgetNet).reduce((s,v)=>s+v,0)) }}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </template>

    <!-- ── Chart Area ── -->
    <div class="chart-section">
      <div class="chart-title">
        <span class="chart-icon">▤</span>
        {{ reports.find(r=>r.value===selectedReport)?.label }}
      </div>

      <!-- Simple bar charts for subcategory / category / monthly -->
      <template v-if="['subcategory','category','monthly'].includes(selectedReport)">
        <div class="bar-chart">
          <div class="bar-y-axis">
            <span>4K</span><span>3K</span><span>2K</span><span>1K</span><span>500</span><span>0</span>
          </div>
          <div class="bars-area">
            <div
              v-for="bar in chartBars"
              :key="bar.label"
              class="bar-col"
            >
              <div class="bar-value-label">{{ bar.height > 20 ? bar.displayValue : '' }}</div>
              <div
                class="bar-rect"
                :style="{ height: bar.height + 'px', background: bar.color }"
              ></div>
              <div class="bar-x-label">{{ bar.label }}</div>
            </div>
          </div>
        </div>
      </template>

      <!-- Yearly chart -->
      <template v-if="selectedReport === 'yearly'">
        <div class="bar-chart">
          <div class="bar-y-axis">
            <span>400K</span><span>300K</span><span>200K</span><span>100K</span><span>50K</span><span>0</span>
          </div>
          <div class="bars-area">
            <div v-for="bar in yearlyChartBars" :key="bar.label" class="bar-col">
              <div class="bar-value-label">{{ bar.height > 20 ? bar.displayValue : '' }}</div>
              <div class="bar-rect" :style="{ height: bar.height + 'px', background: bar.color }"></div>
              <div class="bar-x-label">{{ bar.label }}</div>
            </div>
          </div>
        </div>
      </template>

      <!-- Budget chart (paired bars: expenses + income) -->
      <template v-if="selectedReport === 'budget'">
        <div class="chart-legend">
          <span v-for="cat in budgetCategories.slice(0,7)" :key="cat.name" class="legend-item">
            <span class="legend-dot" :style="{ background: cat.color }"></span>
            {{ cat.name }}
          </span>
          <span class="legend-item"><span class="legend-dot" style="background:#4FC3A1"></span>Έσοδα</span>
        </div>
        <div class="bar-chart">
          <div class="bar-y-axis">
            <span>450K</span><span>350K</span><span>250K</span><span>150K</span><span>50K</span><span>0</span>
          </div>
          <div class="bars-area">
            <div v-for="bar in budgetChartBars" :key="bar.label" class="bar-col bar-col-paired">
              <div class="paired-bars">
                <div class="bar-inner">
                  <div class="bar-value-label small bar-exp-label">{{ bar.expVal }}</div>
                  <div class="bar-rect" :style="{ height: bar.expHeight + 'px', background: '#ef5350' }"></div>
                </div>
                <div class="bar-inner">
                  <div class="bar-value-label small bar-inc-label">{{ bar.incVal }}</div>
                  <div class="bar-rect" :style="{ height: bar.incHeight + 'px', background: '#4FC3A1' }"></div>
                </div>
              </div>
              <div class="bar-x-label">{{ bar.label }}</div>
            </div>
          </div>
        </div>
      </template>
    </div>

  </div>
</template>

<style scoped>
/* ── Base ── */
.reports-page {
  padding: 20px 24px;
  color: #334155;
  min-height: 100vh;
  background: #f4f6f9;
}

/* ── Top Bar ── */
.top-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.page-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
  white-space: nowrap;
}
.filters-row {
  display: flex;
  gap: 10px;
  flex: 1;
}
.top-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}
.date-label {
  font-size: 0.8rem;
  color: #64748b;
}
.select-wrap {
  position: relative;
}
.filter-select {
  appearance: none;
  background: #ffffff;
  border: 1px solid #d6dee8;
  color: #334155;
  padding: 7px 32px 7px 12px;
  border-radius: 6px;
  font-size: 0.83rem;
  cursor: pointer;
  outline: none;
}
.filter-select.wide { min-width: 180px; }
.select-arrow {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  pointer-events: none;
  color: #94a3b8;
  font-size: 0.75rem;
}
.btn-export {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #ffffff;
  border: 1px solid #d6dee8;
  color: #334155;
  padding: 7px 14px;
  border-radius: 6px;
  font-size: 0.83rem;
  cursor: pointer;
  white-space: nowrap;
}
.btn-export:hover { background: #e2e8f0; }
.export-icon { font-size: 1rem; }

/* ── KPI Row ── */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.kpi-card {
  background: #ffffff;
  border-radius: 10px;
  padding: 20px 16px;
  text-align: center;
  border: 1px solid #e8edf3;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.07);
}
.kpi-value {
  font-size: 1.45rem;
  font-weight: 700;
  font-family: monospace;
  color: #1e293b;
  letter-spacing: -0.5px;
  white-space: nowrap;
}
.kpi-label {
  font-size: 0.7rem;
  color: #64748b;
  margin-top: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.kpi-green .kpi-value { color: #4FC3A1; }
.kpi-red   .kpi-value { color: #ef5350; }
.kpi-neutral .kpi-value { color: #1e293b; }

/* ── Data Table ── */
.data-table-wrap {
  background: #ffffff;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 20px;
  border: 1px solid #e8edf3;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.07);
}
.data-table-wrap.scrollable-x {
  overflow-x: auto;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.95rem;
}
.data-table th {
  background: #f0f3f7;
  color: #64748b;
  padding: 13px 18px;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  border-bottom: 1px solid #e2e8f0;
  white-space: nowrap;
}
.data-table td {
  padding: 12px 18px;
  border-bottom: 1px solid #eaeef3;
  white-space: nowrap;
}
.data-table tbody tr:hover { background: #f0f4f8; }
.data-table tfoot td {
  background: #eef1f5;
  border-top: 2px solid #d6dee8;
  font-weight: 700;
  font-size: 0.96rem;
  white-space: nowrap;
}

.col-name     { text-align: left; }
.col-num      { text-align: right; font-family: monospace; }
.col-year     { text-align: right; font-family: monospace; font-size: 0.9rem; padding: 12px 12px; white-space: nowrap; }
.col-pct      { text-align: right; }
.col-moves    { text-align: right; color: #64748b; }
.num-cell     { text-align: right; font-family: monospace; }

.income-val   { color: #4FC3A1; }
.payment-val  { color: #ef5350; }
.neutral-val  { color: #334155; }
.pct-val      { color: #64748b; }
.bold-val     { font-weight: 700; }
.dash         { color: #cbd5e1; }

/* S86.17: λεζαντα επεξηγησης ανα προβολη */
.report-intro {
  font-size: 0.95rem;
  color: #475569;
  line-height: 1.6;
  margin: 0 0 16px;
  padding: 14px 18px;
  background: #eef4fb;
  border-left: 4px solid #4FC3A1;
  border-radius: 8px;
}
.report-intro strong { color: #1e293b; }
/* S86.17: tooltip στις δυσκολες στηλες */
.col-hint {
  font-size: 0.7rem;
  color: #94a3b8;
  cursor: help;
  border: 1px solid #cbd5e1;
  border-radius: 50%;
  width: 15px;
  height: 15px;
  display: inline-block;
  text-align: center;
  line-height: 14px;
  margin-left: 5px;
  vertical-align: middle;
}

.total-row td { font-weight: 700; }
.total-row td.col-name, .total-row > td:first-child { color: #1e293b; }

/* S86.18: Monthly grouped by year */
.year-group-row { cursor: pointer; background: #fff; }
.year-group-row:hover { background: #f8fafc; }
.year-group-name { font-weight: 700; color: #1e293b; }
.year-caret { display: inline-block; width: 14px; color: #94a3b8; }
.month-sub-row { background: #fcfdfe; }
.month-sub-row:hover { background: #f5f8fc; }
.month-sub-name { padding-left: 42px !important; color: #475569; }

/* Budget table specifics */
.col-budget-name {
  text-align: left;
  white-space: nowrap;
  padding-right: 20px;
}
.cat-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}
.budget-expenses-total td { background: #fde8e8; font-weight: 700; }
.budget-income-row td     { background: #e8f5f0; }
.budget-net-row td        { background: #eef1f5; font-weight: 700; border-top: 1px solid #d6dee8; }
.arrow-down { color: #ef5350; font-size: 0.65rem; margin-left: 2px; }
.arrow-up   { color: #4FC3A1; font-size: 0.65rem; margin-left: 2px; }
.net-icon   { margin-right: 4px; }

/* ── Chart Section ── */
.chart-section {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e8edf3;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.07);
}
.chart-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.chart-icon { color: #4FC3A1; }

.bar-chart {
  display: flex;
  gap: 0;
  height: 260px;
  align-items: flex-end;
}
.bar-y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 240px;
  padding-bottom: 24px;
  font-size: 0.68rem;
  color: #94a3b8;
  text-align: right;
  min-width: 42px;
  padding-right: 8px;
}
.bars-area {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  flex: 1;
  height: 240px;
  padding-bottom: 24px;
  overflow-x: auto;
}
.bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  min-width: 50px;
  flex: 1;
  height: 100%;
}
.bar-col-paired { min-width: 70px; }
.bar-value-label {
  font-size: 0.68rem;
  color: #334155;
  margin-bottom: 3px;
  text-align: center;
}
.bar-value-label.small { font-size: 0.6rem; }
.bar-exp-label { color: #1e293b; font-weight: 700; }
.bar-inc-label { color: #0f6e56; font-weight: 700; }
.bar-rect {
  width: 70%;
  border-radius: 3px 3px 0 0;
  min-height: 2px;
  transition: height 0.3s ease;
}
.bar-x-label {
  font-size: 0.65rem;
  color: #94a3b8;
  margin-top: 6px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 60px;
}
.paired-bars {
  display: flex;
  gap: 2px;
  align-items: flex-end;
  width: 100%;
  justify-content: center;
}
.bar-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}
.bar-inner .bar-rect { width: 24px; }

/* Budget legend */
.chart-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 0.72rem;
  color: #64748b;
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* Pivot table */
.pivot-table th, .pivot-table td { white-space: nowrap; }

/* S68: mode toggle styles */
.mode-pill {
  display: inline-flex;
  align-items: center;
  font-size: 0.55em;
  vertical-align: middle;
  margin-left: 12px;
  padding: 4px 10px;
  border-radius: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
}
.mode-pill-planned {
  background: rgba(255, 152, 0, 0.18);
  color: #ffb74d;
  border: 1px solid rgba(255, 152, 0, 0.4);
}
.filter-select.mode-planned {
  background-color: rgba(255, 152, 0, 0.12);
  border-color: rgba(255, 152, 0, 0.5);
  color: #ffb74d;
}
.reports-page.mode-planned-active .top-bar {
  background: linear-gradient(180deg, rgba(255, 152, 0, 0.06), transparent);
  border-bottom: 1px solid rgba(255, 152, 0, 0.18);
}
.reports-page.mode-planned-active .page-title {
  color: #ffb74d;
}
</style>
