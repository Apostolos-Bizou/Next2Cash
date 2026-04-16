<script setup>
import { ref, computed } from 'vue'
import { Bar, Pie, Line } from 'vue-chartjs'
import {
  Chart as ChartJS, Title, Tooltip, Legend,
  BarElement, CategoryScale, LinearScale, ArcElement,
  LineElement, PointElement, Filler
} from 'chart.js'

ChartJS.register(Title, Tooltip, Legend, BarElement, CategoryScale, LinearScale, ArcElement, LineElement, PointElement, Filler)

/* ── Filters ── */
const periodMode = ref('year')  // month / quarter / half / year / all / custom
const selectedMonth = ref('—')
const selectedYear = ref('2026')
const dateFrom = ref('')
const dateTo = ref('')
const months = ['—','Ιανουάριος','Φεβρουάριος','Μάρτιος','Απρίλιος','Μάιος','Ιούνιος','Ιούλιος','Αύγουστος','Σεπτέμβριος','Οκτώβριος','Νοέμβριος','Δεκέμβριος']
const years = ['2017','2018','2019','2020','2021','2022','2023','2024','2025','2026']

/* ── 4 Panels ── */
const obligationsData = {
  badge: '8 εκκρεμείς',
  urgent: 7718.75,
  total: 235597.98,
  items: [
    { date: '17 ΔΕΚ', desc: 'ΤΑΛΙΑΔΟΡΟΣ Λ...', amount: 1500.00 },
    { date: '18 ΔΕΚ', desc: 'ΤΑΛΙΑΔΟΡΟΣ ΛΟΓΙΣΤΗΣ...', amount: 2683.53 },
    { date: '28 ΙΑΝ', desc: 'ΤΑΛΙΑΔΟΡΟΣ ΛΟ...', amount: 3000.00 },
    { date: '29 ΙΑΝ', desc: 'ΤΑΛΙΑΔΟΡΟΣ ΛΟΓ...', amount: 297.50 },
    { date: '26 ΦΕΒ', desc: 'ΚΙΝΗΤΟ 02ος 2026', amount: 80.61 },
    { date: '27 ΜΑΡ', desc: 'ΠΑΠΑΚΙ dn2me.gr Τ...', amount: 36.08 },
    { date: '30 ΜΑΡ', desc: 'ΚΙΝΗΤΟ 03ος 2026...', amount: 80.61 },
    { date: '9 ΑΠΡ', desc: 'MICROSOFT AZU...', amount: 112.42 },
  ]
}
const banksData = {
  badge: '6 Λογ/μοί', total: 251.75,
  accounts: [
    { name: 'Πειραιώς',   type: 'Πειραιώς · EUR', balance: 248.97, date: '15/04/26', icon: '🏦' },
    { name: 'Μετρητά',    type: 'Ταμείο · EUR',   balance: 0.00,   date: '05/04/26', icon: '💵' },
    { name: 'Πορτοφόλι',  type: 'Ταμείο · EUR',   balance: 0.00,   date: '05/04/26', icon: '👛' },
    { name: 'Revolut GBP',type: 'Revolut · GBP',  balance: 0.00,   date: '05/04/26', icon: '💳' },
    { name: 'Revolut USD',type: 'Revolut · USD',   balance: 0.00,   date: '05/04/26', icon: '💳' },
    { name: 'Revolut EUR',type: 'Revolut · EUR',   balance: 2.78,   date: '05/04/26', icon: '💳' },
  ]
}
const reconcData = { badge: '251,75 €', bankTotal: 251.75, incomeTotal: 1045620.13, expensesTotal: 1234520.66, netPeriod: -188900.53 }
const cashData = {
  hero: -7459.00, subtitle: 'Τράπεζες μείον Εκκρεμείς',
  breakdown: [
    { label: 'Τράπεζες',           amount: 251.75,     color: '#29b6f6', icon: '🏦' },
    { label: 'Εκκρεμείς',          amount: -7718.75,   color: '#ff6400', icon: '⚡' },
    { label: 'Σύνολο Υποχρεώσεων', amount: -235597.98, color: '#ff9800', icon: '⚠' },
    { label: 'Καθαρά Διαθέσιμα',   amount: -235346.23, color: '#ef5350', icon: '⚖' },
  ]
}

/* ── KPIs ── */
const kpis = [
  { key: 'income',   label: 'Εισπράξεις',       value: 3580.20,    tone: 'green'   },
  { key: 'expenses', label: 'Πληρωμές',          value: 10887.64,   tone: 'red'     },
  { key: 'net',      label: 'Καθαρό',            value: -7307.44,   tone: 'red'     },
  { key: 'pending',  label: 'Εκκρεμείς',         value: 7718.75,    tone: 'orange'  },
  { key: 'balance',  label: 'Τρέχον Υπόλοιπο',  value: -194583.52, tone: 'red'     },
  { key: 'moves',    label: 'Κινήσεις Περιόδου', value: 89,         tone: 'neutral', isCount: true },
  { key: 'avg',      label: 'Μ.Ο./Κίνηση',      value: 122.33,     tone: 'neutral' },
  { key: 'avgm',     label: 'Μ.Ο./Μήνα',        value: 907.30,     tone: 'neutral' },
]

/* ── Chart common options ── */
const darkTooltip = { backgroundColor: '#1a2f45', titleColor: '#e0e6ed', bodyColor: '#c8d8e8', padding: 10, cornerRadius: 6 }
const darkLegend = (pos) => ({ position: pos, labels: { color: '#8899aa', usePointStyle: true, padding: 16, font: { size: 11 } } })
const xAxis = { grid: { display: false }, ticks: { color: '#4a6a88', font: { size: 11 } }, border: { color: '#2a4a6a' } }
const yAxis = (cb) => ({ beginAtZero: false, grid: { color: 'rgba(42,74,106,0.4)' }, ticks: { color: '#4a6a88', font: { size: 10 }, callback: cb }, border: { color: 'transparent' } })

/* ── Monthly Bar chart ── */
const barData = {
  labels: ['Ιαν 26', 'Φεβ 26', 'Μαρ 26', 'Απρ 26'],
  datasets: [
    { label: 'Εισπράξεις', data: [1040, 282, 694, 1564], backgroundColor: '#4FC3A1', borderRadius: 4, borderSkipped: false, maxBarThickness: 36 },
    { label: 'Πληρωμές',   data: [5200, 1700, 1900, 2200], backgroundColor: '#ef5350', borderRadius: 4, borderSkipped: false, maxBarThickness: 36 }
  ]
}
const barOptions = {
  responsive: true, maintainAspectRatio: false,
  plugins: { legend: darkLegend('top'), tooltip: { ...darkTooltip, callbacks: { label: (ctx) => `${ctx.dataset.label}: ${ctx.parsed.y.toLocaleString('el-GR')} €` } } },
  scales: { x: xAxis, y: { ...yAxis((v) => `${(v/1000).toFixed(0)}K`), beginAtZero: true } }
}

/* ── Pie chart ── */
const pieData = {
  labels: ['Λειτουργικά', 'Λοιπά', 'Εξοπλισμός', 'Απασχόληση', 'Άλλα'],
  datasets: [{ data: [3679, 3297, 2422, 1488, 0], backgroundColor: ['#29b6f6','#4FC3A1','#ff9800','#ef5350','#ab47bc'], borderColor: '#1a2f45', borderWidth: 2, hoverOffset: 6 }]
}
const pieOptions = {
  responsive: true, maintainAspectRatio: false,
  plugins: { legend: { position: 'right', labels: { color: '#8899aa', usePointStyle: true, pointStyle: 'circle', padding: 12, font: { size: 11 } } }, tooltip: darkTooltip }
}

/* ── Net Bar chart ── */
const netData = {
  labels: ['Ιαν 26', 'Φεβ 26', 'Μαρ 26', 'Απρ 26'],
  datasets: [{ label: 'Net', data: [-4160, -1418, -1206, -636], backgroundColor: '#ef5350', borderRadius: 4, borderSkipped: false, maxBarThickness: 60 }]
}
const netOptions = {
  responsive: true, maintainAspectRatio: false,
  plugins: { legend: darkLegend('top'), tooltip: { ...darkTooltip, callbacks: { label: (ctx) => `Net: ${ctx.parsed.y.toLocaleString('el-GR')} €` } } },
  scales: { x: xAxis, y: yAxis((v) => `${v.toLocaleString('el-GR')}`) }
}

/* ── Balance Trend Line chart ── */
// Generate mock daily balance data from Jan to Apr 2026
const balanceDates = []
const balanceValues = []
let bal = -187800
const startDate = new Date('2026-01-01')
for (let i = 0; i < 105; i++) {
  const d = new Date(startDate)
  d.setDate(startDate.getDate() + i)
  balanceDates.push(`${d.getDate().toString().padStart(2,'0')}/${(d.getMonth()+1).toString().padStart(2,'0')}/${String(d.getFullYear()).slice(2)}`)
  // simulate drops
  if (i === 28) bal -= 3200
  if (i > 28 && i < 60) bal -= Math.random() * 80 + 20
  if (i >= 60) bal -= Math.random() * 40 + 5
  balanceValues.push(Math.round(bal))
}
const balanceData = {
  labels: balanceDates,
  datasets: [{
    label: 'Υπόλοιπο',
    data: balanceValues,
    borderColor: '#29b6f6',
    backgroundColor: 'rgba(41,182,246,0.06)',
    fill: true, tension: 0.3,
    pointRadius: 0, pointHoverRadius: 4,
    borderWidth: 1.5
  }]
}
const balanceOptions = {
  responsive: true, maintainAspectRatio: false,
  plugins: { legend: darkLegend('top'), tooltip: { ...darkTooltip, callbacks: { label: (ctx) => `Υπόλοιπο: ${ctx.parsed.y.toLocaleString('el-GR')} €` } } },
  scales: {
    x: { ...xAxis, ticks: { color: '#4a6a88', font: { size: 9 }, maxTicksLimit: 20, maxRotation: 45 } },
    y: yAxis((v) => `${(v/1000).toFixed(0)}K`)
  }
}

/* ── Yearly Comparison Bar chart ── */
const yearlyLabels = ['2017','2018','2019','2020','2021','2022','2023','2024','2025','2026']
const yearlyData = {
  labels: yearlyLabels,
  datasets: [
    { label: 'Ανάπτυξη Λογισμικού', data: [17157,282988,78998,42500,31500,0,0,0,0,0],       backgroundColor: '#29b6f6', borderRadius: 2, maxBarThickness: 18 },
    { label: 'Απασχόληση',           data: [21074,59775,58012,59787,25036,15858,11018,11548,9151,1488], backgroundColor: '#4FC3A1', borderRadius: 2, maxBarThickness: 18 },
    { label: 'Λειτουργικά',          data: [19236,32186,30119,27052,21985,19192,20086,24792,12687,3679], backgroundColor: '#ff9800', borderRadius: 2, maxBarThickness: 18 },
    { label: 'Λοιπά',               data: [6788,21567,50833,45672,23424,5995,10228,3250,10603,3297],  backgroundColor: '#ef5350', borderRadius: 2, maxBarThickness: 18 },
    { label: 'Προσωπικό',            data: [2267,40538,50514,29871,12009,1277,934,682,522,0],          backgroundColor: '#ab47bc', borderRadius: 2, maxBarThickness: 18 },
    { label: 'Εξοπλισμός',           data: [4621,8468,1715,8323,2586,2415,2152,2609,1979,2422],        backgroundColor: '#26c6da', borderRadius: 2, maxBarThickness: 18 },
  ]
}
const yearlyOptions = {
  responsive: true, maintainAspectRatio: false,
  plugins: { legend: { position: 'top', labels: { color: '#8899aa', usePointStyle: true, padding: 12, font: { size: 10 } } }, tooltip: { ...darkTooltip, callbacks: { label: (ctx) => `${ctx.dataset.label}: ${(ctx.parsed.y/1000).toFixed(1)}K €` } } },
  scales: {
    x: xAxis,
    y: { beginAtZero: true, grid: { color: 'rgba(42,74,106,0.4)' }, ticks: { color: '#4a6a88', font: { size: 10 }, callback: (v) => `${(v/1000).toFixed(0)}K` }, border: { color: 'transparent' } }
  }
}

/* ── Tables ── */
const transactions = [
  { date: '14/04/26', description: '4777 - ΕΣΟΔΑ ΠΡΟΣΩΠΙΚΑ ΤΡΑΠΕΖΑ',   category: 'Έσοδα Β',     income: 158.00,  expense: null   },
  { date: '14/04/26', description: 'Πληρωμή #4776 — ΠΑΠΑΚΙ ΑΓΟΡΑ DOM',  category: 'Εξοπλισμός',  income: null,    expense: 12.40  },
  { date: '09/04/26', description: '4775 - MICROSOFT AZURE 03ος 2026',   category: 'Εξοπλισμός',  income: null,    expense: 112.42 },
  { date: '08/04/26', description: 'Πληρωμή #4732 — ΠΑΠΑΚΙ doctornex',  category: 'Εξοπλισμός',  income: null,    expense: 36.08  },
  { date: '07/04/26', description: '4748 - EPASS',                       category: 'Λειτουργικά', income: null,    expense: 50.00  },
  { date: '07/04/26', description: 'Πληρωμή #4747 — ΕΣΟΔΑ ΠΡΟΣΩΠΙΚΑ',   category: 'Έσοδα Β',     income: 134.00,  expense: null   },
  { date: '06/04/26', description: '4732 - ΠΑΠΑΚΙ doctornexttome.gr',    category: 'Εξοπλισμός',  income: null,    expense: 36.08  },
  { date: '05/04/26', description: 'Πληρωμή #4656 — ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ Λ', category: 'Απασχόληση',  income: null,    expense: 186.00 },
]
const topPayments = [
  { description: '4761 - ΤΑΛΙΑΔΟΡΟΣ ΛΟΓΙΣΤΗΣ Dn2Me UK', sub: 'Dn2Me-UK',       amount: 3000.00 },
  { description: '4774 - ΕNOIKIO 04ος 2026',             sub: 'Ενοίκιο',        amount: 650.00  },
  { description: '4773 - ΕNOIKIO 03ος 2026',             sub: 'Ενοίκιο',        amount: 650.00  },
  { description: '4772 - ΕNOIKIO 02ος 2026',             sub: 'Ενοίκιο',        amount: 650.00  },
  { description: 'ΕNOIKIO 01ος 2026',                    sub: 'Ενοίκιο',        amount: 650.00  },
  { description: '4762 - ΤΑΛΙΑΔΟΡΟΣ ΛΟΓΙΣΤΗΣ Dn2Me UK', sub: 'Dn2Me-UK',       amount: 297.50  },
  { description: 'Πληρωμή #4684 — AI CLAUDIE VIVA',      sub: 'Λογισμικά / ERP',amount: 223.20  },
]
const categoryRows = [
  { name: 'Λειτουργικά', amount: 3679.22, pct: 33.8, moves: 30, avg: 122.64,  color: '#29b6f6' },
  { name: 'Λοιπά',       amount: 3297.50, pct: 30.3, moves: 2,  avg: 1648.75, color: '#4FC3A1' },
  { name: 'Εξοπλισμός',  amount: 2422.92, pct: 22.3, moves: 30, avg: 80.76,   color: '#ff9800' },
  { name: 'Απασχόληση',  amount: 1488.00, pct: 13.7, moves: 8,  avg: 186.00,  color: '#ef5350' },
  { name: 'Έσοδα Β',     amount: 0,       pct: 0.0,  moves: 18, avg: 0,       color: '#ab47bc' },
  { name: 'Έσοδα Β',     amount: 0,       pct: 0.0,  moves: 1,  avg: 0,       color: '#26c6da' },
]
const subcategoryRows = [
  { name: 'Dn2Me-UK',          income: 0,       payments: 3297.50, net: -3297.50 },
  { name: 'Ενοίκιο',           income: 0,       payments: 2680.00, net: -2680.00 },
  { name: 'ΚΑΓΚΕΛΑΡΗΣ',        income: 2464.20, payments: 0,       net: 2464.20  },
  { name: 'Λογισμικά / ERP',   income: 0,       payments: 1544.78, net: -1544.78 },
  { name: 'Finance',            income: 0,       payments: 1488.00, net: -1488.00 },
  { name: 'ΒΑΡΙΑΣ',            income: 1116.00, payments: 0,       net: 1116.00  },
  { name: 'Άδειες Χρήσης',     income: 0,       payments: 878.14,  net: -878.14  },
  { name: 'Έξοδα Κίνησης',     income: 0,       payments: 551.28,  net: -551.28  },
  { name: 'Τηλέφωνα',          income: 0,       payments: 419.68,  net: -419.68  },
  { name: 'Έξοδα Διαχείρισης', income: 0,       payments: 181.94,  net: -181.94  },
  { name: 'Γεύματα Εργασίας',  income: 0,       payments: 6.48,    net: -6.48    },
]

/* ── Helpers ── */
const fmt = (n) => {
  if (n === null || n === undefined) return '—'
  return new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n) + ' €'
}
const currentMonthLabel = new Intl.DateTimeFormat('el-GR', { month: 'long', year: 'numeric' }).format(new Date())
</script>

<template>
  <div class="dashboard-page">

    <!-- ── Filters Bar ── -->
    <div class="filters-bar">
      <div class="period-btns">
        <button v-for="p in [{k:'month',l:'Μήνας'},{k:'quarter',l:'Τρίμηνο'},{k:'half',l:'6μηνο'},{k:'year',l:'Έτος'},{k:'all',l:'Όλα'}]"
          :key="p.k" class="period-btn" :class="{ active: periodMode === p.k }" @click="periodMode = p.k">
          {{ p.l }}
        </button>
      </div>
      <div class="filter-sep"></div>
      <span class="filter-label">Μήνας:</span>
      <div class="select-wrap">
        <select v-model="selectedMonth" class="filter-select">
          <option v-for="m in months" :key="m">{{ m }}</option>
        </select>
      </div>
      <div class="select-wrap">
        <select v-model="selectedYear" class="filter-select">
          <option v-for="y in years" :key="y">{{ y }}</option>
        </select>
      </div>
      <span class="filter-label">Από:</span>
      <input v-model="dateFrom" type="date" class="filter-input" />
      <span class="filter-label">Έως:</span>
      <input v-model="dateTo" type="date" class="filter-input" />
      <button class="btn-apply">▼ Εφαρμογή</button>
      <span class="date-label-right">{{ currentMonthLabel }}</span>
    </div>

    <!-- ══ 4 PANELS ══ -->
    <div class="panels-4col">

      <!-- Υποχρεώσεις -->
      <div class="panel-card">
        <div class="panel-card-header">
          <span class="panel-card-title" style="color:#ff9800">📅 Υποχρεώσεις</span>
          <span class="panel-badge" style="background:rgba(255,152,0,0.15);color:#ff9800">{{ obligationsData.badge }}</span>
        </div>
        <div class="oblig-summary">
          <div class="oblig-sum-item">
            <span class="oblig-sum-label">⚡ ΕΚΚΡΕΜΕΙΣ</span>
            <span class="oblig-sum-val" style="color:#ef5350">{{ fmt(obligationsData.urgent) }}</span>
          </div>
          <div class="oblig-sum-item">
            <span class="oblig-sum-label">ΣΥΝΟΛΟ ΑΠΛΗΡΩΤΩΝ</span>
            <span class="oblig-sum-val" style="color:#ef5350">{{ fmt(obligationsData.total) }}</span>
          </div>
        </div>
        <div class="oblig-list">
          <div v-for="item in obligationsData.items" :key="item.date+item.desc" class="oblig-item">
            <span class="oblig-date">{{ item.date }}</span>
            <span class="oblig-desc">{{ item.desc }}</span>
            <span class="oblig-amount" style="color:#ef5350">{{ fmt(item.amount) }}</span>
          </div>
        </div>
      </div>

      <!-- Τράπεζες -->
      <div class="panel-card">
        <div class="panel-card-header">
          <span class="panel-card-title" style="color:#4FC3A1">🏦 Τράπεζες</span>
          <span class="panel-badge" style="background:rgba(79,195,161,0.15);color:#4FC3A1">{{ banksData.badge }}</span>
        </div>
        <div class="bank-total-bar">
          <span class="bank-total-label">Σύνολο Τραπεζών</span>
          <span class="bank-total-val" style="color:#4FC3A1">{{ fmt(banksData.total) }}</span>
        </div>
        <div class="bank-list">
          <div v-for="acc in banksData.accounts" :key="acc.name" class="bank-item">
            <div class="bank-item-left">
              <span class="bank-icon">{{ acc.icon }}</span>
              <div>
                <div class="bank-name">{{ acc.name }}</div>
                <div class="bank-type">{{ acc.type }}</div>
              </div>
            </div>
            <div class="bank-item-right">
              <span class="bank-balance" :style="{ color: acc.balance > 0 ? '#4FC3A1' : '#8899aa' }">{{ fmt(acc.balance) }}</span>
              <span class="bank-date">{{ acc.date }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Ισοσκελισμός -->
      <div class="panel-card">
        <div class="panel-card-header">
          <span class="panel-card-title" style="color:#29b6f6">⚖ Ισοσκελισμός</span>
          <span class="panel-badge" style="background:rgba(41,182,246,0.15);color:#29b6f6">{{ reconcData.badge }}</span>
        </div>
        <div class="recon-grid">
          <div class="recon-item">
            <span class="recon-icon">🏦</span>
            <span class="recon-label">Σύνολο Τραπεζών</span>
            <span class="recon-val">{{ fmt(reconcData.bankTotal) }}</span>
          </div>
          <div class="recon-item">
            <span class="recon-icon">↓</span>
            <span class="recon-label">Εισπράξεις περιόδου</span>
            <span class="recon-val" style="color:#4FC3A1">{{ fmt(reconcData.incomeTotal) }}</span>
          </div>
          <div class="recon-item">
            <span class="recon-icon">↑</span>
            <span class="recon-label">Πληρωμές περιόδου</span>
            <span class="recon-val" style="color:#ef5350">{{ fmt(reconcData.expensesTotal) }}</span>
          </div>
          <div class="recon-item">
            <span class="recon-icon">📊</span>
            <span class="recon-label">Καθαρό περιόδου</span>
            <span class="recon-val" style="color:#ef5350">{{ fmt(reconcData.netPeriod) }}</span>
          </div>
        </div>
      </div>

      <!-- Ταμειακά Διαθέσιμα -->
      <div class="panel-card">
        <div class="panel-card-header">
          <span class="panel-card-title" style="color:#4FC3A1">💰 Ταμειακά Διαθέσιμα</span>
        </div>
        <div class="cash-hero">
          <div class="cash-total" :style="{ color: cashData.hero >= 0 ? '#4FC3A1' : '#ef5350' }">{{ fmt(cashData.hero) }}</div>
          <div class="cash-subtitle">{{ cashData.subtitle }}</div>
        </div>
        <div class="cash-breakdown">
          <div v-for="item in cashData.breakdown" :key="item.label" class="cash-item">
            <div class="cash-item-left">
              <span class="cash-item-icon">{{ item.icon }}</span>
              <span class="cash-item-label">{{ item.label }}</span>
            </div>
            <span class="cash-item-amount" :style="{ color: item.color }">{{ fmt(item.amount) }}</span>
          </div>
        </div>
      </div>

    </div>

    <!-- ══ 8 KPI CARDS ══ -->
    <div class="kpi-grid">
      <div v-for="kpi in kpis" :key="kpi.key" class="kpi-card" :class="'kpi-' + kpi.tone">
        <div class="kpi-value">
          <span v-if="kpi.isCount">{{ kpi.value }}</span>
          <span v-else>{{ fmt(kpi.value) }}</span>
        </div>
        <div class="kpi-label">{{ kpi.label }}</div>
      </div>
    </div>

    <!-- ══ CHARTS ROW 1: Μηνιαία + Κατανομή + Net ══ -->
    <div class="charts-3col">
      <div class="chart-panel">
        <div class="panel-title"><span class="panel-icon">▤</span> Μηνιαία Σύνοψη</div>
        <div class="chart-wrap" style="height:260px"><Bar :data="barData" :options="barOptions" /></div>
      </div>
      <div class="chart-panel">
        <div class="panel-title"><span class="panel-icon">◕</span> Κατανομή Εξόδων</div>
        <div class="chart-wrap" style="height:260px"><Pie :data="pieData" :options="pieOptions" /></div>
      </div>
      <div class="chart-panel">
        <div class="panel-title"><span class="panel-icon">⇄</span> Net (Εισπράξεις − Πληρωμές)</div>
        <div class="chart-wrap" style="height:260px"><Bar :data="netData" :options="netOptions" /></div>
      </div>
    </div>

    <!-- ══ CHART: Πορεία Υπολοίπου ══ -->
    <div class="chart-panel">
      <div class="panel-title"><span class="panel-icon">↗</span> Πορεία Υπολοίπου</div>
      <div class="chart-wrap" style="height:220px"><Line :data="balanceData" :options="balanceOptions" /></div>
    </div>

    <!-- ══ TABLES ROW 1 ══ -->
    <div class="tables-row">
      <div class="table-panel">
        <div class="panel-title"><span class="panel-icon">▤</span> Ανάλυση ανά Κατηγορία</div>
        <table class="data-table">
          <thead>
            <tr>
              <th>ΚΑΤΗΓΟΡΙΑ</th><th class="num">ΠΟΣΟ</th><th class="num">ΚΑΤΑΝΟΜΗ</th><th class="num">%</th><th class="num">Μ.Ο./ΜΗΝΑ</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in categoryRows" :key="row.name+row.pct">
              <td><span class="cat-dot" :style="{ background: row.color }"></span>{{ row.name }}</td>
              <td class="num payment-val">{{ fmt(row.amount) }}</td>
              <td class="num"><div class="progress-bar"><div class="progress-fill" :style="{ width: row.pct + '%', background: row.color }"></div></div></td>
              <td class="num muted-val">{{ row.pct.toFixed(1) }}%</td>
              <td class="num neutral-val">{{ fmt(row.avg) }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td>ΣΥΝΟΛΟ</td>
              <td class="num payment-val">{{ fmt(categoryRows.reduce((s,r)=>s+r.amount,0)) }}</td>
              <td class="num"></td>
              <td class="num income-val">100%</td>
              <td class="num muted-val">{{ fmt(categoryRows.reduce((s,r)=>s+r.amount,0)/4) }}</td>
            </tr>
          </tfoot>
        </table>
      </div>
      <div class="table-panel">
        <div class="panel-title"><span class="panel-icon">⟳</span> Πρόσφατες Κινήσεις</div>
        <div class="tx-list">
          <div v-for="(tx,i) in transactions" :key="i" class="tx-item">
            <div class="tx-left">
              <div class="tx-desc">{{ tx.description }}</div>
              <div class="tx-meta">{{ tx.date }} · {{ tx.category }}</div>
            </div>
            <div class="tx-amount" :class="tx.income ? 'income-val' : 'payment-val'">
              {{ tx.income ? '+' + fmt(tx.income) : '-' + fmt(tx.expense) }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ══ TABLES ROW 2 ══ -->
    <div class="tables-row">
      <div class="table-panel">
        <div class="panel-title"><span class="panel-icon">▤</span> Ανά Υποκατηγορία</div>
        <table class="data-table">
          <thead>
            <tr><th>ΥΠΟΚΑΤΗΓΟΡΙΑ</th><th class="num">ΕΙΣΠΡΑΞΕΙΣ</th><th class="num">ΠΛΗΡΩΜΕΣ</th><th class="num">NET</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in subcategoryRows" :key="row.name">
              <td>{{ row.name }}</td>
              <td class="num income-val"><span v-if="row.income>0">{{ fmt(row.income) }}</span><span v-else class="dash">—</span></td>
              <td class="num payment-val"><span v-if="row.payments>0">{{ fmt(row.payments) }}</span><span v-else class="dash">—</span></td>
              <td class="num" :class="row.net>=0?'income-val':'payment-val'">{{ fmt(row.net) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="table-panel">
        <div class="panel-title"><span class="panel-icon">🏆</span> Top Πληρωμές</div>
        <div class="tx-list">
          <div v-for="(p,i) in topPayments" :key="i" class="tx-item">
            <div class="tx-left">
              <div class="tx-desc">{{ p.description }}</div>
              <div class="tx-meta">{{ p.sub }}</div>
            </div>
            <div class="tx-amount payment-val">{{ fmt(p.amount) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ══ CHART: Ετήσια Σύγκριση ══ -->
    <div class="chart-panel">
      <div class="panel-title"><span class="panel-icon">📅</span> Ετήσια Σύγκριση ανά Κατηγορία</div>
      <div class="chart-wrap" style="height:280px"><Bar :data="yearlyData" :options="yearlyOptions" /></div>
    </div>

  </div>
</template>

<style scoped>
.dashboard-page { padding: 20px 24px; color: #c8d8e8; background: #0d1e2e; display: flex; flex-direction: column; gap: 16px; }

/* ── Filters ── */
.filters-bar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  background: #1a2f45; border-radius: 10px; padding: 12px 16px;
}
.period-btns { display: flex; gap: 4px; }
.period-btn {
  background: #152538; border: none; color: #8899aa;
  padding: 6px 14px; border-radius: 6px; cursor: pointer; font-size: 0.82rem;
  transition: all 0.15s;
}
.period-btn.active { background: #29b6f6; color: #0d1e2e; font-weight: 600; }
.period-btn:hover:not(.active) { background: #1e3a52; color: #c8d8e8; }
.filter-sep { width: 1px; height: 24px; background: #2a4a6a; margin: 0 4px; }
.filter-label { font-size: 0.78rem; color: #8899aa; white-space: nowrap; }
.select-wrap { position: relative; }
.filter-select {
  appearance: none; background: #152538; border: 1px solid #2a4a6a;
  color: #c8d8e8; padding: 6px 28px 6px 10px; border-radius: 6px;
  font-size: 0.82rem; cursor: pointer; outline: none;
}
.filter-input {
  background: #152538; border: 1px solid #2a4a6a; color: #c8d8e8;
  padding: 6px 10px; border-radius: 6px; font-size: 0.82rem; outline: none;
}
.btn-apply {
  background: #29b6f6; border: none; color: #0d1e2e;
  padding: 6px 14px; border-radius: 6px; cursor: pointer;
  font-size: 0.82rem; font-weight: 600;
}
.date-label-right { font-size: 0.78rem; color: #8899aa; margin-left: auto; white-space: nowrap; }

/* ── 4 Panels ── */
.panels-4col { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.panel-card { background: #1a2f45; border-radius: 10px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px; overflow: hidden; }
.panel-card-header { display: flex; align-items: center; justify-content: space-between; }
.panel-card-title { font-size: 0.82rem; font-weight: 600; }
.panel-badge { font-size: 0.7rem; font-weight: 600; padding: 2px 8px; border-radius: 10px; }

/* Obligations */
.oblig-summary { display: flex; gap: 8px; }
.oblig-sum-item { flex: 1; background: #152538; border-radius: 6px; padding: 8px 10px; }
.oblig-sum-label { display: block; font-size: 0.62rem; color: #8899aa; letter-spacing: 0.05em; margin-bottom: 4px; }
.oblig-sum-val { font-size: 0.88rem; font-weight: 700; font-family: monospace; }
.oblig-list { overflow-y: auto; max-height: 220px; display: flex; flex-direction: column; }
.oblig-item { display: flex; align-items: center; gap: 6px; padding: 5px 0; border-bottom: 1px solid #1e3448; font-size: 0.78rem; }
.oblig-date { font-size: 0.68rem; color: #ef5350; background: rgba(239,83,80,0.1); border-radius: 4px; padding: 1px 5px; white-space: nowrap; min-width: 44px; text-align: center; }
.oblig-desc { flex: 1; color: #c8d8e8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.oblig-amount { font-family: monospace; font-size: 0.78rem; font-weight: 600; white-space: nowrap; }

/* Banks */
.bank-total-bar { background: #152538; border-radius: 6px; padding: 8px 12px; display: flex; justify-content: space-between; align-items: center; }
.bank-total-label { font-size: 0.75rem; color: #8899aa; }
.bank-total-val { font-size: 1rem; font-weight: 700; font-family: monospace; }
.bank-list { display: flex; flex-direction: column; overflow-y: auto; max-height: 220px; }
.bank-item { display: flex; align-items: center; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid #1e3448; }
.bank-item-left { display: flex; align-items: center; gap: 8px; }
.bank-icon { font-size: 0.9rem; }
.bank-name { font-size: 0.8rem; color: #c8d8e8; font-weight: 500; }
.bank-type { font-size: 0.68rem; color: #8899aa; }
.bank-item-right { text-align: right; }
.bank-balance { font-size: 0.82rem; font-weight: 600; font-family: monospace; display: block; }
.bank-date { font-size: 0.65rem; color: #8899aa; }

/* Reconciliation */
.recon-grid { display: flex; flex-direction: column; gap: 8px; }
.recon-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; background: #152538; border-radius: 6px; }
.recon-icon { font-size: 0.85rem; width: 18px; text-align: center; }
.recon-label { flex: 1; font-size: 0.73rem; color: #8899aa; }
.recon-val { font-size: 0.85rem; font-weight: 700; font-family: monospace; color: #e0e6ed; }

/* Cash */
.cash-hero { text-align: center; padding: 8px 0 4px; }
.cash-total { font-size: 1.7rem; font-weight: 800; font-family: monospace; letter-spacing: -1px; }
.cash-subtitle { font-size: 0.7rem; color: #8899aa; margin-top: 4px; }
.cash-breakdown { display: flex; flex-direction: column; gap: 6px; }
.cash-item { display: flex; align-items: center; justify-content: space-between; padding: 6px 10px; background: #152538; border-radius: 6px; }
.cash-item-left { display: flex; align-items: center; gap: 8px; }
.cash-item-icon { font-size: 0.82rem; }
.cash-item-label { font-size: 0.73rem; color: #8899aa; }
.cash-item-amount { font-size: 0.83rem; font-weight: 700; font-family: monospace; }

/* KPI Grid */
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.kpi-card { background: #1a2f45; border-radius: 10px; padding: 16px 18px; border-top: 3px solid #2a4a6a; }
.kpi-green   { border-top-color: #4FC3A1; }
.kpi-red     { border-top-color: #ef5350; }
.kpi-orange  { border-top-color: #ff9800; }
.kpi-neutral { border-top-color: #556677; }
.kpi-value { font-size: 1.25rem; font-weight: 700; font-family: monospace; color: #e0e6ed; margin-bottom: 4px; }
.kpi-green  .kpi-value { color: #4FC3A1; }
.kpi-red    .kpi-value { color: #ef5350; }
.kpi-orange .kpi-value { color: #ff9800; }
.kpi-label { font-size: 0.68rem; color: #8899aa; text-transform: uppercase; letter-spacing: 0.05em; }

/* Charts */
.charts-3col { display: grid; grid-template-columns: 1.4fr 1fr 1fr; gap: 16px; }
.chart-panel { background: #1a2f45; border-radius: 10px; padding: 16px; }
.chart-wrap { position: relative; margin-top: 12px; }
.panel-title { font-size: 0.83rem; font-weight: 600; color: #8899aa; display: flex; align-items: center; gap: 8px; }
.panel-icon { color: #4FC3A1; }

/* Tables */
.tables-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.table-panel { background: #1a2f45; border-radius: 10px; padding: 16px; }
.data-table { width: 100%; border-collapse: collapse; font-size: 0.82rem; margin-top: 12px; }
.data-table th { background: #152538; color: #6a8099; padding: 8px 12px; font-size: 0.68rem; font-weight: 600; letter-spacing: 0.06em; border-bottom: 1px solid #223d57; text-align: left; }
.data-table td { padding: 8px 12px; border-bottom: 1px solid #1e3448; vertical-align: middle; }
.data-table tbody tr:hover { background: #1e3a52; }
.data-table tfoot td { background: #152538; border-top: 1px solid #2a4a6a; font-weight: 600; padding: 8px 12px; }
.num { text-align: right; font-family: monospace; }
.income-val  { color: #4FC3A1; }
.payment-val { color: #ef5350; }
.neutral-val { color: #c8d8e8; }
.muted-val   { color: #8899aa; }
.dash        { color: #3a5570; }
.total-row td { color: #e0e6ed; }
.cat-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
.progress-bar { background: #2a4a6a; border-radius: 4px; height: 5px; width: 80px; display: inline-block; vertical-align: middle; }
.progress-fill { height: 5px; border-radius: 4px; }
.tx-list { margin-top: 12px; }
.tx-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid #1e3448; }
.tx-item:last-child { border-bottom: none; }
.tx-left { flex: 1; min-width: 0; }
.tx-desc { font-size: 0.82rem; color: #c8d8e8; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tx-meta { font-size: 0.72rem; color: #8899aa; margin-top: 2px; }
.tx-amount { font-family: monospace; font-size: 0.85rem; font-weight: 600; white-space: nowrap; }

/* Responsive */
@media (max-width: 1200px) {
  .panels-4col { grid-template-columns: repeat(2,1fr); }
  .kpi-grid { grid-template-columns: repeat(2,1fr); }
  .charts-3col { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 900px) {
  .panels-4col { grid-template-columns: 1fr; }
  .charts-3col { grid-template-columns: 1fr; }
  .tables-row { grid-template-columns: 1fr; }
}
</style>
