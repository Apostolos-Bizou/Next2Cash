<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import api from '@/api'
import MarkPaidModal from '@/components/MarkPaidModal.vue'

const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14'
}

const entityKey = ref(localStorage.getItem('n2c_entity') || 'next2me')
const loading = ref(false)
const allObligations = ref([])
const activeTab = ref('list')

// Current user role
const currentUser = computed(() => {
  try { return JSON.parse(localStorage.getItem('n2c_user') || '{}') } catch { return {} }
})
const canModify = computed(() => {
  const role = (currentUser.value.role || '').toLowerCase()
  return role === 'admin' || role === 'user'
})

const search = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const selectedStatus = ref('unpaid_urgent')
const selectedCategory = ref('all')
const selectedMethod = ref('all')
const attachmentsState = ref({ visible: false, transaction: null })

function openAttachments(t) {
  attachmentsState.value = { visible: true, transaction: t }
}
function closeAttachments() {
  attachmentsState.value = { visible: false, transaction: null }
}
function hasAttachments(t) {
  return !!t.blobFileIds && String(t.blobFileIds).trim() !== ""
}
const analysisYear = ref(new Date().getFullYear())

const statusOptions = [
  { value: 'unpaid_urgent', label: 'Απλήρωτες + Εκκρεμείς' },
  { value: 'unpaid', label: 'Μόνο Απλήρωτες' },
  { value: 'urgent', label: 'Μόνο Εκκρεμείς' },
  { value: 'partial', label: 'Μερ. Πληρωμένες' },
  { value: 'paid', label: 'Πληρωμένες' },
  { value: 'all', label: 'Όλες' }
]

const statusLabel = (s) => ({
  unpaid: 'Απλήρωτη', urgent: 'Εκκρεμεί', partial: 'Μερικώς', paid: 'Πληρώθηκε'
}[s] || s)

const statusClass = (s) => ({
  unpaid: 'badge-red', urgent: 'badge-orange', partial: 'badge-orange', paid: 'badge-green'
}[s] || '')

// ───── Toast ─────
const toast = ref({ show: false, type: 'success', message: '' })
let toastTimer = null
function showToast(type, message) {
  toast.value = { show: true, type, message }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.value.show = false }, 3500)
}

// ───── Mark Paid state ─────
const markPaidState = ref({ visible: false, transaction: null })

async function loadObligations() {
  loading.value = true
  try {
    const entityId = ENTITIES[entityKey.value]
    const res = await api.get('/api/transactions', {
      params: { entityId, page: 0, perPage: 10000 }
    })
    if (res.data.success) {
      allObligations.value = res.data.data
        .filter(t => t.recordStatus !== 'void' && t.type === 'expense')
        .map(t => {
          const amount = parseFloat(t.amount || 0)
          const paid = parseFloat(t.amountPaid || 0)
          const remaining = Math.max(0, amount - paid)
          let status = t.paymentStatus || 'unpaid'
          if (status !== 'unpaid' && status !== 'urgent') {
            if (paid > 0 && paid < amount) status = 'partial'
            if (paid >= amount && amount > 0) status = 'paid'
          }
          return {
            id: t.id,
            entityNumber: t.entityNumber || t.id,
            docDate: t.docDate,
            description: t.description || '',
            category: t.category || '',
            account: t.account || '',
            paymentMethod: t.paymentMethod || '',
            amount, paid, remaining, status,
            _raw: t
          }
        })
    }
  } catch (e) {
    console.error('loadObligations error:', e)
    showToast('error', 'Σφάλμα φόρτωσης υποχρεώσεων')
  } finally {
    loading.value = false
  }
}

// ───── MARK PAID ─────
function openMarkPaid(o) {
  if (!canModify.value) return
  if (o.status === 'paid') { showToast('error', 'Η υποχρέωση είναι ήδη πληρωμένη'); return }
  // Build flat transaction shape expected by MarkPaidModal,
  // merging _raw (server fields) with the enriched wrapper fields.
  const raw = o._raw || {}
  const txn = {
    id:              o.id,
    entityId:        ENTITIES[entityKey.value],
    entityNumber:    o.entityNumber,
    amount:          Number(raw.amount ?? o.amount) || 0,
    amountPaid:      Number(raw.amountPaid ?? 0) || 0,
    amountRemaining: Number(raw.amountRemaining ?? ((raw.amount ?? o.amount) - (raw.amountPaid ?? 0))) || 0,
    type:            raw.type || 'expense',
    description:     raw.description || o.description || '',
    counterparty:    raw.counterparty || '',
    paymentMethod:   raw.paymentMethod || o.paymentMethod || 'Τράπεζα',
    category:        raw.category || ''
  }
  markPaidState.value = { visible: true, transaction: txn }
}

// Session 43B ? toggle urgent flag (unpaid <-> urgent)
async function toggleUrgent(o) {
  if (!canModify.value) return
  if (o.status !== 'unpaid' && o.status !== 'urgent') {
    showToast('error', 'Μόνο απλήρωτες μπορούν να σημανθούν ως εκκρεμείς')
    return
  }
  const newStatus = o.status === 'urgent' ? 'unpaid' : 'urgent'
  const prevStatus = o.status
  o.status = newStatus
  const raw = o._raw
  if (raw) raw.paymentStatus = newStatus
  try {
    const payload = {
      docDate: raw.docDate,
      description: raw.description,
      amount: raw.amount,
      type: raw.type,
      category: raw.category,
      account: raw.account,
      paymentMethod: raw.paymentMethod,
      paymentStatus: newStatus,
      paymentDate: raw.paymentDate || null,
      amountPaid: raw.amountPaid || 0
    }
    const res = await api.put('/api/transactions/' + o.id, payload)
    if (!res.data || res.data.success === false) {
      o.status = prevStatus
      if (raw) raw.paymentStatus = prevStatus
      showToast('error', (res.data && res.data.error) || 'Αποτυχία αλλαγής')
      return
    }
    showToast('success', newStatus === 'urgent' ? 'Σημάνθηκε ως Εκκρεμής' : 'Επαναφορά σε Απλήρωτη')
  } catch (e) {
    o.status = prevStatus
    if (raw) raw.paymentStatus = prevStatus
    console.error('toggleUrgent error:', e)
    showToast('error', e.response?.data?.error || 'Σφάλμα σύνδεσης')
  }
}

function closeMarkPaid() {
  markPaidState.value = { visible: false, transaction: null }
}

async function onMarkPaidSaved() {
  closeMarkPaid()
  showToast('success', 'Η πληρωμή καταχωρήθηκε')
  await loadObligations()
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
      dateSearchFormats(t.dueDate),
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

const filteredObligations = computed(() => {
  return allObligations.value.filter(o => {
    if (selectedStatus.value === 'unpaid_urgent') {
      if (o.status !== 'unpaid' && o.status !== 'urgent') return false
    } else if (selectedStatus.value !== 'all' && o.status !== selectedStatus.value) {
      return false
    }
    if (selectedCategory.value !== 'all' && o.category !== selectedCategory.value) return false
    if (selectedMethod.value !== 'all' && o.paymentMethod !== selectedMethod.value) return false
    if (search.value) {
      if (!universalMatch(o, search.value)) return false
    }
    if (dateFrom.value && o.docDate < dateFrom.value) return false
    if (dateTo.value && o.docDate > dateTo.value) return false
    return true
  })
})

const categories = computed(() => {
  const cats = new Set()
  allObligations.value.forEach(o => { if (o.category) cats.add(o.category) })
  return ['all', ...Array.from(cats).sort()]
})

const methods = computed(() => {
  const m = new Set()
  allObligations.value.forEach(o => { if (o.paymentMethod) m.add(o.paymentMethod) })
  return ['all', ...Array.from(m).sort()]
})

const stats = computed(() => {
  const all = allObligations.value
  const unpaid = all.filter(o => o.status === 'unpaid')
  const urgent = all.filter(o => o.status === 'urgent')
  const partial = all.filter(o => o.status === 'partial')
  const paid = all.filter(o => o.status === 'paid')
  const unpaidAll = [...unpaid, ...urgent]
  const cash = unpaidAll.filter(o => ['Μετρητά', 'Απόδειξη'].includes(o.paymentMethod))
  const bank = unpaidAll.filter(o => !['Μετρητά', 'Απόδειξη'].includes(o.paymentMethod) && o.paymentMethod)

  const sum = arr => arr.reduce((s, o) => s + o.amount, 0)
  const sumRem = arr => arr.reduce((s, o) => s + o.remaining, 0)

  return {
    totalCount: all.length, totalAmount: sum(all),
    paidCount: paid.length, paidAmount: sum(paid),
    unpaidCount: unpaidAll.length, unpaidAmount: sumRem(unpaidAll),
    cashCount: cash.length, cashAmount: sumRem(cash),
    bankCount: bank.length, bankAmount: sumRem(bank),
    partialCount: partial.length, partialAmount: sumRem(partial),
    urgentCount: urgent.length, urgentAmount: sumRem(urgent)
  }
})

const categoryAnalysis = computed(() => {
  const source = filteredObligations.value
  const byCategory = {}
  source.forEach(o => {
    if (!o.docDate) return
    const year = parseInt(o.docDate.substring(0, 4))
    if (year !== analysisYear.value) return
    const month = parseInt(o.docDate.substring(5, 7)) - 1
    if (!byCategory[o.category]) {
      byCategory[o.category] = { name: o.category, months: Array(12).fill(0), total: 0, subs: {} }
    }
    byCategory[o.category].months[month] += o.remaining > 0 ? o.remaining : o.amount
    byCategory[o.category].total += o.remaining > 0 ? o.remaining : o.amount
    if (o.account && o.account !== o.category) {
      if (!byCategory[o.category].subs[o.account]) {
        byCategory[o.category].subs[o.account] = { name: o.account, months: Array(12).fill(0), total: 0 }
      }
      byCategory[o.category].subs[o.account].months[month] += o.remaining > 0 ? o.remaining : o.amount
      byCategory[o.category].subs[o.account].total += o.remaining > 0 ? o.remaining : o.amount
    }
  })
  return Object.values(byCategory)
    .map(c => ({ ...c, subs: Object.values(c.subs) }))
    .sort((a, b) => b.total - a.total)
})

const monthlyTotals = computed(() => {
  const totals = Array(12).fill(0)
  categoryAnalysis.value.forEach(c => c.months.forEach((v, i) => totals[i] += v))
  return totals
})

const grandTotal = computed(() => monthlyTotals.value.reduce((s, v) => s + v, 0))

const months = ['Ιαν', 'Φεβ', 'Μαρ', 'Απρ', 'Μάι', 'Ιούν', 'Ιούλ', 'Αυγ', 'Σεπ', 'Οκτ', 'Νοέ', 'Δεκ']
const availableYears = computed(() => {
  const yrs = new Set()
  allObligations.value.forEach(o => {
    if (o.docDate) yrs.add(parseInt(o.docDate.substring(0, 4)))
  })
  return Array.from(yrs).sort((a, b) => b - a)
})

const fmt = (n) => new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n) + ' €'
const fmtDate = (d) => {
  if (!d) return ''
  const parts = d.split('-')
  return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0].slice(2)}` : d
}

function onEntityChanged() {
  entityKey.value = localStorage.getItem('n2c_entity') || 'next2me'
  loadObligations()
}

onMounted(() => {
  loadObligations()
  window.addEventListener('entity-changed', onEntityChanged)
})
onUnmounted(() => {
  window.removeEventListener('entity-changed', onEntityChanged)
})
</script>

<template>
  <div class="obligations-page">

    <!-- Toast -->
    <transition name="toast">
      <div v-if="toast.show" class="toast" :class="'toast-' + toast.type">
        <span>{{ toast.type === 'success' ? '✓' : '!' }}</span>
        {{ toast.message }}
      </div>
    </transition>

    <div class="kpi-grid">
      <div class="kpi-card white">
        <div class="kpi-label">ΣΥΝΟΛΟ ΚΙΝΗΣΕΩΝ</div>
        <div class="kpi-amount">{{ fmt(stats.totalAmount) }}</div>
        <div class="kpi-count">{{ stats.totalCount }} κινήσεις</div>
      </div>
      <div class="kpi-card green">
        <div class="kpi-label">ΕΞΟΦΛΗΜΕΝΕΣ</div>
        <div class="kpi-amount">{{ fmt(stats.paidAmount) }}</div>
        <div class="kpi-count">{{ stats.paidCount }} κιν.</div>
      </div>
      <div class="kpi-card red">
        <div class="kpi-label">ΑΠΛΗΡΩΤΕΣ</div>
        <div class="kpi-amount">{{ fmt(stats.unpaidAmount) }}</div>
        <div class="kpi-count">{{ stats.unpaidCount }} κινήσεις</div>
      </div>
      <div class="kpi-card orange">
        <div class="kpi-label">💰 ΜΕΤΡΗΤΑ</div>
        <div class="kpi-amount">{{ fmt(stats.cashAmount) }}</div>
        <div class="kpi-count">{{ stats.cashCount }} κιν.</div>
      </div>
      <div class="kpi-card blue">
        <div class="kpi-label">🏦 ΤΡΑΠΕΖΑ</div>
        <div class="kpi-amount">{{ fmt(stats.bankAmount) }}</div>
        <div class="kpi-count">{{ stats.bankCount }} κιν.</div>
      </div>
      <div class="kpi-card gray">
        <div class="kpi-label">ΜΕΡ. ΠΛΗΡΩΜΕΝΕΣ</div>
        <div class="kpi-amount">{{ fmt(stats.partialAmount) }}</div>
        <div class="kpi-count">{{ stats.partialCount }} κιν.</div>
      </div>
      <div class="kpi-card orange">
        <div class="kpi-label">⚠ ΕΚΚΡΕΜΕΙΣ</div>
        <div class="kpi-amount">{{ fmt(stats.urgentAmount) }}</div>
        <div class="kpi-count">{{ stats.urgentCount }} κιν.</div>
      </div>
    </div>

    <div class="tabs">
      <button class="tab-btn" :class="{ active: activeTab === 'list' }" @click="activeTab = 'list'">☰ Λίστα</button>
      <button class="tab-btn" :class="{ active: activeTab === 'analysis' }" @click="activeTab = 'analysis'">▤ Ανάλυση ανά Κατηγορία</button>
    </div>

    <div v-if="activeTab === 'list'">
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
        <select v-model="selectedMethod" class="filter-select">
          <option v-for="m in methods" :key="m" :value="m">{{ m === 'all' ? 'Όλες μέθοδοι' : m }}</option>
        </select>
        <button class="btn-refresh" @click="loadObligations" :disabled="loading">{{ loading ? '⏳' : '↻ Ανανέωση' }}</button>
      </div>

      <div v-if="loading" class="loading-state">Φόρτωση...</div>
      <div v-else-if="filteredObligations.length === 0" class="empty-state">Δεν βρέθηκαν αποτελέσματα</div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th><th>ΗΜ/ΝΙΑ</th><th>ΠΕΡΙΓΡΑΦΗ</th>
              <th class="hide-sm">ΚΑΤΗΓΟΡΙΑ</th><th class="hide-sm">ΜΕΘΟΔΟΣ</th>
              <th class="num">ΠΟΣΟ</th><th class="num hide-sm">ΠΛΗΡΩΜΕΝΟ</th>
              <th class="num">ΥΠΟΛΟΙΠΟ</th><th class="hide-sm">STATUS</th>
              <th>ΕΝΕΡΓΕΙΕΣ</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in filteredObligations" :key="o.id" :class="o.status === 'urgent' ? 'row-urgent' : ''">
              <td class="id-col">#{{ o.entityNumber }}</td>
              <td>{{ fmtDate(o.docDate) }}</td>
              <td class="desc-col">{{ o.description }}</td>
              <td class="hide-sm"><span class="cat-badge">{{ o.category }}</span></td>
              <td class="hide-sm">{{ o.paymentMethod }}</td>
              <td class="num red">{{ fmt(o.amount) }}</td>
              <td class="num hide-sm">{{ fmt(o.paid) }}</td>
              <td class="num red">{{ fmt(o.remaining) }}</td>
              <td class="hide-sm"><span class="badge" :class="statusClass(o.status)">{{ statusLabel(o.status) }}</span></td>
              <td class="actions">
                <button
                  v-if="canModify && (o.status === 'unpaid' || o.status === 'urgent')"
                  class="btn-bolt"
                  :class="{ 'is-urgent': o.status === 'urgent' }"
                  @click="toggleUrgent(o)"
                  :title="o.status === 'urgent' ? 'Αναίρεση Εκκρεμούς' : 'Σήμανση ως Εκκρεμής'">
                  ⚡
                </button>
                <button
                  v-if="canModify && o.status !== 'paid'"
                  class="btn-mark-paid"
                  @click="openMarkPaid(o)"
                  title="Μαρκάρισμα ως Εξοφλημένη">
                  ✓ Εξόφληση
                </button>
                <span v-else-if="o.status === 'paid'" class="paid-indicator">✓ Πληρώθηκε</span>
                <button
                  class="btn-attach-ob"
                  @click="openAttachments(o)"
                  :style="hasAttachments(o) ? {} : { opacity: 0.45 }"
                  title="Αρχεία">
                  📎
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="activeTab === 'analysis'">
      <div class="analysis-filters">
        <select v-model.number="analysisYear" class="filter-select">
          <option v-for="y in availableYears" :key="y" :value="y">{{ y }}</option>
        </select>
        <select v-model="selectedStatus" class="filter-select">
          <option v-for="s in statusOptions" :key="s.value" :value="s.value">{{ s.label }}</option>
        </select>
      </div>

      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>Κατηγορία</th>
              <th v-for="m in months" :key="m" class="num">{{ m }}</th>
              <th class="num">Σύνολο</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="cat in categoryAnalysis" :key="cat.name">
              <tr class="cat-row">
                <td><strong>{{ cat.name }}</strong></td>
                <td v-for="(m, i) in cat.months" :key="i" class="num" :class="m > 0 ? 'blue' : 'muted'">{{ m > 0 ? fmt(m) : '—' }}</td>
                <td class="num blue"><strong>{{ fmt(cat.total) }}</strong></td>
              </tr>
              <tr v-for="sub in cat.subs" :key="sub.name" class="sub-row">
                <td class="pl-20">{{ sub.name }}</td>
                <td v-for="(m, i) in sub.months" :key="i" class="num muted">{{ m > 0 ? fmt(m) : '—' }}</td>
                <td class="num muted">{{ fmt(sub.total) }}</td>
              </tr>
            </template>
            <tr class="total-row">
              <td><strong>ΓΕΝΙΚΟ ΣΥΝΟΛΟ</strong></td>
              <td v-for="(m, i) in monthlyTotals" :key="i" class="num"><strong>{{ fmt(m) }}</strong></td>
              <td class="num green"><strong>{{ fmt(grandTotal) }}</strong></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Mark Paid modal (replaces old inline confirm) -->
    <MarkPaidModal
      :visible="markPaidState.visible"
      :transaction="markPaidState.transaction"
      @close="closeMarkPaid"
      @saved="onMarkPaidSaved"
    />

  </div>

    <AttachmentsPopover
      :visible="attachmentsState.visible"
      :transaction="attachmentsState.transaction"
      @close="closeAttachments"
    />

</template>

<style scoped>
.obligations-page { padding: 24px; color: #e0e6ed; position: relative; }
.kpi-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 10px; margin-bottom: 20px; }
.kpi-card { background: #1e3448; border-radius: 8px; padding: 14px 16px; border-top: 3px solid #4FC3A1; }
.kpi-card.green { border-top-color: #4FC3A1; }
.kpi-card.red { border-top-color: #ef5350; }
.kpi-card.orange { border-top-color: #ff9800; }
.kpi-card.blue { border-top-color: #29b6f6; }
.kpi-card.white { border-top-color: #fff; }
.kpi-card.gray { border-top-color: #556677; }
.kpi-label { font-size: 0.68rem; color: #8899aa; letter-spacing: 0.5px; margin-bottom: 6px; }
.kpi-amount { font-size: 1rem; font-weight: 700; color: #fff; }
.kpi-card.green .kpi-amount { color: #4FC3A1; }
.kpi-card.red .kpi-amount { color: #ef5350; }
.kpi-card.orange .kpi-amount { color: #ff9800; }
.kpi-card.blue .kpi-amount { color: #29b6f6; }
.kpi-count { font-size: 0.72rem; color: #8899aa; margin-top: 2px; }
.tabs { display: flex; gap: 4px; margin-bottom: 16px; border-bottom: 1px solid #2a4a6a; }
.tab-btn { background: none; border: none; color: #8899aa; padding: 10px 16px; cursor: pointer; font-size: 0.9rem; border-bottom: 2px solid transparent; margin-bottom: -1px; }
.tab-btn.active { color: #4FC3A1; border-bottom-color: #4FC3A1; }
.filters-bar, .analysis-filters { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.search-wrap { position: relative; flex: 1; min-width: 220px; max-width: 420px; }
.search-icon { position: absolute; left: 10px; top: 50%; transform: translateY(-50%); color: var(--text-muted); font-size: .85rem; pointer-events: none; }
.search-input { width: 100%; background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 32px 8px 32px; border-radius: 8px; font-size: .85rem; }
.search-input:focus { outline: none; border-color: var(--accent, #4A9EFF); }
.search-input::placeholder { color: #6c7a8a; }
.search-clear { position: absolute; right: 6px; top: 50%; transform: translateY(-50%); background: none; border: none; color: #6c7a8a; font-size: 1.2rem; cursor: pointer; padding: 2px 6px; border-radius: 4px; line-height: 1; }
.search-clear:hover { color: #e0e6ed; background: rgba(255,255,255,0.08); }
.filter-input, .filter-select { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; }
.btn-refresh { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 16px; border-radius: 6px; cursor: pointer; }
.btn-refresh:disabled { opacity: 0.5; }
.loading-state, .empty-state { padding: 60px 20px; text-align: center; color: #8899aa; }
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 0.83rem; }
.data-table th { background: #1a2f45; color: #8899aa; padding: 10px 12px; text-align: left; font-size: 0.72rem; border-bottom: 1px solid #2a4a6a; }
.data-table td { padding: 10px 12px; border-bottom: 1px solid #1e3448; }
.data-table tr:hover { background: #1e3448; }
.id-col { color: #8899aa; font-size: 0.8rem; font-family: monospace; }
.desc-col { max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cat-badge { background: #2a4a6a; padding: 2px 8px; border-radius: 4px; font-size: 0.75rem; }
.num { text-align: right; font-family: monospace; }
.green { color: #4FC3A1; }
.red { color: #ef5350; }
.blue { color: #29b6f6; }
.muted { color: #556677; }
.badge { padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; }
.badge-orange { background: rgba(255,152,0,0.15); color: #ff9800; }
.badge-red { background: rgba(239,83,80,0.15); color: #ef5350; }
.badge-green { background: rgba(79,195,161,0.15); color: #4FC3A1; }
.cat-row { background: rgba(255,255,255,0.02); }
.sub-row td { padding-left: 24px; }
.pl-20 { padding-left: 24px !important; color: #8899aa; }
.total-row { background: #1a2f45; }
.actions { white-space: nowrap; }
.row-urgent { border-left: 3px solid #ff6400 !important; background: rgba(255,100,0,0.04); }
.row-urgent:hover { background: rgba(255,100,0,0.08) !important; }
.btn-bolt { background: rgba(255,100,0,0.1); border: 1px solid rgba(255,100,0,0.35); color: #ff6400; padding: 5px 10px; border-radius: 5px; font-size: 0.95rem; cursor: pointer; margin-right: 4px; transition: all 0.15s; line-height: 1; }
.btn-bolt:hover { background: rgba(255,100,0,0.25); }
.btn-bolt.is-urgent { background: rgba(255,100,0,0.3); border-color: #ff6400; animation: urgentPulse 2s ease-in-out infinite; }
@keyframes urgentPulse { 0%, 100% { opacity: 1 } 50% { opacity: 0.7 } }
.btn-mark-paid { background: #4FC3A1; border: none; color: #0d1f2d; padding: 5px 12px; border-radius: 5px; font-size: 0.78rem; font-weight: 600; cursor: pointer; white-space: nowrap; }
.btn-mark-paid:hover { background: #5fd4b3; }
.paid-indicator { color: #4FC3A1; font-size: 0.78rem; font-weight: 600; }

/* Toast */
.toast { position: fixed; bottom: 24px; right: 24px; background: #1e3448; border: 1px solid #2a4a6a; border-radius: 8px; padding: 14px 20px; font-size: 0.9rem; z-index: 2000; box-shadow: 0 4px 20px rgba(0,0,0,0.3); display: flex; align-items: center; gap: 10px; min-width: 240px; color: #e0e6ed; }
.toast-success { border-left: 4px solid #4FC3A1; }
.toast-error { border-left: 4px solid #ef5350; }
.toast-success span { color: #4FC3A1; font-size: 1.2rem; font-weight: bold; }
.toast-error span { color: #ef5350; font-size: 1.2rem; font-weight: bold; }
.toast-enter-active, .toast-leave-active { transition: all 0.3s ease; }
.toast-enter-from { opacity: 0; transform: translateX(30px); }
.toast-leave-to { opacity: 0; transform: translateX(30px); }

/* Modal */
.modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1500; padding: 20px; }
.modal { background: #1e3448; border: 1px solid #2a4a6a; border-radius: 10px; width: 100%; max-width: 720px; max-height: 90vh; display: flex; flex-direction: column; box-shadow: 0 10px 40px rgba(0,0,0,0.5); }
.modal-sm { max-width: 440px; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #2a4a6a; }
.modal-header h3 { margin: 0; font-size: 1.05rem; color: #e0e6ed; }
.modal-body { padding: 20px; overflow-y: auto; flex: 1; }
.modal-footer { display: flex; justify-content: flex-end; gap: 10px; padding: 14px 20px; border-top: 1px solid #2a4a6a; }
.confirm-msg { font-size: 0.95rem; margin: 0 0 10px; color: #e0e6ed; }
.confirm-detail { font-size: 0.85rem; color: #b0bec5; margin: 0 0 14px; padding: 10px 12px; background: #1a2f45; border-radius: 6px; line-height: 1.5; }
.confirm-info { font-size: 0.82rem; color: #8899aa; margin: 0; }
.btn-secondary { background: #1a2f45; border: 1px solid #2a4a6a; color: #b0bec5; padding: 9px 18px; border-radius: 6px; font-size: 0.86rem; cursor: pointer; }
.btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-success { background: #4FC3A1; border: none; color: #0d1f2d; padding: 9px 18px; border-radius: 6px; font-size: 0.86rem; font-weight: 600; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; }
.btn-success:disabled { opacity: 0.6; cursor: not-allowed; }
.spinner-sm { display: inline-block; width: 12px; height: 12px; border: 2px solid rgba(13,31,45,0.3); border-top-color: #0d1f2d; border-radius: 50%; animation: spin 0.7s linear infinite; vertical-align: middle; }
@keyframes spin { to { transform: rotate(360deg); } }

.btn-attach-ob {
  background: transparent;
  border: 1px solid #2c3e50;
  border-radius: 5px;
  padding: 4px 10px;
  font-size: 0.85rem;
  cursor: pointer;
  color: #9aa5b1;
  transition: all 0.15s;
  margin-left: 6px;
}
.btn-attach-ob:hover { border-color: #4A9EFF; color: #4A9EFF; }

/* ───── Mobile responsive ───── */
@media (max-width: 768px) {
  .obligations-page { padding: 12px; }
  .kpi-grid { grid-template-columns: repeat(3, 1fr); gap: 6px; margin-bottom: 12px; }
  .kpi-card { padding: 10px 12px; }
  .kpi-amount { font-size: 0.85rem; }
  .kpi-label { font-size: 0.6rem; }
  .hide-sm { display: none !important; }
  .data-table { display: table !important; font-size: 0.78rem; }
  .data-table thead { display: table-header-group !important; }
  .data-table tbody { display: table-row-group !important; }
  .data-table tr { display: table-row !important; }
  .data-table th, .data-table td { display: table-cell !important; padding: 7px 8px; }
  .desc-col { max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .filters-bar { gap: 6px; }
  .search-wrap { min-width: 140px; }
  .btn-mark-paid { font-size: 0.72rem; padding: 4px 8px; }
}

@media (max-width: 480px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  .data-table { font-size: 0.75rem; }
  .data-table th, .data-table td { padding: 6px 6px; }
  .desc-col { max-width: 100px; }
}
</style>