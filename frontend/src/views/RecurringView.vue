<script setup>
// S82: Recurring Transactions Manager (read-only)
// Lists all isRecurring=true transactions for the selected entity,
// joined with their recurrence_patterns, grouped by Project (OpEx + per-project).
// Edit/Delete reserved for S83.
import { ref, computed, onMounted, watch } from 'vue'
import api from '@/api'

// S77 pattern: inline ENTITY_MAP (no shared utils file yet)
const ENTITY_MAP_S82 = {
  next2me:      '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:        'dea1f32c-7b30-4981-b625-633da9dbe71e',
  next2megroup: '50317f44-9961-4fb4-add0-7a118e32dc14',
}

const ENTITY_LABELS = {
  next2me:      'Next2Me',
  house:        'House',
  next2megroup: 'Next2Me Group',
}

// ── State ─────────────────────────────────────────────────────
const loading       = ref(false)
const error         = ref(null)
const recurringTxns = ref([])
const patterns      = ref([])         // keyed by id for lookup
const projects      = ref([])         // for name lookup
const entityKey     = ref(localStorage.getItem('n2c_entity') || 'next2me')

// ── Helpers ──────────────────────────────────────────────────
function fmtMoney(amount, currency) {
  const cur = currency || 'EUR'
  const num = Number(amount) || 0
  return num.toLocaleString('el-GR', {
    style: 'currency',
    currency: cur,
    minimumFractionDigits: num % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  })
}

function fmtDate(iso) {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    if (isNaN(d.getTime())) return iso
    const dd = String(d.getDate()).padStart(2, '0')
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const yy = String(d.getFullYear()).slice(-2)
    return `${dd}/${mm}/${yy}`
  } catch (e) {
    return iso
  }
}

const FREQUENCY_LABELS = {
  DAILY:     'Ημερήσια',
  WEEKLY:    'Εβδομαδιαία',
  MONTHLY:   'Μηνιαία',
  QUARTERLY: 'Τριμηνιαία',
  YEARLY:    'Ετήσια',
  CUSTOM:    'Custom',
}

function fmtFrequency(pattern) {
  if (!pattern) return '—'
  const base = FREQUENCY_LABELS[pattern.frequency] || pattern.frequency
  const interval = Number(pattern.intervalCount) || 1
  if (interval === 1) return base
  // e.g. "Κάθε 2 μήνες" for MONTHLY interval=2
  if (pattern.frequency === 'MONTHLY')   return `Κάθε ${interval} μήνες`
  if (pattern.frequency === 'QUARTERLY') return `Κάθε ${interval} τρίμηνα`
  if (pattern.frequency === 'YEARLY')    return `Κάθε ${interval} έτη`
  if (pattern.frequency === 'WEEKLY')    return `Κάθε ${interval} εβδομάδες`
  if (pattern.frequency === 'DAILY')     return `Κάθε ${interval} ημέρες`
  return `${base} ×${interval}`
}

// Monthly-normalized amount for summary aggregation
function monthlyEquivalent(txn, pattern) {
  const amt = Number(txn.amount) || 0
  if (!pattern) return 0
  const interval = Number(pattern.intervalCount) || 1
  switch (pattern.frequency) {
    case 'MONTHLY':   return amt / interval
    case 'QUARTERLY': return amt / (3 * interval)
    case 'YEARLY':    return amt / (12 * interval)
    case 'WEEKLY':    return (amt * 52) / (12 * interval)
    case 'DAILY':     return (amt * 365) / (12 * interval)
    default:          return amt
  }
}

const patternById = computed(() => {
  const m = {}
  for (const p of patterns.value) {
    if (p && p.id) m[p.id] = p
  }
  return m
})

const projectById = computed(() => {
  const m = {}
  for (const p of projects.value) {
    if (p && p.id) m[p.id] = p
  }
  return m
})

// Build grouped structure: [{ key, label, items: [...], monthlyTotal }]
const grouped = computed(() => {
  const groups = new Map()
  const opExKey = '__opex__'

  for (const txn of recurringTxns.value) {
    const pat = patternById.value[txn.recurrencePatternId] || null
    const pid = txn.projectId || null
    const groupKey = pid || opExKey
    const groupLabel = pid
      ? (projectById.value[pid]?.name || 'Project')
      : 'OpEx (Γενικά Εταιρείας)'

    if (!groups.has(groupKey)) {
      groups.set(groupKey, {
        key: groupKey,
        label: groupLabel,
        isOpEx: groupKey === opExKey,
        items: [],
        monthlyTotal: 0,
      })
    }
    const g = groups.get(groupKey)
    g.items.push({
      id:          txn.id,
      description: txn.description,
      amount:      txn.amount,
      currency:    txn.currency || 'EUR',
      type:        txn.type,
      pattern:     pat,
      startDate:   pat?.startDate || txn.docDate,
      endDate:     pat?.endDate || null,
      monthly:     monthlyEquivalent(txn, pat),
    })
    g.monthlyTotal += monthlyEquivalent(txn, pat)
  }

  // OpEx group first, then alphabetical by label
  const arr = Array.from(groups.values())
  arr.sort((a, b) => {
    if (a.isOpEx && !b.isOpEx) return -1
    if (!a.isOpEx && b.isOpEx) return 1
    return a.label.localeCompare(b.label, 'el')
  })
  return arr
})

const grandMonthlyTotal = computed(() => {
  return grouped.value.reduce((s, g) => s + g.monthlyTotal, 0)
})

// ── Load ──────────────────────────────────────────────────────
async function loadAll() {
  loading.value = true
  error.value = null
  recurringTxns.value = []
  patterns.value = []
  projects.value = []

  const entityId = ENTITY_MAP_S82[entityKey.value]
  if (!entityId) {
    error.value = 'Άκυρο entity.'
    loading.value = false
    return
  }

  try {
    // 1) All transactions for the entity (we filter client-side for isRecurring)
    const txnRes = await api.get('/api/transactions', {
      params: { entityId, perPage: 10000 },
    })
    const allTxns = Array.isArray(txnRes.data?.data) ? txnRes.data.data : (Array.isArray(txnRes.data) ? txnRes.data : [])
    recurringTxns.value = allTxns.filter(t => t.isRecurring === true)

    // 2) Patterns for this entity
    try {
      const patRes = await api.get('/api/recurrence-patterns', { params: { entityId } })
      patterns.value = Array.isArray(patRes.data?.data) ? patRes.data.data : (Array.isArray(patRes.data) ? patRes.data : [])
    } catch (pe) {
      console.warn('Pattern fetch failed (non-blocking):', pe)
      patterns.value = []
    }

    // 3) Projects (for name lookup; non-blocking)
    try {
      const prjRes = await api.get('/api/projects', { params: { entityId, activeOnly: false } })
      projects.value = Array.isArray(prjRes.data?.data) ? prjRes.data.data : []
    } catch (prje) {
      console.warn('Projects fetch failed (non-blocking):', prje)
      projects.value = []
    }
  } catch (e) {
    console.error('loadAll error:', e)
    if (e.response?.status === 403) {
      error.value = 'Δεν έχετε δικαίωμα πρόσβασης σε αυτή τη σελίδα.'
    } else {
      error.value = 'Σφάλμα σύνδεσης με τον server.'
    }
  } finally {
    loading.value = false
  }
}

// Reload when entity changes via localStorage from anywhere else in the app
watch(entityKey, () => {
  localStorage.setItem('n2c_entity', entityKey.value)
  loadAll()
})

onMounted(loadAll)
</script>

<template>
  <div class="recurring-page">

    <!-- Header -->
    <div class="page-header">
      <div class="page-title">
        <span class="page-icon">🔁</span>
        <h1>Επαναλαμβανόμενες Συναλλαγές</h1>
      </div>
      <div class="page-subtitle">
        Πάγια έσοδα/έξοδα που επαναλαμβάνονται αυτόματα · {{ recurringTxns.length }} εγγραφές
      </div>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <label class="entity-label">Entity:</label>
        <select v-model="entityKey" class="entity-select">
          <option value="next2me">{{ ENTITY_LABELS.next2me }}</option>
          <option value="house">{{ ENTITY_LABELS.house }}</option>
          <option value="next2megroup">{{ ENTITY_LABELS.next2megroup }}</option>
        </select>
      </div>
      <button class="refresh-btn" @click="loadAll" :disabled="loading">
        <span v-if="loading">Φόρτωση…</span>
        <span v-else>↻ Ανανέωση</span>
      </button>
    </div>

    <!-- Error -->
    <div v-if="error" class="error-banner">{{ error }}</div>

    <!-- Loading -->
    <div v-else-if="loading" class="loading-state">
      Φόρτωση…
    </div>

    <!-- Empty -->
    <div v-else-if="grouped.length === 0" class="empty-state">
      <div class="empty-icon">📋</div>
      <h3>Καμία επαναλαμβανόμενη συναλλαγή</h3>
      <p>
        Δεν έχει δημιουργηθεί ακόμα κάποια προγραμματισμένη επαναλαμβανόμενη συναλλαγή
        για το {{ ENTITY_LABELS[entityKey] }}.
      </p>
      <p class="empty-hint">
        Δοκίμασε από <strong>Νέα Καταχώριση → Προγραμματισμένη</strong> και τσέκαρε
        <em>«Είναι επαναλαμβανόμενη»</em>.
      </p>
    </div>

    <!-- Groups -->
    <div v-else>
      <div
        v-for="group in grouped"
        :key="group.key"
        class="group-card"
      >
        <div class="group-header">
          <div class="group-title">
            <span class="group-icon">{{ group.isOpEx ? '🏢' : '🎯' }}</span>
            <span class="group-label">{{ group.label }}</span>
          </div>
          <div class="group-total">
            Μηνιαίο σύνολο:
            <span class="group-total-amount">{{ fmtMoney(group.monthlyTotal, 'EUR') }}</span>
          </div>
        </div>

        <table class="group-table">
          <thead>
            <tr>
              <th class="th-desc">Περιγραφή</th>
              <th class="th-amount">Ποσό</th>
              <th>Συχνότητα</th>
              <th>Έναρξη</th>
              <th>Λήξη</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in group.items"
              :key="item.id"
              :class="{ 'is-income': item.type === 'income' }"
            >
              <td class="td-desc">
                <span v-if="item.type === 'income'" class="type-pill type-income">💰</span>
                {{ item.description }}
              </td>
              <td class="td-amount">{{ fmtMoney(item.amount, item.currency) }}</td>
              <td>{{ fmtFrequency(item.pattern) }}</td>
              <td>{{ fmtDate(item.startDate) }}</td>
              <td class="td-end">
                <span v-if="item.endDate">{{ fmtDate(item.endDate) }}</span>
                <span v-else class="muted">Αόριστη</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Grand total -->
      <div class="grand-total">
        <span class="grand-total-label">Σύνολο όλων των κατηγοριών (μηνιαίο):</span>
        <span class="grand-total-amount">{{ fmtMoney(grandMonthlyTotal, 'EUR') }}</span>
      </div>
    </div>

  </div>
</template>

<style scoped>
.recurring-page {
  padding: 24px;
  max-width: 1280px;
  margin: 0 auto;
  color: #e4e6eb;
}

/* Header */
.page-header { margin-bottom: 20px; }
.page-title { display: flex; align-items: center; gap: 12px; }
.page-icon { font-size: 28px; }
.page-title h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #fff;
}
.page-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #8b949e;
}

/* Toolbar */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 12px 16px;
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 8px;
}
.toolbar-left { display: flex; align-items: center; gap: 10px; }
.entity-label { font-size: 13px; color: #8b949e; }
.entity-select {
  background: #0d1117;
  color: #e4e6eb;
  border: 1px solid #30363d;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 13px;
  cursor: pointer;
}
.entity-select:hover { border-color: #58a6ff; }
.refresh-btn {
  background: #21262d;
  color: #e4e6eb;
  border: 1px solid #30363d;
  border-radius: 6px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
}
.refresh-btn:hover:not(:disabled) { background: #30363d; }
.refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* Error / Loading / Empty */
.error-banner {
  background: #3d1a1a;
  color: #ff8a80;
  border: 1px solid #5c2626;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.loading-state {
  text-align: center;
  padding: 40px;
  color: #8b949e;
}
.empty-state {
  text-align: center;
  padding: 60px 20px;
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
}
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-state h3 { margin: 0 0 8px; color: #e4e6eb; font-weight: 600; }
.empty-state p { color: #8b949e; max-width: 480px; margin: 0 auto 8px; }
.empty-hint { font-size: 13px; color: #6e7681; }
.empty-hint em { color: #ffa657; font-style: normal; }
.empty-hint strong { color: #79c0ff; font-weight: 600; }

/* Group cards */
.group-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  margin-bottom: 16px;
  overflow: hidden;
}
.group-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 14px 18px;
  border-bottom: 1px solid #30363d;
  background: #1c2128;
}
.group-title { display: flex; align-items: center; gap: 10px; }
.group-icon { font-size: 18px; }
.group-label {
  font-size: 15px;
  font-weight: 600;
  color: #e4e6eb;
}
.group-total {
  font-size: 13px;
  color: #8b949e;
}
.group-total-amount {
  margin-left: 6px;
  color: #79c0ff;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* Table */
.group-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.group-table thead th {
  text-align: left;
  padding: 10px 14px;
  font-weight: 500;
  color: #8b949e;
  background: #0d1117;
  border-bottom: 1px solid #30363d;
}
.group-table thead th.th-amount { text-align: right; }
.group-table tbody td {
  padding: 11px 14px;
  border-top: 1px solid #21262d;
  color: #e4e6eb;
}
.group-table tbody tr:first-child td { border-top: none; }
.group-table tbody tr:hover { background: #1c2128; }

.td-desc { max-width: 320px; }
.td-amount {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}
.td-end { color: #8b949e; }
.muted { color: #6e7681; font-style: italic; }

.type-pill {
  display: inline-block;
  margin-right: 6px;
  font-size: 13px;
}

tr.is-income .td-amount { color: #56d364; }

/* Grand total */
.grand-total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 20px;
  padding: 14px 18px;
  background: #0f3024;
  border: 1px solid #1f5c45;
  border-radius: 8px;
}
.grand-total-label {
  font-size: 14px;
  font-weight: 500;
  color: #56d364;
}
.grand-total-amount {
  font-size: 18px;
  font-weight: 600;
  color: #b5ffc8;
  font-variant-numeric: tabular-nums;
}

/* Responsive */
@media (max-width: 720px) {
  .recurring-page { padding: 12px; }
  .toolbar { flex-direction: column; gap: 10px; align-items: stretch; }
  .group-header { flex-direction: column; gap: 6px; align-items: flex-start; }
  .group-table { font-size: 12px; }
  .group-table thead th, .group-table tbody td { padding: 8px 10px; }
}
</style>
