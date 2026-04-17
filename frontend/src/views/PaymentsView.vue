<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import api from '@/api'

const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14'
}

const entityKey = ref(localStorage.getItem('n2c_entity') || 'next2me')
const loading = ref(false)
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

const filteredPayments = computed(() => {
  return allPayments.value.filter(p => {
    if (selectedStatus.value !== 'all' && p.status !== selectedStatus.value) return false
    if (selectedCategory.value !== 'all' && p.category !== selectedCategory.value) return false
    if (search.value) {
      const q = search.value.toLowerCase()
      if (!String(p.entityNumber).includes(q) && !p.description.toLowerCase().includes(q)) return false
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
  const unpaid = all.filter(p => p.status === 'unpaid' || p.status === 'urgent')
  const partial = all.filter(p => p.status === 'partial')
  const paid = all.filter(p => p.status === 'paid')
  const received = all.filter(p => p.status === 'received')

  const sum = arr => arr.reduce((s, p) => s + p.amount, 0)
  const sumRem = arr => arr.reduce((s, p) => s + p.remaining, 0)
  const sumPaid = arr => arr.reduce((s, p) => s + p.paid, 0)

  return {
    total: all.length, totalAmount: sum(all),
    unpaid: unpaid.length, unpaidAmount: sumRem(unpaid),
    partial: partial.length, partialAmount: sumPaid(partial),
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
      <input v-model="search" class="filter-input" placeholder="Αναζήτηση ID, περιγραφή..." />
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
          </tr>
        </tbody>
      </table>
    </div>
  </div>
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
</style>
