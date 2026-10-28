<script setup>
// PolarisForecastView — "Προβλέψεις" page of the Polaris P&L module.
//
// Phase 2 (Oct 2026): 1:1 port of the #pageForecast block of
// docs/polaris-mockup-reference.html: EBITDA / break-even hero, KPI grid,
// EBITDA & members chart, Παραδοχές (admin), Πρόβλεψη Μελών (admin) and
// Πραγματικά Στοιχεία Αστρολάβος (admin/editor). All math lives in the shared
// polarisPl store. Phase 3: data from /api/polaris/*, role from my-role --
// the forecast blocks need assumptions + milestones (admin-only endpoints),
// so an editor sees only the actuals section.
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { Chart, registerables } from 'chart.js'
import PolarisStatus from '@/components/PolarisStatus.vue'
import { usePolarisPlStore, fmtUSD, fmtUSD2, fmtPct, fmtInt, groupSum3, rowEnd } from '@/stores/polarisPl'
import '@/styles/polaris-pl.css'

Chart.register(...registerables)

const pl = usePolarisPlStore()
const dates = computed(() => pl.periodDates)
const isGrowth = computed(() => pl.memberForecastMode === 'growth')

// ---- hero KPIs (same thresholds/colors as the mockup) ----
const ebitdaCls = computed(() => {
  const c = pl.cur
  return c.ebitda < 0 ? 'danger' : (c.ebitda / c.revenue < 0.03 ? 'warn' : 'ok')
})
const ebitdaBarStyle = computed(() => ({
  width: Math.max(2, Math.min(100, (pl.ebitdaMargin / 15 * 100))) + '%',
  background: pl.cur.ebitda < 0 ? 'var(--danger)' : (pl.ebitdaMargin < 3 ? 'var(--warning)' : 'var(--success)')
}))
const breakevenText = computed(() => {
  const d = pl.breakEvenDistance
  return (isFinite(d) ? (d >= 0 ? '+' : '') + fmtInt(d) : '—') + ' μέλη'
})
const breakevenCls = computed(() => {
  const d = pl.breakEvenDistance
  return d < 0 ? 'danger' : (d < 500 ? 'warn' : 'ok')
})
const beBarStyle = computed(() => {
  const c = pl.cur, d = pl.breakEvenDistance
  const beSafety = c.members ? Math.max(2, Math.min(100, (d / c.members * 100 + 50))) : 2
  return { width: beSafety + '%', background: d < 0 ? 'var(--danger)' : 'var(--gold)' }
})

const chartGranNote = computed(() => pl.granularity === 'quarter'
  ? 'ανά τρίμηνο (Q1–Q4), μέχρι τον επιλεγμένο ορίζοντα'
  : 'ανά έτος, μέχρι τον επιλεγμένο ορίζοντα')

// ---- actuals totals (Αστρολάβος) ----
const actNet = computed(() => pl.revSum.end - pl.expSum.end)

// ---------------- chart (destroy + recreate on every change, like the mockup's recalc) ----------------
const chartCanvas = ref(null)
let chartInstance = null

const chartSeries = computed(() => {
  const points = pl.timePoints
  return {
    labels: points.map(p => p.label),
    ebitda: points.map(p => pl.pnlAtYear(p.y).ebitda),
    members: points.map(p => pl.pnlAtYear(p.y).members),
  }
})

function drawChart({ labels, ebitda: ebitdaSeries, members: membersSeries }) {
  const ctx = chartCanvas.value
  if (!ctx) return
  const txt = getComputedStyle(ctx).getPropertyValue('--text-secondary').trim() || '#8da2b8'
  const grid = 'rgba(255,255,255,.06)'
  if (chartInstance) chartInstance.destroy()
  chartInstance = new Chart(ctx, {
    data: {
      labels,
      datasets: [
        {
          type: 'bar', label: 'EBITDA ($)', data: ebitdaSeries, yAxisID: 'y',
          backgroundColor: ebitdaSeries.map(v => v < 0 ? 'rgba(239,68,68,.55)' : 'rgba(46,117,182,.55)'),
          borderRadius: 6, maxBarThickness: 46
        },
        {
          type: 'line', label: 'Μέλη', data: membersSeries, yAxisID: 'y1',
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

// the canvas only exists once the role is known to be admin, so redraw when it appears too
watch([chartSeries, chartCanvas], () => drawChart(chartSeries.value))
onBeforeUnmount(() => { if (chartInstance) { chartInstance.destroy(); chartInstance = null } })
</script>

<template>
  <div class="polaris-pl">
    <PolarisStatus :allowed="pl.hasAccess">

    <div class="page-head">
      <div>
        <h1>Προβλέψεις Κερδοφορίας</h1>
        <div class="sub" v-if="pl.isAdmin">{{ fmtInt(parseFloat(pl.inputs.members) || 0) }} μέλη · βάση Δεκ 2026 · network/monthly/audit/dental fees</div>
        <div class="sub" v-else>Πραγματικά στοιχεία Αστρολάβος{{ pl.canEdit ? '' : ' · μόνο προβολή' }}</div>
      </div>
      <div class="horizon-switch" v-if="pl.isAdmin">
        <button v-for="y in [1, 3, 5]" :key="y" class="horizon-btn" :class="{ active: pl.horizon === y }" @click="pl.setHorizon(y)">{{ y === 1 ? '1 έτος' : `${y} έτη` }}</button>
      </div>
    </div>

    <template v-if="pl.isAdmin">
    <div class="kpi-hero">
      <div class="kpi-hero-card ebitda">
        <div class="kpi-hero-lbl">EBITDA <span>{{ pl.horizon === 1 ? '(έτος 1)' : `(έτος ${pl.horizon})` }}</span></div>
        <div class="kpi-hero-val tnum" :class="ebitdaCls">{{ fmtUSD(pl.cur.ebitda) }}</div>
        <div class="kpi-hero-sub">Περιθώριο EBITDA: <b class="tnum">{{ fmtPct(pl.ebitdaMargin) }}</b> · ανά μέλος <b class="tnum">{{ fmtUSD2(pl.cur.members ? pl.cur.ebitda / pl.cur.members : 0) }}</b></div>
        <div class="margin-bar"><div class="margin-bar__fill" :style="ebitdaBarStyle"></div></div>
      </div>
      <div class="kpi-hero-card breakeven">
        <div class="kpi-hero-lbl">Απόσταση από Break-even</div>
        <div class="kpi-hero-val tnum" :class="breakevenCls">{{ breakevenText }}</div>
        <div class="kpi-hero-sub">Break-even στα <b class="tnum">{{ isFinite(pl.breakEvenMembers) ? fmtInt(pl.breakEvenMembers) : '—' }}</b> μέλη · τρέχοντα <b class="tnum">{{ fmtInt(pl.cur.members) }}</b></div>
        <div class="margin-bar"><div class="margin-bar__fill" :style="beBarStyle"></div></div>
      </div>
    </div>

    <div class="kpi-grid">
      <div class="kpi-card"><div class="kpi-lbl">Μέλη</div><div class="kpi-val tnum">{{ fmtInt(pl.cur.members) }}</div><div class="kpi-sub">στο τέλος ορίζοντα</div></div>
      <div class="kpi-card"><div class="kpi-lbl">Έσοδα / μέλος</div><div class="kpi-val tnum">{{ fmtUSD(pl.cur.members ? pl.cur.revenue / pl.cur.members : 0) }}</div><div class="kpi-sub">ετησίως</div></div>
      <div class="kpi-card"><div class="kpi-lbl">Contribution margin</div><div class="kpi-val tnum">{{ fmtPct(pl.cur.revenue ? pl.cur.contribution / pl.cur.revenue * 100 : 0) }}</div><div class="kpi-sub">μετά 3rd-party κόστη</div></div>
      <div class="kpi-card"><div class="kpi-lbl">Μισθοδοσία / Έσοδα</div><div class="kpi-val tnum">{{ fmtPct(pl.cur.revenue ? pl.cur.payroll / pl.cur.revenue * 100 : 0) }}</div><div class="kpi-sub">Αθήνα + Μανίλα</div></div>
      <div class="kpi-card"><div class="kpi-lbl">Κόστος 3rd-party / μέλος</div><div class="kpi-val tnum">{{ fmtUSD(pl.cur.members ? pl.cur.thirdParty / pl.cur.members : 0) }}</div><div class="kpi-sub">network+audit+dental payable</div></div>
    </div>

    <div class="section">
      <div class="sec-head">
        <div class="sec-title"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 3v18h18M7 14l3-3 3 3 5-5"/></svg>Πρόβλεψη EBITDA &amp; Μελών</div>
        <div class="sec-note">{{ chartGranNote }}</div>
        <div class="sec-actions">
          <div class="mode-pills">
            <button class="mode-pill" :class="{ active: pl.granularity === 'year' }" @click="pl.setGranularity('year')">Ανά έτος</button>
            <button class="mode-pill" :class="{ active: pl.granularity === 'quarter' }" @click="pl.setGranularity('quarter')">Ανά τρίμηνο</button>
          </div>
        </div>
      </div>
      <div class="sec-body">
        <div class="chart-wrap"><canvas ref="chartCanvas"></canvas></div>
      </div>
    </div>

    <!-- ===== Admin only: assumptions ===== -->
    <div class="section">
      <div class="sec-head">
        <div class="sec-title"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 20h9M16.5 3.5a2.1 2.1 0 1 1 3 3L7 19l-4 1 1-4Z"/></svg>Παραδοχές</div>
        <div class="sec-note">μόνο admin — επηρεάζουν όλους τους υπολογισμούς παραπάνω</div>
      </div>
      <div class="sec-body">
        <div class="assume-grid">
          <div class="field"><label>Μέλη (σήμερα)</label><input type="number" v-model="pl.inputs.members" class="tnum" @change="pl.saveAssumption('members')"></div>
          <div class="field"><label>Ετήσια μεταβολή μελών<span class="hint"> — ενεργό μόνο σε λειτουργία "Ποσοστό %"</span></label><input type="number" v-model="pl.inputs.memberGrowth" step="0.5" class="tnum" @change="pl.saveAssumption('memberGrowth')" :disabled="!pl.isAdmin || !isGrowth"></div>
          <div class="field"><label>Network access fee <span class="hint">$/μέλος/έτος</span></label><input type="number" v-model="pl.inputs.netFee" step="0.1" class="tnum" @change="pl.saveAssumption('netFee')"></div>
          <div class="field"><label>Μηνιαίο fee <span class="hint">$/μέλος/μήνα</span></label><input type="number" v-model="pl.inputs.monthlyFee" step="0.005" class="tnum" @change="pl.saveAssumption('monthlyFee')"></div>
          <div class="field"><label>Audit fee rate <span class="hint">% επί $35 claims</span></label><input type="number" v-model="pl.inputs.auditRate" step="0.01" class="tnum" @change="pl.saveAssumption('auditRate')"></div>
          <div class="field"><label>Dental fee <span class="hint">$/μέλος/έτος</span></label><input type="number" v-model="pl.inputs.dentalFee" step="0.05" class="tnum" @change="pl.saveAssumption('dentalFee')"></div>
          <div class="field"><label>Network payable <span class="hint">$/μέλος + 12% ΦΠΑ</span></label><input type="number" v-model="pl.inputs.netPay" step="0.5" class="tnum" @change="pl.saveAssumption('netPay')"></div>
          <div class="field"><label>Audit payable rate <span class="hint">% επί $35 + 12% ΦΠΑ</span></label><input type="number" v-model="pl.inputs.auditPay" step="0.01" class="tnum" @change="pl.saveAssumption('auditPay')"></div>
          <div class="field"><label>Dental payable <span class="hint">$/μέλος + 12% ΦΠΑ</span></label><input type="number" v-model="pl.inputs.dentalPay" step="0.5" class="tnum" @change="pl.saveAssumption('dentalPay')"></div>
          <div class="field"><label>PHP ανά USD<span class="hint"> — ισοτιμία γραφείου Μανίλα</span></label><input type="number" v-model="pl.inputs.phpPerUsd" step="0.1" class="tnum" @change="pl.saveAssumption('phpPerUsd')"></div>
          <div class="field"><label>USD ανά EUR<span class="hint"> — δεν χρησιμοποιείται: η μισθοδοσία Αθήνας είναι ήδη σε $</span></label><input type="number" v-model="pl.inputs.usdPerEur" step="0.01" class="tnum" @change="pl.saveAssumption('usdPerEur')"></div>
        </div>
      </div>
    </div>

    <!-- ===== Admin only: member forecast — flat % growth OR manual milestones ===== -->
    <div class="section">
      <div class="sec-head">
        <div class="sec-title"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/><circle cx="12" cy="12" r="4"/></svg>Πρόβλεψη Μελών</div>
        <div class="sec-note">μόνο admin — επιλέγεις πώς υπολογίζονται τα μελλοντικά μέλη</div>
        <div class="sec-actions">
          <div class="mode-pills">
            <button class="mode-pill" :class="{ active: isGrowth }" @click="pl.setMemberForecastMode('growth')">Ποσοστό %</button>
            <button class="mode-pill" :class="{ active: !isGrowth }" @click="pl.setMemberForecastMode('milestones')">Χειροκίνητοι στόχοι</button>
          </div>
        </div>
      </div>
      <div class="sec-body">
        <div class="sec-note" :class="{ dim: !isGrowth }">Ενεργό: το πεδίο "Ετήσια μεταβολή μελών" στις Παραδοχές — εφαρμόζεται σύνθετα ανά έτος.</div>
        <div style="margin-top:10px" :class="{ dim: isGrowth }">
          <div class="sec-note" style="display:flex;align-items:center;justify-content:space-between">
            <span>Χειροκίνητη πρόβλεψη: ορίζεις στόχο μελών για συγκεκριμένο μήνα/έτος.</span>
            <button class="btn btn-add" :style="{ display: pl.isAdmin ? '' : 'none' }" @click="pl.addMilestone()">+ Προσθήκη στόχου</button>
          </div>
          <div class="table-wrap" style="margin-top:8px">
            <table>
              <thead><tr><th>Μέχρι (μήνας/έτος)</th><th>Στόχος μελών</th><th></th></tr></thead>
              <tbody>
                <tr v-if="pl.milestones.length === 0"><td colspan="3" style="color:var(--text-muted)">Δεν έχουν οριστεί στόχοι — τα μέλη θεωρούνται σταθερά.</td></tr>
                <tr v-for="(m, idx) in pl.milestones" :key="m.id">
                  <td><input class="cell-input" type="month" :value="m.month || ''" :disabled="!pl.isAdmin" @change="pl.updateMilestone(idx, 'month', $event.target.value)"></td>
                  <td><input class="cell-input tnum" type="number" step="100" :value="m.members" :disabled="!pl.isAdmin" @change="pl.updateMilestone(idx, 'members', $event.target.value)"></td>
                  <td><button class="row-del" :style="{ visibility: pl.isAdmin ? 'visible' : 'hidden' }" @click="pl.removeMilestone(idx)" title="Αφαίρεση">✕</button></td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="sec-note" style="margin-top:8px">
            Η καμπύλη μελών παρεμβάλλεται γραμμικά ανάμεσα στο σήμερα (<span>{{ fmtInt(parseFloat(pl.inputs.members) || 0) }} μέλη</span>) και τους στόχους που ορίζεις· μετά τον τελευταίο στόχο τα μέλη παραμένουν σταθερά στην τιμή του.
          </div>
        </div>
      </div>
    </div>

    </template>

    <!-- ===== Editor / Viewer: forecast blocks need admin (assumptions + milestones are admin-only).
         KNOWN TEMPORARY GAP: no read-only hero/chart for non-admin roles yet. ===== -->
    <div class="section" v-if="!pl.isAdmin">
      <div class="sec-body locked-card">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="11" width="18" height="10" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
        <div>Απαιτεί δικαιώματα Polaris admin</div>
        <div style="font-size:12.5px">EBITDA, break-even, παραδοχές και πρόβλεψη μελών είναι ορατά μόνο σε admin.</div>
      </div>
    </div>

    <!-- ===== Admin / Editor (edit) · Viewer (read-only): real actuals from Αστρολάβος (GL trial balance) ===== -->
    <div class="section">
      <div class="sec-head">
        <div class="sec-title"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>Πραγματικά Στοιχεία (Αστρολάβος)</div>
        <div class="sec-note">admin/editor · ίδιες κατηγορίες με το trial balance του Αστρολάβος, για να επιβλέπεις από πού έρχονται τα έσοδα και πού πάνε τα έξοδα</div>
        <div class="sec-actions" v-if="pl.canEdit"><button class="btn btn-add" disabled title="Ενεργό στην παραγωγή, όταν οριστικοποιηθεί η μορφή του CSV από το Αστρολάβος">⬆ Μεταφόρτωση CSV</button></div>
      </div>
      <div class="sec-body">
        <div class="field" style="max-width:320px"><label>Περίοδος</label><input type="text" :value="pl.actPeriod" class="cell-input" @change="pl.actPeriod = $event.target.value"></div>
        <div style="margin-top:12px">
          <div v-for="(group, key) in pl.actualsData" :key="key" class="actuals-group">
            <div class="actuals-group-title">{{ group.title }} <span class="hint">{{ group.code }}</span></div>
            <div class="actuals-row actuals-row--head"><span></span><span>Αρχή<span class="period-date">{{ dates.start }}</span></span><span>Μεταβολή<span class="period-date">{{ dates.start }}–{{ dates.end }}</span></span><span>Τέλος<span class="period-date">{{ dates.end }}</span></span></div>
            <div v-for="(row, idx) in group.rows" :key="idx" class="actuals-row">
              <label>{{ row.label }}</label>
              <input class="cell-input tnum" type="number" :value="row.begin" :disabled="!pl.canEdit" @change="pl.updateActualAmount(key, idx, 'begin', $event.target.value)">
              <input class="cell-input tnum" type="number" :value="row.change" :disabled="!pl.canEdit" @change="pl.updateActualAmount(key, idx, 'change', $event.target.value)">
              <span class="tnum actuals-end">{{ fmtUSD(rowEnd(row)) }}</span>
            </div>
            <div class="actuals-subtotal"><span>Υποσύνολο</span><span class="tnum">{{ fmtUSD(groupSum3(group).begin) }}</span><span class="tnum">{{ fmtUSD(groupSum3(group).change) }}</span><span class="tnum">{{ fmtUSD(groupSum3(group).end) }}</span></div>
          </div>
        </div>
        <div class="agg-row" style="margin-top:14px;padding-top:12px;border-top:1px solid var(--border)">
          <div><div class="l">Σύνολο εσόδων</div><div class="v tnum">{{ fmtUSD(pl.revSum.end) }}</div></div>
          <div><div class="l">Σύνολο εξόδων</div><div class="v tnum">{{ fmtUSD(pl.expSum.end) }}</div></div>
          <div><div class="l">Καθαρό αποτέλεσμα (πραγματικό)</div><div class="v tnum" :class="actNet < 0 ? 'danger' : 'ok'">{{ fmtUSD(actNet) }}</div></div>
          <div><div class="l">EBITDA forecast ίδιας περιόδου<span class="hint" style="display:block;font-size:10px"> — pro-rated από το μοντέλο</span></div><div class="v tnum">{{ fmtUSD(pl.actForecast) }}</div></div>
        </div>
        <div class="sec-note" style="margin-top:10px">
          ⚠️ Δεν είναι ακόμη 1:1 συγκρίσιμο με το forecast: το trial balance αναγνωρίζει έσοδα σε 3 επικαλυπτόμενα policy-years (24-25/25-26/26-27), ενώ το forecast είναι ανά ημερολογιακό έτος. Οι κατηγορίες παραπάνω όμως είναι ήδη οι ίδιες με του Αστρολάβος — μόλις ενεργοποιηθεί το CSV, θα γεμίζουν αυτόματα από τον αντίστοιχο κωδικό λογαριασμού.
        </div>
      </div>
    </div>
    </PolarisStatus>
  </div>
</template>
