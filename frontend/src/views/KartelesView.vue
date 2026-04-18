<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import api from '@/api'

// ─────────────────────────────────────────────────────────
// Entity selection (consistent with other views)
// ─────────────────────────────────────────────────────────
const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14'
}
const entityKey = ref(localStorage.getItem('n2c_entity') || 'next2me')

// ─────────────────────────────────────────────────────────
// State
// ─────────────────────────────────────────────────────────
const loadingList = ref(false)
const loadingTxns = ref(false)
const errorMsg = ref('')
const karteles = ref([])            // [{counterparty, count, incomeCount, expenseCount, total, paid, remaining}]
const selectedCounterparty = ref(null)
const transactions = ref([])

// Filters (client-side on the selected kartela)
const search = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const statusFilter = ref('all')     // all | paid | unpaid | urgent | partial
const typeFilter = ref('all')       // all | income | expense

// ─────────────────────────────────────────────────────────
// API calls
// ─────────────────────────────────────────────────────────
async function loadKarteles() {
  loadingList.value = true
  errorMsg.value = ''
  try {
    const entityId = ENTITIES[entityKey.value]
    const res = await api.get('/api/transactions/counterparties', { params: { entityId } })
    if (res.data && res.data.success) {
      karteles.value = res.data.data || []
      // Auto-select first kartela if nothing selected or previous not in list
      if (karteles.value.length > 0) {
        const stillThere = selectedCounterparty.value &&
          karteles.value.some(k => k.counterparty === selectedCounterparty.value)
        if (!stillThere) {
          selectedCounterparty.value = karteles.value[0].counterparty
          await loadTransactions()
        }
      } else {
        selectedCounterparty.value = null
        transactions.value = []
      }
    } else {
      errorMsg.value = 'Αποτυχία φόρτωσης καρτελών'
    }
  } catch (e) {
    console.error('loadKarteles error:', e)
    errorMsg.value = e?.response?.data?.error || 'Σφάλμα σύνδεσης με τον server'
  } finally {
    loadingList.value = false
  }
}

async function loadTransactions() {
  if (!selectedCounterparty.value) {
    transactions.value = []
    return
  }
  loadingTxns.value = true
  try {
    const entityId = ENTITIES[entityKey.value]
    const res = await api.get('/api/transactions/by-counterparty', {
      params: { entityId, counterparty: selectedCounterparty.value }
    })
    if (res.data && res.data.success) {
      transactions.value = res.data.data || []
    } else {
      transactions.value = []
    }
  } catch (e) {
    console.error('loadTransactions error:', e)
    transactions.value = []
  } finally {
    loadingTxns.value = false
  }
}

function selectKartela(cp) {
  selectedCounterparty.value = cp
  // Reset filters when switching
  search.value = ''
  dateFrom.value = ''
  dateTo.value = ''
  statusFilter.value = 'all'
  typeFilter.value = 'all'
  loadTransactions()
}

function onEntityChanged() {
  entityKey.value = localStorage.getItem('n2c_entity') || 'next2me'
  selectedCounterparty.value = null
  transactions.value = []
  loadKarteles()
}

onMounted(() => {
  loadKarteles()
  window.addEventListener('entity-changed', onEntityChanged)
})
onUnmounted(() => {
  window.removeEventListener('entity-changed', onEntityChanged)
})

// ─────────────────────────────────────────────────────────
// Derived / Filtered data
// ─────────────────────────────────────────────────────────
const currentKartela = computed(() => {
  if (!selectedCounterparty.value) return null
  return karteles.value.find(k => k.counterparty === selectedCounterparty.value) || null
})

const filteredTransactions = computed(() => {
  const s = (search.value || '').trim().toLowerCase()
  return transactions.value.filter(t => {
    if (t.recordStatus === 'void') return false
    if (typeFilter.value !== 'all' && t.type !== typeFilter.value) return false
    if (statusFilter.value !== 'all' && t.paymentStatus !== statusFilter.value) return false
    if (dateFrom.value && t.docDate < dateFrom.value) return false
    if (dateTo.value && t.docDate > dateTo.value) return false
    if (s) {
      const hay = [
        String(t.entityNumber || t.id || ''),
        t.description || '',
        t.category || '',
        t.subcategory || '',
        t.paymentMethod || ''
      ].join(' ').toLowerCase()
      if (!hay.includes(s)) return false
    }
    return true
  })
})

// KPI cards based on FILTERED transactions
const kpis = computed(() => {
  const txns = filteredTransactions.value
  let total = 0, paid = 0, unpaid = 0, income = 0, urgent = 0
  let cTotal = 0, cPaid = 0, cUnpaid = 0, cIncome = 0, cUrgent = 0
  for (const t of txns) {
    const amt = Number(t.amount || 0)
    const rem = Number(t.amountRemaining || 0)
    const paidAmt = Number(t.amountPaid || 0)
    total += amt; cTotal++
    if (t.type === 'income') { income += amt; cIncome++ }
    if (t.paymentStatus === 'paid' || t.paymentStatus === 'received') { paid += paidAmt; cPaid++ }
    if (t.paymentStatus === 'unpaid' || t.paymentStatus === 'partial') { unpaid += rem; cUnpaid++ }
    if (t.paymentStatus === 'urgent') { urgent += rem; cUrgent++ }
  }
  return { total, paid, unpaid, income, urgent, cTotal, cPaid, cUnpaid, cIncome, cUrgent }
})

// ─────────────────────────────────────────────────────────
// Formatting helpers
// ─────────────────────────────────────────────────────────
const fmtMoney = (n) => {
  const v = Number(n || 0)
  return new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v) + ' €'
}
const fmtDate = (iso) => {
  if (!iso) return '—'
  const parts = iso.split('-')
  if (parts.length !== 3) return iso
  return parts[2] + '/' + parts[1] + '/' + parts[0]
}
const statusLabel = (s) => ({
  paid: 'Εξοφλημένη',
  received: 'Εισπράχθηκε',
  unpaid: 'Απλήρωτη',
  urgent: 'Επείγον',
  partial: 'Μερική'
}[s] || s || '—')
const statusClass = (s) => ({
  paid: 'badge-green',
  received: 'badge-green',
  unpaid: 'badge-red',
  urgent: 'badge-orange',
  partial: 'badge-orange'
}[s] || 'badge-gray')
</script>

<template>
  <div class="karteles-page">
    <div class="karteles-layout">

      <!-- ─── Sidebar ─── -->
      <aside class="karteles-sidebar">
        <div class="sidebar-header">
          <span>📋 Καρτέλες ({{ karteles.length }})</span>
        </div>

        <div v-if="loadingList" class="side-msg">Φόρτωση...</div>
        <div v-else-if="errorMsg" class="side-msg error">{{ errorMsg }}</div>
        <div v-else-if="karteles.length === 0" class="side-msg">Δεν υπάρχουν καρτέλες.</div>

        <div
          v-for="k in karteles"
          :key="k.counterparty"
          class="kartela-item"
          :class="{ active: selectedCounterparty === k.counterparty }"
          @click="selectKartela(k.counterparty)"
        >
          <span class="kartela-name" :title="k.counterparty">{{ k.counterparty }}</span>
          <span class="kartela-count">{{ k.count }}</span>
        </div>
      </aside>

      <!-- ─── Main ─── -->
      <section class="karteles-main">

        <div class="kartela-header">
          <h2>
            📋 {{ selectedCounterparty || 'Επίλεξε καρτέλα' }}
            <span v-if="currentKartela" class="meta-badge">
              {{ currentKartela.count }} κινήσεις
            </span>
          </h2>
        </div>

        <!-- Filters -->
        <div class="filters-bar" v-if="selectedCounterparty">
          <input v-model="search" class="filter-input flex-1" placeholder="Αναζήτηση περιγραφή, κατηγορία..." />
          <input v-model="dateFrom" type="date" class="filter-input" />
          <input v-model="dateTo" type="date" class="filter-input" />
          <select v-model="typeFilter" class="filter-select">
            <option value="all">Όλοι τύποι</option>
            <option value="income">Έσοδα</option>
            <option value="expense">Έξοδα</option>
          </select>
          <select v-model="statusFilter" class="filter-select">
            <option value="all">Όλες</option>
            <option value="paid">Εξοφλημένες</option>
            <option value="unpaid">Απλήρωτες</option>
            <option value="urgent">Επείγουσες</option>
            <option value="partial">Μερικές</option>
          </select>
        </div>

        <!-- KPI cards (real numbers from filtered transactions) -->
        <div v-if="selectedCounterparty" class="kpi-row">
          <div class="kpi-card">
            <div class="kpi-label">Σύνολο</div>
            <div class="kpi-amount">{{ fmtMoney(kpis.total) }}</div>
            <div class="kpi-count">{{ kpis.cTotal }} κινήσεις</div>
          </div>
          <div class="kpi-card green">
            <div class="kpi-label">Εξοφλημένες</div>
            <div class="kpi-amount">{{ fmtMoney(kpis.paid) }}</div>
            <div class="kpi-count">{{ kpis.cPaid }} κινήσεις</div>
          </div>
          <div class="kpi-card red">
            <div class="kpi-label">Απλήρωτες</div>
            <div class="kpi-amount">{{ fmtMoney(kpis.unpaid) }}</div>
            <div class="kpi-count">{{ kpis.cUnpaid }} κινήσεις</div>
          </div>
          <div class="kpi-card teal">
            <div class="kpi-label">Εισπράξεις</div>
            <div class="kpi-amount">{{ fmtMoney(kpis.income) }}</div>
            <div class="kpi-count">{{ kpis.cIncome }} κινήσεις</div>
          </div>
          <div class="kpi-card orange">
            <div class="kpi-label">Επείγουσες</div>
            <div class="kpi-amount">{{ fmtMoney(kpis.urgent) }}</div>
            <div class="kpi-count">{{ kpis.cUrgent }} κινήσεις</div>
          </div>
        </div>

        <!-- Table -->
        <div v-if="selectedCounterparty" class="table-wrap">
          <div v-if="loadingTxns" class="empty-state">Φόρτωση κινήσεων...</div>
          <div v-else-if="filteredTransactions.length === 0" class="empty-state">
            Καμία κίνηση για αυτά τα φίλτρα.
          </div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>ΗΜ/ΝΙΑ</th>
                <th>ΠΕΡΙΓΡΑΦΗ</th>
                <th>ΚΑΤΗΓΟΡΙΑ</th>
                <th>ΜΕΘΟΔΟΣ</th>
                <th class="num">ΠΟΣΟ</th>
                <th class="num">ΠΛΗΡΩΜΕΝΟ</th>
                <th class="num">ΥΠΟΛΟΙΠΟ</th>
                <th>ΗΜ/ΝΙΑ ΠΛΗΡ.</th>
                <th>STATUS</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in filteredTransactions" :key="t.id">
                <td class="id-col">{{ t.entityNumber || t.id }}</td>
                <td>{{ fmtDate(t.docDate) }}</td>
                <td class="desc-col" :title="t.description">{{ t.description || '—' }}</td>
                <td><span class="cat-badge">{{ t.category || '—' }}</span></td>
                <td>{{ t.paymentMethod || '—' }}</td>
                <td class="num">{{ fmtMoney(t.amount) }}</td>
                <td class="num green">{{ fmtMoney(t.amountPaid) }}</td>
                <td class="num" :class="Number(t.amountRemaining) > 0 ? 'red' : ''">
                  {{ fmtMoney(t.amountRemaining) }}
                </td>
                <td class="meta">{{ fmtDate(t.paymentDate) }}</td>
                <td>
                  <span class="badge" :class="statusClass(t.paymentStatus)">
                    {{ statusLabel(t.paymentStatus) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- No selection -->
        <div v-if="!selectedCounterparty && !loadingList" class="empty-state">
          Επίλεξε μια καρτέλα από τη λίστα αριστερά.
        </div>
      </section>

    </div>
  </div>
</template>

<style scoped>
.karteles-page { padding: 24px; color: #e0e6ed; height: calc(100vh - 60px); }
.karteles-layout { display: grid; grid-template-columns: 260px 1fr; gap: 20px; height: 100%; }

/* Sidebar */
.karteles-sidebar { background: #1e3448; border-radius: 10px; padding: 12px; overflow-y: auto; }
.sidebar-header { padding: 8px 4px 12px; font-size: 0.85rem; font-weight: 600; color: #4FC3A1; border-bottom: 1px solid #2a4a6a; margin-bottom: 8px; }
.side-msg { padding: 10px 8px; font-size: 0.8rem; color: #8899aa; }
.side-msg.error { color: #ef5350; }
.kartela-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.kartela-item:hover { background: #2a4a6a; }
.kartela-item.active { background: rgba(79,195,161,0.15); color: #4FC3A1; }
.kartela-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kartela-count { background: #2a4a6a; padding: 1px 6px; border-radius: 10px; font-size: 0.7rem; color: #8899aa; }
.kartela-item.active .kartela-count { background: #4FC3A1; color: #0f1e2e; }

/* Main */
.karteles-main { overflow-y: auto; }
.kartela-header { margin-bottom: 16px; }
.kartela-header h2 { margin: 0; font-size: 1.1rem; color: #29b6f6; display: flex; align-items: center; gap: 10px; }
.meta-badge { background: #1e3448; padding: 2px 10px; border-radius: 10px; font-size: 0.72rem; color: #8899aa; font-weight: 400; }

/* Filters */
.filters-bar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.filter-input, .filter-select { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; }
.flex-1 { flex: 1; min-width: 200px; }

/* KPIs */
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-bottom: 16px; }
.kpi-card { background: #1e3448; border-radius: 8px; padding: 12px 14px; border-top: 3px solid #556677; }
.kpi-card.green { border-top-color: #4FC3A1; }
.kpi-card.red { border-top-color: #ef5350; }
.kpi-card.teal { border-top-color: #29b6f6; }
.kpi-card.orange { border-top-color: #ff9800; }
.kpi-label { font-size: 0.68rem; color: #8899aa; margin-bottom: 4px; text-transform: uppercase; }
.kpi-amount { font-size: 1rem; font-weight: 700; color: #fff; }
.kpi-card.green .kpi-amount { color: #4FC3A1; }
.kpi-card.red .kpi-amount { color: #ef5350; }
.kpi-card.teal .kpi-amount { color: #29b6f6; }
.kpi-card.orange .kpi-amount { color: #ff9800; }
.kpi-count { font-size: 0.7rem; color: #8899aa; margin-top: 2px; }

/* Table */
.table-wrap { overflow-x: auto; background: #1a2f45; border-radius: 8px; }
.data-table { width: 100%; border-collapse: collapse; font-size: 0.83rem; }
.data-table th { background: #1a2f45; color: #8899aa; padding: 10px 12px; text-align: left; font-size: 0.72rem; border-bottom: 1px solid #2a4a6a; }
.data-table td { padding: 8px 12px; border-bottom: 1px solid #1e3448; }
.data-table tr:hover { background: #1e3448; }
.id-col { color: #8899aa; font-size: 0.8rem; }
.desc-col { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cat-badge { background: #2a4a6a; padding: 2px 8px; border-radius: 4px; font-size: 0.72rem; }
.num { text-align: right; font-family: monospace; }
.green { color: #4FC3A1; }
.red { color: #ef5350; }
.meta { color: #8899aa; font-size: 0.8rem; }
.badge { padding: 2px 8px; border-radius: 10px; font-size: 0.72rem; font-weight: 600; }
.badge-green { background: rgba(79,195,161,0.15); color: #4FC3A1; }
.badge-red { background: rgba(239,83,80,0.15); color: #ef5350; }
.badge-orange { background: rgba(255,152,0,0.15); color: #ff9800; }
.badge-gray { background: rgba(136,153,170,0.15); color: #8899aa; }
.empty-state { padding: 40px 20px; text-align: center; color: #8899aa; font-size: 0.9rem; background: #1a2f45; border-radius: 8px; }
</style>
