<script setup>
import { ref, computed, onMounted } from 'vue'
import { Bar, Pie } from 'vue-chartjs'
import {
  Chart as ChartJS, Title, Tooltip, Legend,
  BarElement, CategoryScale, LinearScale, ArcElement
} from 'chart.js'
import { useUserStore } from '@/stores/user'
import api from '@/api'

ChartJS.register(Title, Tooltip, Legend, BarElement, CategoryScale, LinearScale, ArcElement)

const userStore = useUserStore()

// έΦΑέΦΑ Entity έΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑ
const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14',
}
const selectedEntity = ref(localStorage.getItem('n2c_entity') || 'next2me')
const entityId = computed(() => ENTITIES[selectedEntity.value])

// έΦΑέΦΑ Filters έΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑ
const periodMode  = ref('year')
const selectedYear = ref(String(new Date().getFullYear()))
const selectedMonth = ref('έΑΦ')
const dateFrom = ref('')
const dateTo   = ref('')
const months = ['έΑΦ','╬β╬▒╬╜╬┐╧Ζ╬υ╧Β╬╣╬┐╧Γ','╬ο╬╡╬▓╧Β╬┐╧Ζ╬υ╧Β╬╣╬┐╧Γ','╬ε╬υ╧Β╧Ε╬╣╬┐╧Γ','╬Σ╧Α╧Β╬ψ╬╗╬╣╬┐╧Γ','╬ε╬υ╬╣╬┐╧Γ','╬β╬┐╧Ξ╬╜╬╣╬┐╧Γ','╬β╬┐╧Ξ╬╗╬╣╬┐╧Γ','╬Σ╧Ξ╬│╬┐╧Ζ╧Δ╧Ε╬┐╧Γ','╬μ╬╡╧Α╧Ε╬φ╬╝╬▓╧Β╬╣╬┐╧Γ','╬θ╬║╧Ε╧Ο╬▓╧Β╬╣╬┐╧Γ','╬ζ╬┐╬φ╬╝╬▓╧Β╬╣╬┐╧Γ','╬Φ╬╡╬║╬φ╬╝╬▓╧Β╬╣╬┐╧Γ']
const years   = ['2017','2018','2019','2020','2021','2022','2023','2024','2025','2026']

// έΦΑέΦΑ State έΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑ
const loading  = ref(false)
const error    = ref(null)
const kpis     = ref({})
const recent   = ref([])
const monthly  = ref([])
const period   = ref({})

// έΦΑέΦΑ Date range from filters έΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑ
function getDateRange() {
  const y = parseInt(selectedYear.value)
  if (periodMode.value === 'all') return { from: '2017-01-01', to: '2026-12-31' }
  if (periodMode.value === 'custom') return { from: dateFrom.value, to: dateTo.value }
  if (periodMode.value === 'month') {
    const m = months.indexOf(selectedMonth.value)
    if (m > 0) {
      const last = new Date(y, m, 0).getDate()
      return { from: `${y}-${String(m).padStart(2,'0')}-01`, to: `${y}-${String(m).padStart(2,'0')}-${last}` }
    }
  }
  if (periodMode.value === 'quarter') {
    const q = Math.ceil((new Date().getMonth()+1)/3)
    const qFrom = [(q-1)*3+1]
    const qTo   = [q*3]
    const last  = new Date(y, qTo[0], 0).getDate()
    return { from: `${y}-${String(qFrom[0]).padStart(2,'0')}-01`, to: `${y}-${String(qTo[0]).padStart(2,'0')}-${last}` }
  }
  if (periodMode.value === 'half') {
    const isFirst = new Date().getMonth() < 6
    return isFirst
      ? { from: `${y}-01-01`, to: `${y}-06-30` }
      : { from: `${y}-07-01`, to: `${y}-12-31` }
  }
  // year (default)
  return { from: `${y}-01-01`, to: `${y}-12-31` }
}

// έΦΑέΦΑ Load dashboard έΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑ
async function loadDashboard() {
  loading.value = true
  error.value   = null
  try {
    const { from, to } = getDateRange()
    const res = await api.get('/api/dashboard', {
      params: { entityId: entityId.value, from, to }
    })
    if (res.data.success) {
      kpis.value   = res.data.kpis
      recent.value = res.data.recent
      monthly.value = res.data.monthlyData || []
      period.value  = res.data.period
    }
  } catch (e) {
    error.value = '╬μ╧Η╬υ╬╗╬╝╬▒ ╧Η╧Ν╧Β╧Ε╧Κ╧Δ╬╖╧Γ ╬┤╬╡╬┤╬┐╬╝╬φ╬╜╧Κ╬╜'
    console.error(e)
  } finally {
    loading.value = false
  }
}

// έΦΑέΦΑ Entity change έΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑ
function changeEntity(e) {
  localStorage.setItem('n2c_entity', e)
  selectedEntity.value = e
  loadDashboard()
}

// έΦΑέΦΑ Apply filters έΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑ
function applyFilters() { loadDashboard() }

// έΦΑέΦΑ Formatters έΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑ
function fmt(v) {
  if (v === null || v === undefined) return '0,00 έΓυ'
  return new Intl.NumberFormat('el-GR', { style: 'currency', currency: 'EUR' }).format(v)
}
function fmtDate(d) {
  if (!d) return 'έΑΦ'
  return new Date(d).toLocaleDateString('el-GR', { day: '2-digit', month: 'short', year: '2-digit' })
}
function payStatusClass(s) {
  return { paid:'status-paid', received:'status-paid', unpaid:'status-unpaid', urgent:'status-urgent', partial:'status-partial' }[s] || ''
}
function payStatusLabel(s) {
  return { paid:'╬ι╬╗╬╖╧Β╧Κ╬╝╬φ╬╜╬╖', received:'╬Χ╬╣╧Δ╧Α╧Β╬υ╧Θ╬╕╬╖╬║╬╡', unpaid:'╬Σ╧Α╬╗╬χ╧Β╧Κ╧Ε╬╖', urgent:'╬Χ╬║╬║╧Β╬╡╬╝╬χ╧Γ', partial:'╬ε╬╡╧Β╬╣╬║╬χ' }[s] || s
}

// έΦΑέΦΑ Chart data έΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑ
const MONTH_LABELS = ['╬β╬▒╬╜','╬ο╬╡╬▓','╬ε╬▒╧Β','╬Σ╧Α╧Β','╬ε╬▒╬╣','╬β╬┐╧Ζ╬╜','╬β╬┐╧Ζ╬╗','╬Σ╧Ζ╬│','╬μ╬╡╧Α','╬θ╬║╧Ε','╬ζ╬┐╬╡','╬Φ╬╡╬║']

const barChartData = computed(() => {
  const incomeByMonth  = Array(12).fill(0)
  const expenseByMonth = Array(12).fill(0)
  monthly.value.forEach(([month, category, total]) => {
    const m = month - 1
    // Determine if income or expense by category name (heuristic)
    const isIncome = category && (category.includes('╬Χ╬μ╬θ╬Φ╬Σ') || category.includes('╬Χ╬β╬μ╬ι╬κ'))
    if (isIncome) incomeByMonth[m] += total
    else expenseByMonth[m] += total
  })
  return {
    labels: MONTH_LABELS,
    datasets: [
      { label: '╬Χ╬╣╧Δ╧Α╧Β╬υ╬╛╬╡╬╣╧Γ', data: incomeByMonth, backgroundColor: '#4FC3A1', borderRadius: 4 },
      { label: '╬ι╬╗╬╖╧Β╧Κ╬╝╬φ╧Γ',   data: expenseByMonth, backgroundColor: '#ef5350', borderRadius: 4 },
    ]
  }
})

const pieChartData = computed(() => {
  const totals = {}
  monthly.value.forEach(([, category, total]) => {
    if (!totals[category]) totals[category] = 0
    totals[category] += total
  })
  const COLORS = ['#4FC3A1','#2196F3','#FF9800','#9C27B0','#ef5350','#00BCD4']
  const labels = Object.keys(totals).slice(0, 6)
  return {
    labels,
    datasets: [{ data: labels.map(l => totals[l]), backgroundColor: COLORS }]
  }
})

const chartOptions = {
  responsive: true, maintainAspectRatio: false,
  plugins: {
    legend: { labels: { color: '#8899aa', font: { size: 11 } } },
    tooltip: { backgroundColor: '#1a2f45', titleColor: '#e0e6ed', bodyColor: '#c8d8e8' }
  },
  scales: {
    x: { grid: { display: false }, ticks: { color: '#4a6a88' }, border: { color: '#2a4a6a' } },
    y: { grid: { color: 'rgba(42,74,106,0.4)' }, ticks: { color: '#4a6a88' }, border: { color: 'transparent' } }
  }
}

const pieOptions = {
  responsive: true, maintainAspectRatio: false,
  plugins: {
    legend: { position: 'right', labels: { color: '#8899aa', font: { size: 11 } } },
    tooltip: { backgroundColor: '#1a2f45', titleColor: '#e0e6ed', bodyColor: '#c8d8e8' }
  }
}

// έΦΑέΦΑ Current period label έΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑέΦΑ
const periodLabel = computed(() => {
  const now = new Date()
  const map = { month: `${selectedMonth.value} ${selectedYear.value}`, quarter: `╬ν╧Β╬ψ╬╝╬╖╬╜╬┐ ${selectedYear.value}`, half: `6╬╝╬╖╬╜╬┐ ${selectedYear.value}`, year: `╬Ι╧Ε╬┐╧Γ ${selectedYear.value}`, all: '╬Ν╬╗╬╡╧Γ ╬┐╬╣ ╧Α╬╡╧Β╬ψ╬┐╬┤╬┐╬╣', custom: '╬ι╧Β╬┐╧Δ╬▒╧Β╬╝╬┐╧Δ╬╝╬φ╬╜╬┐' }
  return map[periodMode.value] || selectedYear.value
})

onMounted(loadDashboard)
</script>

<template>
  <div class="dashboard">
    <!-- Filter Bar -->
    <div class="filter-bar">
      <div class="filter-left">
        <button v-for="p in ['╬ε╬χ╬╜╬▒╧Γ','╬ν╧Β╬ψ╬╝╬╖╬╜╬┐','6╬╝╬╖╬╜╬┐','╬Ι╧Ε╬┐╧Γ','╬Ν╬╗╬▒']" :key="p"
          class="filter-btn" :class="{ active: periodMode === {╬ε╬χ╬╜╬▒╧Γ:'month',╬ν╧Β╬ψ╬╝╬╖╬╜╬┐:'quarter','6╬╝╬╖╬╜╬┐':'half',╬Ι╧Ε╬┐╧Γ:'year',╬Ν╬╗╬▒:'all'}[p] }"
          @click="periodMode = {╬ε╬χ╬╜╬▒╧Γ:'month',╬ν╧Β╬ψ╬╝╬╖╬╜╬┐:'quarter','6╬╝╬╖╬╜╬┐':'half',╬Ι╧Ε╬┐╧Γ:'year',╬Ν╬╗╬▒:'all'}[p]">
          {{ p }}
        </button>
        <select v-if="periodMode === 'month'" v-model="selectedMonth" class="filter-select">
          <option v-for="m in months" :key="m">{{ m }}</option>
        </select>
        <select v-model="selectedYear" class="filter-select">
          <option v-for="y in years" :key="y">{{ y }}</option>
        </select>
        <template v-if="periodMode === 'custom'">
          <input type="date" v-model="dateFrom" class="filter-input" />
          <input type="date" v-model="dateTo" class="filter-input" />
        </template>
        <button class="btn-apply" @click="applyFilters" :disabled="loading">
          <i class="fas fa-filter"></i> {{ loading ? '╬ο╧Ν╧Β╧Ε╧Κ╧Δ╬╖...' : '╬Χ╧Η╬▒╧Β╬╝╬┐╬│╬χ' }}
        </button>
      </div>
      <div class="period-label">{{ periodLabel }}</div>
    </div>

    <!-- Error -->
    <div v-if="error" class="error-bar">{{ error }}</div>

    <!-- Loading overlay -->
    <div v-if="loading" class="loading-overlay">
      <i class="fas fa-spinner fa-spin"></i> ╬ο╧Ν╧Β╧Ε╧Κ╧Δ╬╖...
    </div>

    <!-- KPI Cards -->
    <div class="kpi-grid" v-if="!loading">
      <div class="kpi-card green">
        <div class="kpi-label">╬Χ╬╣╧Δ╧Α╧Β╬υ╬╛╬╡╬╣╧Γ</div>
        <div class="kpi-value">{{ fmt(kpis.totalIncome) }}</div>
      </div>
      <div class="kpi-card red">
        <div class="kpi-label">╬ι╬╗╬╖╧Β╧Κ╬╝╬φ╧Γ</div>
        <div class="kpi-value">{{ fmt(kpis.totalExpense) }}</div>
      </div>
      <div class="kpi-card" :class="kpis.netBalance >= 0 ? 'green' : 'red'">
        <div class="kpi-label">╬γ╬▒╬╕╬▒╧Β╧Ν</div>
        <div class="kpi-value">{{ fmt(kpis.netBalance) }}</div>
      </div>
      <div class="kpi-card orange">
        <div class="kpi-label">╬Χ╬║╬║╧Β╬╡╬╝╬╡╬ψ╧Γ</div>
        <div class="kpi-value">{{ fmt(kpis.urgentTotal) }}</div>
      </div>
      <div class="kpi-card" :class="kpis.cashAvailable >= 0 ? 'green' : 'red'">
        <div class="kpi-label">╬ν╬▒╬╝╬╡╬╣╬▒╬║╬υ ╬Φ╬╣╬▒╬╕╬φ╧Δ╬╣╬╝╬▒</div>
        <div class="kpi-value">{{ fmt(kpis.cashAvailable) }}</div>
      </div>
      <div class="kpi-card neutral">
        <div class="kpi-label">╬Σ╧Α╬╗╬χ╧Β╧Κ╧Ε╬╡╧Γ</div>
        <div class="kpi-value">{{ fmt(kpis.unpaidTotal) }}</div>
      </div>
    </div>

    <!-- Charts -->
    <div class="charts-grid" v-if="!loading && monthly.length">
      <div class="chart-card">
        <div class="chart-title">╬ε╬╖╬╜╬╣╬▒╬ψ╬▒ ╬μ╧Ξ╬╜╬┐╧Ι╬╖</div>
        <div class="chart-wrap">
          <Bar :data="barChartData" :options="chartOptions" />
        </div>
      </div>
      <div class="chart-card">
        <div class="chart-title">╬γ╬▒╧Ε╬▒╬╜╬┐╬╝╬χ ╬Χ╬╛╧Ν╬┤╧Κ╬╜</div>
        <div class="chart-wrap">
          <Pie :data="pieChartData" :options="pieOptions" />
        </div>
      </div>
    </div>

    <!-- Recent Transactions -->
    <div class="recent-card" v-if="!loading && recent.length">
      <div class="recent-header">
        <span>╬ι╧Β╧Ν╧Δ╧Η╬▒╧Ε╬╡╧Γ ╬γ╬╣╬╜╬χ╧Δ╬╡╬╣╧Γ</span>
        <router-link to="/transactions" class="see-all">╬Ν╬╗╬╡╧Γ <i class="fas fa-arrow-right"></i></router-link>
      </div>
      <table class="recent-table">
        <thead>
          <tr>
            <th>ID</th><th>╬Ω╬╝/╬╜╬ψ╬▒</th><th>╬ι╬╡╧Β╬╣╬│╧Β╬▒╧Η╬χ</th><th>╬γ╬▒╧Ε╬╖╬│╬┐╧Β╬ψ╬▒</th>
            <th style="text-align:right">╬ι╬┐╧Δ╧Ν</th><th>Status</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in recent" :key="t.id">
            <td class="id-col">#{{ t.id }}</td>
            <td>{{ fmtDate(t.docDate) }}</td>
            <td class="desc-col">{{ t.description || 'έΑΦ' }}</td>
            <td>{{ t.category }}</td>
            <td style="text-align:right" :class="t.type === 'income' ? 'amount-in' : 'amount-out'">
              {{ t.type === 'income' ? '+' : 'έΙΤ' }} {{ fmt(t.amount) }}
            </td>
            <td><span class="status-badge" :class="payStatusClass(t.paymentStatus)">{{ payStatusLabel(t.paymentStatus) }}</span></td>
            <td>
              <a v-if="t.blobFileIds" :href="`https://drive.google.com/file/d/${t.blobFileIds.split(',')[0]}/view`" target="_blank" class="doc-link">
                <i class="fas fa-paperclip"></i>
              </a>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.dashboard { padding: 0; }

/* Filter Bar */
.filter-bar { display:flex; align-items:center; justify-content:space-between; padding:12px 0 16px; gap:8px; flex-wrap:wrap; }
.filter-left { display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
.filter-btn { background:var(--bg-secondary); border:1px solid var(--border); color:var(--text-muted); padding:6px 12px; border-radius:var(--radius-md); cursor:pointer; font-size:.82rem; transition:all .2s; }
.filter-btn.active { background:var(--accent); color:#0f1e2e; border-color:var(--accent); font-weight:600; }
.filter-select { background:var(--bg-input); border:1px solid var(--border); color:var(--text-primary); padding:6px 10px; border-radius:var(--radius-md); font-size:.82rem; }
.filter-input { background:var(--bg-input); border:1px solid var(--border); color:var(--text-primary); padding:6px 10px; border-radius:var(--radius-md); font-size:.82rem; }
.btn-apply { background:var(--accent); color:#0f1e2e; border:none; padding:6px 14px; border-radius:var(--radius-md); cursor:pointer; font-weight:600; font-size:.82rem; }
.btn-apply:disabled { opacity:.6; cursor:not-allowed; }
.period-label { color:var(--text-muted); font-size:.82rem; }

/* Error */
.error-bar { background:rgba(239,83,80,.1); color:#ef5350; padding:10px 14px; border-radius:var(--radius-md); margin-bottom:12px; }

/* Loading */
.loading-overlay { text-align:center; padding:40px; color:var(--text-muted); font-size:1rem; }

/* KPIs */
.kpi-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(160px,1fr)); gap:12px; margin-bottom:16px; }
.kpi-card { background:var(--bg-secondary); border:1px solid var(--border); border-radius:var(--radius-lg); padding:16px; }
.kpi-label { font-size:.75rem; color:var(--text-muted); margin-bottom:6px; text-transform:uppercase; letter-spacing:.05em; }
.kpi-value { font-size:1.25rem; font-weight:700; }
.kpi-card.green .kpi-value { color:#4FC3A1; }
.kpi-card.red   .kpi-value { color:#ef5350; }
.kpi-card.orange .kpi-value { color:#ff9800; }
.kpi-card.neutral .kpi-value { color:var(--text-primary); }

/* Charts */
.charts-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-bottom:16px; }
@media(max-width:768px) { .charts-grid { grid-template-columns:1fr; } }
.chart-card { background:var(--bg-secondary); border:1px solid var(--border); border-radius:var(--radius-lg); padding:16px; }
.chart-title { font-size:.85rem; font-weight:600; color:var(--text-muted); margin-bottom:12px; }
.chart-wrap { height:220px; }

/* Recent */
.recent-card { background:var(--bg-secondary); border:1px solid var(--border); border-radius:var(--radius-lg); overflow:hidden; }
.recent-header { display:flex; justify-content:space-between; align-items:center; padding:14px 16px; border-bottom:1px solid var(--border); font-size:.9rem; font-weight:600; }
.see-all { color:var(--accent); text-decoration:none; font-size:.8rem; }
.recent-table { width:100%; border-collapse:collapse; font-size:.82rem; }
.recent-table th { padding:8px 12px; color:var(--text-muted); font-weight:500; text-align:left; border-bottom:1px solid var(--border); }
.recent-table td { padding:9px 12px; border-bottom:1px solid rgba(255,255,255,.04); }
.recent-table tr:last-child td { border-bottom:none; }
.id-col { color:var(--text-muted); font-family:monospace; }
.desc-col { max-width:220px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.amount-in  { color:#4FC3A1; font-weight:600; }
.amount-out { color:#ef5350; font-weight:600; }
.status-badge { padding:2px 8px; border-radius:20px; font-size:.72rem; font-weight:600; }
.status-paid    { background:rgba(79,195,161,.15); color:#4FC3A1; }
.status-unpaid  { background:rgba(239,83,80,.15);  color:#ef5350; }
.status-urgent  { background:rgba(255,100,0,.15);  color:#ff6400; }
.status-partial { background:rgba(255,152,0,.15);  color:#ff9800; }
.doc-link { color:var(--accent); opacity:.7; }
.doc-link:hover { opacity:1; }
</style>
