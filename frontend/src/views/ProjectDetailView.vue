<!-- S75-MARKER-PROJECT-DETAIL-VIEW -->
<template>
  <div class="project-detail-page">
    <!-- ===== Loading State ===== -->
    <div v-if="loading" class="state-block">
      <div class="spinner"></div>
      <p>Φόρτωση λεπτομερειών…</p>
    </div>

    <!-- ===== Error State ===== -->
    <div v-else-if="error" class="state-block error">
      <p>⚠ {{ error }}</p>
      <router-link to="/projects" class="btn-back">
        ← Επιστροφή στα Projects
      </router-link>
    </div>

    <!-- ===== Main Detail View ===== -->
    <div v-else-if="detail" class="detail-content">

      <!-- ===== Section 1: Header ===== -->
      <div class="detail-header">
        <div class="header-top">
          <router-link to="/projects" class="btn-back">
            ← Επιστροφή
          </router-link>
        </div>
        <div class="header-main">
          <div class="header-title">
            <span class="header-icon">📁</span>
            <h1>{{ detail.project.name }}</h1>
            <span class="status-badge" :class="statusClass(detail.project.status)">
              {{ statusLabel(detail.project.status) }}
            </span>
          </div>
          <div v-if="detail.project.description" class="header-desc">
            {{ detail.project.description }}
          </div>
          <div class="header-meta">
            <div class="meta-item">
              <span class="meta-label">Όνομα Εταιρείας:</span>
              <span class="meta-value">{{ ownerEntityName }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Έναρξη:</span>
              <span class="meta-value">{{ fmtDate(detail.project.startDate) }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Στόχος Ολοκλήρωσης:</span>
              <span class="meta-value">{{ fmtDate(detail.project.targetCompletionDate) }}</span>
            </div>
          </div>
          <div v-if="detail.totals.progressPct !== null" class="progress-block">
            <div class="progress-label">
              {{ fmtPct(detail.totals.progressPct) }} σπαταλημένο
              <span v-if="timelinePct !== null" class="progress-secondary">
                / {{ timelinePct }}% του χρονοδιαγράμματος
              </span>
            </div>
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: clampPct(detail.totals.progressPct) + '%' }"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- S75-REORDER-ROI-TOP -->
      <!-- ===== Section 2: ROI Analysis (moved to top per CEO request) ===== -->
      <div class="detail-section">
        <h2 class="section-title">
          <span class="section-icon">💡</span>
          ROI Analysis
        </h2>
        <div v-if="detail.roi" class="roi-grid">
          <div class="roi-card">
            <div class="roi-label">Total Investment</div>
            <div class="roi-value">{{ fmtMoney(detail.roi.totalInvestment) }}</div>
          </div>
          <div class="roi-card">
            <div class="roi-label">Monthly Revenue (weighted)</div>
            <div class="roi-value">{{ fmtMoney(detail.roi.monthlyRevenueWeighted) }}</div>
            <div class="roi-sub">Best case: {{ fmtMoney(detail.roi.monthlyRevenueBestCase) }}</div>
          </div>
          <div class="roi-card">
            <div class="roi-label">Break-even (weighted)</div>
            <div class="roi-value">{{ fmtBreakEven(detail.roi.breakEvenMonthsWeighted) }}</div>
            <div class="roi-sub">Best case: {{ fmtBreakEven(detail.roi.breakEvenMonthsBest) }}</div>
          </div>
          <div class="roi-card">
            <div class="roi-label">12-month ROI (weighted)</div>
            <div class="roi-value" :class="roiClass(detail.roi.twelveMonthRoiWeightedPct)">
              {{ fmtSignedPct(detail.roi.twelveMonthRoiWeightedPct) }}
            </div>
            <div class="roi-sub">Best case: {{ fmtSignedPct(detail.roi.twelveMonthRoiBestPct) }}</div>
          </div>
        </div>
        <div v-else class="empty-state">
          <p>Δεν υπάρχουν στοιχεία αναμενόμενων εσόδων για υπολογισμό ROI.</p>
        </div>
      </div>

      <!-- ===== Section 2.5: CFO Inputs (ADMIN only) ===== -->
      <div v-if="isAdmin" class="detail-section">
        <h2 class="section-title">
          <span class="section-icon">💰</span>
          CFO Inputs
          <span class="cfo-sub-title">(δεδομένα τιμολόγησης — μόνο admin)</span>
        </h2>
        <div class="cfo-grid">
          <div class="cfo-field">
            <label>Αριθμός συμβολαίων / πελατών</label>
            <input type="number" min="0" step="1" v-model.number="cfoForm.currentCustomers" placeholder="0" />
            <span class="cfo-hint">πόσους πελάτες έχεις σήμερα</span>
          </div>
          <div class="cfo-field">
            <label>Τρέχον μηνιαίο έσοδο (MRR €)</label>
            <input type="number" min="0" step="0.01" v-model.number="cfoForm.currentMrr" placeholder="0" />
            <span class="cfo-hint">τι εισπράττεις τώρα / μήνα</span>
          </div>
          <div class="cfo-field">
            <label>Άμεσο μηνιαίο κόστος (€)</label>
            <input type="number" min="0" step="0.01" v-model.number="cfoForm.directBurnMonthly" placeholder="auto" />
            <span class="cfo-hint">override — άφησέ το κενό για αυτόματο</span>
          </div>
          <div class="cfo-field">
            <label>Περιθώριο κέρδους (%)</label>
            <input type="number" min="0" max="100" step="0.01" v-model.number="cfoForm.grossMarginPct" placeholder="75" />
            <span class="cfo-hint">gross margin — π.χ. 75</span>
          </div>
          <div class="cfo-field">
            <label>Κόστος απόκτησης πελάτη (CAC €)</label>
            <input type="number" min="0" step="0.01" v-model.number="cfoForm.cacPerCustomer" placeholder="0" />
            <span class="cfo-hint">τι κοστίζει να φέρεις έναν πελάτη</span>
          </div>
        </div>
        <div class="cfo-actions">
          <button class="cfo-save-btn" :disabled="cfoSaving" @click="saveCfo">
            {{ cfoSaving ? 'Αποθήκευση...' : 'Αποθήκευση CFO δεδομένων' }}
          </button>
          <span v-if="cfoSaved" class="cfo-ok">✓ Αποθηκεύθηκε</span>
          <span v-if="cfoError" class="cfo-err">{{ cfoError }}</span>
        </div>
      </div>

      <!-- ===== Section 3: Budget Breakdown ===== -->
      <div class="detail-section">
        <h2 class="section-title">
          <span class="section-icon">💸</span>
          Budget Breakdown
        </h2>
        <div v-if="detail.budgetBreakdown && detail.budgetBreakdown.length > 0" class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>Κατηγορία</th>
                <th class="num">Planned</th>
                <th class="num">Spent</th>
                <th class="num">Remaining</th>
                <th class="num">Progress</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in detail.budgetBreakdown" :key="row.category">
                <td>{{ row.category }}</td>
                <td class="num">{{ row.planned === null ? '—' : fmtMoney(row.planned) }}</td>
                <td class="num">{{ fmtMoney(row.spent) }}</td>
                <td class="num">{{ row.remaining === null ? '—' : fmtMoney(row.remaining) }}</td>
                <td class="num">
                  <span v-if="row.progressPct === null">—</span>
                  <span v-else>{{ fmtPct(row.progressPct) }}</span>
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="totals-row">
                <td><strong>ΣΥΝΟΛΟ</strong></td>
                <td class="num"><strong>{{ detail.totals.planned === null ? '—' : fmtMoney(detail.totals.planned) }}</strong></td>
                <td class="num"><strong>{{ fmtMoney(detail.totals.spent) }}</strong></td>
                <td class="num"><strong>{{ detail.totals.remaining === null ? '—' : fmtMoney(detail.totals.remaining) }}</strong></td>
                <td class="num"><strong>{{ detail.totals.progressPct === null ? '—' : fmtPct(detail.totals.progressPct) }}</strong></td>
              </tr>
            </tfoot>
          </table>
        </div>
        <div v-else class="empty-state">
          <p>Δεν έχουν συνδεθεί ACTUAL έξοδα σε αυτό το project.</p>
          <p class="hint">
            Το συνολικό planned budget: {{ fmtMoney(detail.totals.planned) }}.
          </p>
        </div>
      </div>

      <!-- ===== Section 4: Linked Transactions ===== -->
      <div class="detail-section">
        <h2 class="section-title">
          <span class="section-icon">📋</span>
          Συνδεδεμένες Κινήσεις
          <span class="section-count">({{ detail.linkedTransactions.count }})</span>
        </h2>
        <div v-if="detail.linkedTransactions.count > 0" class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Ημ/νία</th>
                <th>Περιγραφή</th>
                <th>Κατηγορία</th>
                <th>Τύπος</th>
                <th class="num">Ποσό</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tx in detail.linkedTransactions.transactions"
                  :key="tx.id"
                  :class="{ 'planned-row': tx.entryMode === 'PLANNED' }">
                <td>{{ tx.entityNumber }}</td>
                <td>{{ fmtDate(tx.docDate) }}</td>
                <td>
                  <span v-if="tx.entryMode === 'PLANNED'" class="planned-badge"
                        title="Προγραμματισμένη καταχώρηση">📋</span>
                  {{ tx.description || '—' }}
                </td>
                <td>{{ tx.category || '—' }}</td>
                <td>
                  <span class="type-badge" :class="'type-' + tx.type">
                    {{ tx.type === 'income' ? 'Έσοδο' : 'Έξοδο' }}
                  </span>
                </td>
                <td class="num">{{ fmtMoney(tx.amount) }}</td>
              </tr>
            </tbody>
          </table>
          <p v-if="detail.linkedTransactions.count >= 50" class="table-note">
            Εμφανίζονται οι 50 πιϱ πρόσφατες.
          </p>
        </div>
        <div v-else class="empty-state">
          <p>Δεν υπάρχουν συνδεδεμένες κινήσεις.</p>
        </div>
      </div>

      <!-- ===== Section 5: Expected Revenue Streams ===== -->
      <div class="detail-section">
        <h2 class="section-title">
          <span class="section-icon">📈</span>
          Αναμενόμενα Έσοδα
        </h2>
        <div v-if="detail.revenueStreams && detail.revenueStreams.length > 0" class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>Πηγή</th>
                <th class="num">Ποσό / μήνα</th>
                <th class="num">Confidence</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, idx) in detail.revenueStreams" :key="idx">
                <td>{{ s.source }}</td>
                <td class="num">{{ fmtMoney(s.amount) }}</td>
                <td class="num">{{ s.confidencePct }}%</td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="totals-row">
                <td><strong>Weighted total</strong></td>
                <td class="num"><strong>{{ fmtMoney(detail.weightedMonthlyRevenue) }} / μήνα</strong></td>
                <td></td>
              </tr>
            </tfoot>
          </table>
        </div>
        <div v-else class="empty-state">
          <p>Δεν υπάρχουν στοιχεία αναμενόμενων εσόδων.</p>
        </div>
      </div>


    </div>
  </div>
</template>

<script setup>
// S75-HOTFIX-API-CLIENT
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'

const route = useRoute()
const projectId = computed(() => route.params.id)

const loading = ref(true)
const error = ref(null)
const detail = ref(null)
const entities = ref([])

// ----- Fetch detail -------------------------------------------------
async function loadDetail() {
  loading.value = true
  error.value = null
  try {
    // Fetch project detail (api client auto-injects Authorization + baseURL)
    const res = await api.get('/api/projects/' + projectId.value + '/detail')
    if (res.data && res.data.success) {
      detail.value = res.data.data
    } else {
      error.value = (res.data && res.data.error) || 'Failed to load project detail'
    }

    // Fetch entities for owner name lookup (best-effort, not blocking)
    try {
      const entRes = await api.get('/api/config/entities')
      if (entRes.data && Array.isArray(entRes.data.data)) {
        entities.value = entRes.data.data
      } else if (Array.isArray(entRes.data)) {
        entities.value = entRes.data
      }
    } catch (_) { /* non-critical */ }

  } catch (e) {
    if (e.response && e.response.status === 404) {
      error.value = 'Project not found'
    } else if (e.response && e.response.status === 401) {
      error.value = 'Session expired. Please log in again.'
    } else {
      error.value = (e.response && e.response.data && e.response.data.error) || e.message || 'Unknown error'
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)

// S87.10: reload when route param changes (Vue Router reuses the component instance)
watch(projectId, () => { loadDetail() })

// ----- S86.12: CFO inputs editing (ADMIN only) --------------------
const isAdmin = computed(() => {
  try {
    const u = JSON.parse(localStorage.getItem('n2c_user') || '{}')
    return (u.role || '').toUpperCase() === 'ADMIN'
  } catch (_) { return false }
})

const cfoForm = ref({
  currentCustomers: null,
  currentMrr: null,
  directBurnMonthly: null,
  grossMarginPct: null,
  cacPerCustomer: null
})
const cfoSaving = ref(false)
const cfoSaved = ref(false)
const cfoError = ref(null)

// Sync the form from the loaded project whenever detail changes
watch(detail, (d) => {
  const p = d && d.project ? d.project : null
  if (!p) return
  cfoForm.value = {
    currentCustomers: p.currentCustomers ?? null,
    currentMrr: p.currentMrr ?? null,
    directBurnMonthly: p.directBurnMonthly ?? null,
    grossMarginPct: p.grossMarginPct ?? null,
    cacPerCustomer: p.cacPerCustomer ?? null
  }
}, { immediate: true })

async function saveCfo() {
  if (!detail.value || !detail.value.project) return
  cfoSaving.value = true
  cfoSaved.value = false
  cfoError.value = null
  try {
    const body = {
      currentCustomers: cfoForm.value.currentCustomers === '' ? null : cfoForm.value.currentCustomers,
      currentMrr: cfoForm.value.currentMrr === '' ? null : cfoForm.value.currentMrr,
      directBurnMonthly: cfoForm.value.directBurnMonthly === '' ? null : cfoForm.value.directBurnMonthly,
      grossMarginPct: cfoForm.value.grossMarginPct === '' ? null : cfoForm.value.grossMarginPct,
      cacPerCustomer: cfoForm.value.cacPerCustomer === '' ? null : cfoForm.value.cacPerCustomer
    }
    const res = await api.put('/api/projects/' + projectId.value, body)
    if (res.data && res.data.success) {
      // sync back from server response (single source of truth)
      const saved = res.data.data
      if (saved && detail.value && detail.value.project) {
        detail.value.project.currentCustomers = saved.currentCustomers
        detail.value.project.currentMrr = saved.currentMrr
        detail.value.project.directBurnMonthly = saved.directBurnMonthly
        detail.value.project.grossMarginPct = saved.grossMarginPct
        detail.value.project.cacPerCustomer = saved.cacPerCustomer
      }
      cfoSaved.value = true
      setTimeout(() => { cfoSaved.value = false }, 3000)
    } else {
      cfoError.value = (res.data && res.data.error) || 'Failed to save'
    }
  } catch (e) {
    cfoError.value = (e.response && e.response.data && e.response.data.error) || e.message || 'Save failed'
  } finally {
    cfoSaving.value = false
  }
}

// ----- Computed: owner entity name --------------------------------
const ownerEntityName = computed(() => {
  if (!detail.value || !detail.value.project) return '—'
  const eid = detail.value.project.ownerEntityId
  if (!eid) return '—'
  const ent = entities.value.find(x => x.id === eid)
  return ent ? (ent.name || '—') : '—'
})

// ----- Computed: timeline progress percent ------------------------
const timelinePct = computed(() => {
  if (!detail.value || !detail.value.project) return null
  const p = detail.value.project
  if (!p.startDate || !p.targetCompletionDate) return null
  const start = new Date(p.startDate).getTime()
  const end = new Date(p.targetCompletionDate).getTime()
  const now = Date.now()
  if (!isFinite(start) || !isFinite(end) || end <= start) return null
  const pct = ((now - start) / (end - start)) * 100
  return Math.max(0, Math.min(100, Math.round(pct)))
})

// ----- Formatting helpers -----------------------------------------
function fmtMoney(v) {
  if (v === null || v === undefined) return '—'
  const n = parseFloat(v)
  if (!isFinite(n)) return '—'
  return new Intl.NumberFormat('el-GR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(n)
}

function fmtPct(v) {
  if (v === null || v === undefined) return '—'
  const n = parseFloat(v)
  if (!isFinite(n)) return '—'
  return n.toFixed(0) + '%'
}

function fmtSignedPct(v) {
  if (v === null || v === undefined) return '—'
  const n = parseFloat(v)
  if (!isFinite(n)) return '—'
  const sign = n >= 0 ? '+' : ''
  return sign + n.toFixed(0) + '%'
}

function fmtDate(v) {
  if (!v) return '—'
  try {
    const d = new Date(v)
    if (isNaN(d.getTime())) return v
    const dd = String(d.getDate()).padStart(2, '0')
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const yyyy = d.getFullYear()
    return dd + '/' + mm + '/' + yyyy
  } catch (_) { return v }
}

function fmtBreakEven(months) {
  if (months === null || months === undefined) return '—'
  const n = parseFloat(months)
  if (!isFinite(n)) return '—'
  // round to 1 decimal if not whole; otherwise integer
  const rounded = Math.round(n * 10) / 10
  // Use "~" prefix to indicate approximation, as in spec
  return '~' + rounded.toFixed(rounded % 1 === 0 ? 0 : 1) + ' μήνες'
}

function clampPct(v) {
  const n = parseFloat(v)
  if (!isFinite(n)) return 0
  return Math.max(0, Math.min(100, n))
}

function roiClass(v) {
  const n = parseFloat(v)
  if (!isFinite(n)) return ''
  return n >= 0 ? 'roi-positive' : 'roi-negative'
}

function statusLabel(s) {
  if (!s) return '—'
  const map = {
    'IN_DEVELOPMENT': 'In Development',
    'COMPLETED': 'Completed',
    'ON_HOLD': 'On Hold',
    'CANCELLED': 'Cancelled',
    'PLANNING': 'Planning',
  }
  return map[s] || s
}

function statusClass(s) {
  if (!s) return 'status-default'
  return 'status-' + s.toLowerCase().replace(/_/g, '-')
}
</script>

<style scoped>
.project-detail-page {
  padding: 20px 24px;
  color: #e0e6ed;
  min-height: 100vh;
}

/* ----- State blocks ----- */
.state-block {
  text-align: center;
  padding: 60px 20px;
}
.state-block.error {
  color: #f87171;
}
.spinner {
  width: 32px;
  height: 32px;
  margin: 0 auto 16px;
  border: 3px solid rgba(96, 165, 250, 0.2);
  border-top-color: #60a5fa;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ----- Header ----- */
.detail-header {
  margin-bottom: 24px;
}
.header-top {
  margin-bottom: 12px;
}
.btn-back {
  display: inline-block;
  padding: 6px 12px;
  background: rgba(96, 165, 250, 0.1);
  color: #60a5fa;
  border: 1px solid rgba(96, 165, 250, 0.3);
  border-radius: 4px;
  text-decoration: none;
  font-size: 0.9rem;
  transition: background 0.15s;
}
.btn-back:hover {
  background: rgba(96, 165, 250, 0.2);
}
.header-main {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 20px 24px;
}
.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.header-icon {
  font-size: 1.6rem;
}
.header-title h1 {
  margin: 0;
  font-size: 1.6rem;
  color: #e0e6ed;
  flex: 1;
}
.status-badge {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 500;
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.3);
}
.status-in-development { background: rgba(245, 158, 11, 0.15); color: #fbbf24; border-color: rgba(245, 158, 11, 0.3); }
.status-completed      { background: rgba(34, 197, 94, 0.15); color: #4ade80; border-color: rgba(34, 197, 94, 0.3); }
.status-on-hold        { background: rgba(156, 163, 175, 0.15); color: #cbd5e1; border-color: rgba(156, 163, 175, 0.3); }
.status-cancelled      { background: rgba(239, 68, 68, 0.15); color: #f87171; border-color: rgba(239, 68, 68, 0.3); }
.status-planning       { background: rgba(96, 165, 250, 0.15); color: #60a5fa; border-color: rgba(96, 165, 250, 0.3); }

.header-desc {
  color: #94a3b8;
  font-size: 0.95rem;
  margin-bottom: 14px;
  line-height: 1.5;
}
.header-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-bottom: 16px;
}
.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.meta-label {
  font-size: 0.75rem;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.meta-value {
  font-size: 0.95rem;
  color: #e0e6ed;
}

.progress-block {
  margin-top: 14px;
}
.progress-label {
  font-size: 0.85rem;
  color: #cbd5e1;
  margin-bottom: 6px;
}
.progress-secondary {
  color: #94a3b8;
  margin-left: 8px;
}
.progress-bar {
  height: 8px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 4px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #60a5fa, #3b82f6);
  transition: width 0.3s;
}

/* ----- Sections ----- */
.detail-section {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 18px 22px;
  margin-bottom: 20px;
}
.section-title {
  margin: 0 0 14px 0;
  font-size: 1.15rem;
  color: #e0e6ed;
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-icon {
  font-size: 1.2rem;
}
.section-count {
  color: #94a3b8;
  font-weight: normal;
  font-size: 0.9rem;
  margin-left: 4px;
}

/* ----- Tables ----- */
.table-wrap {
  overflow-x: auto;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
.data-table th,
.data-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.data-table th {
  color: #94a3b8;
  font-weight: 500;
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.data-table td {
  color: #e0e6ed;
}
.data-table .num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.data-table tfoot .totals-row td {
  border-top: 2px solid rgba(255, 255, 255, 0.15);
  border-bottom: none;
  padding-top: 12px;
}
.table-note {
  font-size: 0.8rem;
  color: #94a3b8;
  margin: 10px 0 0 0;
  font-style: italic;
}

/* ----- PLANNED row tint ----- */
.planned-row {
  background: rgba(245, 158, 11, 0.06);
}
.planned-row td {
  color: #fbbf24;
}
.planned-badge {
  display: inline-block;
  margin-right: 4px;
  font-size: 0.85rem;
}

/* ----- Type badges ----- */
.type-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
}
.type-income {
  background: rgba(34, 197, 94, 0.15);
  color: #4ade80;
}
.type-expense {
  background: rgba(239, 68, 68, 0.12);
  color: #f87171;
}

/* ----- Empty states ----- */
.empty-state {
  text-align: center;
  padding: 24px 12px;
  color: #94a3b8;
}
.empty-state p {
  margin: 4px 0;
}
.empty-state .hint {
  font-size: 0.85rem;
  color: #64748b;
}

/* ----- ROI grid ----- */
.roi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}
.roi-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 14px 16px;
}
.roi-label {
  font-size: 0.78rem;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 6px;
}
.roi-value {
  font-size: 1.4rem;
  font-weight: 600;
  color: #e0e6ed;
  font-variant-numeric: tabular-nums;
}
.roi-value.roi-positive { color: #4ade80; }
.roi-value.roi-negative { color: #f87171; }
.roi-sub {
  font-size: 0.78rem;
  color: #94a3b8;
  margin-top: 4px;
}

/* S86.12: CFO Inputs panel */
.cfo-sub-title { font-size: 0.7rem; font-weight: 400; font-style: italic; color: #94a3b8; margin-left: 0.5rem; }
.cfo-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1rem; }
.cfo-field { display: flex; flex-direction: column; gap: 0.35rem; }
.cfo-field label { font-size: 0.78rem; color: #cbd5e1; font-weight: 600; }
.cfo-field input { background: #0f172a; border: 1px solid #334155; border-radius: 8px; padding: 0.55rem 0.7rem; color: #f1f5f9; font-size: 0.95rem; }
.cfo-field input:focus { outline: none; border-color: #6366f1; }
.cfo-hint { font-size: 0.68rem; color: #64748b; font-style: italic; }
.cfo-actions { display: flex; align-items: center; gap: 1rem; margin-top: 1.1rem; }
.cfo-save-btn { background: #6366f1; color: #fff; border: none; border-radius: 8px; padding: 0.6rem 1.2rem; font-size: 0.9rem; font-weight: 600; cursor: pointer; }
.cfo-save-btn:hover:not(:disabled) { background: #4f46e5; }
.cfo-save-btn:disabled { opacity: 0.6; cursor: default; }
.cfo-ok { color: #4ade80; font-size: 0.85rem; font-weight: 600; }
.cfo-err { color: #f87171; font-size: 0.85rem; }
</style>
