<script setup>
// PolarisDashboardView — "P&L Dashboard" page of the Polaris P&L module.
//
// Phase 2 (Oct 2026): 1:1 port of the #pageDashboard block + row-detail modal
// of docs/polaris-mockup-reference.html. Phase 3: KPIs from the real Αστρολάβος
// actuals (GET /api/polaris/forecast/actuals) -- visible to every Polaris role.
import { ref, computed, nextTick, onMounted, onBeforeUnmount, onDeactivated } from 'vue'
import { Chart, registerables } from 'chart.js'
import PolarisStatus from '@/components/PolarisStatus.vue'
import { usePolarisPlStore, fmtUSD, groupSum3, rowEnd, EXPENSE_KEYS } from '@/stores/polarisPl'
import '@/styles/polaris-pl.css'

Chart.register(...registerables)

const pl = usePolarisPlStore()
const dates = computed(() => pl.periodDates)

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
onBeforeUnmount(() => { document.removeEventListener('keydown', onKeydown); closeRowDetail() })
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
