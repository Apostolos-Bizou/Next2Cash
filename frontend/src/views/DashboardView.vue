<script setup>
import { ref, computed, onMounted } from 'vue'
import { Bar, Doughnut, Line } from 'vue-chartjs'
import {
  Chart as ChartJS, Title, Tooltip, Legend,
  BarElement, CategoryScale, LinearScale, ArcElement,
  LineElement, PointElement, Filler
} from 'chart.js'
import api from '@/api'

ChartJS.register(Title, Tooltip, Legend, BarElement, CategoryScale, LinearScale,
  ArcElement, LineElement, PointElement, Filler)

// ── Entities ────────────────────────────────────────────────────────
const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14',
}
const selectedEntity = ref(localStorage.getItem('n2c_entity') || 'next2me')
const entityId = computed(() => ENTITIES[selectedEntity.value])

// ── Period ───────────────────────────────────────────────────────────
const periodMode   = ref('year')
const selectedYear = ref(String(new Date().getFullYear()))
const selectedMonth = ref('')
const dateFrom = ref('')
const dateTo   = ref('')
const MONTHS = ['Ιανουάριος','Φεβρουάριος','Μάρτιος','Απρίλιος','Μάιος','Ιούνιος','Ιούλιος','Αύγουστος','Σεπτέμβριος','Οκτώβριος','Νοέμβριος','Δεκέμβριος']
const MONTH_SHORT = ['Ιαν','Φεβ','Μαρ','Απρ','Μαι','Ιουν','Ιουλ','Αυγ','Σεπ','Οκτ','Νοε','Δεκ']
const years = ['2017','2018','2019','2020','2021','2022','2023','2024','2025','2026']

// ── State ────────────────────────────────────────────────────────────
const loading  = ref(false)
const error    = ref(null)
const kpis     = ref({})
const recent   = ref([])
const monthly  = ref([])
const banks    = ref([])
const bankTotal = ref(0)
const obligations = ref([])
const balanceTrend = ref([])
const yearlyData   = ref([])
const catBreakdown = ref([])

// ── Date range ───────────────────────────────────────────────────────
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

// ── Load ─────────────────────────────────────────────────────────────
async function loadDashboard() {
  loading.value = true
  error.value   = null
  try {
    const { from, to } = getDateRange()
    const eid = entityId.value

    const [dashRes, bankRes, trendRes, yearlyRes, catRes] = await Promise.all([
      api.get('/api/dashboard', { params: { entityId: eid, from, to } }),
      api.get('/api/bank-accounts', { params: { entityId: eid } }),
      api.get('/api/dashboard/balance-trend', { params: { entityId: eid, from: '2017-01-01', to } }),
      api.get('/api/dashboard/yearly', { params: { entityId: eid } }),
      api.get('/api/dashboard/category-breakdown', { params: { entityId: eid, from, to } }),
    ])

    if (dashRes.data.success) {
      kpis.value    = dashRes.data.kpis    || {}
      recent.value  = dashRes.data.recent  || []
      monthly.value = dashRes.data.monthlyData || []
    }
    if (bankRes.data.success) {
      banks.value     = bankRes.data.accounts || []
      bankTotal.value = bankRes.data.summary?.total_bank || 0
    }
    if (trendRes.data.success) {
      balanceTrend.value = trendRes.data.data || []
    }
    if (yearlyRes.data.success) {
      yearlyData.value = yearlyRes.data.data || []
    }
    if (catRes.data.success) {
      catBreakdown.value = catRes.data.data || []
    }

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

function setPeriod(p) { periodMode.value = p; loadDashboard() }
function applyFilters() { loadDashboard() }

// ── Formatters ────────────────────────────────────────────────────────
function fmt(v) {
  if (v === null || v === undefined) return '0,00 €'
  return new Intl.NumberFormat('el-GR', { style: 'currency', currency: 'EUR' }).format(Number(v))
}
function fmtDate(d) {
  if (!d) return '—'
  return new Date(d).toLocaleDateString('el-GR', { day: '2-digit', month: 'short', year: '2-digit' })
}
function fmtShortDate(d) {
  if (!d) return ''
  const dt = new Date(d)
  return `${String(dt.getDate()).padStart(2,'0')}/${String(dt.getMonth()+1).padStart(2,'0')}/${String(dt.getFullYear()).substring(2)}`
}
function payStatusClass(s) {
  return { paid:'status-paid', received:'status-paid', unpaid:'status-unpaid', urgent:'status-urgent', partial:'status-partial' }[s] || ''
}
function payStatusLabel(s) {
  return { paid:'Πληρωμένη', received:'Εισπράχθηκε', unpaid:'Απλήρωτη', urgent:'Εκκρεμής', partial:'Μερική' }[s] || s
}
function bankIcon(type) {
  return { checking:'fa-building-columns', savings:'fa-piggy-bank', revolut:'fa-mobile-screen', cash:'fa-money-bill-wave' }[type] || 'fa-wallet'
}

// ── Computed ──────────────────────────────────────────────────────────
const cashAvailable = computed(() => bankTotal.value - (Number(kpis.value.urgentTotal) || 0))
const totalUnpaid   = computed(() => Number(kpis.value.unpaidTotal) || 0)

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

// ── Category breakdown computed ───────────────────────────────────────
const catSummary = computed(() => {
  const map = {}
  catBreakdown.value.forEach(r => {
    const cat = r.category || 'Άλλο'
    if (!map[cat]) map[cat] = { income: 0, expense: 0 }
    map[cat].income  += Number(r.income  || 0)
    map[cat].expense += Number(r.expense || 0)
  })
  const total = Object.values(map).reduce((s,v) => s + v.expense, 0)
  const months = Math.max(1, Math.ceil((new Date(getDateRange().to) - new Date(getDateRange().from)) / (1000*60*60*24*30)))
  return Object.entries(map)
    .sort((a,b) => b[1].expense - a[1].expense)
    .map(([cat, v]) => ({
      cat, income: v.income, expense: v.expense,
      pct: total > 0 ? (v.expense / total * 100).toFixed(1) : '0.0',
      avgMonth: (v.expense / months).toFixed(2),
      barWidth: total > 0 ? (v.expense / total * 100) : 0
    }))
})

const subcatSummary = computed(() => {
  const map = {}
  catBreakdown.value.forEach(r => {
    const key = r.subcategory || '—'
    if (!map[key]) map[key] = { income: 0, expense: 0 }
    map[key].income  += Number(r.income  || 0)
    map[key].expense += Number(r.expense || 0)
  })
  return Object.entries(map)
    .sort((a,b) => b[1].expense - a[1].expense)
    .slice(0, 15)
    .map(([sub, v]) => ({ sub, income: v.income, expense: v.expense, net: v.income - v.expense }))
})

// ── Chart colors ──────────────────────────────────────────────────────
const CAT_COLORS = ['#2E75B6','#10b981','#f59e0b','#ef4444','#a855f7','#06b6d4','#f97316','#84cc16']

// ── Bar chart (monthly) ───────────────────────────────────────────────
const barChartData = computed(() => {
  const inc = Array(12).fill(0)
  const exp = Array(12).fill(0)
  monthly.value.forEach(([month, category, total]) => {
    const m = month - 1
    const isIncome = category && (category.toUpperCase().includes('ΕΣΟΔ') || category.toUpperCase().includes('ΕΙΣΠΡ'))
    if (isIncome) inc[m] += Number(total)
    else exp[m] += Number(total)
  })
  return {
    labels: MONTH_SHORT,
    datasets: [
      { label: 'Εισπράξεις', data: inc, backgroundColor: 'rgba(16,185,129,0.75)', borderRadius: 4 },
      { label: 'Πληρωμές',   data: exp, backgroundColor: 'rgba(239,68,68,0.75)',  borderRadius: 4 },
    ]
  }
})

// ── Pie chart (categories) ────────────────────────────────────────────
const pieChartData = computed(() => {
  const entries = catSummary.value.filter(r => r.expense > 0).slice(0,6)
  return {
    labels: entries.map(e => e.cat),
    datasets: [{ data: entries.map(e => e.expense), backgroundColor: CAT_COLORS, borderWidth: 0 }]
  }
})

// ── Net monthly chart ─────────────────────────────────────────────────
const netChartData = computed(() => {
  const mo = {}
  monthly.value.forEach(([month, category, total]) => {
    const k = String(month)
    if (!mo[k]) mo[k] = 0
    const isIncome = category && (category.toUpperCase().includes('ΕΣΟΔ') || category.toUpperCase().includes('ΕΙΣΠΡ'))
    mo[k] += isIncome ? Number(total) : -Number(total)
  })
  const keys = Object.keys(mo).sort((a,b) => Number(a)-Number(b))
  const data = keys.map(k => mo[k])
  return {
    labels: keys.map(k => MONTH_SHORT[Number(k)-1]),
    datasets: [{
      label: 'Net',
      data,
      backgroundColor: data.map(v => v >= 0 ? 'rgba(16,185,129,0.8)' : 'rgba(239,68,68,0.8)'),
      borderRadius: 4
    }]
  }
})

// ── Balance trend chart ───────────────────────────────────────────────
const trendChartData = computed(() => {
  const raw = balanceTrend.value
  // Sample max 120 points for performance
  const step = Math.max(1, Math.ceil(raw.length / 120))
  const sampled = raw.filter((_, i) => i % step === 0)
  return {
    labels: sampled.map(r => fmtShortDate(r.date)),
    datasets: [{
      label: 'Υπόλοιπο',
      data: sampled.map(r => Number(r.balance)),
      borderColor: '#2E75B6',
      backgroundColor: 'rgba(46,117,182,0.1)',
      fill: true,
      tension: 0.3,
      pointRadius: 0,
      borderWidth: 2
    }]
  }
})

// ── Yearly chart ──────────────────────────────────────────────────────
const yearlyChartData = computed(() => {
  const yrs = [...new Set(yearlyData.value.map(r => String(r.year)))].sort()
  const cats = [...new Set(yearlyData.value.map(r => r.category))]
    .map(cat => ({
      cat,
      total: yearlyData.value.filter(r => r.category === cat).reduce((s,r) => s + Number(r.total), 0)
    }))
    .sort((a,b) => b.total - a.total)
    .slice(0, 6)
    .map(c => c.cat)

  return {
    labels: yrs,
    datasets: cats.map((cat, i) => ({
      label: cat,
      data: yrs.map(y => {
        const row = yearlyData.value.find(r => String(r.year) === y && r.category === cat)
        return row ? Number(row.total) : 0
      }),
      backgroundColor: CAT_COLORS[i] + 'CC',
      borderRadius: 4
    }))
  }
})

// ── Chart options ─────────────────────────────────────────────────────
const cOpts = {
  responsive: true, maintainAspectRatio: false,
  plugins: {
    legend: { labels: { color: '#8da2b8', font: { family: 'Montserrat', size: 11 }, boxWidth: 12 } },
    datalabels: { display: false }
  },
  scales: {
    x: { grid: { color: 'rgba(42,69,99,0.3)' }, ticks: { color: '#5f7d9a', font: { size: 10 }, maxRotation: 45 } },
    y: { grid: { color: 'rgba(42,69,99,0.3)' }, ticks: { color: '#5f7d9a', callback: v => v >= 1000 ? (v/1000).toFixed(0)+'K' : v } }
  }
}
const pieOpts = {
  responsive: true, maintainAspectRatio: false,
  plugins: {
    legend: { position: 'right', labels: { color: '#8da2b8', font: { size: 11 }, padding: 12, boxWidth: 12 } },
    datalabels: { display: false }
  }
}
const trendOpts = {
  responsive: true, maintainAspectRatio: false,
  plugins: {
    legend: { labels: { color: '#8da2b8', font: { size: 11 }, boxWidth: 12 } },
    datalabels: { display: false }
  },
  scales: {
    x: { grid: { color: 'rgba(42,69,99,0.3)' }, ticks: { color: '#5f7d9a', font: { size: 9 }, maxTicksLimit: 12, maxRotation: 0 } },
    y: { grid: { color: 'rgba(42,69,99,0.3)' }, ticks: { color: '#5f7d9a', callback: v => v >= 1000 ? (v/1000).toFixed(0)+'K' : v >= -1000 ? v : (v/1000).toFixed(0)+'K' } }
  }
}

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
      <span class="plbl">Μήνας:</span>
      <select v-if="periodMode==='month'" v-model="selectedMonth" class="psel">
        <option v-for="m in MONTHS" :key="m">{{ m }}</option>
      </select>
      <select v-model="selectedYear" class="psel">
        <option v-for="y in years" :key="y">{{ y }}</option>
      </select>
      <div class="sep"></div>
      <span class="plbl">Από:</span>
      <input type="date" v-model="dateFrom" class="pinp" />
      <span class="plbl">Έως:</span>
      <input type="date" v-model="dateTo" class="pinp" />
      <button class="apply-btn" @click="applyFilters" :disabled="loading">
        <i class="fas fa-filter"></i> Εφαρμογή
      </button>
      <span class="period-cur">{{ periodLabel }}</span>
    </div>

    <div v-if="error" class="err-bar"><i class="fas fa-exclamation-triangle"></i> {{ error }}</div>
    <div v-if="loading" class="load-wrap"><span class="spinner"></span> Φόρτωση...</div>

    <template v-if="!loading">

      <!-- ═══ 4 PANELS ══════════════════════════════════════════════ -->
      <div class="dash-4col">

        <!-- Υποχρεώσεις -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle warn"><i class="fas fa-calendar-check"></i> Υποχρεώσεις</span>
            <span class="pbadge warn">{{ obligations.length }} εκκρεμείς</span>
          </div>
          <div class="oblig-sum">
            <div class="osum-item">
              <div class="osum-lbl">⚡ Εκκρεμείς</div>
              <div class="osum-val" style="color:#ff6400">{{ fmt(kpis.urgentTotal) }}</div>
            </div>
            <div class="osum-item">
              <div class="osum-lbl">Σύνολο Απλήρωτων</div>
              <div class="osum-val" style="color:var(--danger)">{{ fmt(kpis.unpaidTotal) }}</div>
            </div>
          </div>
          <ul class="oblig-list">
            <li v-if="obligations.length===0" class="olist-empty">
              <i class="fas fa-check-circle" style="color:var(--success)"></i> Καμία εκκρεμότητα
            </li>
            <li v-for="o in obligations.slice(0,8)" :key="o.id" class="olist-item"
                :class="o.paymentStatus==='urgent' ? 'overdue' : ''">
              <div class="odate-badge">
                <div class="od-day">{{ new Date(o.docDate||o.date).getDate() }}</div>
                <div class="od-mon">{{ MONTH_SHORT[new Date(o.docDate||o.date).getMonth()] }}</div>
              </div>
              <div class="oinfo">
                <div class="oti">{{ (o.description||'—').substring(0,32) }}</div>
                <div class="ome">{{ o.category }}</div>
              </div>
              <div class="oamt" style="color:var(--danger)">{{ fmt(o.amount) }}</div>
            </li>
          </ul>
        </div>

        <!-- Τράπεζες -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle succ"><i class="fas fa-university"></i> Τράπεζες</span>
            <span class="pbadge succ">{{ banks.length }} λογ/μοί</span>
          </div>
          <div class="bank-tot">
            <span class="bt-lbl">Σύνολο Τραπεζών</span>
            <span class="bt-amt">{{ fmt(bankTotal) }}</span>
          </div>
          <ul class="bank-list">
            <li v-if="banks.length===0" class="olist-empty">Κανένας λογαριασμός</li>
            <li v-for="b in banks" :key="b.id" class="bank-item">
              <div class="bicon"><i class="fas" :class="bankIcon(b.accountType)"></i></div>
              <div class="binfo">
                <div class="bn">{{ b.accountLabel||b.bankName }}</div>
                <div class="bt">{{ b.bankName }} · {{ b.currency }}</div>
              </div>
              <div class="bbal">
                <div class="bamt" :style="{color: b.currentBalance>=0?'var(--success)':'var(--danger)'}">{{ fmt(b.currentBalance) }}</div>
                <div class="bdt">{{ fmtDate(b.balanceDate) }}</div>
              </div>
            </li>
          </ul>
        </div>

        <!-- Ισοσκελισμός -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle acc"><i class="fas fa-balance-scale"></i> Ισοσκελισμός</span>
            <span class="pbadge acc">{{ fmt(bankTotal) }}</span>
          </div>
          <div class="recon-grid">
            <div class="recon-row">
              <span class="rc-lbl"><i class="fas fa-university"></i> Σύνολο Τραπεζών</span>
              <span class="rc-val" style="font-weight:700">{{ fmt(bankTotal) }}</span>
            </div>
            <div class="recon-div"></div>
            <div class="recon-row">
              <span class="rc-lbl sm"><i class="fas fa-arrow-down"></i> Εισπράξεις περιόδου</span>
              <span class="rc-val" style="color:var(--success);font-size:.85rem">{{ fmt(kpis.totalIncome) }}</span>
            </div>
            <div class="recon-row">
              <span class="rc-lbl sm"><i class="fas fa-arrow-up"></i> Πληρωμές περιόδου</span>
              <span class="rc-val" style="color:var(--danger);font-size:.85rem">{{ fmt(kpis.totalExpense) }}</span>
            </div>
            <div class="recon-div"></div>
            <div class="recon-row">
              <span class="rc-lbl"><i class="fas fa-balance-scale"></i> Καθαρό περιόδου</span>
              <span class="rc-val" :style="{color: Number(kpis.netBalance)>=0?'var(--success)':'var(--danger)'}">{{ fmt(kpis.netBalance) }}</span>
            </div>
          </div>
        </div>

        <!-- Ταμειακά Διαθέσιμα -->
        <div class="panel-card">
          <div class="panel-hdr">
            <span class="ptitle succ"><i class="fas fa-coins"></i> Ταμειακά Διαθέσιμα</span>
          </div>
          <div class="cash-hero">
            <div class="cash-tot" :style="{color: cashAvailable>=0?'var(--success)':'#ff6400'}">{{ fmt(cashAvailable) }}</div>
            <div class="cash-lbl">Τράπεζες μείον Εκκρεμείς</div>
          </div>
          <div class="cash-bk">
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--accent-glow);color:var(--accent)"><i class="fas fa-university"></i></div>
              <span class="cr-lbl">Τράπεζες</span>
              <span class="cr-val">{{ fmt(bankTotal) }}</span>
            </div>
            <div class="cash-row urg" v-if="Number(kpis.urgentTotal)>0">
              <div class="cr-ico" style="background:rgba(255,100,0,.15);color:#ff6400"><i class="fas fa-bolt"></i></div>
              <span class="cr-lbl" style="color:#ff6400;font-weight:700">⚡ Εκκρεμείς</span>
              <span class="cr-val" style="color:#ff6400;font-weight:800">-{{ fmt(kpis.urgentTotal) }}</span>
            </div>
            <div class="cash-row">
              <div class="cr-ico" style="background:var(--warning-bg);color:var(--warning)"><i class="fas fa-clock"></i></div>
              <span class="cr-lbl">Σύνολο Υποχρεώσεων</span>
              <span class="cr-val" style="color:var(--danger)">-{{ fmt(totalUnpaid) }}</span>
            </div>
            <div style="height:1px;background:var(--border);margin:4px 0"></div>
            <div class="cash-row avail" :style="{background: cashAvailable>=0?'rgba(16,185,129,.08)':'rgba(239,68,68,.08)'}">
              <div class="cr-ico" :style="{background:cashAvailable>=0?'var(--success-bg)':'var(--danger-bg)',color:cashAvailable>=0?'var(--success)':'var(--danger)'}"><i class="fas fa-coins"></i></div>
              <span class="cr-lbl" style="font-weight:700">Καθαρά Διαθέσιμα</span>
              <span class="cr-val" :style="{color:cashAvailable>=0?'var(--success)':'var(--danger)',fontWeight:'800',fontSize:'1.05rem'}">{{ fmt(cashAvailable) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ═══ KPI CARDS ═════════════════════════════════════════════ -->
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-hdr">
            <div class="kpi-ico" style="background:var(--accent-glow);color:var(--accent)"><i class="fas fa-wallet"></i></div>
            <span class="kpi-chg down" v-if="Number(kpis.netBalance)<0">Αρνητικό</span>
          </div>
          <div class="kpi-lbl">Τρέχον Υπόλοιπο</div>
          <div class="kpi-val" :style="{color:Number(kpis.netBalance)>=0?'var(--text-primary)':'var(--danger)'}">{{ fmt(kpis.netBalance) }}</div>
          <div class="kpi-sub">Εισπράξεις − Πληρωμές</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr"><div class="kpi-ico" style="background:var(--success-bg);color:var(--success)"><i class="fas fa-arrow-down"></i></div></div>
          <div class="kpi-lbl">Εισπράξεις</div>
          <div class="kpi-val" style="color:var(--success)">{{ fmt(kpis.totalIncome) }}</div>
          <div class="kpi-sub">περίοδος</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr"><div class="kpi-ico" style="background:var(--danger-bg);color:var(--danger)"><i class="fas fa-arrow-up"></i></div></div>
          <div class="kpi-lbl">Πληρωμές</div>
          <div class="kpi-val" style="color:var(--danger)">{{ fmt(kpis.totalExpense) }}</div>
          <div class="kpi-sub">περίοδος</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr">
            <div class="kpi-ico" :style="{background:Number(kpis.netBalance)>=0?'var(--success-bg)':'var(--danger-bg)',color:Number(kpis.netBalance)>=0?'var(--success)':'var(--danger)'}"><i class="fas fa-balance-scale"></i></div>
          </div>
          <div class="kpi-lbl">Καθαρό</div>
          <div class="kpi-val" :style="{color:Number(kpis.netBalance)>=0?'var(--success)':'var(--danger)'}">{{ fmt(kpis.netBalance) }}</div>
          <div class="kpi-sub">Εισπράξεις − Πληρωμές</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr"><div class="kpi-ico" style="background:rgba(255,100,0,.12);color:#ff6400"><i class="fas fa-bolt"></i></div></div>
          <div class="kpi-lbl">⚡ Εκκρεμείς</div>
          <div class="kpi-val" style="color:#ff6400">{{ fmt(kpis.urgentTotal) }}</div>
          <div class="kpi-sub">απλήρωτες</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr"><div class="kpi-ico" style="background:var(--warning-bg);color:var(--warning)"><i class="fas fa-clock"></i></div></div>
          <div class="kpi-lbl">Σύνολο Υποχρεώσεων</div>
          <div class="kpi-val" style="color:var(--warning)">{{ fmt(kpis.unpaidTotal) }}</div>
          <div class="kpi-sub">unpaid + urgent</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr"><div class="kpi-ico" style="background:var(--success-bg);color:var(--success)"><i class="fas fa-university"></i></div></div>
          <div class="kpi-lbl">Τράπεζες</div>
          <div class="kpi-val" style="color:var(--success)">{{ fmt(bankTotal) }}</div>
          <div class="kpi-sub">{{ banks.length }} λογαριασμοί</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-hdr">
            <div class="kpi-ico" :style="{background:cashAvailable>=0?'var(--success-bg)':'var(--danger-bg)',color:cashAvailable>=0?'var(--success)':'#ff6400'}"><i class="fas fa-coins"></i></div>
          </div>
          <div class="kpi-lbl">Ταμειακά Διαθέσιμα</div>
          <div class="kpi-val" :style="{color:cashAvailable>=0?'var(--success)':'#ff6400'}">{{ fmt(cashAvailable) }}</div>
          <div class="kpi-sub">Τράπεζες − Εκκρεμείς</div>
        </div>
      </div>

      <!-- ═══ CHARTS ROW 1: Bar (full) ══════════════════════════════ -->
      <div class="chart-card mb">
        <div class="card-hdr">
          <div class="card-ttl"><i class="fas fa-chart-bar"></i> Μηνιαία Σύνοψη</div>
        </div>
        <div class="chart-wrap" style="height:280px">
          <Bar :data="barChartData" :options="cOpts" />
        </div>
      </div>

      <!-- ═══ CHARTS ROW 2: Pie + Net ══════════════════════════════ -->
      <div class="dash-2equal mb">
        <div class="chart-card">
          <div class="card-hdr">
            <div class="card-ttl"><i class="fas fa-chart-pie"></i> Κατανομή Εξόδων</div>
          </div>
          <div class="chart-wrap" style="height:280px">
            <Doughnut :data="pieChartData" :options="pieOpts" />
          </div>
        </div>
        <div class="chart-card">
          <div class="card-hdr">
            <div class="card-ttl"><i class="fas fa-arrows-left-right"></i> Net (Εισπράξεις – Πληρωμές)</div>
          </div>
          <div class="chart-wrap" style="height:280px">
            <Bar :data="netChartData" :options="cOpts" />
          </div>
        </div>
      </div>

      <!-- ═══ CHART: Πορεία Υπολοίπου (full) ══════════════════════ -->
      <div class="chart-card mb" v-if="balanceTrend.length">
        <div class="card-hdr">
          <div class="card-ttl"><i class="fas fa-chart-line"></i> Πορεία Υπολοίπου</div>
        </div>
        <div class="chart-wrap" style="height:220px">
          <Line :data="trendChartData" :options="trendOpts" />
        </div>
      </div>

      <!-- ═══ ROW: Ανάλυση Κατηγορίας + Πρόσφατες ═════════════════ -->
      <div class="dash-2col mb">
        <div class="chart-card">
          <div class="card-hdr">
            <div class="card-ttl"><i class="fas fa-table"></i> Ανάλυση ανά Κατηγορία</div>
          </div>
          <table class="bktable">
            <thead>
              <tr>
                <th>ΚΑΤΗΓΟΡΙΑ</th>
                <th class="ra">ΠΟΣΟ</th>
                <th>ΚΑΤΑΝΟΜΗ</th>
                <th class="ra">%</th>
                <th class="ra">Μ.Ο./ΜΗΝΑ</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r,i) in catSummary" :key="r.cat">
                <td><span class="cat-dot" :style="{background:CAT_COLORS[i%CAT_COLORS.length]}"></span>{{ r.cat }}</td>
                <td class="ra mono">{{ fmt(r.expense) }}</td>
                <td class="bar-cell"><div class="mini-bar"><div class="mini-bar-fill" :style="{width:r.barWidth+'%',background:CAT_COLORS[i%CAT_COLORS.length]}"></div></div></td>
                <td class="ra mono sm">{{ r.pct }}%</td>
                <td class="ra mono sm">{{ fmt(r.avgMonth) }}</td>
              </tr>
              <tr class="total-row">
                <td>ΣΥΝΟΛΟ</td>
                <td class="ra mono">{{ fmt(catSummary.reduce((s,r)=>s+r.expense,0)) }}</td>
                <td></td>
                <td class="ra mono sm">100%</td>
                <td class="ra mono sm">{{ fmt(catSummary.reduce((s,r)=>s+Number(r.avgMonth),0)) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="chart-card">
          <div class="card-hdr">
            <div class="card-ttl"><i class="fas fa-list"></i> Πρόσφατες Κινήσεις</div>
            <router-link to="/transactions" class="see-all">Όλες <i class="fas fa-arrow-right"></i></router-link>
          </div>
          <ul class="rec-list">
            <li v-for="t in recent.slice(0,10)" :key="t.id" class="rec-item">
              <div class="ri-l">
                <span class="ri-d">{{ (t.description||t.category||'—').substring(0,35) }}</span>
                <span class="ri-m">{{ fmtDate(t.docDate) }} · {{ t.category }}</span>
              </div>
              <span class="ri-a" :class="t.type==='income'?'inf':'out'">
                {{ t.type==='income'?'+':'-' }}{{ fmt(t.amount) }}
              </span>
            </li>
          </ul>
        </div>
      </div>

      <!-- ═══ ROW: Ανά Υποκατηγορία + Top Πληρωμές ════════════════ -->
      <div class="dash-2equal mb">
        <div class="chart-card">
          <div class="card-hdr">
            <div class="card-ttl"><i class="fas fa-university"></i> Ανά Υποκατηγορία</div>
          </div>
          <table class="bktable">
            <thead>
              <tr>
                <th>ΥΠΟΚΑΤΗΓΟΡΙΑ</th>
                <th class="ra">ΕΙΣΠΡΑΞΕΙΣ</th>
                <th class="ra">ΠΛΗΡΩΜΕΣ</th>
                <th class="ra">NET</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in subcatSummary" :key="r.sub">
                <td>{{ r.sub }}</td>
                <td class="ra mono" style="color:var(--success)">{{ r.income>0?fmt(r.income):'—' }}</td>
                <td class="ra mono" style="color:var(--danger)">{{ r.expense>0?fmt(r.expense):'—' }}</td>
                <td class="ra mono" :style="{color:r.net>=0?'var(--success)':'var(--danger)'}">{{ fmt(r.net) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="chart-card">
          <div class="card-hdr">
            <div class="card-ttl"><i class="fas fa-trophy"></i> Top Πληρωμές</div>
          </div>
          <ul class="rec-list">
            <li v-for="t in [...recent].filter(t=>t.type==='expense').sort((a,b)=>b.amount-a.amount).slice(0,10)" :key="'tp'+t.id" class="rec-item">
              <div class="ri-l">
                <span class="ri-d">{{ (t.description||t.category||'—').substring(0,35) }}</span>
                <span class="ri-m">{{ fmtDate(t.docDate) }} · {{ t.category }}</span>
              </div>
              <span class="ri-a out">{{ fmt(t.amount) }}</span>
            </li>
          </ul>
        </div>
      </div>

      <!-- ═══ Ετήσια Σύγκριση (full) ═══════════════════════════════ -->
      <div class="chart-card mb" v-if="yearlyData.length">
        <div class="card-hdr">
          <div class="card-ttl"><i class="fas fa-calendar-alt"></i> Ετήσια Σύγκριση ανά Κατηγορία</div>
        </div>
        <div class="chart-wrap" style="height:280px">
          <Bar :data="yearlyChartData" :options="cOpts" />
        </div>
      </div>

    </template>
  </div>
</template>

<style scoped>
.dashboard { padding: 0 24px 24px 24px; }

/* Period bar */
.period-bar { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:14px 18px; margin-bottom:20px; display:flex; align-items:center; flex-wrap:wrap; gap:10px; }
.quick-btns { display:flex; gap:4px; }
.qbtn { padding:5px 13px; border-radius:20px; border:1px solid var(--border); background:transparent; color:var(--text-secondary); font-family:var(--font); font-size:.75rem; font-weight:500; cursor:pointer; transition:all .2s; }
.qbtn:hover { border-color:var(--accent); color:var(--accent); }
.qbtn.active { background:var(--accent); color:#fff; border-color:var(--accent); }
.sep { width:1px; height:26px; background:var(--border); }
.plbl { font-size:.75rem; color:var(--text-muted); }
.psel { background:var(--bg-input); border:1px solid var(--border); border-radius:var(--radius-sm); color:var(--text-primary); padding:6px 10px; font-size:.82rem; }
.pinp { background:var(--bg-input); border:1px solid var(--border); border-radius:var(--radius-sm); color:var(--text-primary); padding:6px 10px; font-size:.82rem; width:140px; }
.apply-btn { padding:6px 14px; border-radius:var(--radius-sm); border:none; background:var(--accent); color:#fff; font-family:var(--font); font-size:.8rem; font-weight:500; cursor:pointer; }
.apply-btn:hover { background:var(--accent-hover); }
.apply-btn:disabled { opacity:.5; cursor:not-allowed; }
.period-cur { font-size:.8rem; color:var(--text-muted); margin-left:auto; }

.err-bar { background:var(--danger-bg); border:1px solid rgba(239,68,68,.2); color:var(--danger); padding:10px 14px; border-radius:var(--radius-md); margin-bottom:12px; }
.load-wrap { text-align:center; padding:60px; color:var(--text-muted); }
.spinner { display:inline-block; width:18px; height:18px; border:2px solid var(--border); border-top-color:var(--accent); border-radius:50%; animation:spin .7s linear infinite; vertical-align:middle; margin-right:8px; }
@keyframes spin { to { transform:rotate(360deg); } }
.mb { margin-bottom:18px; }

/* Grids */
.dash-4col { display:grid; grid-template-columns:1fr 1fr 1fr 1fr; gap:18px; margin-bottom:18px; }
.dash-2col  { display:grid; grid-template-columns:3fr 2fr; gap:18px; }
.dash-2equal { display:grid; grid-template-columns:1fr 1fr; gap:18px; }
@media(max-width:1200px) { .dash-4col { grid-template-columns:1fr 1fr; } }
@media(max-width:900px)  { .dash-2col,.dash-2equal { grid-template-columns:1fr; } }
@media(max-width:768px)  { .dash-4col { grid-template-columns:1fr; } }

/* Panels */
.panel-card { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:18px; overflow:hidden; }
.panel-hdr { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.ptitle { font-size:.95rem; font-weight:600; display:flex; align-items:center; gap:8px; }
.ptitle.warn { color:var(--warning); }
.ptitle.succ { color:var(--success); }
.ptitle.acc  { color:var(--accent); }
.pbadge { font-size:.68rem; font-weight:600; padding:3px 10px; border-radius:12px; }
.pbadge.warn { background:rgba(245,158,11,.15); color:var(--warning); }
.pbadge.succ { background:var(--success-bg); color:var(--success); }
.pbadge.acc  { background:var(--accent-glow); color:var(--accent); }

/* Obligations */
.oblig-sum { display:grid; grid-template-columns:1fr 1fr; gap:8px; margin-bottom:12px; }
.osum-item { background:var(--bg-input); border-radius:var(--radius-sm); padding:10px 12px; border:1px solid var(--border); }
.osum-lbl { font-size:.68rem; color:var(--text-muted); font-weight:500; text-transform:uppercase; letter-spacing:.3px; }
.osum-val { font-size:1rem; font-weight:700; font-family:var(--font-mono); margin-top:2px; }
.oblig-list { list-style:none; max-height:320px; overflow-y:auto; scrollbar-width:thin; padding:0; margin:0; }
.olist-empty { padding:20px; text-align:center; color:var(--text-muted); font-size:.85rem; }
.olist-item { display:flex; align-items:flex-start; gap:10px; padding:8px 0; border-bottom:1px solid rgba(42,69,99,.35); }
.olist-item:last-child { border-bottom:none; }
.olist-item.overdue .odate-badge { border-color:var(--danger); background:var(--danger-bg); }
.olist-item.overdue .od-day { color:var(--danger); }
.odate-badge { min-width:40px; text-align:center; background:var(--bg-input); border-radius:var(--radius-sm); padding:5px 4px; border:1px solid var(--border); }
.od-day { font-size:1rem; font-weight:700; line-height:1; font-family:var(--font-mono); }
.od-mon { font-size:.58rem; color:var(--text-muted); text-transform:uppercase; }
.oinfo { flex:1; min-width:0; }
.oti { font-size:.88rem; font-weight:500; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.ome { font-size:.75rem; color:var(--text-muted); margin-top:2px; }
.oamt { font-family:var(--font-mono); font-size:.9rem; font-weight:600; white-space:nowrap; }

/* Banks */
.bank-tot { display:flex; align-items:center; justify-content:space-between; padding:10px 12px; background:var(--accent-glow); border:1px solid rgba(46,117,182,.2); border-radius:var(--radius-md); margin-bottom:12px; }
.bt-lbl { font-size:.78rem; font-weight:500; color:var(--accent); }
.bt-amt { font-family:var(--font-mono); font-size:1.1rem; font-weight:700; color:var(--accent); }
.bank-list { list-style:none; padding:0; margin:0; }
.bank-item { display:flex; align-items:center; gap:10px; padding:9px 0; border-bottom:1px solid rgba(42,69,99,.35); }
.bank-item:last-child { border-bottom:none; }
.bicon { width:34px; height:34px; border-radius:var(--radius-sm); display:flex; align-items:center; justify-content:center; font-size:.88rem; background:var(--bg-input); border:1px solid var(--border); color:var(--text-secondary); }
.binfo { flex:1; min-width:0; }
.bn { font-size:.88rem; font-weight:500; }
.bt { font-size:.75rem; color:var(--text-muted); }
.bbal { text-align:right; }
.bamt { font-family:var(--font-mono); font-size:.95rem; font-weight:600; }
.bdt { font-size:.7rem; color:var(--text-muted); }

/* Reconciliation */
.recon-grid { display:flex; flex-direction:column; gap:6px; }
.recon-row { display:flex; align-items:center; justify-content:space-between; padding:6px 2px; }
.rc-lbl { font-size:.85rem; color:var(--text-secondary); display:flex; align-items:center; gap:6px; }
.rc-lbl.sm { font-size:.82rem; color:var(--text-secondary); }
.rc-val { font-family:var(--font-mono); font-size:.9rem; font-weight:600; }
.recon-div { height:1px; background:var(--border); margin:4px 0; }

/* Cash */
.cash-hero { text-align:center; padding:16px 0 12px; }
.cash-tot { font-family:var(--font-mono); font-size:1.8rem; font-weight:700; }
.cash-lbl { font-size:.75rem; color:var(--text-secondary); margin-top:2px; }
.cash-bk { display:flex; flex-direction:column; gap:6px; }
.cash-row { display:flex; align-items:center; gap:8px; padding:7px 10px; border-radius:var(--radius-sm); background:var(--bg-input); border:1px solid var(--border); }
.cash-row.urg { background:rgba(255,100,0,.08); border-radius:8px; }
.cash-row.avail { border-radius:8px; }
.cr-ico { width:26px; height:26px; border-radius:6px; display:flex; align-items:center; justify-content:center; font-size:.72rem; }
.cr-lbl { flex:1; font-size:.82rem; font-weight:500; }
.cr-val { font-family:var(--font-mono); font-size:.85rem; font-weight:600; }

/* KPI */
.kpi-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:14px; margin-bottom:18px; }
@media(max-width:1200px) { .kpi-grid { grid-template-columns:repeat(2,1fr); } }
.kpi-card { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:16px 18px; transition:transform .2s,box-shadow .2s; }
.kpi-card:hover { transform:translateY(-2px); box-shadow:var(--shadow); }
.kpi-hdr { display:flex; align-items:center; justify-content:space-between; margin-bottom:8px; }
.kpi-ico { width:36px; height:36px; border-radius:var(--radius-md); display:flex; align-items:center; justify-content:center; font-size:.95rem; }
.kpi-chg { font-size:.7rem; font-weight:600; padding:2px 8px; border-radius:10px; }
.kpi-chg.down { background:var(--danger-bg); color:var(--danger); }
.kpi-lbl { font-size:.82rem; color:var(--text-secondary); margin-bottom:4px; font-weight:500; }
.kpi-val { font-size:1.4rem; font-weight:700; font-family:var(--font-mono); }
.kpi-sub { font-size:.75rem; color:var(--text-muted); margin-top:4px; }

/* Charts */
.chart-card { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:18px; }
.card-hdr { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }
.card-ttl { font-size:.95rem; font-weight:600; display:flex; align-items:center; gap:8px; }
.card-ttl i { color:var(--accent); }
.chart-wrap { position:relative; }
.see-all { font-size:.78rem; color:var(--accent); text-decoration:none; }

/* Tables */
.bktable { width:100%; border-collapse:collapse; font-size:.85rem; }
.bktable th { text-align:left; padding:8px 12px; font-size:.75rem; font-weight:600; color:var(--text-muted); text-transform:uppercase; letter-spacing:.5px; border-bottom:1px solid var(--border); }
.bktable td { padding:9px 12px; border-bottom:1px solid rgba(42,69,99,.35); }
.bktable tbody tr:hover { background:var(--bg-card-hover); }
.bktable .total-row { font-weight:700; border-top:2px solid var(--border); }
.ra { text-align:right; }
.mono { font-family:var(--font-mono); font-weight:600; }
.sm { font-size:.78rem; color:var(--text-muted); }
.cat-dot { display:inline-block; width:8px; height:8px; border-radius:50%; margin-right:8px; vertical-align:middle; }
.bar-cell { width:120px; }
.mini-bar { height:6px; border-radius:3px; background:var(--bg-input); overflow:hidden; }
.mini-bar-fill { height:100%; border-radius:3px; transition:width .6s ease; }

/* Recent list */
.rec-list { list-style:none; padding:0; margin:0; }
.rec-item { display:flex; align-items:center; justify-content:space-between; padding:9px 2px; border-bottom:1px solid rgba(42,69,99,.3); }
.rec-item:last-child { border-bottom:none; }
.ri-l { flex:1; min-width:0; margin-right:12px; }
.ri-d { display:block; font-size:.88rem; font-weight:500; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.ri-m { display:block; font-size:.75rem; color:var(--text-muted); margin-top:2px; }
.ri-a { font-family:var(--font-mono); font-size:.9rem; font-weight:600; white-space:nowrap; }
.ri-a.inf { color:var(--success); }
.ri-a.out { color:var(--danger); }
</style>