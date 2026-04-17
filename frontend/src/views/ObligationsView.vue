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
const allObligations = ref([])
const activeTab = ref('list')

const search = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const selectedStatus = ref('unpaid_urgent')
const selectedCategory = ref('all')
const selectedMethod = ref('all')
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

async function loadObligations() {
  loading.value = true
  try {
    const entityId = ENTITIES[entityKey.value]
    const res = await api.get('/api/transactions', {
      params: { entityId, page: 0, perPage: 10000 }
    })
    if (res.data.success) {
      // ONLY expenses for obligations
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
            amount, paid, remaining, status
          }
        })
    }
  } catch (e) {
    console.error('loadObligations error:', e)
  } finally {
    loading.value = false
  }
}

const filteredObligations = computed(() => {
  return allObligations.value.filter(o => {
    // Status filter
    if (selectedStatus.value === 'unpaid_urgent') {
      if (o.status !== 'unpaid' && o.status !== 'urgent') return false
    } else if (selectedStatus.value !== 'all' && o.status !== selectedStatus.value) {
      return false
    }
    if (selectedCategory.value !== 'all' && o.category !== selectedCategory.value) return false
    if (selectedMethod.value !== 'all' && o.paymentMethod !== selectedMethod.value) return false
    if (search.value) {
      const q = search.value.toLowerCase()
      if (!String(o.entityNumber).includes(q) && !o.description.toLowerCase().includes(q)) return false
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

// Category analysis per month
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

const months = ['Ιαν', 'Φεβ', 'Μαρ', 'Απρ', 'Μάι', 'Ιούν', 'Ιούλ', 'Αύγ', 'Σεπ', 'Οκτ', 'Νοέ', 'Δεκ']
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
        <div class="kpi-label">⚡ ΕΚΚΡΕΜΕΙΣ</div>
        <div class="kpi-amount">{{ fmt(stats.urgentAmount) }}</div>
        <div class="kpi-count">{{ stats.urgentCount }} κιν.</div>
      </div>
    </div>

    <div class="tabs">
      <button class="tab-btn" :class="{ active: activeTab === 'list' }" @click="activeTab = 'list'">≡ Λίστα</button>
      <button class="tab-btn" :class="{ active: activeTab === 'analysis' }" @click="activeTab = 'analysis'">⊞ Ανάλυση ανά Κατηγορία</button>
    </div>

    <div v-if="activeTab === 'list'">
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
        <select v-model="selectedMethod" class="filter-select">
          <option v-for="m in methods" :key="m" :value="m">{{ m === 'all' ? 'Όλες μέθοδοι' : m }}</option>
        </select>
        <button class="btn-refresh" @click="loadObligations" :disabled="loading">{{ loading ? '⟳' : '↻ Ανανέωση' }}</button>
      </div>

      <div v-if="loading" class="loading-state">Φόρτωση...</div>
      <div v-else-if="filteredObligations.length === 0" class="empty-state">Δεν βρέθηκαν αποτελέσματα</div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th><th>ΗΜ/ΝΙΑ</th><th>ΠΕΡΙΓΡΑΦΗ</th>
              <th>ΚΑΤΗΓΟΡΙΑ</th><th>ΜΕΘΟΔΟΣ</th>
              <th class="num">ΠΟΣΟ</th><th class="num">ΠΛΗΡΩΜΕΝΟ</th>
              <th class="num">ΥΠΟΛΟΙΠΟ</th><th>STATUS</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in filteredObligations" :key="o.id">
              <td class="id-col">#{{ o.entityNumber }}</td>
              <td>{{ fmtDate(o.docDate) }}</td>
              <td class="desc-col">{{ o.description }}</td>
              <td><span class="cat-badge">{{ o.category }}</span></td>
              <td>{{ o.paymentMethod }}</td>
              <td class="num red">{{ fmt(o.amount) }}</td>
              <td class="num">{{ fmt(o.paid) }}</td>
              <td class="num red">{{ fmt(o.remaining) }}</td>
              <td><span class="badge" :class="statusClass(o.status)">{{ statusLabel(o.status) }}</span></td>
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
  </div>
</template>

<style scoped>
.obligations-page { padding: 24px; color: #e0e6ed; }
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
</style>
