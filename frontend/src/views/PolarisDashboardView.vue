<script setup>
// PolarisDashboardView — "P&L Dashboard" page of the Polaris P&L module.
//
// Phase 2 (Oct 2026): 1:1 port of the #pageDashboard block + row-detail modal
// of docs/polaris-mockup-reference.html. Phase 3: KPIs from the real Αστρολάβος
// actuals (GET /api/polaris/forecast/actuals) -- visible to every Polaris role.
// Phase 4: read-only "Προβλέψεις Κερδοφορίας" block (hero, KPI tiles, chart)
// from GET /api/polaris/forecast/summary -- computed server-side, so this page
// never fetches payroll people or assumptions (a viewer may render it).
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount, onDeactivated } from 'vue'
import { Chart, registerables } from 'chart.js'
import PolarisStatus from '@/components/PolarisStatus.vue'
import { usePolarisPlStore, fmtUSD, fmtUSD2, fmtPct, fmtInt, groupSum3, rowEnd, EXPENSE_KEYS } from '@/stores/polarisPl'
import { fetchForecastSummary } from '@/api/polaris'
import '@/styles/polaris-pl.css'

Chart.register(...registerables)

const pl = usePolarisPlStore()
const dates = computed(() => pl.periodDates)

// ---------------- forecast summary block (server-side aggregates) ----------------
const fcYears = ref(1)              // 1 | 3 | 5
const fc = ref(null)                // ForecastSummary from the API
const fcLoading = ref(false)
const fcError = ref(null)
const fcCanvas = ref(null)
let fcChart = null

async function loadForecast() {
  if (!pl.hasAccess) { fc.value = null; return }
  fcLoading.value = true
  fcError.value = null
  try {
    fc.value = await fetchForecastSummary(fcYears.value)
  } catch (e) {
    fc.value = null
    fcError.value = e?.response?.status === 403 ? 'Δεν έχεις πρόσβαση στις προβλέψεις.' : 'Δεν ήταν δυνατή η φόρτωση των προβλέψεων.'
  } finally {
    fcLoading.value = false
  }
}
function setFcYears(y) { fcYears.value = y }

// reload when the store finishes (re)loading for this role/entity, and on every horizon change
watch([() => pl.loaded, () => pl.hasAccess, fcYears], loadForecast, { immediate: true })

// same thresholds / colors as PolarisForecastView (mockup)
const fcEbitdaCls = computed(() => {
  const s = fc.value
  if (!s) return ''
  return s.ebitda < 0 ? 'danger' : (s.ebitdaMarginPct < 3 ? 'warn' : 'ok')
})
const fcEbitdaBarStyle = computed(() => {
  const s = fc.value
  if (!s) return {}
  return {
    width: Math.max(2, Math.min(100, (s.ebitdaMarginPct / 15 * 100))) + '%',
    background: s.ebitda < 0 ? 'var(--danger)' : (s.ebitdaMarginPct < 3 ? 'var(--warning)' : 'var(--success)')
  }
})
const fcBreakevenText = computed(() => {
  const d = fc.value?.breakEvenDistance
  return (d != null && isFinite(d) ? (d >= 0 ? '+' : '') + fmtInt(d) : '—') + ' μέλη'
})
const fcBreakevenCls = computed(() => {
  const d = fc.value?.breakEvenDistance
  if (d == null) return ''
  return d < 0 ? 'danger' : (d < 500 ? 'warn' : 'ok')
})
const fcBeBarStyle = computed(() => {
  const s = fc.value
  if (!s) return {}
  const d = s.breakEvenDistance ?? 0
  const m = s.members?.horizon || 0
  const beSafety = m ? Math.max(2, Math.min(100, (d / m * 100 + 50))) : 2
  return { width: beSafety + '%', background: d < 0 ? 'var(--danger)' : 'var(--gold)' }
})

function drawForecastChart() {
  const s = fc.value, ctx = fcCanvas.value
  if (!s || !ctx) return
  const txt = getComputedStyle(ctx).getPropertyValue('--text-secondary').trim() || '#8da2b8'
  const grid = 'rgba(255,255,255,.06)'
  const ebitdaSeries = s.series.map(p => p.ebitda)
  if (fcChart) fcChart.destroy()
  fcChart = new Chart(ctx, {
    data: {
      labels: s.series.map(p => p.label),
      datasets: [
        {
          type: 'bar', label: 'EBITDA ($)', data: ebitdaSeries, yAxisID: 'y',
          backgroundColor: ebitdaSeries.map(v => v < 0 ? 'rgba(239,68,68,.55)' : 'rgba(46,117,182,.55)'),
          borderRadius: 6, maxBarThickness: 46
        },
        {
          type: 'line', label: 'Μέλη', data: s.series.map(p => p.members), yAxisID: 'y1',
          borderColor: '#f5b43e', backgroundColor: 'rgba(245,180,62,.15)',
          tension: .3, pointRadius: 4, pointBackgroundColor: '#f5b43e', fill: false
        }
      ]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { labels: { color: txt, font: { family: 'Montserrat', size: 11.5 } } },
        tooltip: {
          backgroundColor: '#1E3650', borderColor: '#2a4563', borderWidth: 1, titleColor: '#e8ecf4', bodyColor: '#e8ecf4',
          callbacks: {
            label: (c) => c.dataset.yAxisID === 'y1'
              ? `Μέλη: ${Math.round(c.raw).toLocaleString('el-GR')}`
              : `EBITDA: $${Math.round(c.raw).toLocaleString('el-GR')}`
          }
        }
      },
      scales: {
        x: { ticks: { color: txt, font: { family: 'Montserrat', size: 11.5 } }, grid: { color: grid } },
        y: { position: 'left', ticks: { color: txt, callback: v => '$' + Math.round(v / 1000) + 'k' }, grid: { color: grid } },
        y1: { position: 'right', ticks: { color: txt }, grid: { display: false } }
      }
    }
  })
}
// the canvas appears only once data is loaded, so redraw when either changes
watch([fc, fcCanvas], () => nextTick(drawForecastChart))

// revenue card first, then the expense groups — same order as the mockup
const kpiGroups = computed(() => [
  { key: 'revenue', group: pl.actualsData.revenue },
  ...EXPENSE_KEYS.map(k => ({ key: k, group: pl.actualsData[k] }))
])

function groupChangeCls(group) {
  const sum = groupSum3(group)
  return group.isRevenue ? (sum.change >= 0 ? 'ok' : 'danger') : (sum.change <= 0 ? 'ok' : 'danger')
}

// ---------------- row-detail modal (click any KPI row/total to see its chart) ----------------
const detail = ref(null)   // { title, code, begin, change, end }
const rdCanvas = ref(null)
let rdChartInstance = null

function openRowDetail(entry) {
  detail.value = entry
  document.body.style.overflow = 'hidden'
  nextTick(drawRowDetailChart)
}
function openGroup(group) {
  const sum = groupSum3(group)
  openRowDetail({ title: group.title, code: group.code, begin: sum.begin, change: sum.change, end: sum.end })
}
function openRow(group, row) {
  openRowDetail({ title: row.label, code: `${group.title} · ${group.code}`, begin: row.begin, change: row.change, end: rowEnd(row) })
}

function drawRowDetailChart() {
  const entry = detail.value
  if (!entry || !rdCanvas.value) return
  const d = dates.value
  const txt = getComputedStyle(rdCanvas.value).getPropertyValue('--text-secondary').trim() || '#8da2b8'
  const grid = 'rgba(255,255,255,.06)'
  if (rdChartInstance) rdChartInstance.destroy()
  // waterfall layout: Αρχή is the base, Μεταβολή floats from Αρχή to Τέλος,
  // Τέλος is the final cumulative bar from zero.
  const changeRange = entry.change >= 0 ? [entry.begin, entry.end] : [entry.end, entry.begin]
  rdChartInstance = new Chart(rdCanvas.value, {
    type: 'bar',
    data: {
      labels: [`Αρχή (${d.start})`, `Μεταβολή (${d.start}–${d.end})`, `Τέλος (${d.end})`],
      datasets: [{
        label: entry.title,
        data: [[0, entry.begin], changeRange, [0, entry.end]],
        backgroundColor: [
          'rgba(46,117,182,.65)',
          entry.change < 0 ? 'rgba(239,68,68,.65)' : 'rgba(16,185,129,.65)',
          'rgba(245,180,62,.65)'
        ],
        borderRadius: 6, maxBarThickness: 70
      }]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#1E3650', borderColor: '#2a4563', borderWidth: 1, titleColor: '#e8ecf4', bodyColor: '#e8ecf4',
          callbacks: {
            label: (c) => {
              if (c.dataIndex === 1) return `Μεταβολή: ${fmtUSD(entry.change)}`
              return fmtUSD(c.dataIndex === 0 ? entry.begin : entry.end)
            }
          }
        }
      },
      scales: {
        x: { ticks: { color: txt, font: { family: 'Montserrat', size: 11 } }, grid: { display: false } },
        y: { ticks: { color: txt, callback: v => '$' + Math.round(v / 1000) + 'k' }, grid: { color: grid } }
      }
    }
  })
}

function closeRowDetail() {
  detail.value = null
  document.body.style.overflow = ''
  if (rdChartInstance) { rdChartInstance.destroy(); rdChartInstance = null }
}
function onKeydown(e) { if (e.key === 'Escape') closeRowDetail() }

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  closeRowDetail()
  if (fcChart) { fcChart.destroy(); fcChart = null }
})
onDeactivated(closeRowDetail) // KeepAlive: never leave the modal (and body overflow lock) behind
</script>

<template>
  <div class="polaris-pl">
    <PolarisStatus :allowed="pl.hasAccess">

    <div class="page-head">
      <div>
        <h1>P&amp;L Dashboard</h1>
        <div class="sub">Περίοδος {{ pl.actPeriod }} · ίδιες κατηγορίες με το Αστρολάβος</div>
      </div>
    </div>

    <div>
      <div class="dash-headline">
        <div class="dash-headline-card" @click="openRowDetail({ title: 'Σύνολο Εσόδων', code: 'κωδ. 91xxx', ...pl.revSum })">
          <div class="l">Σύνολο Εσόδων</div>
          <div class="dash-headline-vals">
            <div><span class="t">Αρχή<span class="period-date">{{ dates.start }}</span></span><span class="v tnum">{{ fmtUSD(pl.revSum.begin) }}</span></div>
            <div><span class="t">Μεταβολή<span class="period-date">{{ dates.start }}–{{ dates.end }}</span></span><span class="v tnum ok">{{ fmtUSD(pl.revSum.change) }}</span></div>
            <div><span class="t">Τέλος<span class="period-date">{{ dates.end }}</span></span><span class="v tnum">{{ fmtUSD(pl.revSum.end) }}</span></div>
          </div>
        </div>
        <div class="dash-headline-card expenses" @click="openRowDetail({ title: 'Σύνολο Εξόδων', code: 'κωδ. 92/96/97/99xxx', ...pl.expSum })">
          <div class="l">Σύνολο Εξόδων</div>
          <div class="dash-headline-vals">
            <div><span class="t">Αρχή<span class="period-date">{{ dates.start }}</span></span><span class="v tnum">{{ fmtUSD(pl.expSum.begin) }}</span></div>
            <div><span class="t">Μεταβολή<span class="period-date">{{ dates.start }}–{{ dates.end }}</span></span><span class="v tnum danger">{{ fmtUSD(pl.expSum.change) }}</span></div>
            <div><span class="t">Τέλος<span class="period-date">{{ dates.end }}</span></span><span class="v tnum">{{ fmtUSD(pl.expSum.end) }}</span></div>
          </div>
        </div>
        <div class="dash-headline-card net" @click="openRowDetail({ title: 'Καθαρό Αποτέλεσμα', code: 'Έσοδα − Έξοδα', ...pl.netSum })">
          <div class="l">Καθαρό Αποτέλεσμα</div>
          <div class="dash-headline-vals">
            <div><span class="t">Αρχή<span class="period-date">{{ dates.start }}</span></span><span class="v tnum" :class="pl.netSum.begin < 0 ? 'danger' : 'ok'">{{ fmtUSD(pl.netSum.begin) }}</span></div>
            <div><span class="t">Μεταβολή<span class="period-date">{{ dates.start }}–{{ dates.end }}</span></span><span class="v tnum" :class="pl.netSum.change < 0 ? 'danger' : 'ok'">{{ fmtUSD(pl.netSum.change) }}</span></div>
            <div><span class="t">Τέλος<span class="period-date">{{ dates.end }}</span></span><span class="v tnum" :class="pl.netSum.end < 0 ? 'danger' : 'ok'">{{ fmtUSD(pl.netSum.end) }}</span></div>
          </div>
        </div>
      </div>

      <div class="dash-kpi-grid">
        <div v-for="{ key, group } in kpiGroups" :key="key" class="dash-kpi-card">
          <div class="dash-kpi-head">
            <div class="dash-kpi-title">{{ group.title }}<span class="hint">{{ group.code }}</span></div>
          </div>
          <div class="dash-kpi-totals" @click="openGroup(group)">
            <div><span class="l">Αρχή περιόδου<span class="period-date">{{ dates.start }}</span></span><span class="v tnum">{{ fmtUSD(groupSum3(group).begin) }}</span></div>
            <div><span class="l">Μεταβολή περιόδου<span class="period-date">{{ dates.start }}–{{ dates.end }}</span></span><span class="v tnum" :class="groupChangeCls(group)">{{ fmtUSD(groupSum3(group).change) }}</span></div>
            <div><span class="l">Τέλος περιόδου<span class="period-date">{{ dates.end }}</span></span><span class="v tnum">{{ fmtUSD(groupSum3(group).end) }}</span></div>
          </div>
          <div class="dash-kpi-rows">
            <div v-for="(row, idx) in group.rows" :key="idx" class="dash-kpi-row" @click="openRow(group, row)">
              <span>{{ row.label }}</span>
              <span class="tnum">{{ fmtUSD(row.begin) }}</span>
              <span class="tnum">{{ fmtUSD(row.change) }}</span>
              <span class="tnum">{{ fmtUSD(rowEnd(row)) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========================= Προβλέψεις Κερδοφορίας (server-side summary, read-only) ========================= -->
    <div class="section fc-block">
      <div class="sec-head">
        <div class="sec-title"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>Προβλέψεις Κερδοφορίας</div>
        <div class="sec-note" v-if="fc">{{ fmtInt(fc.members.current) }} μέλη σήμερα · ορίζοντας {{ fc.horizonCalendarYear }} · μόνο προβολή</div>
        <div class="sec-actions">
          <div class="horizon-switch">
            <button v-for="y in [1, 3, 5]" :key="y" class="horizon-btn" :class="{ active: fcYears === y }" @click="setFcYears(y)">{{ y === 1 ? '1 έτος' : `${y} έτη` }}</button>
          </div>
        </div>
      </div>
      <div class="sec-body">
        <div v-if="fcError" class="locked-card">
          <div>{{ fcError }}</div>
          <button class="btn" @click="loadForecast">Δοκιμή ξανά</button>
        </div>
        <div v-else-if="!fc" class="locked-card"><div>Φόρτωση προβλέψεων…</div></div>
        <template v-else>
          <div class="kpi-hero" :class="{ dim: fcLoading }">
            <div class="kpi-hero-card ebitda">
              <div class="kpi-hero-lbl">EBITDA <span>{{ fc.years === 1 ? '(έτος 1)' : `(έτος ${fc.years})` }}</span></div>
              <div class="kpi-hero-val tnum" :class="fcEbitdaCls">{{ fmtUSD(fc.ebitda) }}</div>
              <div class="kpi-hero-sub">Περιθώριο EBITDA: <b class="tnum">{{ fmtPct(fc.ebitdaMarginPct) }}</b> · ανά μέλος <b class="tnum">{{ fmtUSD2(fc.ebitdaPerMember) }}</b></div>
              <div class="margin-bar"><div class="margin-bar__fill" :style="fcEbitdaBarStyle"></div></div>
            </div>
            <div class="kpi-hero-card breakeven">
              <div class="kpi-hero-lbl">Απόσταση από Break-even</div>
              <div class="kpi-hero-val tnum" :class="fcBreakevenCls">{{ fcBreakevenText }}</div>
              <div class="kpi-hero-sub">Break-even στα <b class="tnum">{{ fc.breakEvenMembers != null ? fmtInt(fc.breakEvenMembers) : '—' }}</b> μέλη · τρέχοντα <b class="tnum">{{ fmtInt(fc.members.horizon) }}</b></div>
              <div class="margin-bar"><div class="margin-bar__fill" :style="fcBeBarStyle"></div></div>
            </div>
          </div>

          <div class="kpi-grid" :class="{ dim: fcLoading }">
            <div class="kpi-card"><div class="kpi-lbl">Μέλη</div><div class="kpi-val tnum">{{ fmtInt(fc.members.horizon) }}</div><div class="kpi-sub">στο τέλος ορίζοντα</div></div>
            <div class="kpi-card"><div class="kpi-lbl">Έσοδα / μέλος</div><div class="kpi-val tnum">{{ fmtUSD(fc.revenuePerMember) }}</div><div class="kpi-sub">ετησίως</div></div>
            <div class="kpi-card"><div class="kpi-lbl">Contribution margin</div><div class="kpi-val tnum">{{ fmtPct(fc.contributionMarginPct) }}</div><div class="kpi-sub">μετά 3rd-party κόστη</div></div>
            <div class="kpi-card"><div class="kpi-lbl">Μισθοδοσία / Έσοδα</div><div class="kpi-val tnum">{{ fmtPct(fc.payrollToRevenuePct) }}</div><div class="kpi-sub">Αθήνα + Μανίλα</div></div>
            <div class="kpi-card"><div class="kpi-lbl">Κόστος 3rd-party / μέλος</div><div class="kpi-val tnum">{{ fmtUSD(fc.thirdPartyPerMember) }}</div><div class="kpi-sub">network+audit+dental payable</div></div>
          </div>

          <div class="sec-note" style="margin-bottom:8px">Πρόβλεψη EBITDA &amp; Μελών · ανά έτος, μέχρι τον επιλεγμένο ορίζοντα</div>
          <div class="chart-wrap"><canvas ref="fcCanvas"></canvas></div>
        </template>
      </div>
    </div>

    <!-- ========================= Row-detail modal ========================= -->
    <div class="rd-overlay" v-if="detail" @click.self="closeRowDetail">
      <div class="rd-modal">
        <div class="rd-head">
          <div>
            <div class="rd-title">{{ detail.title }}</div>
            <div class="rd-sub">{{ detail.code }} · περίοδος {{ dates.start }} – {{ dates.end }}</div>
          </div>
          <button class="rd-close" @click="closeRowDetail" aria-label="Κλείσιμο">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6 6 18M6 6l12 12"/></svg>
          </button>
        </div>
        <div class="rd-body">
          <div class="dash-kpi-totals" style="margin-bottom:16px">
            <div><span class="l">Αρχή περιόδου<span class="period-date">{{ dates.start }}</span></span><span class="v tnum">{{ fmtUSD(detail.begin) }}</span></div>
            <div><span class="l">Μεταβολή περιόδου<span class="period-date">{{ dates.start }}–{{ dates.end }}</span></span><span class="v tnum" :class="detail.change >= 0 ? 'ok' : 'danger'">{{ fmtUSD(detail.change) }}</span></div>
            <div><span class="l">Τέλος περιόδου<span class="period-date">{{ dates.end }}</span></span><span class="v tnum">{{ fmtUSD(detail.end) }}</span></div>
          </div>
          <div class="rd-chart-wrap"><canvas ref="rdCanvas"></canvas></div>
        </div>
      </div>
    </div>
    </PolarisStatus>
  </div>
</template>
