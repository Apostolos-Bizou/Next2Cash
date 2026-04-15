<script setup>
import { computed } from 'vue'
import { Bar, Pie } from 'vue-chartjs'
import {
  Chart as ChartJS,
  Title,
  Tooltip,
  Legend,
  BarElement,
  CategoryScale,
  LinearScale,
  ArcElement
} from 'chart.js'

ChartJS.register(
  Title,
  Tooltip,
  Legend,
  BarElement,
  CategoryScale,
  LinearScale,
  ArcElement
)

/* ---------- Theme helpers ---------- */
const THEME = {
  navy: '#162B40',
  navySoft: '#263f5e',
  teal: '#4FC3A1',
  green: '#2FB57D',
  red: '#E5484D',
  orange: '#F5A623',
  blue: '#3B82F6',
  purple: '#8B5CF6',
  gridLine: 'rgba(22, 43, 64, 0.06)'
}

/* ---------- Dummy data ---------- */
// Numbers are in euros. Using a current date of mid-April 2026.
const kpis = [
  {
    key: 'income',
    label: 'Συνολικά Έσοδα',
    value: 184_250,
    delta: 12.4,
    tone: 'green',
    icon: 'arrow-up'
  },
  {
    key: 'expenses',
    label: 'Συνολικά Έξοδα',
    value: 126_480,
    delta: 8.1,
    tone: 'red',
    icon: 'arrow-down'
  },
  {
    key: 'net',
    label: 'Καθαρή Ταμειακή Ροή',
    value: 57_770,
    delta: 21.7,
    tone: 'blue',
    icon: 'trend'
  },
  {
    key: 'pending',
    label: 'Εκκρεμείς Πληρωμές',
    value: 32_900,
    delta: -4.3,
    tone: 'orange',
    icon: 'clock'
  }
]

/* ---------- Bar chart: last 6 months, income vs expenses ---------- */
const barData = {
  labels: ['Νοέ', 'Δεκ', 'Ιαν', 'Φεβ', 'Μάρ', 'Απρ'],
  datasets: [
    {
      label: 'Έσοδα',
      data: [142_300, 168_900, 151_200, 173_400, 179_800, 184_250],
      backgroundColor: THEME.teal,
      borderRadius: 6,
      borderSkipped: false,
      maxBarThickness: 28
    },
    {
      label: 'Έξοδα',
      data: [112_100, 124_600, 118_700, 131_200, 129_800, 126_480],
      backgroundColor: THEME.red,
      borderRadius: 6,
      borderSkipped: false,
      maxBarThickness: 28
    }
  ]
}

const barOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: 'bottom',
      labels: {
        color: THEME.navy,
        usePointStyle: true,
        pointStyle: 'rectRounded',
        padding: 16,
        font: { size: 12, weight: '500' }
      }
    },
    tooltip: {
      backgroundColor: THEME.navy,
      titleColor: '#fff',
      bodyColor: '#fff',
      padding: 10,
      cornerRadius: 6,
      callbacks: {
        label: (ctx) => `${ctx.dataset.label}: €${ctx.parsed.y.toLocaleString('el-GR')}`
      }
    }
  },
  scales: {
    x: {
      grid: { display: false },
      ticks: { color: '#6B7A8C', font: { size: 12 } }
    },
    y: {
      beginAtZero: true,
      grid: { color: THEME.gridLine, drawBorder: false },
      ticks: {
        color: '#6B7A8C',
        font: { size: 11 },
        callback: (v) => `€${(v / 1000).toFixed(0)}k`
      }
    }
  }
}

/* ---------- Pie chart: category breakdown ---------- */
const pieData = {
  labels: ['Μισθοδοσία', 'Προμηθευτές', 'Λειτουργικά', 'Φόροι', 'Λοιπά'],
  datasets: [
    {
      data: [42_300, 38_900, 22_100, 15_480, 7_700],
      backgroundColor: [
        THEME.teal,
        THEME.blue,
        THEME.orange,
        THEME.purple,
        THEME.navySoft
      ],
      borderColor: '#fff',
      borderWidth: 2,
      hoverOffset: 6
    }
  ]
}

const pieOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: 'right',
      labels: {
        color: THEME.navy,
        usePointStyle: true,
        pointStyle: 'circle',
        padding: 12,
        font: { size: 12 }
      }
    },
    tooltip: {
      backgroundColor: THEME.navy,
      titleColor: '#fff',
      bodyColor: '#fff',
      padding: 10,
      cornerRadius: 6,
      callbacks: {
        label: (ctx) => `${ctx.label}: €${ctx.parsed.toLocaleString('el-GR')}`
      }
    }
  }
}

/* ---------- Recent transactions ---------- */
const transactions = [
  {
    date: '2026-04-14',
    description: 'Τιμολόγιο #2026-0412 — ALPHA Retail',
    category: 'Πωλήσεις',
    amount: 18_450,
    type: 'income',
    status: 'Πληρώθηκε'
  },
  {
    date: '2026-04-13',
    description: 'Μισθοδοσία Απριλίου (Α΄ δόση)',
    category: 'Μισθοδοσία',
    amount: -22_800,
    type: 'expense',
    status: 'Πληρώθηκε'
  },
  {
    date: '2026-04-12',
    description: 'Προμηθευτής — BetaSoft Υπηρεσίες',
    category: 'Προμηθευτές',
    amount: -4_920,
    type: 'expense',
    status: 'Εκκρεμεί'
  },
  {
    date: '2026-04-11',
    description: 'Εισπράξεις POS — Κατάστημα Αθηνών',
    category: 'Πωλήσεις',
    amount: 6_310,
    type: 'income',
    status: 'Πληρώθηκε'
  },
  {
    date: '2026-04-10',
    description: 'ΦΠΑ Μαρτίου 2026',
    category: 'Φόροι',
    amount: -9_150,
    type: 'expense',
    status: 'Εκκρεμεί'
  },
  {
    date: '2026-04-09',
    description: 'Τιμολόγιο #2026-0401 — Gamma Logistics',
    category: 'Πωλήσεις',
    amount: 12_780,
    type: 'income',
    status: 'Πληρώθηκε'
  }
]

/* ---------- Formatters ---------- */
const euroFmt = new Intl.NumberFormat('el-GR', {
  style: 'currency',
  currency: 'EUR',
  maximumFractionDigits: 0
})

const euroFmtSigned = new Intl.NumberFormat('el-GR', {
  style: 'currency',
  currency: 'EUR',
  maximumFractionDigits: 0,
  signDisplay: 'always'
})

const dateFmt = new Intl.DateTimeFormat('el-GR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric'
})

function formatEuro(n) {
  return euroFmt.format(n)
}

function formatSignedEuro(n) {
  return euroFmtSigned.format(n)
}

function formatDate(iso) {
  return dateFmt.format(new Date(iso))
}

/* ---------- Header ---------- */
const headerSubtitle = computed(() => {
  const now = new Date('2026-04-15T00:00:00')
  return `Ενημέρωση: ${dateFmt.format(now)}`
})
</script>

<template>
  <section class="dashboard">
    <header class="dashboard__header">
      <div>
        <h2 class="dashboard__title">Πίνακας Ελέγχου</h2>
        <p class="dashboard__subtitle">{{ headerSubtitle }}</p>
      </div>
      <div class="dashboard__period">
        <span class="period-chip">Απρίλιος 2026</span>
      </div>
    </header>

    <!-- ================= KPI CARDS ================= -->
    <div class="kpi-grid">
      <article
        v-for="k in kpis"
        :key="k.key"
        class="kpi-card"
        :class="`kpi-card--${k.tone}`"
      >
        <div class="kpi-card__head">
          <span class="kpi-card__label">{{ k.label }}</span>
          <span class="kpi-card__icon" :class="`kpi-card__icon--${k.tone}`">
            <!-- arrow up -->
            <svg v-if="k.icon === 'arrow-up'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="19" x2="12" y2="5" />
              <polyline points="5 12 12 5 19 12" />
            </svg>
            <!-- arrow down -->
            <svg v-else-if="k.icon === 'arrow-down'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="5" x2="12" y2="19" />
              <polyline points="19 12 12 19 5 12" />
            </svg>
            <!-- trend -->
            <svg v-else-if="k.icon === 'trend'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="3 17 9 11 13 15 21 7" />
              <polyline points="15 7 21 7 21 13" />
            </svg>
            <!-- clock -->
            <svg v-else-if="k.icon === 'clock'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9" />
              <polyline points="12 7 12 12 15 14" />
            </svg>
          </span>
        </div>
        <div class="kpi-card__value">{{ formatEuro(k.value) }}</div>
        <div class="kpi-card__delta" :class="k.delta >= 0 ? 'is-up' : 'is-down'">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline v-if="k.delta >= 0" points="18 15 12 9 6 15" />
            <polyline v-else points="6 9 12 15 18 9" />
          </svg>
          <span>{{ Math.abs(k.delta).toFixed(1) }}%</span>
          <span class="kpi-card__delta-note">vs προηγούμενο μήνα</span>
        </div>
      </article>
    </div>

    <!-- ================= CHARTS ROW ================= -->
    <div class="charts-row">
      <article class="panel panel--bar">
        <header class="panel__head">
          <div>
            <h3 class="panel__title">Μηνιαία Ταμειακή Ροή</h3>
            <p class="panel__subtitle">Τελευταίοι 6 μήνες · Έσοδα vs Έξοδα</p>
          </div>
        </header>
        <div class="panel__chart">
          <Bar :data="barData" :options="barOptions" />
        </div>
      </article>

      <article class="panel panel--pie">
        <header class="panel__head">
          <div>
            <h3 class="panel__title">Κατανομή Κατηγοριών</h3>
            <p class="panel__subtitle">Έξοδα τρέχοντος μήνα</p>
          </div>
        </header>
        <div class="panel__chart panel__chart--pie">
          <Pie :data="pieData" :options="pieOptions" />
        </div>
      </article>
    </div>

    <!-- ================= TRANSACTIONS TABLE ================= -->
    <article class="panel panel--table">
      <header class="panel__head">
        <div>
          <h3 class="panel__title">Πρόσφατες Συναλλαγές</h3>
          <p class="panel__subtitle">Οι τελευταίες {{ transactions.length }} κινήσεις</p>
        </div>
        <button class="btn-link" type="button">Προβολή όλων →</button>
      </header>

      <div class="table-wrap">
        <table class="tx-table">
          <thead>
            <tr>
              <th>Ημερομηνία</th>
              <th>Περιγραφή</th>
              <th>Κατηγορία</th>
              <th class="num">Ποσό</th>
              <th>Κατάσταση</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(tx, i) in transactions" :key="i">
              <td class="tx-date">{{ formatDate(tx.date) }}</td>
              <td class="tx-desc">{{ tx.description }}</td>
              <td>
                <span class="category-pill">{{ tx.category }}</span>
              </td>
              <td class="num amount" :class="tx.type === 'income' ? 'amount--in' : 'amount--out'">
                {{ formatSignedEuro(tx.amount) }}
              </td>
              <td>
                <span
                  class="status-badge"
                  :class="tx.status === 'Πληρώθηκε' ? 'status-badge--paid' : 'status-badge--pending'"
                >
                  {{ tx.status }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ---------- Header ---------- */
.dashboard__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 4px;
}

.dashboard__title {
  font-size: 1.5rem;
  margin: 0;
  color: var(--navy-800);
}

.dashboard__subtitle {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.period-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--navy-800);
  color: var(--text-primary);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

/* ---------- KPI cards ---------- */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi-card {
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  position: relative;
  overflow: hidden;
  transition: transform 120ms ease, box-shadow 120ms ease;
}

.kpi-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(22, 43, 64, 0.06);
}

.kpi-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--stripe, var(--navy-800));
}

.kpi-card--green  { --stripe: #2FB57D; }
.kpi-card--red    { --stripe: #E5484D; }
.kpi-card--blue   { --stripe: #3B82F6; }
.kpi-card--orange { --stripe: #F5A623; }

.kpi-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.kpi-card__label {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text-muted);
  font-weight: 600;
}

.kpi-card__icon {
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
}

.kpi-card__icon svg {
  width: 18px;
  height: 18px;
}

.kpi-card__icon--green  { background: rgba(47, 181, 125, 0.12); color: #2FB57D; }
.kpi-card__icon--red    { background: rgba(229, 72, 77, 0.12);  color: #E5484D; }
.kpi-card__icon--blue   { background: rgba(59, 130, 246, 0.12); color: #3B82F6; }
.kpi-card__icon--orange { background: rgba(245, 166, 35, 0.14); color: #F5A623; }

.kpi-card__value {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--navy-800);
  letter-spacing: -0.01em;
}

.kpi-card__delta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
}

.kpi-card__delta svg {
  width: 14px;
  height: 14px;
}

.kpi-card__delta.is-up   { color: #2FB57D; }
.kpi-card__delta.is-down { color: #E5484D; }

.kpi-card__delta-note {
  color: var(--text-muted);
  font-weight: 400;
  margin-left: 4px;
}

/* ---------- Panels (charts + table) ---------- */
.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.panel__title {
  font-size: 15px;
  margin: 0;
  color: var(--navy-800);
  font-weight: 600;
}

.panel__subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}

.panel__chart {
  position: relative;
  height: 300px;
}

.panel__chart--pie {
  height: 280px;
}

.btn-link {
  background: none;
  border: none;
  color: var(--accent);
  font-weight: 600;
  font-size: 13px;
  padding: 6px 8px;
  border-radius: 6px;
  transition: background-color 120ms ease;
}

.btn-link:hover {
  background: rgba(79, 195, 161, 0.1);
}

/* ---------- Charts row ---------- */
.charts-row {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 16px;
}

/* ---------- Transactions table ---------- */
.table-wrap {
  overflow-x: auto;
  margin: 0 -8px;
}

.tx-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.tx-table thead th {
  text-align: left;
  padding: 10px 12px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-muted);
  font-weight: 600;
  border-bottom: 1px solid var(--border-subtle);
  background: #F7F9FC;
}

.tx-table thead th.num {
  text-align: right;
}

.tx-table tbody td {
  padding: 12px;
  border-bottom: 1px solid var(--border-subtle);
  color: #1a2332;
  vertical-align: middle;
}

.tx-table tbody tr:last-child td {
  border-bottom: none;
}

.tx-table tbody tr:hover {
  background: #FAFBFD;
}

.tx-date {
  color: var(--text-muted);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.tx-desc {
  font-weight: 500;
  color: var(--navy-800);
}

.num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.amount {
  font-weight: 600;
}

.amount--in  { color: #2FB57D; }
.amount--out { color: #E5484D; }

.category-pill {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(22, 43, 64, 0.06);
  color: var(--navy-800);
  font-size: 11.5px;
  font-weight: 500;
  white-space: nowrap;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
}

.status-badge::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-badge--paid {
  background: rgba(47, 181, 125, 0.14);
  color: #2FB57D;
}

.status-badge--pending {
  background: rgba(245, 166, 35, 0.16);
  color: #B97A10;
}

/* ---------- Responsive ---------- */
@media (max-width: 1100px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  .charts-row { grid-template-columns: 1fr; }
  .panel__chart--pie { height: 260px; }
}

@media (max-width: 600px) {
  .kpi-grid { grid-template-columns: 1fr; }
  .dashboard__header { flex-direction: column; align-items: flex-start; }
}
</style>
