<script setup>
// S71-B: Projects Portfolio view
// Reads /api/projects (production-verified S71-A) and renders cards per spec section 5.5.
// Spent/progress calculations are intentional placeholders until S71-D backend aggregation.
import { ref, computed, onMounted } from 'vue'
import api from '@/api'

// ── State ───────────────────────────────────────────────────────────
const loading = ref(false)
const error = ref(null)
const projects = ref([])

// ── Filters ─────────────────────────────────────────────────────────
const STATUS_LABELS = {
  PLANNING: 'Σχεδιασμός',
  IN_DEVELOPMENT: 'Σε Ανάπτυξη',
  TESTING: 'Δοκιμές',
  LIVE: 'Σε Παραγωγή',
  PAUSED: 'Παύση',
  CANCELLED: 'Ακυρωμένο',
}

const STATUS_FILTER_OPTIONS = [
  { value: 'all', label: 'Όλες οι καταστάσεις' },
  { value: 'PLANNING', label: 'Σχεδιασμός' },
  { value: 'IN_DEVELOPMENT', label: 'Σε Ανάπτυξη' },
  { value: 'TESTING', label: 'Δοκιμές' },
  { value: 'LIVE', label: 'Σε Παραγωγή' },
  { value: 'PAUSED', label: 'Παύση' },
  { value: 'CANCELLED', label: 'Ακυρωμένο' },
]

const SORT_OPTIONS = [
  { value: 'name', label: 'Όνομα (Α-Ω)' },
  { value: 'budget_desc', label: 'Budget (φθίνον)' },
  { value: 'budget_asc', label: 'Budget (αύξον)' },
  { value: 'status', label: 'Κατάσταση' },
]

const statusFilter = ref('all')
const sortBy = ref('name')
const showInactive = ref(false)

// ── Computed: filtered + sorted projects ────────────────────────────
const filteredProjects = computed(() => {
  let list = projects.value.slice()

  if (!showInactive.value) {
    list = list.filter(p => p.isActive !== false)
  }
  if (statusFilter.value !== 'all') {
    list = list.filter(p => p.status === statusFilter.value)
  }

  switch (sortBy.value) {
    case 'budget_desc':
      list.sort((a, b) => (parseFloat(b.totalBudget) || 0) - (parseFloat(a.totalBudget) || 0))
      break
    case 'budget_asc':
      list.sort((a, b) => (parseFloat(a.totalBudget) || 0) - (parseFloat(b.totalBudget) || 0))
      break
    case 'status':
      list.sort((a, b) => (a.status || '').localeCompare(b.status || ''))
      break
    case 'name':
    default:
      list.sort((a, b) => (a.name || '').localeCompare(b.name || '', 'el'))
  }

  return list
})

// ── Totals (header summary) ─────────────────────────────────────────
const totals = computed(() => {
  let budget = 0
  let monthlyRev = 0
  let count = 0
  for (const p of filteredProjects.value) {
    budget += parseFloat(p.totalBudget) || 0
    monthlyRev += parseFloat(p.expectedMonthlyRevenue) || 0
    count++
  }
  return { budget, monthlyRev, count }
})

// ── Formatters ──────────────────────────────────────────────────────
function fmtMoney(n) {
  const v = parseFloat(n) || 0
  return new Intl.NumberFormat('el-GR', {
    style: 'currency',
    currency: 'EUR',
    maximumFractionDigits: 0,
  }).format(v)
}

function fmtDate(d) {
  if (!d) return '—'
  const date = new Date(d)
  if (isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('el-GR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function statusBadgeStyle(status) {
  const colors = {
    PLANNING: { bg: '#1e3a5f', fg: '#4A9EFF' },
    IN_DEVELOPMENT: { bg: '#3d2f1a', fg: '#F59E0B' },
    TESTING: { bg: '#2a1f3d', fg: '#A78BFA' },
    LIVE: { bg: '#1a3d2f', fg: '#4FC3A1' },
    PAUSED: { bg: '#3d2a1a', fg: '#FB923C' },
    CANCELLED: { bg: '#3d1a1a', fg: '#EF4444' },
  }
  const c = colors[status] || { bg: '#2a4a6a', fg: '#b0bec5' }
  return `background-color: ${c.bg}; color: ${c.fg};`
}

function statusLabel(status) {
  return STATUS_LABELS[status] || status || '—'
}

// Timeline progress: % of elapsed time between start_date and target_completion_date
function timelineProgress(p) {
  if (!p.startDate || !p.targetCompletionDate) return null
  const start = new Date(p.startDate).getTime()
  const end = new Date(p.targetCompletionDate).getTime()
  const now = Date.now()
  if (isNaN(start) || isNaN(end) || end <= start) return null
  if (now <= start) return 0
  if (now >= end) return 100
  return Math.round(((now - start) / (end - start)) * 100)
}

// ── Load projects ───────────────────────────────────────────────────
async function loadProjects() {
  loading.value = true
  error.value = null
  try {
    // Backend supports activeOnly=false to include inactive
    // S77-PATCH-APPLIED S77-HOTFIX-APPLIED: inline ENTITY_MAP (this file does not import it)
    const ENTITY_MAP_S77 = {
      next2me:      '58202b71-4ddb-45c9-8e3c-39e816bde972',
      house:        'dea1f32c-7b30-4981-b625-633da9dbe71e',
      next2megroup: '50317f44-9961-4fb4-add0-7a118e32dc14',
    }
    const entityKey = localStorage.getItem('n2c_entity') || 'next2me'
    const entityIdForQuery = ENTITY_MAP_S77[entityKey] || null
    const res = await api.get('/api/projects', {
      params: { entityId: entityIdForQuery, activeOnly: showInactive.value ? false : true },
    })
    if (res.data && res.data.success) {
      projects.value = Array.isArray(res.data.data) ? res.data.data : []
    } else {
      error.value = res.data?.error || 'Σφάλμα φόρτωσης projects'
    }
  } catch (e) {
    console.error('loadProjects error:', e)
    if (e.response?.status === 403) {
      error.value = 'Δεν έχετε δικαίωμα πρόσβασης σε αυτή τη σελίδα.'
    } else {
      error.value = 'Σφάλμα σύνδεσης με τον server.'
    }
  } finally {
    loading.value = false
  }
}

// Reload when showInactive toggle changes
async function toggleInactive() {
  showInactive.value = !showInactive.value
  await loadProjects()
}

onMounted(loadProjects)
</script>

<template>
  <div class="projects-page">
    <!-- Header -->
    <div class="page-header">
      <div class="page-title">
        <span class="page-icon">🎯</span>
        <h1>Projects Portfolio</h1>
      </div>
      <div class="page-subtitle">
        Επισκόπηση όλων των projects του ομίλου · {{ totals.count }} έργα
      </div>
    </div>

    <!-- Filters bar -->
    <div class="filters-bar">
      <div class="filter-group">
        <label>Κατάσταση:</label>
        <select v-model="statusFilter" class="filter-select">
          <option v-for="o in STATUS_FILTER_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </div>
      <div class="filter-group">
        <label>Ταξινόμηση:</label>
        <select v-model="sortBy" class="filter-select">
          <option v-for="o in SORT_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </div>
      <div class="filter-group">
        <button class="toggle-btn" :class="{ active: showInactive }" @click="toggleInactive">
          {{ showInactive ? '✓ ' : '' }}Συμπερίληψη ανενεργών
        </button>
      </div>
      <div class="filter-spacer"></div>
      <button class="btn-reload" @click="loadProjects" :disabled="loading">
        ↻ Ανανέωση
      </button>
    </div>

    <!-- Summary strip -->
    <div class="summary-strip">
      <div class="summary-item">
        <div class="summary-label">Σύνολο Budget</div>
        <div class="summary-value">{{ fmtMoney(totals.budget) }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">Αναμενόμενα Έσοδα / μήνα</div>
        <div class="summary-value">{{ fmtMoney(totals.monthlyRev) }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">Πλήθος Projects</div>
        <div class="summary-value">{{ totals.count }}</div>
      </div>
    </div>

    <!-- Loading / Error / Empty states -->
    <div v-if="loading" class="state-box">
      <div class="spinner"></div>
      <div>Φόρτωση projects...</div>
    </div>

    <div v-else-if="error" class="state-box state-error">
      <div class="state-icon">⚠️</div>
      <div>{{ error }}</div>
      <button class="btn-retry" @click="loadProjects">Δοκιμή ξανά</button>
    </div>

    <div v-else-if="filteredProjects.length === 0" class="state-box">
      <div class="state-icon">📭</div>
      <div>Δεν βρέθηκαν projects με τα τρέχοντα φίλτρα.</div>
    </div>

    <!-- Cards grid -->
    <div v-else class="cards-grid">
      <div
        v-for="p in filteredProjects"
        :key="p.id"
        class="project-card"
        :style="{ borderLeftColor: p.color || '#4A9EFF' }"
      >
        <!-- Card header -->
        <div class="card-header">
          <div class="card-title-wrap">
            <span class="color-dot" :style="{ backgroundColor: p.color || '#4A9EFF' }"></span>
            <h3 class="card-title">{{ p.name }}</h3>
          </div>
          <span class="status-badge" :style="statusBadgeStyle(p.status)">{{ statusLabel(p.status) }}</span>
        </div>

        <!-- Description -->
        <div v-if="p.description" class="card-description">{{ p.description }}</div>

        <!-- Budget row -->
        <div class="card-row">
          <div class="row-label">Budget:</div>
          <div class="row-value row-value-big">{{ fmtMoney(p.totalBudget) }}</div>
        </div>

        <!-- Spent placeholder (S71-D will fill) -->
        <div class="spent-placeholder">
          <div class="placeholder-label">Δαπάνες & πρόοδος budget</div>
          <div class="placeholder-text">
            Διαθέσιμο στο Project Deep-Dive (επόμενη φάση)
          </div>
          <div class="progress-bar progress-placeholder">
            <div class="progress-fill" style="width: 0%"></div>
          </div>
        </div>

        <!-- Timeline -->
        <div v-if="p.startDate || p.targetCompletionDate" class="timeline-block">
          <div class="timeline-dates">
            <span>{{ fmtDate(p.startDate) }}</span>
            <span class="timeline-arrow">→</span>
            <span>{{ fmtDate(p.targetCompletionDate) }}</span>
          </div>
          <div v-if="timelineProgress(p) !== null" class="progress-bar">
            <div class="progress-fill progress-fill-timeline" :style="{ width: timelineProgress(p) + '%' }"></div>
          </div>
          <div v-if="timelineProgress(p) !== null" class="timeline-info">
            {{ timelineProgress(p) }}% του χρονοδιαγράμματος
          </div>
        </div>

        <!-- Revenue expectation -->
        <div v-if="parseFloat(p.expectedMonthlyRevenue) > 0" class="revenue-row">
          <span class="revenue-label">📈 Αναμενόμενα έσοδα:</span>
          <span class="revenue-value">{{ fmtMoney(p.expectedMonthlyRevenue) }} / μήνα</span>
        </div>

        <!-- Footer: actions -->
        <div class="card-footer">
          <!-- S75-MARKER-DETAILS-LINK -->
          <router-link class="btn-details btn-details-active" :to="`/projects/${p.id}`">
            Λεπτομέρειες →
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.projects-page {
  padding: 20px 24px;
  color: #e0e6ed;
  min-height: 100vh;
}

/* ── Header ─────────────────────────────────────── */
.page-header {
  margin-bottom: 18px;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.page-icon {
  font-size: 1.6rem;
}
.page-title h1 {
  margin: 0;
  font-size: 1.5rem;
  color: #e0e6ed;
}
.page-subtitle {
  font-size: 0.85rem;
  color: #8899aa;
  margin-top: 4px;
}

/* ── Filters bar ────────────────────────────────── */
.filters-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
  padding: 12px 14px;
  background: #142536;
  border: 1px solid #2a4a6a;
  border-radius: 8px;
}
.filter-group {
  display: flex;
  align-items: center;
  gap: 6px;
}
.filter-group label {
  font-size: 0.82rem;
  color: #8899aa;
}
.filter-select {
  background: #1a2f45;
  border: 1px solid #2a4a6a;
  color: #e0e6ed;
  padding: 6px 10px;
  border-radius: 5px;
  font-size: 0.85rem;
  cursor: pointer;
}
.filter-select:focus {
  outline: none;
  border-color: #4A9EFF;
}
.toggle-btn {
  background: #1a2f45;
  border: 1px solid #2a4a6a;
  color: #b0bec5;
  padding: 6px 12px;
  border-radius: 5px;
  font-size: 0.82rem;
  cursor: pointer;
  transition: all 0.15s;
}
.toggle-btn.active {
  background: #1a3d2f;
  border-color: #4FC3A1;
  color: #4FC3A1;
}
.toggle-btn:hover {
  border-color: #4A9EFF;
}
.filter-spacer {
  flex: 1;
}
.btn-reload {
  background: #1a2f45;
  border: 1px solid #2a4a6a;
  color: #4A9EFF;
  padding: 6px 12px;
  border-radius: 5px;
  font-size: 0.82rem;
  cursor: pointer;
}
.btn-reload:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-reload:hover:not(:disabled) {
  border-color: #4A9EFF;
}

/* ── Summary strip ──────────────────────────────── */
.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}
.summary-item {
  background: #142536;
  border: 1px solid #2a4a6a;
  border-radius: 8px;
  padding: 12px 16px;
}
.summary-label {
  font-size: 0.72rem;
  color: #8899aa;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}
.summary-value {
  font-size: 1.3rem;
  font-weight: 600;
  color: #e0e6ed;
}

/* ── State boxes (loading / error / empty) ─────── */
.state-box {
  background: #142536;
  border: 1px solid #2a4a6a;
  border-radius: 8px;
  padding: 40px 20px;
  text-align: center;
  color: #8899aa;
}
.state-box.state-error {
  border-color: #5a2a2a;
  color: #FCA5A5;
}
.state-icon {
  font-size: 2rem;
  margin-bottom: 12px;
}
.spinner {
  display: inline-block;
  width: 28px;
  height: 28px;
  border: 3px solid rgba(74, 158, 255, 0.2);
  border-top-color: #4A9EFF;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 14px;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.btn-retry {
  margin-top: 12px;
  background: #1a2f45;
  border: 1px solid #2a4a6a;
  color: #4A9EFF;
  padding: 7px 16px;
  border-radius: 5px;
  font-size: 0.85rem;
  cursor: pointer;
}
.btn-retry:hover {
  border-color: #4A9EFF;
}

/* ── Cards grid ────────────────────────────────── */
.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}
.project-card {
  background: #142536;
  border: 1px solid #2a4a6a;
  border-left: 4px solid #4A9EFF;
  border-radius: 8px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color 0.15s, transform 0.15s;
}
.project-card:hover {
  border-color: #4A9EFF;
  border-left-color: inherit;
  transform: translateY(-1px);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.card-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.color-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}
.card-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 600;
  color: #e0e6ed;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status-badge {
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 0.72rem;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.card-description {
  font-size: 0.83rem;
  color: #b0bec5;
  line-height: 1.4;
  padding-bottom: 4px;
  border-bottom: 1px solid #2a4a6a;
}

.card-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 0.85rem;
}
.row-label {
  color: #8899aa;
}
.row-value {
  color: #e0e6ed;
  font-weight: 500;
}
.row-value-big {
  font-size: 1.05rem;
  font-weight: 600;
}

/* ── Spent placeholder ─────────────────────────── */
.spent-placeholder {
  background: #1a2f45;
  border: 1px dashed #2a4a6a;
  border-radius: 6px;
  padding: 10px 12px;
}
.placeholder-label {
  font-size: 0.72rem;
  color: #8899aa;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  margin-bottom: 4px;
}
.placeholder-text {
  font-size: 0.8rem;
  color: #b0bec5;
  font-style: italic;
  margin-bottom: 6px;
}

/* ── Timeline ──────────────────────────────────── */
.timeline-block {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.timeline-dates {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.78rem;
  color: #b0bec5;
}
.timeline-arrow {
  color: #4A9EFF;
}
.timeline-info {
  font-size: 0.72rem;
  color: #8899aa;
  text-align: right;
}

/* ── Progress bars ─────────────────────────────── */
.progress-bar {
  height: 6px;
  background: #1a2f45;
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: #4FC3A1;
  border-radius: 3px;
  transition: width 0.3s ease;
}
.progress-fill-timeline {
  background: #4A9EFF;
}
.progress-placeholder .progress-fill {
  background: #2a4a6a;
}

/* ── Revenue ───────────────────────────────────── */
.revenue-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.83rem;
  padding-top: 4px;
}
.revenue-label {
  color: #8899aa;
}
.revenue-value {
  color: #4FC3A1;
  font-weight: 600;
}

/* ── Footer ────────────────────────────────────── */
.card-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 6px;
  border-top: 1px solid #2a4a6a;
}
.btn-details {
  background: transparent;
  border: 1px solid #2a4a6a;
  color: #8899aa;
  padding: 6px 12px;
  border-radius: 5px;
  font-size: 0.82rem;
  cursor: not-allowed;
}
.btn-details:disabled {
  opacity: 0.6;
}

/* ── Mobile ────────────────────────────────────── */
@media (max-width: 768px) {
  .projects-page {
    padding: 12px;
  }
  .summary-strip {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .summary-item {
    padding: 10px 12px;
  }
  .summary-value {
    font-size: 1.1rem;
  }
  .cards-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .filters-bar {
    padding: 10px;
    gap: 8px;
  }
}
</style>
