<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import api from '@/api'
import MarkPaidModal from '@/components/MarkPaidModal.vue'
import AttachmentsPopover from '@/components/AttachmentsPopover.vue'

const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14'
}

const entityKey = ref(localStorage.getItem('n2c_entity') || 'next2me')
const loading = ref(false)

// User role
const currentUser = computed(() => {
  try { return JSON.parse(localStorage.getItem('n2c_user') || '{}') } catch { return {} }
})
const canModify = computed(() => {
  const role = (currentUser.value.role || '').toLowerCase()
  return role === 'admin' || role === 'user'
})

// Mark paid state
const markPaidState = ref({ visible: false, transaction: null })
function openMarkPaid(t) {
  const txn = { ...t, entityId: ENTITIES[entityKey.value] }
  markPaidState.value = { visible: true, transaction: txn }
}
function closeMarkPaid() { markPaidState.value = { visible: false, transaction: null } }
function onMarkPaidSaved() { closeMarkPaid(); loadPayments() }
function canMarkPaid(t) {
  return t.recordSource !== 'PAYMENT' && (Number(t.remaining) || 0) > 0.01
}

// Attachments state
const attachmentsState = ref({ visible: false, transaction: null })
function openAttachments(t) { attachmentsState.value = { visible: true, transaction: t } }
function closeAttachments() { attachmentsState.value = { visible: false, transaction: null } }
function hasAttachments(t) { return !!t.blobFileIds && String(t.blobFileIds).trim() !== "" }
const allPayments = ref([])

const search = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const selectedStatus = ref('all')
const selectedCategory = ref('all')

const statusOptions = [
  { value: 'all', label: 'Όλα' },
  { value: 'unpaid', label: 'Απλήρωτες' },
  { value: 'urgent', label: 'Εκκρεμείς' },
  { value: 'partial', label: 'Μερ. Πληρωμένες' },
  { value: 'paid', label: 'Πληρωμένες' },
  { value: 'received', label: 'Εισπραχθείσες' }
]

const statusLabel = (s) => ({
  unpaid: 'Απλήρωτη', urgent: 'Εκκρεμεί', partial: 'Μερικώς',
  paid: 'Πληρώθηκε', received: 'Εισπράχθηκε'
}[s] || s)

const statusClass = (s) => ({
  unpaid: 'badge-red', urgent: 'badge-orange', partial: 'badge-orange',
  paid: 'badge-green', received: 'badge-teal'
}[s] || '')

async function loadPayments() {
  loading.value = true
  try {
    const entityId = ENTITIES[entityKey.value]
    const res = await api.get('/api/transactions', {
      params: { entityId, page: 0, perPage: 10000 }
    })
    if (res.data.success) {
      allPayments.value = res.data.data
        .filter(t => t.recordStatus !== 'void')
        .map(t => {
          const amount = parseFloat(t.amount || 0)
          const paid = parseFloat(t.amountPaid || 0)
          const remaining = Math.max(0, amount - paid)
          let status = t.paymentStatus || 'unpaid'
          if (status !== 'unpaid' && status !== 'urgent') {
            if (paid > 0 && paid < amount) status = 'partial'
            if (paid >= amount && amount > 0) status = t.type === 'income' ? 'received' : 'paid'
          }
          return {
            id: t.id,
            entityNumber: t.entityNumber || t.id,
            docDate: t.docDate,
            description: t.description || '',
            category: t.category || '',
            paymentMethod: t.paymentMethod || '',
            type: t.type,
            amount, paid, remaining, status,
            progress: amount > 0 ? Math.round((paid / amount) * 100) : 0
          }
        })
    }
  } catch (e) {
    console.error('loadPayments error:', e)
  } finally {
    loading.value = false
  }
}


// ── Universal Search helpers (legacy parity) ──────────────────────
function dateSearchFormats(dateStr) {
  if (!dateStr) return ''
  let dd = '', mm = '', yyyy = ''
  if (String(dateStr).includes('-')) {
    const p = String(dateStr).split('-'); yyyy = p[0]; mm = p[1]; dd = p[2]
  } else if (String(dateStr).includes('/')) {
    const p = String(dateStr).split('/'); dd = p[0]; mm = p[1]; yyyy = p[2]
  }
  if (!dd) return String(dateStr)
  const yy = yyyy.substring(2)
  return [
    dd+'/'+mm+'/'+yyyy, dd+'/'+mm+'/'+yy, dd+'-'+mm+'-'+yyyy, dd+'-'+mm+'-'+yy,
    dd+'/'+mm, mm+'/'+yyyy, mm+'/'+yy, yyyy+'-'+mm+'-'+dd, dd+'.'+mm+'.'+yyyy, dd+'.'+mm+'.'+yy
  ].join(' ')
}

function parseSearchAmount(q) {
  let s = q.replace(/\s/g, '').replace('€', '')
  if (/^\d{1,3}(\.\d{3})*(,\d{1,2})?$/.test(s)) { s = s.replace(/\./g, '').replace(',', '.'); const n = parseFloat(s); return isNaN(n) ? null : n }
  if (/^\d{1,3}(,\d{3})*(\.\d{1,2})?$/.test(s)) { s = s.replace(/,/g, ''); const n = parseFloat(s); return isNaN(n) ? null : n }
  s = s.replace(',', '.'); const n = parseFloat(s); return isNaN(n) ? null : n
}

function universalMatch(t, searchRaw) {
  if (!searchRaw) return true
  const words = searchRaw.trim().toLowerCase().split(/\s+/)
  return words.every(word => {
    const text = [
      String(t.entityNumber ?? t.id ?? ''),
      t.description ?? '', t.category ?? '', t.subcategory ?? t.account ?? '',
      t.counterparty ?? '', t.paymentMethod ?? '', t.status ?? t.paymentStatus ?? '',
      dateSearchFormats(t.docDate), dateSearchFormats(t.paymentDate),
      t.type === 'income' ? 'είσπραξη εισόδημα' : 'πληρωμή έξοδο',
      t.recordStatus ?? ''
    ].filter(Boolean).join(' ').toLowerCase()
    if (text.includes(word)) return true
    const searchAmt = parseSearchAmount(word)
    if (searchAmt !== null) {
      const fields = [t.amount, t.amountPaid, t.amountRemaining, t.remaining]
      for (const f of fields) {
        const v = parseFloat(f || 0)
        if (v > 0 && Math.abs(v - searchAmt) < 0.005) return true
      }
    }
    return false
  })
}

const filteredPayments = computed(() => {
  return allPayments.value.filter(p => {
    if (selectedStatus.value !== 'all' && p.status !== selectedStatus.value) return false
    if (selectedCategory.value !== 'all' && p.category !== selectedCategory.value) return false
    if (search.value) {
      if (!universalMatch(p, search.value)) return false
    }
    if (dateFrom.value && p.docDate < dateFrom.value) return false
    if (dateTo.value && p.docDate > dateTo.value) return false
    return true
  })
})

const categories = computed(() => {
  const cats = new Set()
  allPayments.value.forEach(p => { if (p.category) cats.add(p.category) })
  return ['all', ...Array.from(cats).sort()]
})

const stats = computed(() => {
  const all = filteredPayments.value
  const expenses = all.filter(p => p.type === 'expense')
  const incomes = all.filter(p => p.type === 'income')
  const unpaid = expenses.filter(p => p.status === 'unpaid' || p.status === 'urgent')
  const partial = expenses.filter(p => p.status === 'partial')
  const paid = expenses.filter(p => p.status === 'paid')
  const received = incomes.filter(p => p.status === 'received' || p.status === 'paid')

  const sum = arr => arr.reduce((s, p) => s + p.amount, 0)
  const sumRem = arr => arr.reduce((s, p) => s + p.remaining, 0)

  return {
    total: expenses.length, totalAmount: sum(expenses),
    unpaid: unpaid.length, unpaidAmount: sumRem(unpaid),
    partial: partial.length, partialAmount: sumRem(partial),
    paid: paid.length, paidAmount: sum(paid),
    received: received.length, receivedAmount: sum(received)
  }
})

const fmt = (n) => new Intl.NumberFormat('el-GR', {
  minimumFractionDigits: 2, maximumFractionDigits: 2
}).format(n) + ' €'

const fmtDate = (d) => {
  if (!d) return ''
  const parts = d.split('-')
  return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0].slice(2)}` : d
}

function onEntityChanged() {
  entityKey.value = localStorage.getItem('n2c_entity') || 'next2me'
  loadPayments()
}

onMounted(() => {
  loadPayments()
  window.addEventListener('entity-changed', onEntityChanged)
})
onUnmounted(() => {
  window.removeEventListener('entity-changed', onEntityChanged)
})
</script>

<template>
  <div class="plirome-page">
    <div class="summary-cards">
      <div class="sum-card">
        <div class="sum-label">{{ stats.total }} πληρωμές — Σύνολο Κινήσεων</div>
        <div class="sum-amount white">{{ fmt(stats.totalAmount) }}</div>
      </div>
      <div class="sum-card red">
        <div class="sum-label">{{ stats.unpaid }} κινήσεις — Απλήρωτες</div>
        <div class="sum-amount red">{{ fmt(stats.unpaidAmount) }}</div>
      </div>
      <div class="sum-card orange">
        <div class="sum-label">{{ stats.partial }} κινήσεις — Μερ. Πληρωμένες</div>
        <div class="sum-amount orange">{{ fmt(stats.partialAmount) }}</div>
      </div>
      <div class="sum-card green">
        <div class="sum-label">{{ stats.paid }} κινήσεις — Πληρωμένες</div>
        <div class="sum-amount green">{{ fmt(stats.paidAmount) }}</div>
      </div>
      <div class="sum-card teal">
        <div class="sum-label">{{ stats.received }} εισπράξεις — Εισπραχθείσες</div>
        <div class="sum-amount teal">{{ fmt(stats.receivedAmount) }}</div>
      </div>
    </div>

    <div class="filters-bar">
      <div class="search-wrap">
            <i class="fas fa-search search-icon"></i>
            <input v-model="search" class="search-input" placeholder="Αναζήτηση: ID, περιγραφή, ποσό, κατηγορία, ημ/νία..." />
            <button v-if="search" class="search-clear" @click="search = ''" title="Καθάρισμα">×</button>
          </div>
      <input v-model="dateFrom" type="date" class="filter-input" />
      <input v-model="dateTo" type="date" class="filter-input" />
      <select v-model="selectedStatus" class="filter-select">
        <option v-for="s in statusOptions" :key="s.value" :value="s.value">{{ s.label }}</option>
      </select>
      <select v-model="selectedCategory" class="filter-select">
        <option v-for="c in categories" :key="c" :value="c">{{ c === 'all' ? 'Όλες κατηγορίες' : c }}</option>
      </select>
      <button class="btn-refresh" @click="loadPayments" :disabled="loading">
        {{ loading ? '⟳ Φόρτωση...' : '↻ Ανανέωση' }}
      </button>
    </div>

    <div v-if="loading" class="loading-state">Φόρτωση δεδομένων...</div>
    <div v-else-if="filteredPayments.length === 0" class="empty-state">
      Δεν βρέθηκαν αποτελέσματα
    </div>
    <div v-else class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th><th>ΗΜ/ΝΙΑ</th><th>ΠΕΡΙΓΡΑΦΗ</th>
            <th>ΚΑΤΗΓΟΡΙΑ</th><th>ΜΕΘΟΔΟΣ</th>
            <th class="num">ΠΟΣΟ</th><th class="num">ΠΛΗΡΩΜΕΝΟ</th>
            <th class="num">ΥΠΟΛΟΙΠΟ</th><th>STATUS</th><th>ΠΡΟΟΔΟΣ</th>
            <th>ΕΝΕΡΓΕΙΕΣ</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in filteredPayments" :key="p.id">
            <td class="id-col">#{{ p.entityNumber }}</td>
            <td>{{ fmtDate(p.docDate) }}</td>
            <td class="desc-col">{{ p.description }}</td>
            <td><span class="cat-badge">{{ p.category }}</span></td>
            <td>{{ p.paymentMethod }}</td>
            <td class="num" :class="p.type === 'income' ? 'green' : 'red'">{{ fmt(p.amount) }}</td>
            <td class="num green">{{ fmt(p.paid) }}</td>
            <td class="num red">{{ fmt(p.remaining) }}</td>
            <td><span class="badge" :class="statusClass(p.status)">{{ statusLabel(p.status) }}</span></td>
            <td>
              <div class="progress-bar">
                <div class="progress-fill" :style="{ width: p.progress + '%' }"></div>
              </div>
              <span class="progress-text">{{ p.progress }}%</span>
            </td>
            <td class="actions-col">
              <button
                v-if="canMarkPaid(p)"
                class="btn-action btn-mark-paid-sm"
                @click="openMarkPaid(p)">
                ✓ Εξόφληση
              </button>
              <button
                class="btn-action btn-attach-sm"
                @click="openAttachments(p)"
                :style="hasAttachments(p) ? {} : { opacity: 0.45 }">
                📎
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

    <MarkPaidModal
      :visible="markPaidState.visible"
      :transaction="markPaidState.transaction"
      @close="closeMarkPaid"
      @saved="onMarkPaidSaved"
    />
    <AttachmentsPopover
      :visible="attachmentsState.visible"
      :transaction="attachmentsState.transaction"
      @close="closeAttachments"
    />

</template>

<style scoped>
.plirome-page { padding: 24px; color: #e0e6ed; }
.summary-cards { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
.sum-card { background: #1e3448; border-radius: 8px; padding: 16px 20px; flex: 1; min-width: 180px; border-top: 3px solid #4FC3A1; }
.sum-card.red { border-top-color: #ef5350; }
.sum-card.orange { border-top-color: #ff9800; }
.sum-card.green { border-top-color: #4FC3A1; }
.sum-card.teal { border-top-color: #29b6f6; }
.sum-label { font-size: 0.75rem; color: #8899aa; margin-bottom: 8px; }
.sum-amount { font-size: 1.2rem; font-weight: 700; }
.sum-amount.white { color: #fff; }
.sum-amount.red { color: #ef5350; }
.sum-amount.orange { color: #ff9800; }
.sum-amount.green { color: #4FC3A1; }
.sum-amount.teal { color: #29b6f6; }
.filters-bar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.search-wrap { position: relative; flex: 1; min-width: 220px; max-width: 420px; }
.search-icon { position: absolute; left: 10px; top: 50%; transform: translateY(-50%); color: var(--text-muted); font-size: .85rem; pointer-events: none; }
.search-input { width: 100%; background: var(--bg-input, #1e3448); border: 1px solid var(--border, #2a4a6a); color: var(--text-primary, #e0e6ed); padding: 8px 32px 8px 32px; border-radius: 8px; font-size: .85rem; }
.search-input:focus { outline: none; border-color: var(--accent, #4A9EFF); }
.search-input::placeholder { color: var(--text-muted, #6c7a8a); }
.search-clear { position: absolute; right: 6px; top: 50%; transform: translateY(-50%); background: none; border: none; color: var(--text-muted, #6c7a8a); font-size: 1.2rem; cursor: pointer; padding: 2px 6px; border-radius: 4px; line-height: 1; }
.search-clear:hover { color: var(--text-primary, #e0e6ed); background: rgba(255,255,255,0.08); }
.filter-input, .filter-select { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; }
.btn-refresh { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 16px; border-radius: 6px; cursor: pointer; }
.btn-refresh:disabled { opacity: 0.5; cursor: wait; }
.loading-state, .empty-state { padding: 60px 20px; text-align: center; color: #8899aa; }
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.data-table th { background: #1a2f45; color: #8899aa; padding: 10px 12px; text-align: left; font-size: 0.72rem; letter-spacing: 0.5px; border-bottom: 1px solid #2a4a6a; }
.data-table td { padding: 10px 12px; border-bottom: 1px solid #1e3448; }
.data-table tr:hover { background: #1e3448; }
.id-col { color: #8899aa; font-size: 0.8rem; font-family: monospace; }
.desc-col { max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cat-badge { background: #2a4a6a; padding: 2px 8px; border-radius: 4px; font-size: 0.75rem; }
.num { text-align: right; font-family: monospace; }
.green { color: #4FC3A1; }
.red { color: #ef5350; }
.badge { padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; }
.badge-orange { background: rgba(255,152,0,0.15); color: #ff9800; }
.badge-red { background: rgba(239,83,80,0.15); color: #ef5350; }
.badge-green { background: rgba(79,195,161,0.15); color: #4FC3A1; }
.badge-teal { background: rgba(41,182,246,0.15); color: #29b6f6; }
.progress-bar { background: #2a4a6a; border-radius: 4px; height: 6px; width: 80px; display: inline-block; vertical-align: middle; }
.progress-fill { background: #4FC3A1; height: 6px; border-radius: 4px; }
.progress-text { font-size: 0.75rem; color: #8899aa; margin-left: 6px; }

.actions-col { display: flex; gap: 6px; align-items: center; }
.btn-action {
  padding: 4px 10px; border-radius: 5px; font-size: 0.78rem;
  font-weight: 600; cursor: pointer; border: 1px solid #2c3e50;
  background: transparent; transition: all 0.15s; white-space: nowrap;
}
.btn-mark-paid-sm { color: #4FC3A1; border-color: #4FC3A1; }
.btn-mark-paid-sm:hover { background: #4FC3A1; color: #0d1f2d; }
.btn-attach-sm { color: #9aa5b1; font-size: 0.85rem; }
.btn-attach-sm:hover { border-color: #4A9EFF; color: #4A9EFF; }
</style>
