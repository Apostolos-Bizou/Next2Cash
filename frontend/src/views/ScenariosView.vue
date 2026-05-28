<script setup>
// ScenariosView.vue — S97 + S97.1 (filters) + S98 (comparison toggle)
// Reads /api/scenarios (entity-scoped OR all-group) and lets ADMIN edit
// revenue/expense adjustment %, color, name. Baseline locked at 0/0.
// S98: a "Σύγκριση" toggle in the header swaps the list for a side-by-side
// comparison panel (4 cards + cumulative-net chart) backed by
// GET /api/forecast/compare. No new route/section.
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import api from '@/api'
import {
  Chart, LineController, LineElement, PointElement, LinearScale,
  CategoryScale, Tooltip, Legend, Filler
} from 'chart.js'

Chart.register(LineController, LineElement, PointElement, LinearScale,
  CategoryScale, Tooltip, Legend, Filler)

const scenarios = ref([])
const loading = ref(false)
const error = ref('')
const saving = ref(false)

const isAdmin = computed(() => {
  try {
    const raw = localStorage.getItem('n2c_user')
    if (!raw) return false
    const u = JSON.parse(raw)
    return (u.role || '').toLowerCase() === 'admin'
  } catch { return false }
})

// "Next2Me Group" entity uses localStorage key 'next2megroup' (renamed from
// 'polaris' in App.vue) but maps to the polaris UUID where the data lives.
const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house: 'dea1f32c-7b30-4981-b625-633da9dbe71e',
  next2megroup: '50317f44-9961-4fb4-add0-7a118e32dc14',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14'
}
const ENTITY_NAMES = {
  '58202b71-4ddb-45c9-8e3c-39e816bde972': 'Next2Me',
  'dea1f32c-7b30-4981-b625-633da9dbe71e': 'House',
  '50317f44-9961-4fb4-add0-7a118e32dc14': 'Next2Me Group'
}
function entityName(id) { return ENTITY_NAMES[id] || '—' }
function currentEntityId() {
  const key = localStorage.getItem('n2c_entity') || 'next2me'
  return ENTITIES[key] || ENTITIES.next2me
}

// ── S97.1 filter state ──
const filterEntity = ref('current')   // 'current' (active entity) or 'all' (Όλος ο Όμιλος)
const filterType = ref('ALL')         // ALL | BASELINE | OPTIMISTIC | PESSIMISTIC | CUSTOM
const filterActive = ref('active')    // 'active' | 'all'
const filterName = ref('')

const showGroupColumn = computed(() => filterEntity.value === 'all')

async function loadScenarios() {
  loading.value = true
  error.value = ''
  try {
    const params = {}
    if (filterEntity.value === 'current') params.entityId = currentEntityId()
    params.activeOnly = (filterActive.value === 'active')
    const res = await api.get('/api/scenarios', { params })
    if (res.data && res.data.success && Array.isArray(res.data.data)) {
      scenarios.value = res.data.data
    } else {
      scenarios.value = []
    }
  } catch (e) {
    console.error('loadScenarios error:', e)
    error.value = (e.response && e.response.data && e.response.data.error) || e.message || 'Σφάλμα.'
    scenarios.value = []
  } finally {
    loading.value = false
  }
}

const filteredScenarios = computed(() => {
  let list = scenarios.value
  if (filterType.value !== 'ALL') {
    list = list.filter(s => (s.scenarioType || '').toUpperCase() === filterType.value)
  }
  const q = filterName.value.trim().toLowerCase()
  if (q) {
    list = list.filter(s => (s.name || '').toLowerCase().includes(q))
  }
  return list
})

function isBaseline(s) { return (s.scenarioType || '').toUpperCase() === 'BASELINE' || s.isDefault === true }

const showModal = ref(false)
const modalForm = ref({ id: '', name: '', scenarioType: '', revenueAdjustPct: 0, expenseAdjustPct: 0, color: '#6B7280', description: '' })

function openEdit(s) {
  if (!isAdmin.value) { window.alert('Μόνο διαχειριστές μπορούν να επεξεργαστούν σενάρια.'); return }
  if (isBaseline(s)) { window.alert('Το Baseline είναι κλειδωμένο στο 0% / 0% — είναι το σημείο αναφοράς.'); return }
  modalForm.value = {
    id: s.id,
    name: s.name || '',
    scenarioType: s.scenarioType || 'CUSTOM',
    revenueAdjustPct: Number(s.revenueAdjustPct) || 0,
    expenseAdjustPct: Number(s.expenseAdjustPct) || 0,
    color: s.color || '#6B7280',
    description: s.description || ''
  }
  showModal.value = true
}
function closeModal() { showModal.value = false }

async function saveScenario() {
  saving.value = true
  try {
    const payload = {
      name: modalForm.value.name,
      revenueAdjustPct: Number(modalForm.value.revenueAdjustPct) || 0,
      expenseAdjustPct: Number(modalForm.value.expenseAdjustPct) || 0,
      color: modalForm.value.color,
      description: modalForm.value.description
    }
    const res = await api.put('/api/scenarios/' + modalForm.value.id, payload)
    if (res.data && res.data.success) {
      showModal.value = false
      await loadScenarios()
      if (viewMode.value === 'compare') await loadComparison()
    } else {
      window.alert((res.data && res.data.error) || 'Σφάλμα.')
    }
  } catch (e) {
    window.alert((e.response && e.response.data && e.response.data.error) || e.message || 'Σφάλμα.')
  } finally {
    saving.value = false
  }
}

function clearFilters() {
  filterType.value = 'ALL'
  filterName.value = ''
  filterActive.value = 'active'
  filterEntity.value = 'current'
  loadScenarios()
}

// ── S98 comparison ──
const viewMode = ref('list')          // 'list' | 'compare'
const cmpLoading = ref(false)
const cmpError = ref('')
const cmpData = ref(null)
const horizon = ref(24)
const chartCanvas = ref(null)
let chartInstance = null

const cmpScenarios = computed(() => (cmpData.value && cmpData.value.scenarios) || [])

function fmtEur(v) {
  const n = Number(v) || 0
  return '€' + Math.round(n).toLocaleString('el-GR')
}
function fmtPct(v) {
  const n = Number(v) || 0
  return (n > 0 ? '+' : '') + n + '%'
}

async function loadComparison() {
  cmpLoading.value = true
  cmpError.value = ''
  try {
    const params = { entityId: currentEntityId(), horizonMonths: horizon.value }
    const res = await api.get('/api/forecast/compare', { params })
    cmpData.value = res.data
    await nextTick()
    renderChart()
  } catch (e) {
    console.error('loadComparison error:', e)
    cmpError.value = (e.response && e.response.data && e.response.data.error) || e.message || 'Σφάλμα.'
    cmpData.value = null
  } finally {
    cmpLoading.value = false
  }
}

function renderChart(attempt = 0) {
  if (!cmpData.value) return
  if (!chartCanvas.value) {
    // canvas not in DOM yet — retry on next frame (up to ~10 frames)
    if (attempt < 10) requestAnimationFrame(() => renderChart(attempt + 1))
    return
  }
  if (chartInstance) { chartInstance.destroy(); chartInstance = null }
  const labels = cmpData.value.monthLabels || []
  const datasets = cmpScenarios.value.map(s => {
    const isPess = (s.scenarioType || '').toUpperCase() === 'PESSIMISTIC'
    return {
      label: s.name,
      data: (s.cumulativeNet || []).map(x => Number(x) || 0),
      borderColor: s.color || '#6B7280',
      backgroundColor: s.color || '#6B7280',
      borderWidth: 2,
      borderDash: isPess ? [6, 4] : [],
      tension: 0.25,
      pointRadius: 0,
      fill: false
    }
  })
  chartInstance = new Chart(chartCanvas.value, {
    type: 'line',
    data: { labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (c) => c.dataset.label + ': ' + fmtEur(c.parsed.y) } }
      },
      scales: {
        x: { grid: { display: false }, ticks: { maxTicksLimit: 9, color: '#e2e8f0' } },
        y: { grid: { color: 'rgba(226,232,240,0.18)' }, ticks: { color: '#e2e8f0', callback: (v) => fmtEur(v) } }
      }
    },
    plugins: [{
      id: 'zeroLine',
      afterDraw: (chart) => {
        const y = chart.scales.y.getPixelForValue(0)
        const { left, right } = chart.chartArea
        const ctx = chart.ctx
        ctx.save()
        ctx.strokeStyle = 'rgba(239,68,68,0.5)'
        ctx.lineWidth = 1
        ctx.setLineDash([4, 3])
        ctx.beginPath(); ctx.moveTo(left, y); ctx.lineTo(right, y); ctx.stroke()
        ctx.restore()
      }
    }]
  })
}

function setMode(mode) {
  viewMode.value = mode
  if (mode === 'compare') loadComparison()
}

function onEntityChanged() {
  if (filterEntity.value === 'current') loadScenarios()
  if (viewMode.value === 'compare') loadComparison()
}
onMounted(() => {
  loadScenarios()
  window.addEventListener('entity-changed', onEntityChanged)
})
onUnmounted(() => {
  window.removeEventListener('entity-changed', onEntityChanged)
  if (chartInstance) { chartInstance.destroy(); chartInstance = null }
})
</script>

<template>
  <div class="scenarios-view">
    <div class="sv-header">
      <div>
        <h1 class="sv-title">Σενάρια</h1>
        <p class="sv-sub">Διαχείριση σεναρίων πρόβλεψης ανά εταιρεία (Baseline / Optimistic / Pessimistic / Custom).</p>
      </div>
      <div class="sv-head-actions">
        <div class="sv-toggle">
          <button :class="{ active: viewMode === 'list' }" @click="setMode('list')">Λίστα</button>
          <button :class="{ active: viewMode === 'compare' }" @click="setMode('compare')">Σύγκριση</button>
        </div>
        <button v-if="viewMode === 'list'" class="btn-reload" @click="loadScenarios" :disabled="loading">↻ Ανανέωση</button>
        <button v-else class="btn-reload" @click="loadComparison" :disabled="cmpLoading">↻ Ανανέωση</button>
      </div>
    </div>

    <!-- ════════ LIST MODE ════════ -->
    <template v-if="viewMode === 'list'">
      <!-- S97.1 Filters -->
      <div class="sv-filters">
        <div class="sv-filter">
          <label>Εταιρεία</label>
          <select v-model="filterEntity" @change="loadScenarios">
            <option value="current">Τρέχουσα εταιρεία</option>
            <option value="all">Όλος ο Όμιλος</option>
          </select>
        </div>
        <div class="sv-filter">
          <label>Τύπος</label>
          <select v-model="filterType">
            <option value="ALL">Όλοι</option>
            <option value="BASELINE">Baseline</option>
            <option value="OPTIMISTIC">Optimistic</option>
            <option value="PESSIMISTIC">Pessimistic</option>
            <option value="CUSTOM">Custom</option>
          </select>
        </div>
        <div class="sv-filter">
          <label>Κατάσταση</label>
          <select v-model="filterActive" @change="loadScenarios">
            <option value="active">Ενεργά</option>
            <option value="all">Όλα</option>
          </select>
        </div>
        <div class="sv-filter sv-filter-grow">
          <label>Αναζήτηση</label>
          <input v-model="filterName" type="text" placeholder="Όνομα σεναρίου…" class="sv-search" />
        </div>
        <button class="btn-clear" @click="clearFilters">Καθαρισμός</button>
      </div>

      <div v-if="loading" class="sv-state">Φόρτωση σεναρίων…</div>
      <div v-else-if="error" class="sv-state sv-error">
        {{ error }}
        <button class="btn-retry" @click="loadScenarios">Δοκιμή ξανά</button>
      </div>
      <div v-else-if="filteredScenarios.length === 0" class="sv-state">Δεν βρέθηκαν σενάρια με αυτά τα φίλτρα.</div>

      <div v-else class="sv-grid">
        <div v-for="s in filteredScenarios" :key="s.id" class="sv-card" :style="{ borderLeftColor: s.color || '#6B7280' }">
          <div class="sv-card-head">
            <span class="sv-card-name">{{ s.name }}</span>
            <span v-if="isBaseline(s)" class="sv-badge">ΠΡΟΕΠΙΛΟΓΗ</span>
          </div>
          <div v-if="showGroupColumn" class="sv-entity-tag">{{ entityName(s.ownerEntityId) }}</div>
          <div class="sv-card-body">
            <div class="sv-row"><span>Έσοδα</span><strong :style="{color: (s.revenueAdjustPct>0?'#10b981':(s.revenueAdjustPct<0?'#ef4444':'#64748b'))}">{{ Number(s.revenueAdjustPct).toFixed(0) }}%</strong></div>
            <div class="sv-row"><span>Έξοδα</span><strong :style="{color: (s.expenseAdjustPct>0?'#ef4444':(s.expenseAdjustPct<0?'#10b981':'#64748b'))}">{{ Number(s.expenseAdjustPct).toFixed(0) }}%</strong></div>
          </div>
          <div class="sv-card-foot">
            <span v-if="isBaseline(s)" class="sv-locked">κλειδωμένο</span>
            <button v-else-if="isAdmin" class="btn-edit" @click="openEdit(s)">✎ Επεξεργασία</button>
          </div>
        </div>
      </div>
    </template>

    <!-- ════════ COMPARE MODE ════════ -->
    <template v-else>
      <div class="cmp-controls">
        <label>Προβολή</label>
        <select v-model.number="horizon" @change="loadComparison">
          <option :value="12">12 μήνες</option>
          <option :value="24">24 μήνες</option>
          <option :value="36">36 μήνες</option>
        </select>
        <span class="cmp-entity">{{ entityName(currentEntityId()) }}</span>
      </div>

      <div v-if="cmpLoading" class="sv-state">Φόρτωση σύγκρισης…</div>
      <div v-else-if="cmpError" class="sv-state sv-error">
        {{ cmpError }}
        <button class="btn-retry" @click="loadComparison">Δοκιμή ξανά</button>
      </div>

      <template v-else-if="cmpData">
        <div class="cmp-cards">
          <div v-for="s in cmpScenarios" :key="s.scenarioId" class="cmp-card" :class="{ 'cmp-base': s.isBaseline }">
            <div class="cmp-card-head">
              <span class="cmp-dot" :style="{ background: s.color }"></span>
              <span class="cmp-name">{{ s.name }}</span>
            </div>
            <div class="cmp-adj">{{ fmtPct(s.revenueAdjustPct) }} / {{ fmtPct(s.expenseAdjustPct) }}<span v-if="s.isBaseline"> · default</span></div>

            <div class="cmp-lbl">Έσοδα</div>
            <div class="cmp-val cmp-income">{{ fmtEur(s.totalIncome) }}</div>
            <div class="cmp-lbl">Έξοδα</div>
            <div class="cmp-val cmp-expense">{{ fmtEur(s.totalExpenses) }}</div>

            <div class="cmp-net-box">
              <div class="cmp-lbl">Καθαρό</div>
              <div class="cmp-net" :class="{ neg: Number(s.netCashFlow) < 0 }">{{ fmtEur(s.netCashFlow) }}</div>
              <div v-if="!s.isBaseline" class="cmp-vs" :class="Number(s.netVsBaseline) >= 0 ? 'pos' : 'neg'">
                {{ Number(s.netVsBaseline) >= 0 ? '▲' : '▼' }} {{ fmtEur(Math.abs(Number(s.netVsBaseline))) }} vs Baseline
              </div>
            </div>
          </div>
        </div>

        <div class="cmp-chart-head">Σωρευτικό καθαρό ταμείο</div>
        <div class="cmp-chart-sub">cumulative net cash flow ανά μήνα</div>
        <div class="cmp-legend">
          <span v-for="s in cmpScenarios" :key="'lg-'+s.scenarioId" class="cmp-leg-item">
            <span class="cmp-leg-line" :style="{ background: s.color }"></span>{{ s.name }}
          </span>
        </div>
        <div class="cmp-chart"><canvas ref="chartCanvas"></canvas></div>

        <div class="cmp-note">Τα adjustments εφαρμόζονται μόνο σε μελλοντικές (PLANNED + project) ροές — ποτέ σε πραγματικές.</div>
      </template>
    </template>

    <!-- Edit modal (shared) -->
    <div v-if="showModal" class="sv-modal-overlay" @click.self="closeModal">
      <div class="sv-modal">
        <h2 class="sv-modal-title">Επεξεργασία σεναρίου</h2>
        <div class="sv-field">
          <label>Όνομα</label>
          <input v-model="modalForm.name" type="text" class="sv-input" />
        </div>
        <div class="sv-field-row">
          <div class="sv-field">
            <label>Προσαρμογή εσόδων (%)</label>
            <input v-model.number="modalForm.revenueAdjustPct" type="number" step="1" class="sv-input" />
          </div>
          <div class="sv-field">
            <label>Προσαρμογή εξόδων (%)</label>
            <input v-model.number="modalForm.expenseAdjustPct" type="number" step="1" class="sv-input" />
          </div>
        </div>
        <div class="sv-field">
          <label>Χρώμα</label>
          <input v-model="modalForm.color" type="color" class="sv-color" />
        </div>
        <div class="sv-field">
          <label>Περιγραφή</label>
          <textarea v-model="modalForm.description" rows="2" class="sv-input"></textarea>
        </div>
        <div class="sv-modal-foot">
          <button class="btn-cancel" @click="closeModal">Άκυρο</button>
          <button class="btn-save" @click="saveScenario" :disabled="saving">{{ saving ? 'Αποθήκευση…' : 'Αποθήκευση' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.scenarios-view { padding: 24px; }
.sv-header { display:flex; align-items:flex-start; justify-content:space-between; margin-bottom:20px; gap:16px; }
.sv-title { font-size:1.6rem; font-weight:700; color:#ffffff; margin:0; }
.sv-sub { font-size:.9rem; color:#cbd5e1; margin:4px 0 0; font-style:italic; }
.sv-head-actions { display:flex; align-items:center; gap:10px; white-space:nowrap; }
.sv-toggle { display:flex; border:1px solid #d6dee8; border-radius:8px; overflow:hidden; }
.sv-toggle button { padding:8px 16px; border:0; background:#fff; color:#64748b; cursor:pointer; font-weight:600; font-size:.9rem; }
.sv-toggle button.active { background:#2E75B6; color:#fff; }
.btn-reload { padding:8px 16px; border:1px solid #d6dee8; border-radius:8px; background:#fff; color:#2E75B6; cursor:pointer; font-weight:600; white-space:nowrap; }
.btn-reload:disabled { opacity:.5; cursor:default; }
.sv-filters { display:flex; align-items:flex-end; gap:12px; flex-wrap:wrap; margin-bottom:20px; padding:14px 16px; background:#f8fafc; border:1px solid #e2e8f0; border-radius:10px; }
.sv-filter { display:flex; flex-direction:column; gap:4px; }
.sv-filter label { font-size:.75rem; color:#64748b; font-weight:600; }
.sv-filter select, .sv-search { padding:7px 10px; border:1px solid #d6dee8; border-radius:8px; font-size:.9rem; background:#fff; color:#1e293b; }
.sv-filter-grow { flex:1; min-width:160px; }
.sv-search { width:100%; box-sizing:border-box; }
.btn-clear { padding:8px 14px; border:1px solid #d6dee8; border-radius:8px; background:#fff; color:#64748b; cursor:pointer; font-size:.85rem; }
.sv-state { padding:40px; text-align:center; color:#64748b; }
.sv-error { color:#ef4444; }
.btn-retry { margin-left:12px; padding:6px 14px; border:1px solid #ef4444; border-radius:6px; background:#fff; color:#ef4444; cursor:pointer; }
.sv-grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(200px, 1fr)); gap:16px; }
.sv-card { background:#fff; border:1px solid #e2e8f0; border-left:4px solid #6B7280; border-radius:12px; padding:16px; }
.sv-card-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:6px; }
.sv-card-name { font-weight:700; color:#1e293b; font-size:1.05rem; }
.sv-badge { background:#eef4fb; color:#2E75B6; font-size:.7rem; font-weight:700; padding:2px 8px; border-radius:6px; }
.sv-entity-tag { font-size:.72rem; color:#94a3b8; font-weight:600; margin-bottom:8px; text-transform:uppercase; letter-spacing:.03em; }
.sv-card-body { display:flex; flex-direction:column; gap:6px; margin-bottom:12px; margin-top:6px; }
.sv-row { display:flex; align-items:center; justify-content:space-between; font-size:.9rem; color:#64748b; }
.sv-card-foot { border-top:1px solid #f1f5f9; padding-top:10px; }
.sv-locked { font-size:.8rem; color:#94a3b8; }
.btn-edit { padding:5px 12px; border:1px solid #d6dee8; border-radius:6px; background:#fff; color:#2E75B6; cursor:pointer; font-size:.85rem; font-weight:600; }
/* comparison */
.cmp-controls { display:flex; align-items:center; gap:10px; margin-bottom:18px; }
.cmp-controls label { font-size:.8rem; color:#cbd5e1; font-weight:600; }
.cmp-controls select { padding:7px 10px; border:1px solid #d6dee8; border-radius:8px; background:#fff; color:#1e293b; }
.cmp-entity { font-weight:700; color:#ffffff; margin-left:6px; }
.cmp-cards { display:grid; grid-template-columns:repeat(4, 1fr); gap:14px; margin-bottom:28px; }
.cmp-card { background:#fff; border:1px solid #e2e8f0; border-radius:12px; padding:16px; }
.cmp-base { border:2px solid #2E75B6; }
.cmp-card-head { display:flex; align-items:center; gap:7px; margin-bottom:3px; }
.cmp-dot { width:10px; height:10px; border-radius:50%; display:inline-block; }
.cmp-name { font-weight:700; font-size:1.05rem; color:#1e293b; }
.cmp-adj { font-size:.78rem; color:#94a3b8; margin-bottom:14px; }
.cmp-lbl { font-size:.78rem; color:#94a3b8; }
.cmp-val { font-size:1.15rem; font-weight:600; margin-bottom:8px; }
.cmp-income { color:#10b981; }
.cmp-expense { color:#ef4444; }
.cmp-net-box { border-top:1px solid #f1f5f9; padding-top:8px; margin-top:4px; }
.cmp-net { font-size:1.4rem; font-weight:700; color:#1e293b; }
.cmp-net.neg { color:#ef4444; }
.cmp-vs { font-size:.78rem; margin-top:3px; }
.cmp-vs.pos { color:#10b981; }
.cmp-vs.neg { color:#ef4444; }
.cmp-chart-head { font-size:1.05rem; font-weight:700; color:#ffffff; }
.cmp-chart-sub { font-size:.85rem; font-style:italic; color:#cbd5e1; margin-bottom:10px; }
.cmp-legend { display:flex; flex-wrap:wrap; gap:16px; margin-bottom:10px; font-size:.8rem; color:#ffffff; }
.cmp-leg-item { display:flex; align-items:center; gap:6px; }
.cmp-leg-line { width:14px; height:3px; display:inline-block; }
.cmp-chart { position:relative; width:100%; height:320px; }
.cmp-note { font-size:.78rem; color:#cbd5e1; font-style:italic; margin-top:14px; }
@media (max-width: 900px) { .cmp-cards { grid-template-columns:repeat(2, 1fr); } }
/* modal */
.sv-modal-overlay { position:fixed; inset:0; background:rgba(15,23,42,.45); display:flex; align-items:center; justify-content:center; z-index:1000; }
.sv-modal { background:#fff; border-radius:14px; padding:24px; width:min(440px, 92vw); }
.sv-modal-title { font-size:1.2rem; font-weight:700; color:#1e293b; margin:0 0 18px; }
.sv-field { margin-bottom:14px; }
.sv-field label { display:block; font-size:.85rem; color:#64748b; margin-bottom:5px; font-weight:600; }
.sv-field-row { display:flex; gap:12px; }
.sv-field-row .sv-field { flex:1; }
.sv-input { width:100%; padding:8px 10px; border:1px solid #d6dee8; border-radius:8px; font-size:.95rem; box-sizing:border-box; }
.sv-color { width:60px; height:38px; border:1px solid #d6dee8; border-radius:8px; }
.sv-modal-foot { display:flex; justify-content:flex-end; gap:10px; margin-top:8px; }
.btn-cancel { padding:8px 16px; border:1px solid #d6dee8; border-radius:8px; background:#fff; color:#64748b; cursor:pointer; }
.btn-save { padding:8px 16px; border:0; border-radius:8px; background:#2E75B6; color:#fff; cursor:pointer; font-weight:600; }
.btn-save:disabled { opacity:.5; cursor:default; }
</style>
