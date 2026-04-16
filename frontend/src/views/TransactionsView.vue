<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '@/api'

const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14',
}
const selectedEntity = ref(localStorage.getItem('n2c_entity') || 'next2me')
const entityId = computed(() => ENTITIES[selectedEntity.value])

const dateFrom      = ref('')
const dateTo        = ref('')
const selectedType  = ref('')
const selectedStatus = ref('')
const page    = ref(0)
const perPage = ref(25)
const total   = ref(0)
const pages   = ref(0)
const loading = ref(false)
const error   = ref(null)
const transactions = ref([])

async function loadTransactions() {
  loading.value = true
  error.value   = null
  try {
    const params = { entityId: entityId.value, page: page.value, perPage: perPage.value }
    if (dateFrom.value)       params.from   = dateFrom.value
    if (dateTo.value)         params.to     = dateTo.value
    if (selectedType.value)   params.type   = selectedType.value
    if (selectedStatus.value) params.status = selectedStatus.value
    const res = await api.get('/api/transactions', { params })
    if (res.data.success) {
      transactions.value = res.data.data  || []
      total.value        = res.data.total || 0
      pages.value        = res.data.pages || 0
    }
  } catch (e) {
    error.value = 'Σφάλμα φόρτωσης'
    console.error(e)
  } finally {
    loading.value = false
  }
}

function applyFilters() { page.value = 0; loadTransactions() }
function goToPage(p)    { page.value = p; loadTransactions() }
function resetFilters() {
  dateFrom.value = ''; dateTo.value = ''
  selectedType.value = ''; selectedStatus.value = ''
  page.value = 0; loadTransactions()
}

const kpis = computed(() => {
  const txns = transactions.value
  const income  = txns.filter(t => t.type === 'income').reduce((s,t) => s + Number(t.amount||0), 0)
  const expense = txns.filter(t => t.type === 'expense').reduce((s,t) => s + Number(t.amount||0), 0)
  const unpaid  = txns.filter(t => t.paymentStatus === 'unpaid' || t.paymentStatus === 'urgent').length
  return { income, expense, net: income - expense, unpaid, total: total.value }
})

function fmt(v) {
  if (!v && v !== 0) return '—'
  return new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2 }).format(Number(v)) + ' €'
}
function fmtDate(d) {
  if (!d) return '—'
  return new Date(d).toLocaleDateString('el-GR', { day:'2-digit', month:'2-digit', year:'2-digit' })
}
function statusLabel(s) {
  return { paid:'Πληρωμένη', received:'Εισπράχθηκε', unpaid:'Απλήρωτη', urgent:'Εκκρεμής', partial:'Μερική' }[s] || s || '—'
}
function statusClass(s) {
  return { paid:'badge-paid', received:'badge-paid', unpaid:'badge-unpaid', urgent:'badge-urgent', partial:'badge-partial' }[s] || ''
}

const pageButtons = computed(() => {
  const btns = []
  const start = Math.max(0, page.value - 3)
  const end   = Math.min(pages.value - 1, page.value + 3)
  for (let i = start; i <= end; i++) btns.push(i)
  return btns
})

onMounted(loadTransactions)
</script>

<template>
  <div class="txn-page">

    <div class="kpi-bar">
      <div class="kpi-item">
        <span class="kpi-lbl">ΣΥΝΟΛΟ ΚΙΝΗΣΕΩΝ</span>
        <span class="kpi-val">{{ kpis.total.toLocaleString('el-GR') }}</span>
      </div>
      <div class="kpi-item red">
        <span class="kpi-lbl">ΠΛΗΡΩΜΕΣ</span>
        <span class="kpi-val">{{ fmt(kpis.expense) }}</span>
      </div>
      <div class="kpi-item green">
        <span class="kpi-lbl">ΕΙΣΠΡΑΞΕΙΣ</span>
        <span class="kpi-val">{{ fmt(kpis.income) }}</span>
      </div>
      <div class="kpi-item" :class="kpis.net >= 0 ? 'green' : 'red'">
        <span class="kpi-lbl">ΚΑΘΑΡΟ</span>
        <span class="kpi-val">{{ fmt(kpis.net) }}</span>
      </div>
      <div class="kpi-item orange">
        <span class="kpi-lbl">ΑΠΛΗΡΩΤΕΣ</span>
        <span class="kpi-val">{{ kpis.unpaid }}</span>
      </div>
      <div class="kpi-item">
        <span class="kpi-lbl">ΣΕΛΙΔΑ</span>
        <span class="kpi-val">{{ page + 1 }} / {{ pages || 1 }}</span>
      </div>
    </div>

    <div class="filters-bar">
      <input v-model="dateFrom" type="date" class="f-input" />
      <input v-model="dateTo"   type="date" class="f-input" />
      <select v-model="selectedType" class="f-select">
        <option value="">Όλοι τύποι</option>
        <option value="income">Έσοδο</option>
        <option value="expense">Έξοδο</option>
      </select>
      <select v-model="selectedStatus" class="f-select">
        <option value="">Όλα status</option>
        <option value="paid">Πληρωμένη</option>
        <option value="unpaid">Απλήρωτη</option>
        <option value="urgent">Εκκρεμής</option>
        <option value="partial">Μερική</option>
      </select>
      <select v-model="perPage" class="f-select" @change="applyFilters">
        <option :value="25">25/σελίδα</option>
        <option :value="50">50/σελίδα</option>
        <option :value="100">100/σελίδα</option>
      </select>
      <button class="btn-apply" @click="applyFilters" :disabled="loading">
        <i class="fas fa-filter"></i> Εφαρμογή
      </button>
      <button class="btn-reset" @click="resetFilters">
        <i class="fas fa-times"></i> Reset
      </button>
    </div>

    <div v-if="error" class="err-bar"><i class="fas fa-exclamation-triangle"></i> {{ error }}</div>
    <div v-if="loading" class="load-wrap"><span class="spinner"></span> Φόρτωση...</div>

    <div class="table-wrap" v-if="!loading">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>ΗΜ/ΝΙΑ</th>
            <th>ΠΕΡΙΓΡΑΦΗ</th>
            <th>ΚΑΤΗΓΟΡΙΑ</th>
            <th>ΥΠΟΚΑΤΗΓΟΡΙΑ</th>
            <th>ΤΡΟΠΟΣ</th>
            <th class="ra">ΕΙΣΠΡΑΞΗ</th>
            <th class="ra">ΠΛΗΡΩΜΗ</th>
            <th>STATUS</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="transactions.length === 0">
            <td colspan="10" class="empty-row">Δεν βρέθηκαν κινήσεις</td>
          </tr>
          <tr v-for="t in transactions" :key="t.id"
              :class="t.paymentStatus === 'urgent' ? 'row-urgent' : ''">
            <td class="id-col">#{{ t.id }}</td>
            <td class="date-col">{{ fmtDate(t.docDate) }}</td>
            <td class="desc-col" :title="t.description">{{ (t.description || '—').substring(0,40) }}</td>
            <td><span class="cat-badge">{{ t.category || '—' }}</span></td>
            <td class="sub-col">{{ t.account || t.subcategory || '—' }}</td>
            <td class="method-col">{{ t.paymentMethod || '—' }}</td>
            <td class="ra green mono">{{ t.type === 'income'  ? fmt(t.amount) : '—' }}</td>
            <td class="ra red   mono">{{ t.type === 'expense' ? fmt(t.amount) : '—' }}</td>
            <td><span class="status-badge" :class="statusClass(t.paymentStatus)">{{ statusLabel(t.paymentStatus) }}</span></td>
            <td class="actions">
              <button class="icon-btn" title="Επεξεργασία"><i class="fas fa-edit"></i></button>
              <button class="icon-btn" title="Παραστατικά" v-if="t.blobFileIds"><i class="fas fa-paperclip"></i></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pagination" v-if="!loading && pages > 1">
      <button class="pag-btn" :disabled="page===0" @click="goToPage(0)"><i class="fas fa-angle-double-left"></i></button>
      <button class="pag-btn" :disabled="page===0" @click="goToPage(page-1)"><i class="fas fa-angle-left"></i></button>
      <button v-for="p in pageButtons" :key="p" class="pag-btn" :class="{active: page===p}" @click="goToPage(p)">{{ p+1 }}</button>
      <button class="pag-btn" :disabled="page>=pages-1" @click="goToPage(page+1)"><i class="fas fa-angle-right"></i></button>
      <button class="pag-btn" :disabled="page>=pages-1" @click="goToPage(pages-1)"><i class="fas fa-angle-double-right"></i></button>
      <span class="pag-info">{{ page*perPage+1 }}–{{ Math.min((page+1)*perPage, total) }} από {{ total.toLocaleString('el-GR') }}</span>
    </div>

  </div>
</template>

<style scoped>
.txn-page { padding: 0 24px 24px; color: var(--text-primary); }
.kpi-bar { display:flex; gap:12px; margin-bottom:20px; flex-wrap:wrap; }
.kpi-item { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:14px 20px; min-width:150px; border-left:3px solid var(--accent); flex:1; }
.kpi-item.red    { border-left-color:var(--danger); }
.kpi-item.green  { border-left-color:var(--success); }
.kpi-item.orange { border-left-color:var(--warning); }
.kpi-lbl { display:block; font-size:.7rem; color:var(--text-muted); letter-spacing:1px; margin-bottom:4px; font-weight:600; }
.kpi-val { display:block; font-size:1.1rem; font-weight:700; font-family:var(--font-mono); }
.filters-bar { display:flex; gap:8px; margin-bottom:16px; flex-wrap:wrap; align-items:center; background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:12px 16px; }
.f-input,.f-select { background:var(--bg-input); border:1px solid var(--border); color:var(--text-primary); padding:7px 10px; border-radius:var(--radius-sm); font-size:.82rem; font-family:var(--font); }
.btn-apply { background:var(--accent); border:none; color:#fff; padding:7px 16px; border-radius:var(--radius-sm); font-size:.82rem; font-weight:600; cursor:pointer; font-family:var(--font); }
.btn-apply:disabled { opacity:.5; cursor:not-allowed; }
.btn-reset { background:var(--bg-card); border:1px solid var(--border); color:var(--text-secondary); padding:7px 14px; border-radius:var(--radius-sm); font-size:.82rem; cursor:pointer; font-family:var(--font); }
.err-bar { background:var(--danger-bg); color:var(--danger); padding:10px 14px; border-radius:var(--radius-md); margin-bottom:12px; }
.load-wrap { text-align:center; padding:40px; color:var(--text-muted); }
.spinner { display:inline-block; width:16px; height:16px; border:2px solid var(--border); border-top-color:var(--accent); border-radius:50%; animation:spin .7s linear infinite; vertical-align:middle; margin-right:8px; }
@keyframes spin { to { transform:rotate(360deg); } }
.table-wrap { overflow-x:auto; background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); }
.data-table { width:100%; border-collapse:collapse; font-size:.85rem; }
.data-table th { background:var(--bg-input); color:var(--text-muted); padding:10px 12px; text-align:left; font-size:.72rem; letter-spacing:.5px; font-weight:600; border-bottom:1px solid var(--border); white-space:nowrap; }
.data-table td { padding:9px 12px; border-bottom:1px solid rgba(42,69,99,.3); vertical-align:middle; }
.data-table tbody tr:hover { background:var(--bg-card-hover); }
.data-table tbody tr:last-child td { border-bottom:none; }
.row-urgent { border-left:3px solid #ff6400; background:rgba(255,100,0,.04); }
.empty-row { text-align:center; padding:40px; color:var(--text-muted); }
.id-col { color:var(--text-muted); font-family:var(--font-mono); font-size:.8rem; white-space:nowrap; }
.date-col { white-space:nowrap; font-size:.82rem; }
.desc-col { max-width:280px; }
.sub-col,.method-col { color:var(--text-secondary); font-size:.82rem; }
.ra { text-align:right; }
.mono { font-family:var(--font-mono); font-weight:600; }
.green { color:var(--success); }
.red   { color:var(--danger); }
.cat-badge { background:var(--bg-input); border:1px solid var(--border); padding:2px 8px; border-radius:var(--radius-sm); font-size:.75rem; white-space:nowrap; }
.status-badge { padding:2px 8px; border-radius:10px; font-size:.72rem; font-weight:600; white-space:nowrap; }
.badge-paid    { background:var(--success-bg); color:var(--success); }
.badge-unpaid  { background:var(--danger-bg);  color:var(--danger); }
.badge-urgent  { background:rgba(255,100,0,.15); color:#ff6400; }
.badge-partial { background:var(--warning-bg); color:var(--warning); }
.actions { white-space:nowrap; text-align:center; }
.icon-btn { background:none; border:1px solid var(--border); border-radius:var(--radius-sm); color:var(--text-muted); padding:4px 8px; cursor:pointer; font-size:.78rem; transition:all .2s; margin:0 2px; }
.icon-btn:hover { background:var(--bg-card-hover); color:var(--text-primary); }
.pagination { display:flex; align-items:center; gap:4px; margin-top:16px; flex-wrap:wrap; }
.pag-btn { background:var(--bg-card); border:1px solid var(--border); color:var(--text-secondary); padding:6px 12px; border-radius:var(--radius-sm); cursor:pointer; font-size:.82rem; font-family:var(--font); }
.pag-btn:hover:not(:disabled) { background:var(--bg-card-hover); color:var(--text-primary); }
.pag-btn.active { background:var(--accent); color:#fff; border-color:var(--accent); }
.pag-btn:disabled { opacity:.4; cursor:not-allowed; }
.pag-info { margin-left:12px; font-size:.82rem; color:var(--text-muted); }
</style>