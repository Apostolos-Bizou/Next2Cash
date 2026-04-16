<script setup>
import { ref, computed, onMounted } from 'vue'
import { Bar, Doughnut } from 'vue-chartjs'
import {
  Chart as ChartJS, Title, Tooltip, Legend,
  BarElement, CategoryScale, LinearScale, ArcElement
} from 'chart.js'
import api from '@/api'

ChartJS.register(Title, Tooltip, Legend, BarElement, CategoryScale, LinearScale, ArcElement)

// ── Entity ──────────────────────────────────────────────────────────
const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14',
}
const selectedEntity = ref(localStorage.getItem('n2c_entity') || 'next2me')
const entityId = computed(() => ENTITIES[selectedEntity.value])

// ── Filters ──────────────────────────────────────────────────────────
const periodMode   = ref('year')
const selectedYear = ref(String(new Date().getFullYear()))
const selectedMonth = ref('')
const dateFrom = ref('')
const dateTo   = ref('')
const MONTHS = ['Ιανουάριος','Φεβρουάριος','Μάρτιος','Απρίλιος','Μάιος','Ιούνιος','Ιούλιος','Αύγουστος','Σεπτέμβριος','Οκτώβριος','Νοέμβριος','Δεκέμβριος']
const MONTH_SHORT = ['Ιαν','Φεβ','Μαρ','Απρ','Μαι','Ιουν','Ιουλ','Αυγ','Σεπ','Οκτ','Νοε','Δεκ']
const years = ['2017','2018','2019','2020','2021','2022','2023','2024','2025','2026']

// ── State ─────────────────────────────────────────────────────────────
const loading  = ref(false)
const error    = ref(null)
const kpis     = ref({})
const recent   = ref([])
const monthly  = ref([])
const banks    = ref([])
const bankTotal = ref(0)
const obligations = ref([])

// ── Date range ────────────────────────────────────────────────────────
function getDateRange() {
  const y = parseInt(selectedYear.value)
  if (periodMode.value === 'all')    return { from: '2017-01-01', to: '2026-12-31' }
  if (periodMode.value === 'custom') return { from: dateFrom.value, to: dateTo.value }
  if (periodMode.value === 'month') {
    const m = MONTHS.indexOf(selectedMonth.value) + 1
    if (m > 0) {
      const last = new Date(y, m, 0).getDate()
      return { from: `${y}-${String(m).padStart(2,'0')}-01`, to: `${y}-${String(m).padStart(2,'0')}-${last}` }
    }
  }
  if (periodMode.value === 'quarter') {
    const q = Math.ceil((new Date().getMonth()+1)/3)
    const qF = (q-1)*3+1, qT = q*3
    const last = new Date(y, qT, 0).getDate()
    return { from: `${y}-${String(qF).padStart(2,'0')}-01`, to: `${y}-${String(qT).padStart(2,'0')}-${last}` }
  }
  if (periodMode.value === 'half') {
    return new Date().getMonth() < 6
      ? { from: `${y}-01-01`, to: `${y}-06-30` }
      : { from: `${y}-07-01`, to: `${y}-12-31` }
  }
  return { from: `${y}-01-01`, to: `${y}-12-31` }
}

// ── Load all data ─────────────────────────────────────────────────────
async function loadDashboard() {
  loading.value = true
  error.value   = null
  try {
    const { from, to } = getDateRange()

    // 1. Dashboard KPIs + recent + monthly
    const res = await api.get('/api/dashboard', {
      params: { entityId: entityId.value, from, to }
    })
    if (res.data.success) {
      kpis.value    = res.data.kpis    || {}
      recent.value  = res.data.recent  || []
      monthly.value = res.data.monthlyData || []
    }

    // 2. Bank accounts
    const bankRes = await api.get('/api/bank-accounts', {
      params: { entityId: entityId.value }
    })
    if (bankRes.data.success) {
      banks.value    = bankRes.data.accounts || []
      bankTotal.value = bankRes.data.summary?.total_bank || 0
    }

    // 3. Obligations — φιλτράρισμα από recent (urgent/unpaid)
    // Χτίζονται από KPIs που ήδη έχουμε — δεν χρειάζεται ξεχωριστό call
    obligations.value = recent.value.filter(t =>
      t.paymentStatus === 'urgent' || t.paymentStatus === 'unpaid'
    )

  } catch (e) {
    error.value = 'Σφάλμα φόρτωσης δεδομένων'
    console.error(e)
  } finally {
    loading.value = false
  }
}

function changeEntity(e) {
  localStorage.setItem('n2c_entity', e)
  selectedEntity.value = e
  loadDashboard()
}
function applyFilters() { loadDashboard() }
function setPeriod(p) { periodMode.value = p; loadDashboard() }

// ── Formatters ────────────────────────────────────────────────────────
function fmt(v) {
  if (v === null || v === undefined) return '0,00 €'
  return new Intl.NumberFormat('el-GR', { style: 'currency', currency: 'EUR' }).format(v)
}
function fmtDate(d) {
  if (!d) return '—'
  return new Date(d).toLocaleDateString('el-GR', { day: '2-digit', month: 'short', year: '2-digit' })
}
function payStatusClass(s) {
  return { paid:'status-paid', received:'status-paid', unpaid:'status-unpaid', urgent:'status-urgent', partial:'status-partial' }[s] || ''
}
function payStatusLabel(s) {
  return { paid:'Πληρωμένη', received:'Εισπράχθηκε', unpaid:'Απλήρωτη', urgent:'Εκκρεμής', partial:'Μερική' }[s] || s
}

// ── Bank icon ─────────────────────────────────────────────────────────
function bankIcon(type) {
  return { checking:'fa-building-columns', savings:'fa-piggy-bank', revolut:'fa-mobile-screen', cash:'fa-money-bill-wave' }[type] || 'fa-wallet'
}

// ── Computed: cash available = banks - urgent ──────────────────────────
const cashAvailable = computed(() => bankTotal.value - (kpis.value.urgentTotal || 0))
const totalUnpaid   = computed(() => kpis.value.unpaidTotal || 0)

// ── Chart data ────────────────────────────────────────────────────────
const barChartData = computed(() => {
  const inc = Array(12).fill(0)
  const exp = Array(12).fill(0)
  monthly.value.forEach(([month, category, total]) => {
    const m = month - 1
    const isIncome = category && (category.toUpperCase().includes('ΕΣΟΔΑ') || category.toUpperCase().includes('ΕΙΣΠΡ'))
    if (isIncome) inc[m] += total
    else exp[m] += total
  })
  return {
    labels: MONTH_SHORT,
    datasets: [
      { label: 'Εισπράξεις', data: inc, backgroundColor: 'rgba(16,185,129,0.75)', borderRadius: 4 },
      { label: 'Πληρωμές',   data: exp, backgroundColor: 'rgba(239,68,68,0.75)',  borderRadius: 4 },
    ]
  }
})

const pieChartData = computed(() => {
  const totals = {}
  monthly.value.forEach(([, category, total]) => {
    if (!totals[category]) totals[category] = 0
    totals[category] += total
  })
  const COLORS = ['#2E75B6','#10b981','#f59e0b','#ef4444','#a855f7','#06b6d4']
  const entries = Object.entries(totals).sort((a,b) => b[1]-a[1]).slice(0,6)
  return {
    labels: entries.map(e => e[0]),
    datasets: [{ data: entries.map(e => e[1]), backgroundColor: COLORS, borderWidth: 0 }]
  }
})

const chartOptions = {
  responsive: true, maintainAspectRatio: false,
  plugins: {
    legend: { labels: { color: '#8da2b8', font: { family: 'Montserrat', size: 11 }, boxWidth: 12 } },
    datalabels: { display: false }
  },
  scales: {
    x: { grid: { color: 'rgba(42,69,99,0.3)' }, ticks: { color: '#5f7d9a', font: { size: 10 } } },
    y: { grid: { color: 'rgba(42,69,99,0.3)' }, ticks: { color: '#5f7d9a', callback: v => v >= 1000 ? (v/1000).toFixed(0)+'K' : v } }
  }
}
const pieOptions = {
  responsive: true, maintainAspectRatio: false,
  plugins: {
    legend: { position: 'right', labels: { color: '#8da2b8', font: { size: 11 }, padding: 12, boxWidth: 12 } },
    datalabels: { display: false }
  }
}

const periodLabel = computed(() => {
  const map = {
    month: `${selectedMonth.value} ${selectedYear.value}`,
    quarter: `Τρίμηνο ${selectedYear.value}`,
    half: `6μηνο ${selectedYear.value}`,
    year: `Έτος ${selectedYear.value}`,
    all: 'Όλες οι περίοδοι',
    custom: 'Προσαρμοσμένο'
  }
  return map[periodMode.value] || selectedYear.value
})

onMounted(loadDashboard)
</script>

<template>
  <div class="dashboard">

    <!-- Period Bar -->
    <div class="period-bar">
      <div class="quick-btns">
        <button class="qbtn" :class="{ active: periodMode==='month' }"   @click="setPeriod('month')">Μήνας</button>
        <button class="qbtn" :class="{ active: periodMode==='quarter' }" @click="setPeriod('quarter')">Τρίμηνο</button>
        <button class="qbtn" :class="{ active: periodMode==='half' }"    @click="setPeriod('half')">6μηνο</button>
        <button class="qbtn" :class="{ active: periodMode==='year' }"    @click="setPeriod('year')">Έτος</button>
        <button class="qbtn" :class="{ active: periodMode==='all' }"     @click="setPeriod('all')">Όλα</button>
      </div>
      <div class="sep"></div>
      <span class="period-label-sm">Μήνας:</span>
      <select v-model="selectedMonth" class="period-select" v-if="periodMode==='month'">
        <option v-for="m in MONTHS" :key="m">{{ m }}</option>
      </select>
      <select v-model="selectedYear" class="period-select">
        <option v-for="y in years" :key="y">{{ y }}</option>
      </select>
      <div class="sep"></div>
      <span class="period-label-sm">Από:</span>
      <input type="date" v-model="dateFrom" class="period-input" />
      <span class="period-label-sm">Έως:</span>
      <input type="date" v-model="dateTo" class="period-input" />
      <button class="apply-btn" @click="applyFilters" :disabled="loading">
        <i class="fas fa-filter"></i> Εφαρμογή
      </button>
      <span class="period-current">{{ periodLabel }}</span>
    </div>

    <!-- Error -->
    <div v-if="error" class="error-bar">
      <i class="fas fa-exclamation-triangle"></i> {{ error }}
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-wrap">
      <span class="spinner"></span> Φόρτωση...
    </div>

    <template v-if="!loading">

      <!-- ═══ 4 PANELS ═══════════════════════════════════════════════ -->
      <div class="dash-4col">

        <!-- Panel 1: Υποχρεώσεις -->
        <div class="panel-card">
          <div class="panel-header">
            <span class="panel-title" style="color:var(--warning)">
              <i class="fas fa-calendar-check"></i> Υποχρεώσεις
            </span>
            <span class="panel-badge warn">{{ kpis.urgentTotal > 0 ? Math.round(kpis.urgentTotal / 1000 * 10) / 10 + 'K εκκρεμείς' : '✓' }}</span>
          </div>
          <div class="oblig-summary">
            <div class="oblig-summary-item">
              <div class="os-label">⚡ Εκκρεμείς</div>
              <div class="os-value" style="color:#ff6400">{{ fmt(kpis.urgentTotal) }}</div>
            </div>
            <div class="oblig-summary-item">
              <div class="os-label">Σύνολο Απλήρωτων</div>
              <div class="os-value" style="color:var(--danger)">{{ fmt(kpis.unpaidTotal) }}</div>
            </div>
          </div>
          <ul class="oblig-list">
            <li v-if="obligations.length === 0" class="oblig-empty">
              <i class="fas fa-check-circle" style="color:var(--success)"></i> Καμία εκκρεμότητα
            </li>
            <li v-for="o in obligations.slice(0,8)" :key="o.id" class="oblig-item"
                :class="o.paymentStatus === 'urgent' ? 'overdue' : ''">
              <div class="oblig-date-badge">
                <div class="ob-day">{{ new Date(o.docDate || o.date).getDate() }}</div>
                <div class="ob-month">{{ MONTH_SHORT[new Date(o.docDate || o.date).getMonth()] }}</div>
              </div>
              <div class="oblig-info">
                <div class="ob-title">{{ (o.description || '—').substring(0,32) }}</div>
                <div class="ob-meta">{{ o.category }}</div>
              </div>
              <div class="oblig-amount" style="color:var(--danger)">{{ fmt(o.amount) }}</div>
            </li>
          </ul>
        </div>

        <!-- Panel 2: Τράπεζες -->
        <div class="panel-card">
          <div class="panel-header">
            <span class="panel-title" style="color:var(--success)">
              <i class="fas fa-university"></i> Τράπεζες
            </span>
            <span class="panel-badge success">{{ banks.length }} λογ/μοί</span>
          </div>
          <div class="bank-total">
            <span class="bt-label">Σύνολο Τραπεζών</span>
            <span class="bt-amount">{{ fmt(bankTotal) }}</span>
          </div>
          <ul class="bank-list">
            <li v-if="banks.length === 0" class="oblig-empty">Κανένας λογαριασμός</li>
            <li v-for="b in banks" :key="b.id" class="bank-item">
              <div class="bank-icon">
                <i class="fas" :class="bankIcon(b.accountType)"></i>
              </div>
              <div class="bank-info">
                <div class="bk-name">{{ b.accountLabel || b.bankName }}</div>
                <div class="bk-type">{{ b.bankName }} · {{ b.currency }}</div>
              </div>
              <div class="bank-balance">
                <div class="bk-amount" :style="{ color: b.currentBalance >= 0 ? 'var(--success)' : 'var(--danger)' }">
                  {{ fmt(b.currentBalance) }}
                </div>
                <div class="bk-date">{{ fmtDate(b.balanceDate) }}</div>
              </div>
            </li>
          </ul>
        </div>

        <!-- Panel 3: Ισοσκελισμός -->
        <div class="panel-card">
          <div class="panel-header">
            <span class="panel-title" style="color:var(--accent)">
              <i class="fas fa-balance-scale"></i> Ισοσκελισμός
            </span>
            <span class="panel-badge accent">{{ fmt(bankTotal) }}</span>
          </div>
          <div class="recon-grid">
            <div class="recon-row">
              <span class="rc-label"><i class="fas fa-university"></i> Σύνολο Τραπεζών</span>
              <span class="rc-value" style="font-weight:700">{{ fmt(bankTotal) }}</span>
            </div>
            <div class="recon-divider"></div>
            <div class="recon-row">
              <span class="rc-label" style="font-size:.82rem;color:var(--text-secondary)">
                <i class="fas fa-arrow-down"></i> Εισπράξεις περιόδου
              </span>
              <span class="rc-value" style="color:var(--success);font-size:.85rem">{{ fmt(kpis.totalIncome) }}</span>
            </div>
            <div class="recon-row">
              <span class="rc-label" style="font-size:.82rem;color:var(--text-secondary)">
                <i class="fas fa-arrow-up"></i> Πληρωμές περιόδου
              </span>
              <span class="rc-value" style="color:var(--danger);font-size:.85rem">{{ fmt(kpis.totalExpense) }}</span>
            </div>
            <div class="recon-divider"></div>
            <div class="recon-row">
              <span class="rc-label"><i class="fas fa-balance-scale"></i> Καθαρό περιόδου</span>
              <span class="rc-value" :style="{ color: kpis.netBalance >= 0 ? 'var(--success)' : 'var(--danger)' }">
                {{ fmt(kpis.netBalance) }}
              </span>
            </div>
          </div>
        </div>

        <!-- Panel 4: Ταμειακά Διαθέσιμα -->
        <div class="panel-card">
          <div class="panel-header">
            <span class="panel-title" style="color:var(--success)">
              <i class="fas fa-coins"></i> Ταμειακά Διαθέσιμα
            </span>
          </div>
          <div class="cash-hero">
            <div class="cash-total" :style="{ color: cashAvailable >= 0 ? 'var(--success)' : '#ff6400' }">
              {{ fmt(cashAvailable) }}
            </div>
            <div class="cash-label">Τράπεζες μείον Εκκρεμείς</div>
          </div>
          <div class="cash-breakdown">
            <div class="cash-row">
              <div class="cr-icon" style="background:var(--accent-glow);color:var(--accent)"><i class="fas fa-university"></i></div>
              <span class="cr-label">Τράπεζες</span>
              <span class="cr-value">{{ fmt(bankTotal) }}</span>
            </div>
            <div class="cash-row" v-if="kpis.urgentTotal > 0" style="background:rgba(255,100,0,0.08);border-radius:8px">
              <div class="cr-icon" style="background:rgba(255,100,0,0.15);color:#ff6400"><i class="fas fa-bolt"></i></div>
              <span class="cr-label" style="color:#ff6400;font-weight:700">⚡ Εκκρεμείς</span>
              <span class="cr-value" style="color:#ff6400;font-weight:800">-{{ fmt(kpis.urgentTotal) }}</span>
            </div>
            <div class="cash-row">
              <div class="cr-icon" style="background:var(--warning-bg);color:var(--warning)"><i class="fas fa-clock"></i></div>
              <span class="cr-label">Σύνολο Υποχρεώσεων</span>
              <span class="cr-value" style="color:var(--danger)">-{{ fmt(totalUnpaid) }}</span>
            </div>
            <div style="height:1px;background:var(--border);margin:4px 0"></div>
            <div class="cash-row" :style="{ background: cashAvailable >= 0 ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)', borderRadius: '8px' }">
              <div class="cr-icon" :style="{ background: cashAvailable >= 0 ? 'var(--success-bg)' : 'var(--danger-bg)', color: cashAvailable >= 0 ? 'var(--success)' : 'var(--danger)' }">
                <i class="fas fa-coins"></i>
              </div>
              <span class="cr-label" style="font-weight:700">Καθαρά Διαθέσιμα</span>
              <span class="cr-value" :style="{ color: cashAvailable >= 0 ? 'var(--success)' : 'var(--danger)', fontWeight: '800', fontSize: '1.1rem' }">
                {{ fmt(cashAvailable) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- ═══ KPI CARDS ══════════════════════════════════════════════ -->
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-header">
            <div class="kpi-icon" style="background:var(--accent-glow);color:var(--accent)"><i class="fas fa-wallet"></i></div>
            <span class="kpi-change down" v-if="kpis.netBalance < 0">Αρνητικό</span>
          </div>
          <div class="kpi-label">Τρέχον Υπόλοιπο</div>
          <div class="kpi-value" :style="{ color: kpis.netBalance >= 0 ? 'var(--text-primary)' : 'var(--danger)' }">{{ fmt(kpis.netBalance) }}</div>
          <div class="kpi-sub">Εισπράξεις − Πληρωμές</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-header">
            <div class="kpi-icon" style="background:var(--success-bg);color:var(--success)"><i class="fas fa-arrow-down"></i></div>
          </div>
          <div class="kpi-label">Εισπράξεις</div>
          <div class="kpi-value" style="color:var(--success)">{{ fmt(kpis.totalIncome) }}</div>
          <div class="kpi-sub">περίοδος</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-header">
            <div class="kpi-icon" style="background:var(--danger-bg);color:var(--danger)"><i class="fas fa-arrow-up"></i></div>
          </div>
          <div class="kpi-label">Πληρωμές</div>
          <div class="kpi-value" style="color:var(--danger)">{{ fmt(kpis.totalExpense) }}</div>
          <div class="kpi-sub">περίοδος</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-header">
            <div class="kpi-icon" :style="{ background: kpis.netBalance >= 0 ? 'var(--success-bg)' : 'var(--danger-bg)', color: kpis.netBalance >= 0 ? 'var(--success)' : 'var(--danger)' }">
              <i class="fas fa-balance-scale"></i>
            </div>
          </div>
          <div class="kpi-label">Καθαρό</div>
          <div class="kpi-value" :style="{ color: kpis.netBalance >= 0 ? 'var(--success)' : 'var(--danger)' }">{{ fmt(kpis.netBalance) }}</div>
          <div class="kpi-sub">Εισπράξεις − Πληρωμές</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-header">
            <div class="kpi-icon" style="background:var(--info-bg);color:var(--info)"><i class="fas fa-hashtag"></i></div>
          </div>
          <div class="kpi-label">⚡ Εκκρεμείς</div>
          <div class="kpi-value" style="color:#ff6400">{{ fmt(kpis.urgentTotal) }}</div>
          <div class="kpi-sub">απλήρωτες</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-header">
            <div class="kpi-icon" style="background:var(--warning-bg);color:var(--warning)"><i class="fas fa-clock"></i></div>
          </div>
          <div class="kpi-label">Σύνολο Υποχρεώσεων</div>
          <div class="kpi-value" style="color:var(--warning)">{{ fmt(kpis.unpaidTotal) }}</div>
          <div class="kpi-sub">unpaid + urgent</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-header">
            <div class="kpi-icon" style="background:var(--success-bg);color:var(--success)"><i class="fas fa-university"></i></div>
          </div>
          <div class="kpi-label">Τράπεζες</div>
          <div class="kpi-value" style="color:var(--success)">{{ fmt(bankTotal) }}</div>
          <div class="kpi-sub">{{ banks.length }} λογαριασμοί</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-header">
            <div class="kpi-icon" :style="{ background: cashAvailable >= 0 ? 'var(--success-bg)' : 'var(--danger-bg)', color: cashAvailable >= 0 ? 'var(--success)' : '#ff6400' }">
              <i class="fas fa-coins"></i>
            </div>
          </div>
          <div class="kpi-label">Ταμειακά Διαθέσιμα</div>
          <div class="kpi-value" :style="{ color: cashAvailable >= 0 ? 'var(--success)' : '#ff6400' }">{{ fmt(cashAvailable) }}</div>
          <div class="kpi-sub">Τράπεζες − Εκκρεμείς</div>
        </div>
      </div>

      <!-- ═══ CHARTS ═════════════════════════════════════════════════ -->
      <div class="dash-2col" v-if="monthly.length">
        <div class="chart-card">
          <div class="card-header">
            <div class="card-title"><i class="fas fa-chart-bar"></i> Μηνιαία Σύνοψη</div>
          </div>
          <div class="chart-wrapper" style="height:260px">
            <Bar :data="barChartData" :options="chartOptions" />
          </div>
        </div>
        <div class="chart-card">
          <div class="card-header">
            <div class="card-title"><i class="fas fa-chart-pie"></i> Κατανομή Εξόδων</div>
          </div>
          <div class="chart-wrapper" style="height:260px">
            <Doughnut :data="pieChartData" :options="pieOptions" />
          </div>
        </div>
      </div>

      <!-- ═══ RECENT TRANSACTIONS ════════════════════════════════════ -->
      <div class="dash-2col" v-if="recent.length">
        <div class="chart-card">
          <div class="card-header">
            <div class="card-title"><i class="fas fa-list"></i> Πρόσφατες Κινήσεις</div>
            <router-link to="/transactions" style="font-size:.78rem;color:var(--accent);text-decoration:none">
              Όλες <i class="fas fa-arrow-right"></i>
            </router-link>
          </div>
          <ul class="recent-list">
            <li v-for="t in recent.slice(0,10)" :key="t.id" class="recent-item">
              <div class="ri-left">
                <span class="ri-desc">{{ (t.description || t.category || '—').substring(0,35) }}</span>
                <span class="ri-meta">{{ fmtDate(t.docDate) }} · {{ t.category }}</span>
              </div>
              <span class="ri-amount" :class="t.type === 'income' ? 'inflow' : 'outflow'">
                {{ t.type === 'income' ? '+' : '-' }}{{ fmt(t.amount) }}
              </span>
            </li>
          </ul>
        </div>
        <div class="chart-card">
          <div class="card-header">
            <div class="card-title"><i class="fas fa-trophy"></i> Top Πληρωμές</div>
          </div>
          <ul class="recent-list">
            <li v-for="t in [...recent].filter(t => t.type === 'expense').sort((a,b) => b.amount - a.amount).slice(0,10)" :key="'top-'+t.id" class="recent-item">
              <div class="ri-left">
                <span class="ri-desc">{{ (t.description || t.category || '—').substring(0,35) }}</span>
                <span class="ri-meta">{{ fmtDate(t.docDate) }} · {{ t.category }}</span>
              </div>
              <span class="ri-amount outflow">{{ fmt(t.amount) }}</span>
            </li>
          </ul>
        </div>
      </div>

    </template>
  </div>
</template>

<style scoped>
.dashboard { padding: 0; }

/* Period Bar */
.period-bar { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:14px 18px; margin-bottom:20px; display:flex; align-items:center; flex-wrap:wrap; gap:10px; }
.quick-btns { display:flex; gap:4px; }
.qbtn { padding:5px 13px; border-radius:20px; border:1px solid var(--border); background:transparent; color:var(--text-secondary); font-family:var(--font); font-size:.75rem; font-weight:500; cursor:pointer; transition:all .2s; }
.qbtn:hover { border-color:var(--accent); color:var(--accent); }
.qbtn.active { background:var(--accent); color:#fff; border-color:var(--accent); }
.sep { width:1px; height:26px; background:var(--border); }
.period-label-sm { font-size:.75rem; color:var(--text-muted); }
.period-select { background:var(--bg-input); border:1px solid var(--border); border-radius:var(--radius-sm); color:var(--text-primary); padding:6px 10px; font-size:.82rem; }
.period-input { background:var(--bg-input); border:1px solid var(--border); border-radius:var(--radius-sm); color:var(--text-primary); padding:6px 10px; font-size:.82rem; width:140px; }
.apply-btn { padding:6px 14px; border-radius:var(--radius-sm); border:none; background:var(--accent); color:#fff; font-family:var(--font); font-size:.8rem; font-weight:500; cursor:pointer; }
.apply-btn:hover { background:var(--accent-hover); }
.apply-btn:disabled { opacity:.5; cursor:not-allowed; }
.period-current { font-size:.8rem; color:var(--text-muted); margin-left:auto; }

/* Error / Loading */
.error-bar { background:var(--danger-bg); border:1px solid rgba(239,68,68,.2); color:var(--danger); padding:10px 14px; border-radius:var(--radius-md); margin-bottom:12px; }
.loading-wrap { text-align:center; padding:60px; color:var(--text-muted); }
.spinner { display:inline-block; width:18px; height:18px; border:2px solid var(--border); border-top-color:var(--accent); border-radius:50%; animation:spin .7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* 4-col panels */
.dash-4col { display:grid; grid-template-columns:1fr 1fr 1fr 1fr; gap:18px; margin-bottom:18px; }
@media(max-width:1200px) { .dash-4col { grid-template-columns:1fr 1fr; } }
@media(max-width:768px)  { .dash-4col { grid-template-columns:1fr; } }

/* 2-col */
.dash-2col { display:grid; grid-template-columns:3fr 2fr; gap:18px; margin-bottom:18px; }
@media(max-width:900px) { .dash-2col { grid-template-columns:1fr; } }

/* Panel cards */
.panel-card { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:18px; position:relative; overflow:hidden; }
.panel-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.panel-title { font-size:.95rem; font-weight:600; display:flex; align-items:center; gap:8px; }
.panel-badge { font-size:.68rem; font-weight:600; padding:3px 10px; border-radius:12px; }
.panel-badge.warn    { background:rgba(245,158,11,.15); color:var(--warning); }
.panel-badge.success { background:var(--success-bg); color:var(--success); }
.panel-badge.accent  { background:var(--accent-glow); color:var(--accent); }

/* Obligations */
.oblig-summary { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin-bottom:12px; }
.oblig-summary-item { background:var(--bg-input); border-radius:var(--radius-sm); padding:10px 12px; border:1px solid var(--border); }
.os-label { font-size:.68rem; color:var(--text-muted); font-weight:500; text-transform:uppercase; letter-spacing:.3px; }
.os-value { font-size:1rem; font-weight:700; font-family:var(--font-mono); margin-top:2px; }
.oblig-list { list-style:none; max-height:320px; overflow-y:auto; scrollbar-width:thin; padding:0; margin:0; }
.oblig-empty { padding:20px; text-align:center; color:var(--text-muted); font-size:.85rem; }
.oblig-item { display:flex; align-items:flex-start; gap:10px; padding:8px 0; border-bottom:1px solid rgba(42,69,99,.35); }
.oblig-item:last-child { border-bottom:none; }
.oblig-item.overdue .oblig-date-badge { border-color:var(--danger); background:var(--danger-bg); }
.oblig-item.overdue .ob-day { color:var(--danger); }
.oblig-date-badge { min-width:40px; text-align:center; background:var(--bg-input); border-radius:var(--radius-sm); padding:5px 4px; border:1px solid var(--border); }
.ob-day { font-size:1rem; font-weight:700; line-height:1; font-family:var(--font-mono); }
.ob-month { font-size:.58rem; color:var(--text-muted); text-transform:uppercase; }
.oblig-info { flex:1; min-width:0; }
.ob-title { font-size:.88rem; font-weight:500; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.ob-meta { font-size:.75rem; color:var(--text-muted); margin-top:2px; }
.oblig-amount { font-family:var(--font-mono); font-size:.9rem; font-weight:600; white-space:nowrap; }

/* Banks */
.bank-total { display:flex; align-items:center; justify-content:space-between; padding:10px 12px; background:var(--accent-glow); border:1px solid rgba(46,117,182,.2); border-radius:var(--radius-md); margin-bottom:12px; }
.bt-label { font-size:.78rem; font-weight:500; color:var(--accent); }
.bt-amount { font-family:var(--font-mono); font-size:1.1rem; font-weight:700; color:var(--accent); }
.bank-list { list-style:none; padding:0; margin:0; }
.bank-item { display:flex; align-items:center; gap:10px; padding:9px 0; border-bottom:1px solid rgba(42,69,99,.35); }
.bank-item:last-child { border-bottom:none; }
.bank-icon { width:34px; height:34px; border-radius:var(--radius-sm); display:flex; align-items:center; justify-content:center; font-size:.88rem; background:var(--bg-input); border:1px solid var(--border); color:var(--text-secondary); }
.bank-info { flex:1; min-width:0; }
.bk-name { font-size:.88rem; font-weight:500; }
.bk-type { font-size:.75rem; color:var(--text-muted); }
.bank-balance { text-align:right; }
.bk-amount { font-family:var(--font-mono); font-size:.95rem; font-weight:600; }
.bk-date { font-size:.7rem; color:var(--text-muted); }

/* Reconciliation */
.recon-grid { display:flex; flex-direction:column; gap:6px; }
.recon-row { display:flex; align-items:center; justify-content:space-between; padding:6px 2px; }
.rc-label { font-size:.85rem; color:var(--text-secondary); display:flex; align-items:center; gap:6px; }
.rc-value { font-family:var(--font-mono); font-size:.9rem; font-weight:600; }
.recon-divider { height:1px; background:var(--border); margin:4px 0; }

/* Cash panel */
.cash-hero { text-align:center; padding:16px 0 12px; }
.cash-total { font-family:var(--font-mono); font-size:1.8rem; font-weight:700; }
.cash-label { font-size:.75rem; color:var(--text-secondary); margin-top:2px; }
.cash-breakdown { display:flex; flex-direction:column; gap:6px; }
.cash-row { display:flex; align-items:center; gap:8px; padding:7px 10px; border-radius:var(--radius-sm); background:var(--bg-input); border:1px solid var(--border); }
.cr-icon { width:26px; height:26px; border-radius:6px; display:flex; align-items:center; justify-content:center; font-size:.72rem; }
.cr-label { flex:1; font-size:.82rem; font-weight:500; }
.cr-value { font-family:var(--font-mono); font-size:.85rem; font-weight:600; }

/* KPI Grid */
.kpi-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:14px; margin-bottom:18px; }
@media(max-width:1200px) { .kpi-grid { grid-template-columns:repeat(2,1fr); } }
@media(max-width:600px)  { .kpi-grid { grid-template-columns:1fr 1fr; } }
.kpi-card { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:16px 18px; transition:transform .2s,box-shadow .2s; }
.kpi-card:hover { transform:translateY(-2px); box-shadow:var(--shadow); }
.kpi-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:8px; }
.kpi-icon { width:36px; height:36px; border-radius:var(--radius-md); display:flex; align-items:center; justify-content:center; font-size:.95rem; }
.kpi-change { font-size:.7rem; font-weight:600; padding:2px 8px; border-radius:10px; }
.kpi-change.down { background:var(--danger-bg); color:var(--danger); }
.kpi-label { font-size:.82rem; color:var(--text-secondary); margin-bottom:4px; font-weight:500; }
.kpi-value { font-size:1.4rem; font-weight:700; font-family:var(--font-mono); }
.kpi-sub { font-size:.75rem; color:var(--text-muted); margin-top:4px; }

/* Charts */
.chart-card { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:18px; }
.card-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }
.card-title { font-size:.95rem; font-weight:600; display:flex; align-items:center; gap:8px; }
.card-title i { color:var(--accent); }
.chart-wrapper { position:relative; }

/* Recent list */
.recent-list { list-style:none; padding:0; margin:0; }
.recent-item { display:flex; align-items:center; justify-content:space-between; padding:9px 2px; border-bottom:1px solid rgba(42,69,99,.3); }
.recent-item:last-child { border-bottom:none; }
.ri-left { flex:1; min-width:0; margin-right:12px; }
.ri-desc { display:block; font-size:.88rem; font-weight:500; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.ri-meta { display:block; font-size:.75rem; color:var(--text-muted); margin-top:2px; }
.ri-amount { font-family:var(--font-mono); font-size:.9rem; font-weight:600; white-space:nowrap; }
.ri-amount.inflow  { color:var(--success); }
.ri-amount.outflow { color:var(--danger); }
</style>