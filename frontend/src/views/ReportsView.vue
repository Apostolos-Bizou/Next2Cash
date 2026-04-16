<script setup>
import { ref, computed } from 'vue'

// ── Filters ────────────────────────────────────────────────────────
const selectedYear   = ref('2026')
const selectedReport = ref('subcategory')
const selectedType   = ref('all')

const years   = ['2017','2018','2019','2020','2021','2022','2023','2024','2025','2026']
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

// ── Mock Data ──────────────────────────────────────────────────────

// Subcategory report data
const subcategoryData = ref([
  { name: 'Dn2Me-UK',          income: 0,        payments: 3297.50, moves: 2  },
  { name: 'Ενοίκιο',           income: 0,        payments: 2680.00, moves: 4  },
  { name: 'ΚΑΓΚΕΛΑΡΗΣ',        income: 2464.20,  payments: 0,       moves: 18 },
  { name: 'Λογισμικά / ERP',   income: 0,        payments: 1544.78, moves: 21 },
  { name: 'Finance',            income: 0,        payments: 1488.00, moves: 8  },
  { name: 'ΒΑΡΙΑΣ',            income: 1116.00,  payments: 0,       moves: 1  },
  { name: 'Άδειες Χρήσης',     income: 0,        payments: 878.14,  moves: 9  },
  { name: 'Έξοδα Κίνησης',     income: 0,        payments: 551.28,  moves: 13 },
  { name: 'Τηλέφωνα',          income: 0,        payments: 419.68,  moves: 7  },
  { name: 'Έξοδα Διαχείρισης', income: 0,        payments: 181.94,  moves: 5  },
  { name: 'Γεύματα Εργασίας',  income: 0,        payments: 6.48,    moves: 1  },
])

// Category report data
const categoryData = ref([
  { name: 'Λειτουργικά',  amount: 3679.22, pct: 33.8, moves: 30, avg: 122.64 },
  { name: 'Λοιπά',        amount: 3297.50, pct: 30.3, moves: 2,  avg: 1648.75 },
  { name: 'Εξοπλισμός',   amount: 2422.92, pct: 22.3, moves: 30, avg: 80.76 },
  { name: 'Απασχόληση',   amount: 1488.00, pct: 13.7, moves: 8,  avg: 186.00 },
  { name: 'Έσοδα Β',      amount: 0,       pct: 0.0,  moves: 18, avg: 0 },
  { name: 'Έσοδα Β',      amount: 0,       pct: 0.0,  moves: 1,  avg: 0 },
])

// Yearly comparison data
const yearlyCategoryData = ref([
  { name: 'Ανάπτυξη Λογισμικού', years: { '2017':17157.07,'2018':282988.00,'2019':78998.00,'2020':42500.00,'2021':31500.00,'2022':0,'2023':0,'2024':0,'2025':0,'2026':0 }, total: 373863.07 },
  { name: 'Απασχόληση',          years: { '2017':21074.37,'2018':59775.22,'2019':58012.56,'2020':59787.45,'2021':25036.45,'2022':15858.00,'2023':11018.89,'2024':11548.82,'2025':9151.83,'2026':1488.00 }, total: 271871.59 },
  { name: 'Λειτουργικά',         years: { '2017':19236.82,'2018':32186.64,'2019':30119.25,'2020':27052.58,'2021':21985.79,'2022':19192.68,'2023':20086.46,'2024':24792.77,'2025':12687.35,'2026':3679.22 }, total: 210698.76 },
  { name: 'Λοιπά',               years: { '2017':6788.00,'2018':21567.91,'2019':50833.55,'2020':45672.56,'2021':23424.18,'2022':5995.68,'2023':10228.25,'2024':3250.00,'2025':10603.53,'2026':3297.50 }, total: 180853.08 },
  { name: 'Προσωπικό',           years: { '2017':2267.71,'2018':40538.79,'2019':50514.83,'2020':29871.24,'2021':12009.54,'2022':1277.64,'2023':934.40,'2024':682.29,'2025':522.50,'2026':0 }, total: 137810.94 },
  { name: 'Εξοπλισμός',          years: { '2017':4621.00,'2018':8468.75,'2019':1715.25,'2020':8323.53,'2021':2586.74,'2022':2415.75,'2023':2152.00,'2024':2609.84,'2025':1979.78,'2026':2422.92 }, total: 37215.56 },
  { name: 'Προβολή & Προώθηση',  years: { '2017':0,'2018':21580.00,'2019':1341.40,'2020':1465.47,'2021':530.58,'2022':2230.00,'2023':488.28,'2024':1189.00,'2025':0,'2026':0 }, total: 28824.65 },
])
const yearlyTotals = computed(() => {
  const t = {}
  years.forEach(y => { t[y] = yearlyCategoryData.value.reduce((s,r) => s + (r.years[y]||0), 0) })
  return t
})
const yearlyGrandTotal = computed(() => yearlyCategoryData.value.reduce((s,r) => s+r.total, 0))

// Monthly data
const monthlyData = ref([
  { month: 'Ιανουάριος', income: 0,       payments: 3204.78, net: -3204.78, moves: 12 },
  { month: 'Φεβρουάριος',income: 0,       payments: 2981.22, net: -2981.22, moves: 10 },
  { month: 'Μάρτιος',    income: 1116.00, payments: 4236.44, net: -3120.44, moves: 18 },
  { month: 'Απρίλιος',   income: 2464.20, payments: 465.20,  net: 1999.00,  moves: 14 },
  { month: 'Μάιος',      income: 0,       payments: 0,        net: 0,        moves: 0  },
  { month: 'Ιούνιος',    income: 0,       payments: 0,        net: 0,        moves: 0  },
  { month: 'Ιούλιος',    income: 0,       payments: 0,        net: 0,        moves: 0  },
  { month: 'Αύγουστος',  income: 0,       payments: 0,        net: 0,        moves: 0  },
  { month: 'Σεπτέμβριος',income: 0,       payments: 0,        net: 0,        moves: 0  },
  { month: 'Οκτώβριος',  income: 0,       payments: 0,        net: 0,        moves: 0  },
  { month: 'Νοέμβριος',  income: 0,       payments: 0,        net: 0,        moves: 0  },
  { month: 'Δεκέμβριος', income: 0,       payments: 0,        net: 0,        moves: 0  },
])

// Budget analysis data
const budgetCategories = ref([
  { name: 'Λειτουργικά',         color:'#29b6f6', years: { '2017':19236.02,'2018':32186.64,'2019':30119.25,'2020':27052.58,'2021':21985.79,'2022':19192.68,'2023':20086.46,'2024':24792.77,'2025':12687.35,'2026':3679.22 }, total: 210698.76 },
  { name: 'Προβολή & Προώθηση',  color:'#ab47bc', years: { '2017':0,'2018':21580.00,'2019':1341.40,'2020':1465.47,'2021':530.58,'2022':2230.00,'2023':488.28,'2024':1189.00,'2025':0,'2026':0 }, total: 28824.65 },
  { name: 'Ανάπτυξη Λογισμικού', color:'#4FC3A1', years: { '2017':17157.07,'2018':282988.00,'2019':78998.00,'2020':42500.00,'2021':31500.00,'2022':0,'2023':0,'2024':0,'2025':0,'2026':0 }, total: 373863.07 },
  { name: 'Εξοπλισμός',          color:'#ff9800', years: { '2017':4621.00,'2018':8468.75,'2019':1715.25,'2020':8323.53,'2021':2586.74,'2022':2415.75,'2023':2152.00,'2024':2609.84,'2025':1979.78,'2026':2422.92 }, total: 37215.56 },
  { name: 'Απασχόληση',          color:'#ef5350', years: { '2017':21074.37,'2018':59775.22,'2019':58012.56,'2020':59787.45,'2021':25036.45,'2022':15858.00,'2023':11018.89,'2024':11548.82,'2025':9151.83,'2026':1488.00 }, total: 271871.59 },
  { name: 'Λοιπά',               color:'#ff5252', years: { '2017':6788.00,'2018':21567.91,'2019':50833.55,'2020':45672.56,'2021':23424.18,'2022':5995.68,'2023':10228.25,'2024':3250.00,'2025':10603.53,'2026':3297.50 }, total: 180853.08 },
  { name: 'ΠΡΟΣΩΠΙΚΟ',           color:'#e91e63', years: { '2017':2267.71,'2018':40538.79,'2019':50514.83,'2020':29871.24,'2021':12009.54,'2022':1277.64,'2023':934.40,'2024':682.29,'2025':522.50,'2026':0 }, total: 137810.94 },
  { name: 'ΠΡΟΒΟΛΗ ΚΑΙ ΠΡΟΩΘΗΣΗ',color:'#8bc34a', years: { '2017':0,'2018':21580.00,'2019':1341.40,'2020':1465.47,'2021':530.58,'2022':2230.00,'2023':488.28,'2024':1189.00,'2025':0,'2026':0 }, total: 28824.65 },
  { name: 'Άλλο',                 color:'#ff9800', years: {}, total: 0 },
])
const budgetIncome = ref({
  years: { '2017':123332.24,'2018':483189.85,'2019':228751.69,'2020':158768.72,'2021':50373.48,'2022':18226.47,'2023':34660.51,'2024':28533.24,'2025':28417.73,'2026':3580.20 },
  total: 1045754.13
})
const budgetTotalExpenses = computed(() => {
  const t = {}
  years.forEach(y => { t[y] = budgetCategories.value.reduce((s,c) => s+(c.years[y]||0), 0) })
  return t
})
const budgetNet = computed(() => {
  const t = {}
  years.forEach(y => { t[y] = (budgetIncome.value.years[y]||0) - (budgetTotalExpenses.value[y]||0) })
  return t
})

// ── KPI Computed ───────────────────────────────────────────────────
const kpis = computed(() => {
  if (selectedReport.value === 'subcategory') {
    const filtered = filteredSubcategory.value
    const inc  = filtered.reduce((s,r) => s+r.income, 0)
    const pay  = filtered.reduce((s,r) => s+r.payments, 0)
    const subs = filtered.length
    return [
      { label:'Εισπράξεις',    value: fmt(inc),      color:'green' },
      { label:'Πληρωμές',      value: fmt(pay),       color:'red' },
      { label:'Υποκατηγορίες', value: subs.toString(),color:'neutral' },
      { label:'Καθαρό',        value: fmt(inc - pay), color: inc-pay >= 0 ? 'green':'red' },
    ]
  }
  if (selectedReport.value === 'category') {
    const filtered = filteredCategory.value
    const total = filtered.reduce((s,r) => s+r.amount, 0)
    const moves = filtered.reduce((s,r) => s+r.moves, 0)
    const avg   = filtered.length ? total/filtered.length : 0
    return [
      { label:'Σύνολο',      value: fmt(total),          color:'red' },
      { label:'Κατηγορίες',  value: filtered.length.toString(), color:'neutral' },
      { label:'Κινήσεις',    value: moves.toString(),    color:'neutral' },
      { label:'Μ.Ο./Κατηγορία', value: fmt(avg),         color:'neutral' },
    ]
  }
  if (selectedReport.value === 'yearly') {
    const total  = yearlyGrandTotal.value
    const yCount = years.length
    const cats   = yearlyCategoryData.value.length
    const avg    = yCount ? total/yCount : 0
    return [
      { label:'Γενικό Σύνολο', value: fmt(total),        color:'neutral' },
      { label:'Έτη',           value: yCount.toString(),  color:'neutral' },
      { label:'Κατηγορίες',    value: cats.toString(),    color:'neutral' },
      { label:'Μ.Ο./Έτος',     value: fmt(avg),           color:'neutral' },
    ]
  }
  if (selectedReport.value === 'budget') {
    const totalExp = Object.values(budgetTotalExpenses.value).reduce((s,v)=>s+v,0)
    const totalInc = Object.values(budgetIncome.value.years).reduce((s,v)=>s+v,0)
    const cats     = budgetCategories.value.length
    return [
      { label:'Σύν. Εξόδων',  value: fmt(totalExp), color:'red' },
      { label:'Σύν. Εσόδων',  value: fmt(totalInc), color:'green' },
      { label:'Κατηγορίες',   value: cats.toString(),color:'neutral' },
      { label:'Έτη (2017–2026)',value: '10',          color:'neutral' },
    ]
  }
  if (selectedReport.value === 'monthly') {
    const active = monthlyData.value.filter(m => m.moves > 0)
    const inc    = monthlyData.value.reduce((s,m) => s+m.income, 0)
    const pay    = monthlyData.value.reduce((s,m) => s+m.payments, 0)
    const moves  = monthlyData.value.reduce((s,m) => s+m.moves, 0)
    return [
      { label:'Εισπράξεις', value: fmt(inc),           color:'green' },
      { label:'Πληρωμές',   value: fmt(pay),            color:'red' },
      { label:'Καθαρό',     value: fmt(inc-pay),        color: inc-pay>=0?'green':'red' },
      { label:'Ενεργοί Μήνες', value: active.length.toString(), color:'neutral' },
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
  if (n >= 1000) return (n/1000).toFixed(0) + 'K'
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
    data = filteredCategory.value.map((r,i) => ({
      label: r.name,
      value: r.amount,
      raw: r.amount,
      isIncome: false,
    }))
  } else if (selectedReport.value === 'monthly') {
    data = monthlyData.value.map(m => ({
      label: m.month.substring(0,3),
      value: Math.abs(m.net),
      raw: m.net,
      isIncome: m.net >= 0,
    }))
  }
  const max = Math.max(...data.map(d => d.value), 1)
  const colors = ['#29b6f6','#4FC3A1','#ef5350','#ab47bc','#ff9800','#e91e63','#8bc34a','#ff5252','#26c6da','#ffd54f','#78909c']
  return data.map((d,i) => ({
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
  return years.map((y,i) => ({
    label: y,
    value: yearlyTotals.value[y] || 0,
    height: Math.max(((yearlyTotals.value[y]||0) / max) * 220, (yearlyTotals.value[y]||0) > 0 ? 4 : 0),
    color: colors[i % colors.length],
    displayValue: fmtShort(yearlyTotals.value[y]||0),
  }))
})

// Budget chart (stacked per year — simplified as grouped)
const budgetChartBars = computed(() => {
  const max = Math.max(...years.map(y => Math.max(budgetTotalExpenses.value[y]||0, budgetIncome.value.years[y]||0)), 1)
  return years.map((y,i) => ({
    label: y,
    expHeight: Math.max(((budgetTotalExpenses.value[y]||0)/max)*220, (budgetTotalExpenses.value[y]||0)>0?4:0),
    incHeight: Math.max(((budgetIncome.value.years[y]||0)/max)*220, (budgetIncome.value.years[y]||0)>0?4:0),
    expVal: fmtShort(budgetTotalExpenses.value[y]||0),
    incVal: fmtShort(budgetIncome.value.years[y]||0),
  }))
})

// ── Export (stub) ──────────────────────────────────────────────────
const exportExcel = () => {
  alert('Export Excel — θα συνδεθεί με το API στη φάση 2')
}

// ── Current date label ─────────────────────────────────────────────
const currentMonthLabel = new Intl.DateTimeFormat('el-GR', { month: 'long', year: 'numeric' }).format(new Date())
</script>

<template>
  <div class="reports-page">

    <!-- ── Top Bar ── -->
    <div class="top-bar">
      <h1 class="page-title">Αναφορές</h1>

      <div class="filters-row">
        <!-- Year -->
        <div class="select-wrap">
          <select v-model="selectedYear" class="filter-select">
            <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
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
      <div class="data-table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-name">ΥΠΟΚΑΤΗΓΟΡΙΑ</th>
              <th class="col-num">ΕΙΣΠΡΑΞΕΙΣ</th>
              <th class="col-num">ΠΛΗΡΩΜΕΣ</th>
              <th class="col-num">NET</th>
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
      <div class="data-table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-name">ΚΑΤΗΓΟΡΙΑ</th>
              <th class="col-num">ΠΟΣΟ</th>
              <th class="col-pct">%</th>
              <th class="col-moves">ΚΙΝΗΣΕΙΣ</th>
              <th class="col-num">Μ.Ο./ΚΙΝΗΣΗ</th>
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
      <div class="data-table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-name">ΜΗΝΑΣ</th>
              <th class="col-num">ΕΙΣΠΡΑΞΕΙΣ</th>
              <th class="col-num">ΠΛΗΡΩΜΕΣ</th>
              <th class="col-num">NET</th>
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
                  <div class="bar-value-label small">{{ bar.expVal }}</div>
                  <div class="bar-rect" :style="{ height: bar.expHeight + 'px', background: '#ef5350' }"></div>
                </div>
                <div class="bar-inner">
                  <div class="bar-value-label small">{{ bar.incVal }}</div>
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
  color: #c8d8e8;
  min-height: 100vh;
  background: #0d1e2e;
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
  color: #e0e6ed;
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
  color: #8899aa;
}
.select-wrap {
  position: relative;
}
.filter-select {
  appearance: none;
  background: #1a2f45;
  border: 1px solid #2a4a6a;
  color: #c8d8e8;
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
  color: #556677;
  font-size: 0.75rem;
}
.btn-export {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #1a2f45;
  border: 1px solid #2a4a6a;
  color: #c8d8e8;
  padding: 7px 14px;
  border-radius: 6px;
  font-size: 0.83rem;
  cursor: pointer;
  white-space: nowrap;
}
.btn-export:hover { background: #223d57; }
.export-icon { font-size: 1rem; }

/* ── KPI Row ── */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.kpi-card {
  background: #1a2f45;
  border-radius: 8px;
  padding: 16px 20px;
  text-align: center;
}
.kpi-value {
  font-size: 1.4rem;
  font-weight: 700;
  font-family: monospace;
  color: #e0e6ed;
  letter-spacing: -0.5px;
}
.kpi-label {
  font-size: 0.7rem;
  color: #8899aa;
  margin-top: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.kpi-green .kpi-value { color: #4FC3A1; }
.kpi-red   .kpi-value { color: #ef5350; }
.kpi-neutral .kpi-value { color: #e0e6ed; }

/* ── Data Table ── */
.data-table-wrap {
  background: #1a2f45;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 20px;
}
.data-table-wrap.scrollable-x {
  overflow-x: auto;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.83rem;
}
.data-table th {
  background: #152538;
  color: #6a8099;
  padding: 10px 16px;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  border-bottom: 1px solid #223d57;
}
.data-table td {
  padding: 9px 16px;
  border-bottom: 1px solid #1e3448;
}
.data-table tbody tr:hover { background: #1e3a52; }
.data-table tfoot td {
  background: #152538;
  border-top: 1px solid #2a4a6a;
  font-weight: 600;
  font-size: 0.84rem;
}

.col-name     { text-align: left; }
.col-num      { text-align: right; font-family: monospace; }
.col-year     { text-align: right; font-family: monospace; font-size: 0.79rem; padding: 9px 10px; }
.col-pct      { text-align: right; }
.col-moves    { text-align: right; color: #8899aa; }
.num-cell     { text-align: right; font-family: monospace; }

.income-val   { color: #4FC3A1; }
.payment-val  { color: #ef5350; }
.neutral-val  { color: #c8d8e8; }
.pct-val      { color: #8899aa; }
.bold-val     { font-weight: 700; }
.dash         { color: #3a5570; }

.total-row td { color: #e0e6ed; }

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
.budget-expenses-total td { background: #1b3a54; font-weight: 700; }
.budget-income-row td     { background: #142a3c; }
.budget-net-row td        { background: #0f2030; font-weight: 700; border-top: 1px solid #2a4a6a; }
.arrow-down { color: #ef5350; font-size: 0.65rem; margin-left: 2px; }
.arrow-up   { color: #4FC3A1; font-size: 0.65rem; margin-left: 2px; }
.net-icon   { margin-right: 4px; }

/* ── Chart Section ── */
.chart-section {
  background: #1a2f45;
  border-radius: 10px;
  padding: 20px;
}
.chart-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: #8899aa;
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
  color: #4a6a88;
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
  color: #c8d8e8;
  margin-bottom: 3px;
  text-align: center;
}
.bar-value-label.small { font-size: 0.6rem; }
.bar-rect {
  width: 70%;
  border-radius: 3px 3px 0 0;
  min-height: 2px;
  transition: height 0.3s ease;
}
.bar-x-label {
  font-size: 0.65rem;
  color: #4a6a88;
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
  color: #8899aa;
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* Pivot table */
.pivot-table th, .pivot-table td { white-space: nowrap; }
</style>
