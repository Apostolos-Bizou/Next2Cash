<script setup>
/* eslint-disable */
// Investor Reports / Forecast View - S85 Step C.3
// Real UI: KPI cards (CFO standard), 3-mode chart (Bar/Stacked/Line),
// project breakdown, detailed forecast entries table.
//
// Data source: GET /api/forecast?entityId=...&horizonMonths=...
// Backend service: ForecastService (Phase A expenses + Phase B LIVE income)

import { ref, computed, onMounted, watch } from 'vue'
import { Bar, Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  Title, Tooltip, Legend,
  BarElement, CategoryScale, LinearScale,
  PointElement, LineElement, Filler,
} from 'chart.js'
import api from '@/api'

ChartJS.register(
  Title, Tooltip, Legend,
  BarElement, CategoryScale, LinearScale,
  PointElement, LineElement, Filler
)

/* ----------------------------------------------------------------
   Entity mapping (matches all other views; S77 entity-scoped)
   ---------------------------------------------------------------- */
const ENTITY_MAP = {
  next2me:      { id: '58202b71-4ddb-45c9-8e3c-39e816bde972', label: 'Next2Me' },
  house:        { id: 'dea1f32c-7b30-4981-b625-633da9dbe71e', label: 'House' },
  next2megroup: { id: '50317f44-9961-4fb4-add0-7a118e32dc14', label: 'Next2Me Group' },
}

const entities = ref([])  // populated from /api/config/entities; falls back to hardcoded
const entityKey = ref(localStorage.getItem('n2c_entity') || 'next2megroup')
watch(entityKey, (v) => {
  localStorage.setItem('n2c_entity', v)
  loadForecast()
})

const HORIZON_OPTIONS = [
  { value: 12,  label: '1 year (12 months)' },
  { value: 24,  label: '2 years (24 months)' },
  { value: 36,  label: '3 years (36 months)' },
  { value: 60,  label: '5 years (60 months)' },
]
const horizonMonths = ref(Number(localStorage.getItem('n2c_forecast_horizon')) || 24)
watch(horizonMonths, (v) => {
  localStorage.setItem('n2c_forecast_horizon', String(v))
  loadForecast()
})

const CHART_MODES = ['bar', 'stacked', 'line']
const chartMode = ref(localStorage.getItem('n2c_forecast_chart') || 'bar')
watch(chartMode, (v) => {
  localStorage.setItem('n2c_forecast_chart', v)
})

const loading  = ref(false)
const error    = ref(null)
const forecast = ref(null)

/* ----------------------------------------------------------------
   Formatters
   ---------------------------------------------------------------- */
function fmtEur(amount) {
  const n = Number(amount) || 0
  return n.toLocaleString('el-GR', {
    style: 'currency', currency: 'EUR',
    minimumFractionDigits: 0, maximumFractionDigits: 0,
  })
}
function fmtEurSigned(amount) {
  const n = Number(amount) || 0
  if (n === 0) return fmtEur(0)
  const sign = n > 0 ? '+' : '-'
  const abs = Math.abs(n).toLocaleString('el-GR', {
    style: 'currency', currency: 'EUR',
    minimumFractionDigits: 0, maximumFractionDigits: 0,
  })
  return sign + abs.replace('-', '')
}
function fmtDate(d) {
  if (!d) return ''
  const parts = String(d).split('-')
  if (parts.length !== 3) return d
  return `${parts[2]}/${parts[1]}/${parts[0]}`
}
function monthKey(d) {
  // YYYY-MM from a YYYY-MM-DD date string
  return String(d || '').slice(0, 7)
}
function monthLabel(ymKey) {
  // 2026-05 -> 'May 2026'
  const [y, m] = ymKey.split('-')
  const names = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
  return `${names[Number(m) - 1] || m} ${y}`
}

/* ----------------------------------------------------------------
   API loaders
   ---------------------------------------------------------------- */
async function loadEntities() {
  try {
    const res = await api.get('/api/config/entities')
    const list = Array.isArray(res.data) ? res.data : (res.data?.data || [])
    if (list.length > 0) {
      entities.value = list.map(e => ({
        key:   e.code || e.key || String(e.name || '').toLowerCase().replace(/\s+/g, ''),
        id:    e.id,
        label: e.name || e.label || e.code,
      }))
      return
    }
  } catch (e) {
    // 403 or other -- fall through to hardcoded
  }
  // Fallback to hardcoded 3 entities
  entities.value = Object.keys(ENTITY_MAP).map(k => ({
    key:   k,
    id:    ENTITY_MAP[k].id,
    label: ENTITY_MAP[k].label,
  }))
}

const currentEntityId = computed(() => {
  const found = entities.value.find(e => e.key === entityKey.value)
  if (found) return found.id
  return ENTITY_MAP[entityKey.value]?.id || ENTITY_MAP.next2megroup.id
})

async function loadForecast() {
  loading.value = true
  error.value = null
  try {
    const res = await api.get('/api/forecast', {
      params: { entityId: currentEntityId.value, horizonMonths: horizonMonths.value }
    })
    forecast.value = res.data
  } catch (e) {
    const status = e?.response?.status
    if (status === 404) {
      error.value = 'Forecast API not yet deployed (HTTP 404). Backend deploy pending in Step D.'
    } else if (status === 403) {
      error.value = 'No access to forecast for this entity (HTTP 403).'
    } else {
      error.value = e?.message || 'Unknown error loading forecast'
    }
    forecast.value = null
  } finally {
    loading.value = false
  }
}

/* ----------------------------------------------------------------
   Derived KPIs
   ---------------------------------------------------------------- */
const hasData = computed(() => forecast.value && (forecast.value.entries?.length ?? 0) > 0)

const totalIncome   = computed(() => Number(forecast.value?.totalIncome   ?? 0))
const totalExpenses = computed(() => Number(forecast.value?.totalExpenses ?? 0))
const netCashFlow   = computed(() => Number(forecast.value?.netCashFlow   ?? 0))
const months        = computed(() => Number(forecast.value?.horizonMonths ?? horizonMonths.value))
const monthlyBurn   = computed(() => {
  if (months.value === 0) return 0
  return totalExpenses.value / months.value
})
const monthlyMrr = computed(() => {
  if (months.value === 0) return 0
  return totalIncome.value / months.value
})
const netStatus = computed(() => {
  const n = netCashFlow.value
  if (n > 0)  return { label: 'Positive',  cls: 'positive' }
  if (n < 0)  return { label: 'Burning',   cls: 'negative' }
  return { label: 'Break-even', cls: 'neutral' }
})

/* ----------------------------------------------------------------
   Group entries by month for chart
   ---------------------------------------------------------------- */
const monthlyBuckets = computed(() => {
  if (!hasData.value) return []
  const buckets = new Map()
  for (const e of forecast.value.entries) {
    const k = monthKey(e.date)
    if (!buckets.has(k)) buckets.set(k, { ym: k, income: 0, expense: 0 })
    const b = buckets.get(k)
    const amt = Number(e.amount) || 0
    if ((e.type || '').toLowerCase() === 'income') b.income += amt
    else b.expense += amt
  }
  return Array.from(buckets.values()).sort((a, b) => a.ym.localeCompare(b.ym))
})

const chartData = computed(() => {
  const labels = monthlyBuckets.value.map(b => monthLabel(b.ym))
  const burnArr = monthlyBuckets.value.map(b => Math.round(b.expense))
  const mrrArr  = monthlyBuckets.value.map(b => Math.round(b.income))

  if (chartMode.value === 'line') {
    return {
      labels,
      datasets: [
        {
          label: 'Burn (expenses)',
          data: burnArr,
          borderColor: '#ef4444',
          backgroundColor: 'rgba(239, 68, 68, 0.15)',
          fill: true,
          tension: 0.3,
          pointRadius: 3,
        },
        {
          label: 'MRR (income)',
          data: mrrArr,
          borderColor: '#10b981',
          backgroundColor: 'rgba(16, 185, 129, 0.15)',
          fill: true,
          tension: 0.3,
          pointRadius: 3,
        },
      ],
    }
  }

  // Bar or Stacked
  return {
    labels,
    datasets: [
      {
        label: 'Burn (expenses)',
        data: burnArr,
        backgroundColor: '#ef4444',
        borderRadius: 4,
        stack: 'stack1',
      },
      {
        label: 'MRR (income)',
        data: mrrArr,
        backgroundColor: '#10b981',
        borderRadius: 4,
        stack: 'stack2',
      },
    ],
  }
})

const chartOptions = computed(() => {
  const stacked = chartMode.value === 'stacked'
  return {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: {
        position: 'top',
        labels: { color: '#cbd5e1', font: { size: 12 } },
      },
      tooltip: {
        backgroundColor: '#0f172a',
        titleColor: '#f1f5f9',
        bodyColor: '#cbd5e1',
        borderColor: '#334155',
        borderWidth: 1,
        callbacks: {
          label: (ctx) => `${ctx.dataset.label}: ${fmtEur(ctx.parsed.y)}`,
        },
      },
    },
    scales: {
      x: {
        stacked,
        ticks: { color: '#94a3b8', maxRotation: 45, minRotation: 45 },
        grid:  { color: 'rgba(148, 163, 184, 0.08)' },
      },
      y: {
        stacked,
        ticks: {
          color: '#94a3b8',
          callback: (v) => fmtEur(v),
        },
        grid:  { color: 'rgba(148, 163, 184, 0.08)' },
      },
    },
  }
})

/* ----------------------------------------------------------------
   Project breakdown (income vs expense per project, plus OpEx bucket)
   ---------------------------------------------------------------- */
const OPEX_KEY = '__opex__'

const projectBreakdown = computed(() => {
  if (!hasData.value) return []
  const map = new Map()
  for (const e of forecast.value.entries) {
    const isOpex = e.isOpex === true || !e.projectId
    const key = isOpex ? OPEX_KEY : e.projectId
    if (!map.has(key)) {
      map.set(key, {
        key,
        name: isOpex ? 'OpEx (General Company)' : (e.projectName || 'Unnamed Project'),
        status: isOpex ? null : (e.projectStatus || null),
        color: isOpex ? '#f59e0b' : (e.projectColor || '#3b82f6'),
        income: 0,
        expense: 0,
        entries: 0,
        isOpex,
      })
    }
    const row = map.get(key)
    const amt = Number(e.amount) || 0
    if ((e.type || '').toLowerCase() === 'income') row.income += amt
    else row.expense += amt
    row.entries += 1
  }
  return Array.from(map.values())
    .map(r => ({ ...r, net: r.income - r.expense }))
    .sort((a, b) => b.expense - a.expense)
})

/* ----------------------------------------------------------------
   Detail entries table (with filters)
   ---------------------------------------------------------------- */
const entryFilter = ref('all')  // 'all' | 'income' | 'expense'
const projectFilter = ref('all')  // 'all' | project id | '__opex__'

const filteredEntries = computed(() => {
  if (!hasData.value) return []
  let list = forecast.value.entries
  if (entryFilter.value === 'income')  list = list.filter(e => (e.type || '').toLowerCase() === 'income')
  if (entryFilter.value === 'expense') list = list.filter(e => (e.type || '').toLowerCase() !== 'income')
  if (projectFilter.value === OPEX_KEY) list = list.filter(e => e.isOpex || !e.projectId)
  else if (projectFilter.value !== 'all') list = list.filter(e => e.projectId === projectFilter.value)
  return list
})

const showAllEntries = ref(false)
const visibleEntries = computed(() => {
  const list = filteredEntries.value
  return showAllEntries.value ? list : list.slice(0, 50)
})

/* ----------------------------------------------------------------
   Lifecycle
   ---------------------------------------------------------------- */
onMounted(async () => {
  await loadEntities()
  // Ensure the saved entityKey is actually in the entity list; else fallback
  if (!entities.value.find(e => e.key === entityKey.value)) {
    entityKey.value = entities.value[0]?.key || 'next2megroup'
  }
  await loadForecast()
})
</script>

<template>
  <div class="forecast">
    <!-- Header bar -->
    <header class="forecast__header">
      <div class="forecast__title-group">
        <h1 class="forecast__title">
          <span class="forecast__icon">&#x1F48E;</span>
          Investor Reports
        </h1>
        <p class="forecast__subtitle">
          <em>Forecast Engine &middot; Cash Planning &middot; Runway Analysis</em>
        </p>
      </div>

      <div class="forecast__controls">
        <div class="forecast__control">
          <label>Entity</label>
          <select v-model="entityKey">
            <option v-for="e in entities" :key="e.key" :value="e.key">
              {{ e.label }}
            </option>
          </select>
        </div>

        <div class="forecast__control">
          <label>Horizon</label>
          <select v-model.number="horizonMonths">
            <option v-for="h in HORIZON_OPTIONS" :key="h.value" :value="h.value">
              {{ h.label }}
            </option>
          </select>
        </div>

        <button class="forecast__btn" @click="loadForecast" :disabled="loading">
          <span v-if="loading">Loading...</span>
          <span v-else>Refresh</span>
        </button>
      </div>
    </header>

    <!-- Loading state -->
    <div v-if="loading" class="forecast__loading">
      <div class="forecast__spinner"></div>
      <p>Loading forecast data...</p>
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="forecast__error">
      <div class="forecast__error-icon">&#x26A0;</div>
      <h3>Could not load forecast</h3>
      <p>{{ error }}</p>
      <button class="forecast__btn" @click="loadForecast">Try again</button>
    </div>

    <!-- Empty state -->
    <div v-else-if="!hasData" class="forecast__empty">
      <h3>No forecast data</h3>
      <p>
        No PLANNED recurring transactions or LIVE projects with expected revenue
        for the selected entity. Add some via Recurring Manager or Projects to
        populate the forecast.
      </p>
    </div>

    <!-- Main content -->
    <div v-else class="forecast__body">
      <!-- KPI Cards -->
      <div class="kpi-grid">
        <div class="kpi-card kpi-card--income">
          <div class="kpi-card__label">TOTAL INCOME</div>
          <div class="kpi-card__sublabel"><em>(σύνολο αναμενόμενων εσόδων στο horizon)</em></div>
          <div class="kpi-card__value">{{ fmtEur(totalIncome) }}</div>
          <div class="kpi-card__hint">~ {{ fmtEur(monthlyMrr) }} / month (MRR)</div>
        </div>

        <div class="kpi-card kpi-card--expense">
          <div class="kpi-card__label">TOTAL EXPENSES</div>
          <div class="kpi-card__sublabel"><em>(σύνολο αναμενόμενων εξόδων στο horizon)</em></div>
          <div class="kpi-card__value">{{ fmtEur(totalExpenses) }}</div>
          <div class="kpi-card__hint">~ {{ fmtEur(monthlyBurn) }} / month (burn)</div>
        </div>

        <div class="kpi-card" :class="'kpi-card--' + netStatus.cls">
          <div class="kpi-card__label">NET CASH FLOW</div>
          <div class="kpi-card__sublabel"><em>(income - expenses)</em></div>
          <div class="kpi-card__value">{{ fmtEurSigned(netCashFlow) }}</div>
          <div class="kpi-card__hint">{{ netStatus.label }}</div>
        </div>

        <div class="kpi-card kpi-card--info">
          <div class="kpi-card__label">HORIZON</div>
          <div class="kpi-card__sublabel"><em>(διάστημα πρόβλεψης)</em></div>
          <div class="kpi-card__value">{{ months }} mo</div>
          <div class="kpi-card__hint">
            {{ forecast.entryCount }} entries &middot; {{ forecast.patternCount }} patterns
          </div>
        </div>
      </div>

      <!-- Chart section -->
      <section class="forecast__section">
        <div class="forecast__section-head">
          <h2>Burn vs MRR Trend</h2>
          <div class="chart-mode-toggle">
            <button
              v-for="mode in CHART_MODES"
              :key="mode"
              :class="{ active: chartMode === mode }"
              @click="chartMode = mode">
              {{ mode === 'bar' ? 'Bar' : mode === 'stacked' ? 'Stacked' : 'Line' }}
            </button>
          </div>
        </div>
        <div class="forecast__chart">
          <Bar  v-if="chartMode !== 'line'" :data="chartData" :options="chartOptions" />
          <Line v-else                       :data="chartData" :options="chartOptions" />
        </div>
      </section>

      <!-- Project breakdown -->
      <section class="forecast__section">
        <div class="forecast__section-head">
          <h2>Project Breakdown</h2>
          <span class="forecast__meta">{{ projectBreakdown.length }} groups</span>
        </div>
        <table class="forecast__table">
          <thead>
            <tr>
              <th></th>
              <th>Project / Type</th>
              <th class="text-right">Income</th>
              <th class="text-right">Expense</th>
              <th class="text-right">Net</th>
              <th class="text-right">Entries</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in projectBreakdown" :key="row.key">
              <td>
                <span class="dot" :style="{ background: row.color }"></span>
              </td>
              <td>
                {{ row.name }}
                <span v-if="row.status" class="status-pill">{{ row.status }}</span>
                <span v-else-if="row.isOpex" class="status-pill status-pill--opex">OPEX</span>
              </td>
              <td class="text-right income">{{ fmtEur(row.income) }}</td>
              <td class="text-right expense">{{ fmtEur(row.expense) }}</td>
              <td class="text-right" :class="row.net >= 0 ? 'income' : 'expense'">
                {{ fmtEurSigned(row.net) }}
              </td>
              <td class="text-right muted">{{ row.entries }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Detail entries table -->
      <section class="forecast__section">
        <div class="forecast__section-head">
          <h2>Forecast Entries</h2>
          <div class="forecast__filters">
            <select v-model="entryFilter">
              <option value="all">All types</option>
              <option value="income">Income only</option>
              <option value="expense">Expenses only</option>
            </select>
            <select v-model="projectFilter">
              <option value="all">All projects</option>
              <option :value="OPEX_KEY">OpEx only</option>
              <option
                v-for="row in projectBreakdown.filter(r => !r.isOpex)"
                :key="row.key"
                :value="row.key">
                {{ row.name }}
              </option>
            </select>
          </div>
        </div>

        <table class="forecast__table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Description</th>
              <th>Project</th>
              <th>Freq</th>
              <th class="text-right">Amount</th>
              <th class="text-right">Conf.</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(e, idx) in visibleEntries" :key="idx">
              <td>{{ fmtDate(e.date) }}</td>
              <td>{{ e.description || '-' }}</td>
              <td>
                <span v-if="e.projectName">{{ e.projectName }}</span>
                <span v-else class="muted">OpEx</span>
              </td>
              <td class="muted">{{ e.patternFrequency || '-' }}</td>
              <td
                class="text-right"
                :class="(e.type || '').toLowerCase() === 'income' ? 'income' : 'expense'">
                {{ (e.type || '').toLowerCase() === 'income' ? '+' : '-' }}{{ fmtEur(e.amount) }}
              </td>
              <td class="text-right muted">{{ e.confidencePct ?? 100 }}%</td>
            </tr>
          </tbody>
        </table>

        <div v-if="filteredEntries.length > 50" class="forecast__more">
          <span>Showing {{ visibleEntries.length }} of {{ filteredEntries.length }}</span>
          <button class="forecast__btn forecast__btn--secondary" @click="showAllEntries = !showAllEntries">
            {{ showAllEntries ? 'Show less' : 'Show all' }}
          </button>
        </div>
      </section>

      <!-- Footer hint -->
      <p class="forecast__footnote">
        <em>
          Forecast combines PLANNED recurring transactions (expenses) with expected
          monthly revenue from LIVE projects (income). ACTUAL transactions are
          excluded. Numbers in EUR; multi-currency handled by backend fx_rate_to_eur.
        </em>
      </p>
    </div>
  </div>
</template>

<style scoped>
.forecast {
  padding: 24px 32px 48px;
  color: #e2e8f0;
  min-height: calc(100vh - 80px);
}

/* Header */
.forecast__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}
.forecast__title-group { flex: 1; min-width: 280px; }
.forecast__title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 600;
  color: #f1f5f9;
  display: flex;
  align-items: center;
  gap: 10px;
}
.forecast__icon { font-size: 28px; }
.forecast__subtitle {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
}

.forecast__controls {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
}
.forecast__control { display: flex; flex-direction: column; gap: 4px; }
.forecast__control label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #94a3b8;
}
.forecast__control select,
.forecast__filters select {
  background: #1e293b;
  color: #e2e8f0;
  border: 1px solid #334155;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  min-width: 140px;
  cursor: pointer;
}
.forecast__control select:focus,
.forecast__filters select:focus {
  outline: none;
  border-color: #3b82f6;
}

.forecast__btn {
  background: #3b82f6;
  color: white;
  border: none;
  padding: 9px 18px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.forecast__btn:hover { background: #2563eb; }
.forecast__btn:disabled { background: #475569; cursor: not-allowed; }
.forecast__btn--secondary {
  background: transparent;
  border: 1px solid #334155;
  color: #cbd5e1;
}
.forecast__btn--secondary:hover { background: #1e293b; }

/* States */
.forecast__loading,
.forecast__error,
.forecast__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 12px;
  text-align: center;
}
.forecast__error-icon { font-size: 40px; margin-bottom: 12px; }
.forecast__error h3,
.forecast__empty h3 { margin: 0 0 12px; color: #f1f5f9; }
.forecast__error p,
.forecast__empty p,
.forecast__loading p { color: #94a3b8; margin: 0 0 16px; max-width: 480px; }
.forecast__spinner {
  width: 32px; height: 32px;
  border: 3px solid #334155;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 12px;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* KPI Grid */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.kpi-card {
  background: #1e293b;
  border: 1px solid #334155;
  border-left: 4px solid #475569;
  border-radius: 10px;
  padding: 18px 20px;
}
.kpi-card--income   { border-left-color: #10b981; }
.kpi-card--expense  { border-left-color: #ef4444; }
.kpi-card--positive { border-left-color: #10b981; }
.kpi-card--negative { border-left-color: #ef4444; }
.kpi-card--neutral  { border-left-color: #f59e0b; }
.kpi-card--info     { border-left-color: #3b82f6; }

.kpi-card__label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  font-weight: 600;
  color: #cbd5e1;
}
.kpi-card__sublabel {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  margin-bottom: 12px;
}
.kpi-card__value {
  font-size: 26px;
  font-weight: 600;
  color: #f1f5f9;
  font-variant-numeric: tabular-nums;
}
.kpi-card__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
}

/* Section */
.forecast__section {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
}
.forecast__section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.forecast__section-head h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #f1f5f9;
}
.forecast__meta { color: #94a3b8; font-size: 12px; }

/* Chart */
.forecast__chart { height: 320px; position: relative; }

.chart-mode-toggle { display: flex; gap: 4px; }
.chart-mode-toggle button {
  background: #0f172a;
  border: 1px solid #334155;
  color: #94a3b8;
  padding: 6px 14px;
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s;
}
.chart-mode-toggle button:hover { color: #e2e8f0; }
.chart-mode-toggle button.active {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
}

/* Table */
.forecast__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.forecast__table thead th {
  text-align: left;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  padding: 8px 12px;
  border-bottom: 1px solid #334155;
}
.forecast__table tbody td {
  padding: 8px 12px;
  border-bottom: 1px solid #1e293b;
}
.forecast__table tbody tr:hover { background: #0f172a; }
.text-right { text-align: right; }
.muted { color: #64748b; }
.income { color: #10b981; }
.expense { color: #ef4444; }

.dot {
  display: inline-block;
  width: 10px; height: 10px;
  border-radius: 50%;
}
.status-pill {
  display: inline-block;
  margin-left: 8px;
  padding: 2px 8px;
  background: #134e4a;
  color: #6ee7b7;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
}
.status-pill--opex {
  background: #78350f;
  color: #fcd34d;
}

.forecast__filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.forecast__more {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  color: #94a3b8;
  font-size: 12px;
}

.forecast__footnote {
  text-align: center;
  font-size: 12px;
  color: #64748b;
  margin: 12px 0 0;
}
</style>
