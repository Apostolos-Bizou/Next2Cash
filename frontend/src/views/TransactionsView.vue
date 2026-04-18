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

// Current user role (for hiding edit/delete buttons for viewer/accountant)
const currentUser = computed(() => {
  try { return JSON.parse(localStorage.getItem('n2c_user') || '{}') } catch { return {} }
})
const canModify = computed(() => {
  const role = (currentUser.value.role || '').toLowerCase()
  return role === 'admin' || role === 'user'
})

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
const searchQuery = ref('')

// ───── Toast notifications ─────
const toast = ref({ show: false, type: 'success', message: '' })
let toastTimer = null
function showToast(type, message) {
  toast.value = { show: true, type, message }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.value.show = false }, 3500)
}

// ───── Edit modal state ─────
const editModal = ref({
  show: false,
  saving: false,
  deleting: false,
  data: null,
  original: null
})

// ───── Delete confirm state ─────
const deleteConfirm = ref({ show: false, item: null, deleting: false })

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

// ───── EDIT ─────
function openEdit(t) {
  if (!canModify.value) return
  editModal.value = {
    show: true,
    saving: false,
    deleting: false,
    data: {
      id: t.id,
      docDate: t.docDate || '',
      description: t.description || '',
      amount: t.amount != null ? String(t.amount) : '',
      type: t.type || 'expense',
      category: t.category || '',
      account: t.account || '',
      paymentMethod: t.paymentMethod || '',
      paymentStatus: t.paymentStatus || 'unpaid',
      paymentDate: t.paymentDate || ''
    },
    original: { ...t }
  }
}

function closeEdit() {
  if (editModal.value.saving || editModal.value.deleting) return
  editModal.value.show = false
}

async function saveEdit() {
  const d = editModal.value.data
  if (!d.docDate)     { showToast('error', 'Η ημερομηνία είναι υποχρεωτική'); return }
  if (!d.description) { showToast('error', 'Η περιγραφή είναι υποχρεωτική'); return }
  const amountNum = parseFloat(d.amount)
  if (isNaN(amountNum) || amountNum <= 0) { showToast('error', 'Μη έγκυρο ποσό'); return }

  editModal.value.saving = true
  try {
    const payload = {
      docDate: d.docDate,
      description: d.description,
      amount: amountNum,
      type: d.type,
      category: d.category,
      account: d.account,
      paymentMethod: d.paymentMethod,
      paymentStatus: d.paymentStatus,
      paymentDate: d.paymentDate || null
    }
    const res = await api.put('/api/transactions/' + d.id, payload)
    if (res.data && res.data.success !== false) {
      showToast('success', 'Η κίνηση αποθηκεύτηκε')
      editModal.value.show = false
      await loadTransactions()
    } else {
      showToast('error', (res.data && res.data.error) || 'Αποτυχία αποθήκευσης')
    }
  } catch (e) {
    console.error('saveEdit error:', e)
    showToast('error', e.response?.data?.error || 'Σφάλμα αποθήκευσης')
  } finally {
    editModal.value.saving = false
  }
}

// ───── DELETE ─────
function openDelete(t) {
  if (!canModify.value) return
  deleteConfirm.value = { show: true, item: t, deleting: false }
}

function closeDelete() {
  if (deleteConfirm.value.deleting) return
  deleteConfirm.value.show = false
}

async function confirmDelete() {
  const item = deleteConfirm.value.item
  if (!item) return
  deleteConfirm.value.deleting = true
  try {
    await api.delete('/api/transactions/' + item.id)
    showToast('success', 'Η κίνηση διαγράφηκε')
    deleteConfirm.value.show = false
    await loadTransactions()
  } catch (e) {
    console.error('delete error:', e)
    showToast('error', e.response?.data?.error || 'Αποτυχία διαγραφής')
  } finally {
    deleteConfirm.value.deleting = false
  }
}

// Client-side search filter across all visible fields
const filteredTransactions = computed(() => {
  if (!searchQuery.value) return transactions.value
  const q = searchQuery.value.toLowerCase().trim()
  return transactions.value.filter(t => {
    const fields = [
      String(t.id || ''),
      t.docDate || '',
      fmtDate(t.docDate),
      t.description || '',
      t.category || '',
      t.account || '',
      t.subcategory || '',
      t.paymentMethod || '',
      statusLabel(t.paymentStatus),
      t.paymentStatus || '',
      t.type === 'income' ? 'εισπραξη' : 'πληρωμη',
      String(t.amount || '')
    ]
    return fields.some(f => String(f).toLowerCase().includes(q))
  })
})

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

    <!-- Toast -->
    <transition name="toast">
      <div v-if="toast.show" class="toast" :class="'toast-' + toast.type">
        <i class="fas" :class="toast.type === 'success' ? 'fa-check-circle' : 'fa-exclamation-circle'"></i>
        {{ toast.message }}
      </div>
    </transition>

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
      <div class="search-wrap">
        <i class="fas fa-search search-icon"></i>
        <input
          v-model="searchQuery"
          type="text"
          class="search-input"
          placeholder="Αναζήτηση: ID, περιγραφή, κατηγορία, τρόπος, status..." />
        <button v-if="searchQuery" class="search-clear" @click="searchQuery = ''" title="Καθάρισμα">×</button>
      </div>
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
          <tr v-if="filteredTransactions.length === 0">
            <td colspan="10" class="empty-row">
              {{ searchQuery ? 'Δεν βρέθηκαν αποτελέσματα για "' + searchQuery + '"' : 'Δεν βρέθηκαν κινήσεις' }}
            </td>
          </tr>
          <tr v-for="t in filteredTransactions" :key="t.id"
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
              <button v-if="canModify" class="icon-btn" title="Επεξεργασία" @click="openEdit(t)">
                <i class="fas fa-edit"></i>
              </button>
              <button v-if="canModify" class="icon-btn icon-danger" title="Διαγραφή" @click="openDelete(t)">
                <i class="fas fa-trash"></i>
              </button>
              <button class="icon-btn" title="Παραστατικά" v-if="t.blobFileIds">
                <i class="fas fa-paperclip"></i>
              </button>
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

    <!-- ═══════ EDIT MODAL ═══════ -->
    <div v-if="editModal.show" class="modal-backdrop" @click.self="closeEdit">
      <div class="modal">
        <div class="modal-header">
          <h3>Επεξεργασία Κίνησης #{{ editModal.data.id }}</h3>
          <button class="modal-close" @click="closeEdit" :disabled="editModal.saving">×</button>
        </div>
        <div class="modal-body">
          <div class="form-grid">
            <div class="form-field">
              <label>Ημερομηνία *</label>
              <input v-model="editModal.data.docDate" type="date" class="f-input" />
            </div>
            <div class="form-field">
              <label>Τύπος</label>
              <select v-model="editModal.data.type" class="f-select">
                <option value="income">Έσοδο</option>
                <option value="expense">Έξοδο</option>
              </select>
            </div>
            <div class="form-field full">
              <label>Περιγραφή *</label>
              <input v-model="editModal.data.description" type="text" class="f-input" maxlength="500" />
            </div>
            <div class="form-field">
              <label>Ποσό (€) *</label>
              <input v-model="editModal.data.amount" type="number" step="0.01" min="0" class="f-input" />
            </div>
            <div class="form-field">
              <label>Κατηγορία</label>
              <input v-model="editModal.data.category" type="text" class="f-input" />
            </div>
            <div class="form-field">
              <label>Λογαριασμός / Υποκατηγορία</label>
              <input v-model="editModal.data.account" type="text" class="f-input" />
            </div>
            <div class="form-field">
              <label>Μέθοδος Πληρωμής</label>
              <input v-model="editModal.data.paymentMethod" type="text" class="f-input" />
            </div>
            <div class="form-field">
              <label>Status</label>
              <select v-model="editModal.data.paymentStatus" class="f-select">
                <option value="paid">Πληρωμένη</option>
                <option value="unpaid">Απλήρωτη</option>
                <option value="urgent">Εκκρεμής</option>
                <option value="partial">Μερική</option>
              </select>
            </div>
            <div class="form-field">
              <label>Ημερομηνία Πληρωμής</label>
              <input v-model="editModal.data.paymentDate" type="date" class="f-input" />
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeEdit" :disabled="editModal.saving">
            Ακύρωση
          </button>
          <button class="btn-primary" @click="saveEdit" :disabled="editModal.saving">
            <span v-if="editModal.saving"><span class="spinner-sm"></span> Αποθήκευση...</span>
            <span v-else><i class="fas fa-save"></i> Αποθήκευση</span>
          </button>
        </div>
      </div>
    </div>

    <!-- ═══════ DELETE CONFIRM ═══════ -->
    <div v-if="deleteConfirm.show" class="modal-backdrop" @click.self="closeDelete">
      <div class="modal modal-sm">
        <div class="modal-header">
          <h3>Επιβεβαίωση Διαγραφής</h3>
        </div>
        <div class="modal-body">
          <p class="confirm-msg">
            Σίγουρα θέλεις να διαγράψεις την κίνηση
            <strong>#{{ deleteConfirm.item?.id }}</strong>;
          </p>
          <p class="confirm-detail" v-if="deleteConfirm.item">
            {{ deleteConfirm.item.description }} — <strong>{{ fmt(deleteConfirm.item.amount) }}</strong>
          </p>
          <p class="confirm-warn">Η ενέργεια δεν αναιρείται.</p>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeDelete" :disabled="deleteConfirm.deleting">
            Ακύρωση
          </button>
          <button class="btn-danger" @click="confirmDelete" :disabled="deleteConfirm.deleting">
            <span v-if="deleteConfirm.deleting"><span class="spinner-sm"></span> Διαγραφή...</span>
            <span v-else><i class="fas fa-trash"></i> Διαγραφή</span>
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.txn-page { padding: 0 24px 24px; color: var(--text-primary); position: relative; }
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
.spinner-sm { display:inline-block; width:12px; height:12px; border:2px solid rgba(255,255,255,.3); border-top-color:#fff; border-radius:50%; animation:spin .7s linear infinite; vertical-align:middle; margin-right:6px; }
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
.icon-btn.icon-danger:hover { color:var(--danger); border-color:var(--danger); }
.pagination { display:flex; align-items:center; gap:4px; margin-top:16px; flex-wrap:wrap; }
.pag-btn { background:var(--bg-card); border:1px solid var(--border); color:var(--text-secondary); padding:6px 12px; border-radius:var(--radius-sm); cursor:pointer; font-size:.82rem; font-family:var(--font); }
.pag-btn:hover:not(:disabled) { background:var(--bg-card-hover); color:var(--text-primary); }
.pag-btn.active { background:var(--accent); color:#fff; border-color:var(--accent); }
.pag-btn:disabled { opacity:.4; cursor:not-allowed; }
.pag-info { margin-left:12px; font-size:.82rem; color:var(--text-muted); }

.search-wrap { position:relative; flex:1; min-width:260px; max-width:420px; }
.search-icon { position:absolute; left:10px; top:50%; transform:translateY(-50%); color:var(--text-muted); font-size:.8rem; pointer-events:none; }
.search-input { width:100%; background:var(--bg-input); border:1px solid var(--border); color:var(--text-primary); padding:7px 32px 7px 30px; border-radius:var(--radius-sm); font-size:.82rem; font-family:var(--font); box-sizing:border-box; }
.search-input:focus { outline:none; border-color:var(--accent); }
.search-clear { position:absolute; right:6px; top:50%; transform:translateY(-50%); background:none; border:none; color:var(--text-muted); font-size:1.2rem; cursor:pointer; line-height:1; padding:2px 6px; border-radius:3px; }
.search-clear:hover { color:var(--text-primary); background:var(--bg-card-hover); }

/* ───── Toast ───── */
.toast { position:fixed; bottom:24px; right:24px; background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-md); padding:14px 20px; font-size:.9rem; z-index:2000; box-shadow:0 4px 20px rgba(0,0,0,.3); display:flex; align-items:center; gap:10px; min-width:240px; }
.toast-success { border-left:4px solid var(--success); color:var(--success); }
.toast-error   { border-left:4px solid var(--danger);  color:var(--danger); }
.toast-enter-active, .toast-leave-active { transition: all .3s ease; }
.toast-enter-from { opacity:0; transform:translateX(30px); }
.toast-leave-to   { opacity:0; transform:translateX(30px); }

/* ───── Modal ───── */
.modal-backdrop { position:fixed; inset:0; background:rgba(0,0,0,.6); display:flex; align-items:center; justify-content:center; z-index:1500; padding:20px; }
.modal { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); width:100%; max-width:720px; max-height:90vh; display:flex; flex-direction:column; box-shadow:0 10px 40px rgba(0,0,0,.5); }
.modal-sm { max-width:440px; }
.modal-header { display:flex; justify-content:space-between; align-items:center; padding:16px 20px; border-bottom:1px solid var(--border); }
.modal-header h3 { margin:0; font-size:1.05rem; color:var(--text-primary); }
.modal-close { background:none; border:none; color:var(--text-muted); font-size:1.6rem; cursor:pointer; line-height:1; padding:0 4px; }
.modal-close:hover:not(:disabled) { color:var(--text-primary); }
.modal-close:disabled { opacity:.4; cursor:not-allowed; }
.modal-body { padding:20px; overflow-y:auto; flex:1; }
.modal-footer { display:flex; justify-content:flex-end; gap:10px; padding:14px 20px; border-top:1px solid var(--border); }
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
.form-field { display:flex; flex-direction:column; gap:5px; }
.form-field.full { grid-column: 1 / -1; }
.form-field label { font-size:.75rem; color:var(--text-muted); font-weight:600; letter-spacing:.3px; }
.btn-primary { background:var(--accent); border:none; color:#fff; padding:9px 18px; border-radius:var(--radius-sm); font-size:.86rem; font-weight:600; cursor:pointer; font-family:var(--font); display:inline-flex; align-items:center; gap:6px; }
.btn-primary:disabled { opacity:.6; cursor:not-allowed; }
.btn-secondary { background:var(--bg-input); border:1px solid var(--border); color:var(--text-secondary); padding:9px 18px; border-radius:var(--radius-sm); font-size:.86rem; cursor:pointer; font-family:var(--font); }
.btn-secondary:disabled { opacity:.5; cursor:not-allowed; }
.btn-danger { background:var(--danger); border:none; color:#fff; padding:9px 18px; border-radius:var(--radius-sm); font-size:.86rem; font-weight:600; cursor:pointer; font-family:var(--font); display:inline-flex; align-items:center; gap:6px; }
.btn-danger:disabled { opacity:.6; cursor:not-allowed; }
.confirm-msg { font-size:.95rem; margin:0 0 10px; color:var(--text-primary); }
.confirm-detail { font-size:.85rem; color:var(--text-secondary); margin:0 0 14px; padding:10px 12px; background:var(--bg-input); border-radius:var(--radius-sm); }
.confirm-warn { font-size:.8rem; color:var(--warning); margin:0; }

@media (max-width: 640px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>