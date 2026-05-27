<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'   // Session #40
import api from '@/api'
import MarkPaidModal from '@/components/MarkPaidModal.vue'
import AttachmentsPopover from '@/components/AttachmentsPopover.vue'

const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  next2megroup: '50317f44-9961-4fb4-add0-7a118e32dc14',
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
// Phase 1-F2.1: entry mode filter — default to ACTUAL so PLANNED business
// plan entries do not pollute the day-to-day cash flow view.
// Values: 'ACTUAL' (default) | 'PLANNED' | 'ALL'
const selectedMode  = ref(localStorage.getItem('n2c_tx_mode') || 'ACTUAL')
const page    = ref(0)
const perPage = ref(25)
const total   = ref(0)
const pages   = ref(0)
const loading = ref(false)
const error   = ref(null)
const transactions = ref([])
const searchQuery = ref('')

// Phase 60-E: column filter state
const filterCategory    = ref([])
const filterSubcategory = ref([])
const filterMethod      = ref([])
const openFilterMenu    = ref(null) // 'category' | 'subcategory' | 'method' | null
const filterMenuSearch  = ref('')
function toggleFilterMenu(name) {
  openFilterMenu.value = openFilterMenu.value === name ? null : name
  filterMenuSearch.value = ''
}
function closeFilterMenu() {
  openFilterMenu.value = null
  filterMenuSearch.value = ''
}
let searchDebounceTimer = null

// Phase 1-F2.1: persist mode selection per user so it survives refresh
watch(selectedMode, (m) => {
  try { localStorage.setItem('n2c_tx_mode', m || 'ACTUAL') } catch (_) {}
  page.value = 0  // reset to first page when mode changes
})

// --- Phase 4 / Step 4.2: Bank accounts for edit modal dropdown -----
// Loaded on mount + when entityId changes. Generic methods are kept
// as a secondary group so non-bank cases (cash, card, etc.) remain
// selectable. Legacy values fall back as a leading option if missing.
const editBankAccounts = ref([])
const GENERIC_PAYMENT_METHODS = [
  'Τράπεζα', 'Μετρητά', 'Κάρτα', 'Επιταγή', 'PayPal', 'Άλλο'
]

async function loadEditBankAccounts() {
  const eid = entityId.value
  if (!eid) { editBankAccounts.value = []; return }
  try {
    const res = await api.get('/api/bank-accounts', { params: { entityId: eid } })
    if (res && res.data && res.data.success) {
      editBankAccounts.value = res.data.accounts || []
    } else {
      editBankAccounts.value = []
    }
  } catch (e) {
    console.error('[TransactionsView] loadEditBankAccounts error:', e)
    editBankAccounts.value = []
  }
}

const availableEditPaymentMethods = computed(() => {
  const result = []
  for (const b of editBankAccounts.value) {
    if (!b || !b.accountLabel) continue
    if (b.accountLabel === 'Ανεκχώρητο') continue
    if (b.isActive === false) continue
    if (!result.includes(b.accountLabel)) result.push(b.accountLabel)
  }
  for (const m of GENERIC_PAYMENT_METHODS) {
    if (!result.includes(m)) result.push(m)
  }
  // Legacy fallback: keep current value visible if missing from both groups
  const cur = editModal.value && editModal.value.data && editModal.value.data.paymentMethod
  if (cur && !result.includes(cur)) result.unshift(cur)
  return result
})

// Reload when the user switches entity
watch(entityId, () => { loadEditBankAccounts(); loadEditConfig() })


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

// ───── S88: PLANNED edit support ─────
// When the edited transaction is PLANNED, the modal exposes the same blocks
// as NewEntryView (recurrence/project/scenario/confidence/notes). These refs
// hold the editable PLANNED state, pre-filled in openEdit() and consumed in
// saveEdit(). ACTUAL editing never touches them.
const editPlanned = ref({
  isRecurring:   false,
  frequency:     'MONTHLY',
  dayOfMonth:    1,
  dayOfWeek:     1,
  intervalCount: 1,
  startDate:     '',
  endDate:       '',
  isOpenEnded:   true,
  isOpEx:        true,
  projectId:     '',
  scenario:      'BASELINE',
  confidence:    100,
  notes:         '',
  // internal: existing pattern id (if the txn was already recurring)
  recurrencePatternId: null
})
const editProjects = ref([])
const editLoadingProjects = ref(false)

const editFrequencyOptions = [
  { value: 'DAILY',     label: '\u039a\u03b1\u03b8\u03b7\u03bc\u03b5\u03c1\u03b9\u03bd\u03ac' },
  { value: 'WEEKLY',    label: '\u0395\u03b2\u03b4\u03bf\u03bc\u03b1\u03b4\u03b9\u03b1\u03af\u03b1' },
  { value: 'MONTHLY',   label: '\u039c\u03b7\u03bd\u03b9\u03b1\u03af\u03b1' },
  { value: 'QUARTERLY', label: '\u03a4\u03c1\u03b9\u03bc\u03b7\u03bd\u03b9\u03b1\u03af\u03b1' },
  { value: 'YEARLY',    label: '\u0395\u03c4\u03ae\u03c3\u03b9\u03b1' },
]
const editDayOfWeekOptions = [
  { value: 1, label: '\u0394\u03b5\u03c5\u03c4\u03ad\u03c1\u03b1' },
  { value: 2, label: '\u03a4\u03c1\u03af\u03c4\u03b7' },
  { value: 3, label: '\u03a4\u03b5\u03c4\u03ac\u03c1\u03c4\u03b7' },
  { value: 4, label: '\u03a0\u03ad\u03bc\u03c0\u03c4\u03b7' },
  { value: 5, label: '\u03a0\u03b1\u03c1\u03b1\u03c3\u03ba\u03b5\u03c5\u03ae' },
  { value: 6, label: '\u03a3\u03ac\u03b2\u03b2\u03b1\u03c4\u03bf' },
  { value: 7, label: '\u039a\u03c5\u03c1\u03b9\u03b1\u03ba\u03ae' },
]
const editScenarioOptions = [
  { value: 'BASELINE',    label: 'Baseline',    color: '#3b82f6' },
  { value: 'OPTIMISTIC',  label: 'Optimistic',  color: '#10b981' },
  { value: 'PESSIMISTIC', label: 'Pessimistic', color: '#ef4444' },
]

async function loadEditProjects() {
  if (editProjects.value.length > 0) return
  editLoadingProjects.value = true
  try {
    const res = await api.get('/api/projects', { params: { entityId: entityId.value } })
    if (res && res.data && res.data.success && Array.isArray(res.data.data)) {
      editProjects.value = res.data.data
    } else if (res && res.data && Array.isArray(res.data)) {
      editProjects.value = res.data
    }
  } catch (e) {
    console.warn('[TransactionsView] loadEditProjects failed:', e)
  } finally {
    editLoadingProjects.value = false
  }
}

// --- Step 57-A.2: Categories + subcategories for edit modal cascading dropdowns -----
// Categories and subcategories come from /api/config. Subcategories are
// filtered by the currently selected category in the edit modal via the
// filteredEditSubcategories computed below. The watch on the category field
// resets the subcategory whenever the user changes the category, EXCEPT on
// the initial pre-fill from openEdit (where _skipNextSubcatReset is set).
//
// IMPORTANT: This block MUST live AFTER the editModal declaration above,
// otherwise the watch's getter throws a TDZ error during setup execution.
const editCategories = ref([])    // [{ key, value, ... }]
const editAllSubcats = ref([])    // [{ key, value, parentKey, ... }]
let _skipNextSubcatReset = false

async function loadEditConfig() {
  const eid = entityId.value
  if (!eid) { editCategories.value = []; editAllSubcats.value = []; return }
  try {
    const res = await api.get('/api/config', { params: { entityId: eid } })
    if (res && res.data && res.data.success) {
      editCategories.value = res.data.categories    || []
      editAllSubcats.value = res.data.subcategories || []
    } else {
      editCategories.value = []
      editAllSubcats.value = []
    }
  } catch (e) {
    console.error('[TransactionsView] loadEditConfig error:', e)
    editCategories.value = []
    editAllSubcats.value = []
  }
}

const filteredEditSubcategories = computed(() => {
  const cat = editModal.value && editModal.value.data && editModal.value.data.category
  if (!cat) return []
  const list = editAllSubcats.value.filter(s => s.parentKey === cat)
  // Legacy fallback: if the currently selected subcategory is NOT in the
  // canonical filtered list (e.g. the parent category has a Greek/Latin
  // letter mismatch from legacy data, or the subcategory was renamed), keep
  // it visible as a leading option so the user sees their actual value.
  const cur = editModal.value && editModal.value.data && editModal.value.data.subcategory
  if (cur && !list.some(s => s.key === cur)) {
    return [{ key: cur, value: cur + ' (legacy)', parentKey: cat }, ...list]
  }
  return list
})

// Legacy fallback for the category dropdown - mirrors the subcategory pattern.
// If the current category is not in the canonical list (e.g. legacy "ΕΣΟΔΑ B"
// with Latin B vs the canonical "ΕΣΟΔΑ Β" with Greek B), prepend it so the
// user sees the actual stored value.
const editCategoriesWithFallback = computed(() => {
  const list = editCategories.value || []
  const cur = editModal.value && editModal.value.data && editModal.value.data.category
  if (cur && !list.some(c => c.key === cur)) {
    return [{ key: cur, value: cur + ' (legacy)', parentKey: null }, ...list]
  }
  return list
})

watch(
  () => editModal.value && editModal.value.data && editModal.value.data.category,
  (newCat, oldCat) => {
    if (_skipNextSubcatReset) { _skipNextSubcatReset = false; return }
    if (!editModal.value || !editModal.value.data) return
    if (newCat !== oldCat) {
      editModal.value.data.subcategory = ''
    }
  }
)

// --- Step 57-A.3: Attachments list inside edit modal -----------------------
// Loaded when openEdit fires. Uses the same endpoints as AttachmentsPopover.
// Per-row: preview (open in new tab) + delete (DELETE /api/documents/by-transaction/{id}).
// On delete success the entry is spliced from the local array (no full reload).
const editAttachments = ref([])              // [{ fileName, blobPath, sizeBytes, downloadUrl }]
const editAttachmentsLoading = ref(false)
const editAttachmentsError = ref('')
const editAttachmentDeletingIdx = ref(-1)

async function loadEditAttachments(txId) {
  if (!txId) { editAttachments.value = []; return }
  editAttachmentsLoading.value = true
  editAttachmentsError.value = ''
  editAttachments.value = []
  try {
    const res = await api.get('/api/documents/by-transaction/' + txId)
    if (res && res.data && res.data.success) {
      editAttachments.value = res.data.data || []
    } else {
      editAttachmentsError.value = (res.data && res.data.error) || 'Αποτυχία φόρτωσης αρχείων'
    }
  } catch (e) {
    console.error('[TransactionsView] loadEditAttachments error:', e)
    editAttachmentsError.value = e.response?.data?.error || e.message || 'Σφάλμα σύνδεσης'
  } finally {
    editAttachmentsLoading.value = false
  }
}

function onPreviewEditAttachment(file) {
  if (file && file.downloadUrl) {
    window.open(file.downloadUrl, '_blank', 'noopener,noreferrer')
  }
}

async function onDeleteEditAttachment(file, idx) {
  // Greek confirm dialog via \u escapes (PowerShell display safety + .cjs compat).
  const ok = window.confirm(
    '\u0394\u03b9\u03b1\u03b3\u03c1\u03b1\u03c6\u03ae \u03b1\u03c1\u03c7\u03b5\u03af\u03bf\u03c5;\n\n' +
    (file.fileName || '') + '\n\n' +
    '\u0397 \u03b5\u03bd\u03ad\u03c1\u03b3\u03b5\u03b9\u03b1 \u03b4\u03b5\u03bd \u03b1\u03bd\u03b1\u03b9\u03c1\u03b5\u03af\u03c4\u03b1\u03b9.'
  )
  if (!ok) return

  const txId = editModal.value && editModal.value.data && editModal.value.data.id
  if (!txId) return
  editAttachmentDeletingIdx.value = idx
  try {
    const res = await api.delete(
      '/api/documents/by-transaction/' + txId,
      { data: { blobPath: file.blobPath } }
    )
    if (res && res.data && res.data.success) {
      editAttachments.value.splice(idx, 1)
      showToast('success', 'Το αρχείο διαγράφηκε')
    } else {
      const err = (res.data && res.data.error) || 'άγνωστο σφάλμα'
      showToast('error', 'Σφάλμα διαγραφής: ' + err)
    }
  } catch (e) {
    console.error('[TransactionsView] onDeleteEditAttachment error:', e)
    showToast('error', 'Σφάλμα διαγραφής: ' + (e.response?.data?.error || e.message || ''))
  } finally {
    editAttachmentDeletingIdx.value = -1
  }
}

// --- Step 57-A.4: Multi-file upload area in edit modal ---------------------
// Files are STAGED here (not uploaded immediately) and are POSTed in saveEdit
// after the PUT succeeds. Mirrors the NewEntryView pattern from Phase 56-A.1.
const editUploadedFiles = ref([])    // Array<File>
const editDriveFileNames = ref([])   // Array<string>, parallel index

function buildEditDefaultName(file, indexAmongAll) {
  const ext = file.name.includes('.') ? file.name.substring(file.name.lastIndexOf('.')) : ''
  const desc = (editModal.value && editModal.value.data && editModal.value.data.description) || ''
  const baseDesc = desc.trim() ||
    String((editModal.value && editModal.value.data && editModal.value.data.entityNumber) ||
           (editModal.value && editModal.value.data && editModal.value.data.id) || '')
  if (indexAmongAll === 0) return baseDesc + ext
  return baseDesc + ' (' + (indexAmongAll + 1) + ')' + ext
}

function onEditFileChange(e) {
  const incoming = Array.from(e.target.files || [])
  if (incoming.length === 0) return
  for (const file of incoming) {
    const idx = editUploadedFiles.value.length
    editUploadedFiles.value.push(file)
    editDriveFileNames.value.push(buildEditDefaultName(file, idx))
  }
  try { e.target.value = '' } catch (_) {}
}

function removeEditFile(idx) {
  editUploadedFiles.value.splice(idx, 1)
  editDriveFileNames.value.splice(idx, 1)
}

function resetEditUploadedFiles() {
  editUploadedFiles.value = []
  editDriveFileNames.value = []
}

// ───── Delete confirm state ─────
const deleteConfirm = ref({ show: false, item: null, deleting: false })

// Mark paid state
const markPaidState = ref({ visible: false, transaction: null })
// S93B-PAID-EDIT-DIALOG: shown when a PUT is blocked because the txn has a payment.
const paidEditState = ref({ visible: false, paymentId: null, paymentAmount: null, paymentDate: '', reopenData: null, deleting: false })
// S94B-PAID-NO-PAYMENT-DIALOG: shown when backend returns paid_no_payment_record (legacy paid txns without a Payment row).
const paidNoPaymentState = ref({ visible: false, paymentStatus: '', amountPaid: 0, reopenData: null })
function cancelPaidNoPayment() {
  paidNoPaymentState.value = { visible: false, paymentStatus: '', amountPaid: 0, reopenData: null }
}
function openMarkPaid(t) {
  const txn = { ...t, entityId: ENTITIES[selectedEntity.value] }
  markPaidState.value = { visible: true, transaction: txn }
}
function closeMarkPaid() { markPaidState.value = { visible: false, transaction: null } }
function onMarkPaidSaved() { closeMarkPaid(); loadTransactions() }
function canMarkPaid(t) {
  // Defensive: if the row is already paid/received, never show the button,
  // even if amountRemaining is stale (e.g. legacy imports where the
  // recompute trigger didn't fire and amountRemaining wasn't zeroed).
  if (t.paymentStatus === 'paid' || t.paymentStatus === 'received') return false
  return t.recordSource !== 'PAYMENT' && (Number(t.amountRemaining) || 0) > 0.01
}
function isUnpaid(t) {
  const s = t.paymentStatus || ''
  return s === 'unpaid' || s === 'urgent' || s === 'partially_paid' || s === 'overdue'
}

// Attachments state
const attachmentsState = ref({ visible: false, transaction: null })
function openAttachments(t) {
  // For payment rows (cashflow mode), attachments live on the parent
  // transaction. Pass the txn id (not the synthetic 'pay-N' row id) so
  // /api/documents/by-transaction/{id} hits a real Integer resource.
  const realId = t._txnRef != null ? t._txnRef : t.id
  attachmentsState.value = { visible: true, transaction: { ...t, id: realId } }
}
function closeAttachments() { attachmentsState.value = { visible: false, transaction: null } }
function hasAttachments(t) { return !!t.blobFileIds && String(t.blobFileIds).trim() !== "" }

async function loadTransactions() {
      loading.value = true
      error.value   = null
      try {
        const searchWords = (searchQuery.value || '').trim().split(/\s+/).filter(Boolean)
        const isSearch = searchWords.length > 0

        // Cashflow mode: BOTH dates set, no search, no type/status filters.
        const isCashflowMode = !isSearch
                            && dateFrom.value
                            && dateTo.value
                            && !selectedType.value
                            && !selectedStatus.value

        if (isCashflowMode) {
          try {
            const cfParams = {
              entityId: entityId.value,
              from: dateFrom.value,
              to: dateTo.value
            }
            const cfRes = await api.get('/api/cashflow', { params: cfParams })
            const events = (cfRes.data && cfRes.data.events) || []
            transactions.value = events.map(mapCashflowEventToRow)
            total.value = events.length
            pages.value = 0
            return
          } catch (cfErr) {
            console.warn('cashflow endpoint failed, falling back to /api/transactions', cfErr)
          }
        }

        // Standard mode: /api/transactions
        // Fetch ALL transactions for the entity in one shot (perPage=10000)
        // so virtual payment rows can be injected at their true paymentDate
        // and displayed alongside the canonical invoice rows in chronological
        // order - matching legacy CashControl behaviour. All filtering,
        // searching, and pagination is then handled client-side via the
        // filteredTransactions and paginatedTransactions computeds.
        const params = { entityId: entityId.value, page: 0, perPage: 10000 }
        const res = await api.get('/api/transactions', { params })
        if (res.data.success) {
          transactions.value = injectVirtualPaymentRows(res.data.data || [])
          // total and pages are now derived client-side from the augmented
          // and filtered list (see paginatedTransactions computed below).
        }
      } catch (e) {
    error.value = 'Σφάλμα φόρτωσης'
    console.error(e)
  } finally {
    loading.value = false
  }
}

function stripPaymentPrefix(desc, entityNumber) {
      // [62-E] strip leading entity number from parent desc
      // Two patterns are removed:
      //   1. Legacy "Πληρωμή για #1234 — " (preserved from before)
      //   2. Leading "<entityNumber>" + separator (hyphen, en-dash, em-dash)
      //      when entityNumber matches the leading digits, to avoid
      //      "Payment #4797 — 4797 - DESC". Uses string-methods, no RegExp.
      if (!desc) return desc
      let out = desc.replace(/^Πληρωμή για #\d+\s+—\s+/, '')
      if (entityNumber != null) {
        const pENs = String(entityNumber)
        if (out.startsWith(pENs)) {
          let rest = out.slice(pENs.length)
          let i = 0
          while (i < rest.length && (rest[i] === ' ' || rest[i] === '\t')) i++
          if (i < rest.length && (rest[i] === '-' || rest[i] === '–' || rest[i] === '—')) {
            i++
            while (i < rest.length && (rest[i] === ' ' || rest[i] === '\t')) i++
            out = rest.slice(i)
          }
        }
      }
      return out
    }

    function mapCashflowEventToRow(ev) {
      const isPayment = ev.eventType === 'payment'
      const inflow = Number(ev.inflow || 0)
      // For display: use ev.entityNumber (the user-visible number, e.g. "#4747"
      // or "#101"), NOT ev.transactionId (internal DB id like #90129).
      // For payment rows, strip the "Payment for #N - " prefix from description
      // since the row is already marked with a "<<" badge in the id column;
      // the bare description fits cleanly within the 40-char display cap.
      // Step 57-E.4: align with standard-mode injectVirtualPaymentRows.
      // Standard mode produces "💳 Πληρωμή #X — [parent]" for
      // virtual rows. Cashflow mode should match for consistency.
      const cleanDesc = isPayment ? stripPaymentPrefix(ev.description, ev.entityNumber != null ? ev.entityNumber : ev.transactionId) : ev.description
      const idForLabel = ev.entityNumber != null ? ev.entityNumber : ev.transactionId
      const displayDesc = isPayment
        ? '💳 Πληρωμή #' + idForLabel + ' — ' + cleanDesc
        : ev.description
      return {
        id: isPayment ? 'pay-' + ev.paymentId : ev.transactionId,
        entityNumber: ev.entityNumber != null ? ev.entityNumber : ev.transactionId,
        docDate: ev.date,
        description: displayDesc,
        category: ev.category,
        account: ev.account,
        subcategory: ev.subcategory,
        paymentMethod: ev.paymentMethod,
        amount: ev.amount,
        type: inflow > 0 ? 'income' : 'expense',
        // Step 57-E.3: populate amountRemaining so canMarkPaid() works
        // in cashflow mode. For paid/received/payment rows it's 0; for
        // urgent/unpaid it's the full event amount.
        amountRemaining: (isPayment
          || ev.paymentStatus === 'paid'
          || ev.paymentStatus === 'received')
          ? 0
          : Number(ev.amount || 0),
        paymentStatus: isPayment ? 'paid' : (ev.paymentStatus || 'unpaid'),
        blobFileIds: ev.blobFileIds,
        _isPaymentRow: isPayment,
        _paymentId: isPayment ? ev.paymentId : null,
        _txnRef: ev.transactionId
      }
    }


    // ------------------------------------------------------------------
    // injectVirtualPaymentRows(rows)
    // ------------------------------------------------------------------
    // Legacy CashControl behaviour: when a paid transaction has a
    // paymentDate that differs from its docDate, the Transactions list
    // shows TWO rows - the canonical invoice row at docDate, plus a
    // "virtual" payment row at paymentDate. This restores that view so
    // the user can see WHEN money actually moved, not only when the
    // invoice was filed.
    //
    // Backend stays canonical (one row per transaction). Cashflow report
    // and BankBalance recompute read raw transactions and are NOT
    // affected by this client-side augmentation.
    //
    // Virtual rows are flagged with _isPaymentRow:true so:
    //   - kpis computed already excludes them (no double-counting)
    //   - template applies 'row-payment' CSS class
    //   - Mark-Paid / Edit / Delete buttons are auto-hidden
    //
    // After injection, list is re-sorted by docDate DESC so virtual
    // rows land in their correct chronological slot.
    // ------------------------------------------------------------------
    function injectVirtualPaymentRows(rows) {
      if (!Array.isArray(rows) || rows.length === 0) return rows || []
      const out = []
      for (const r of rows) {
        out.push(r)
        const hasSplit = r.paymentDate
                        && r.docDate
                        && r.paymentDate !== r.docDate
                        && r.paymentStatus === 'paid'
        if (!hasSplit) continue
        const idLabel   = r.entityNumber != null ? r.entityNumber : r.id
        const cleanDesc = stripPaymentPrefix(r.description || '', idLabel)
        const virtualRow = {
          ...r,
          id:            'pay-' + r.id,
          docDate:       r.paymentDate,
          description:   '\ud83d\udcb3 \u03a0\u03bb\u03b7\u03c1\u03c9\u03bc\u03ae #' + idLabel + ' \u2014 ' + cleanDesc,
          _isPaymentRow: true,
          _txnRef:       r.id,
          _paymentId:    null
        }
        out.push(virtualRow)
      }
      // Re-sort by docDate DESC (most recent first), stable on equal dates.
      out.sort((a, b) => {
        const da = a.docDate || ''
        const db = b.docDate || ''
        if (da < db) return  1
        if (da > db) return -1
        return 0
      })
      return out
    }

    // Filter / pagination handlers are now pure client-side state changes.
// No backend round-trip - the paginatedTransactions computed reacts
// automatically to filter and page changes.
function applyFilters() {
  // Step 57-D.7: reset page AND reload transactions so cashflow vs
  // standard mode switches correctly when date filters change.
  page.value = 0
  loadTransactions()
}
function goToPage(p)    { page.value = p }
function resetFilters() {
  dateFrom.value = ''; dateTo.value = ''
  selectedType.value = ''; selectedStatus.value = ''
  // Phase 60-E: clear column filters too
  filterCategory.value = []
  filterSubcategory.value = []
  filterMethod.value = []
  // Phase 1-F2.1: selectedMode is intentionally NOT reset — it's a persistent
  // user view preference (Real vs Planned vs All), not a filter. Reset clears
  // the search filters, not the lens through which the user views transactions.
  closeFilterMenu()
  page.value = 0
}

// ───── Step 57-D.2: Route Edit click for virtual payment rows ─────
// Virtual payment rows (» #X — Πληρωμή #X) carry _txnRef pointing to the
// parent transaction. When the user clicks Edit on such a row, we open
// the edit modal for the PARENT transaction (which holds the editable
// fields — type, category, amount, etc).
function onEditAnyRow(t) {
  if (!t) return
  if (t._isPaymentRow) {
    const realId = t._txnRef
    if (realId == null) return
    // Find the canonical (non-virtual) parent row in the loaded list.
    const parent = transactions.value.find(x => x.id === realId && !x._isPaymentRow)
    if (parent) {
      openEdit(parent)
    } else {
      // Fallback: open edit on the virtual row itself (data is partial,
      // but openEdit expects fields that exist on the virtual row too).
      // This branch should be rare since the parent is always loaded
      // alongside its virtual injections.
      openEdit(t)
    }
  } else {
    openEdit(t)
  }
}

// ───── EDIT ─────
function openEdit(t) {
  if (!canModify.value) return
  // Skip the next subcategory reset - we are pre-filling both fields below
  // and the watch on data.category would otherwise wipe the subcategory.
  _skipNextSubcatReset = true
  editModal.value = {
    show: true,
    saving: false,
    deleting: false,
    data: {
      id: t.id,
      entityNumber: t.entityNumber != null ? t.entityNumber : null,
      docDate: t.docDate || '',
      description: t.description || '',
      amount: t.amount != null ? String(t.amount) : '',
      type: t.type || 'expense',
      category: t.category || '',
      subcategory: t.subcategory || t.account || '',
      account: t.account || '',
      paymentMethod: t.paymentMethod || '',
      paymentStatus: t.paymentStatus || 'unpaid',
      paymentDate: t.paymentDate || ''
    },
    original: { ...t }
  }
  // Step 57-A.3: load attachments for this transaction
  loadEditAttachments(t.id)
  // Step 57-A.4: reset any staged uploads from a previous open
  resetEditUploadedFiles()
  // S88: pre-fill PLANNED state (entryMode comes from the API as camelCase)
  prefillEditPlanned(t)
}

// S88: copy PLANNED fields from the transaction into editPlanned, and
// stamp entryMode onto editModal.data so the template can branch on it.
function prefillEditPlanned(t) {
  const mode = (t.entryMode || 'ACTUAL').toUpperCase()
  editModal.value.data.entryMode = mode
  // Strip the "[Σημ.] ..." notes suffix back out of the description so the
  // notes field shows it separately (same convention as NewEntryView).
  const NOTE_MARK = '\n[\u03a3\u03b7\u03bc.] '
  let baseDesc = t.description || ''
  let notes = ''
  const markIdx = baseDesc.indexOf(NOTE_MARK)
  if (markIdx >= 0) {
    notes = baseDesc.substring(markIdx + NOTE_MARK.length)
    baseDesc = baseDesc.substring(0, markIdx)
    editModal.value.data.description = baseDesc
  }
  editPlanned.value = {
    isRecurring:   !!t.isRecurring,
    frequency:     'MONTHLY',
    dayOfMonth:    1,
    dayOfWeek:     1,
    intervalCount: 1,
    startDate:     t.docDate || '',
    endDate:       '',
    isOpenEnded:   true,
    isOpEx:        !t.projectId,
    projectId:     t.projectId || '',
    scenario:      'BASELINE',
    confidence:    (t.confidencePct != null ? t.confidencePct : 100),
    notes:         notes,
    recurrencePatternId: t.recurrencePatternId || null
  }
  if (mode === 'PLANNED') {
    loadEditProjects()
    if (t.recurrencePatternId) {
      loadEditPattern(t.recurrencePatternId)
    }
  }
}

// S88: fetch an existing recurrence pattern's details to fill the form.
async function loadEditPattern(patternId) {
  try {
    const res = await api.get('/api/recurrence-patterns/' + patternId)
    const p = (res && res.data && (res.data.data || res.data)) || null
    if (p && (p.frequency || p.id)) {
      editPlanned.value.frequency     = p.frequency || 'MONTHLY'
      editPlanned.value.intervalCount = p.intervalCount || 1
      if (p.dayOfMonth != null) editPlanned.value.dayOfMonth = p.dayOfMonth
      if (p.dayOfWeek  != null) editPlanned.value.dayOfWeek  = p.dayOfWeek
      if (p.startDate) editPlanned.value.startDate = p.startDate
      if (p.endDate) {
        editPlanned.value.endDate = p.endDate
        editPlanned.value.isOpenEnded = false
      } else {
        editPlanned.value.isOpenEnded = true
      }
    }
  } catch (e) {
    console.warn('[TransactionsView] loadEditPattern failed:', e)
  }
}

// S93B: confirm deletion of the blocking payment, then reopen edit.
async function confirmPaidEditDelete() {
  const st = paidEditState.value
  if (!st.paymentId) { paidEditState.value.visible = false; return }
  paidEditState.value.deleting = true
  try {
    const res = await api.delete('/api/payments/' + st.paymentId)
    if (res.data && res.data.success !== false) {
      showToast('success', 'Η πληρωμή διαγράφηκε')
      const reopen = st.reopenData
      paidEditState.value = { visible: false, paymentId: null, paymentAmount: null, paymentDate: '', reopenData: null, deleting: false }
      await loadTransactions()
      if (reopen && reopen.id != null) {
        const fresh = transactions.value.find(function (x) { return x.id === reopen.id && !x._isPaymentRow })
        openEdit(fresh || reopen)
      }
    } else {
      showToast('error', (res.data && res.data.error) || 'Αποτυχία διαγραφής')
      paidEditState.value.deleting = false
    }
  } catch (ex93b) {
    console.error('confirmPaidEditDelete failed:', ex93b)
    showToast('error', 'Σφάλμα διαγραφής')
    paidEditState.value.deleting = false
  }
}
function cancelPaidEdit() {
  if (paidEditState.value.deleting) return
  paidEditState.value = { visible: false, paymentId: null, paymentAmount: null, paymentDate: '', reopenData: null, deleting: false }
}

function closeEdit() {
  if (editModal.value.saving || editModal.value.deleting) return
  editModal.value.show = false
  // Step 57-A.4: clear staged uploads so reopening starts fresh
  resetEditUploadedFiles()
}

async function saveEdit() {
  const d = editModal.value.data
  if (!d.docDate)     { showToast('error', 'Η ημερομηνία είναι υποχρεωτική'); return }
  if (!d.description) { showToast('error', 'Η περιγραφή είναι υποχρεωτική'); return }
  const amountNum = parseFloat(d.amount)
  if (isNaN(amountNum) || amountNum <= 0) { showToast('error', 'Μη έγκυρο ποσό'); return }

  editModal.value.saving = true
  try {
    const isPlanned = (d.entryMode || 'ACTUAL').toUpperCase() === 'PLANNED'
    const ep = editPlanned.value

    // S88: For PLANNED + recurring, create or update the recurrence pattern.
    let recurrencePatternId = ep.recurrencePatternId || null
    if (isPlanned && ep.isRecurring) {
      if (ep.confidence < 0 || ep.confidence > 100) {
        showToast('error', '\u0392\u03b5\u03b2\u03b1\u03b9\u03cc\u03c4\u03b7\u03c4\u03b1 \u03c0\u03c1\u03ad\u03c0\u03b5\u03b9 \u03bd\u03b1 \u03b5\u03af\u03bd\u03b1\u03b9 0-100')
        editModal.value.saving = false; return
      }
      if (!ep.startDate) {
        showToast('error', '\u03a3\u03c5\u03bc\u03c0\u03bb\u03b7\u03c1\u03ce\u03c3\u03c4\u03b5 \u0397\u03bc/\u03bd\u03af\u03b1 \u0388\u03bd\u03b1\u03c1\u03be\u03b7\u03c2 \u03b5\u03c0\u03b1\u03bd\u03ac\u03bb\u03b7\u03c8\u03b7\u03c2')
        editModal.value.saving = false; return
      }
      if (!ep.isOpenEnded && ep.endDate && ep.endDate < ep.startDate) {
        showToast('error', '\u0397 \u039b\u03ae\u03be\u03b7 \u03c0\u03c1\u03ad\u03c0\u03b5\u03b9 \u03bd\u03b1 \u03b5\u03af\u03bd\u03b1\u03b9 \u03bc\u03b5\u03c4\u03ac \u03c4\u03b7\u03bd \u0388\u03bd\u03b1\u03c1\u03be\u03b7')
        editModal.value.saving = false; return
      }
      const patternPayload = {
        frequency:     ep.frequency,
        intervalCount: Number(ep.intervalCount) || 1,
        startDate:     ep.startDate,
        timezone:      'Europe/Athens',
      }
      if (ep.frequency === 'WEEKLY') {
        patternPayload.dayOfWeek = Number(ep.dayOfWeek)
      } else if (['MONTHLY','QUARTERLY','YEARLY'].includes(ep.frequency)) {
        patternPayload.dayOfMonth = Number(ep.dayOfMonth)
      }
      if (!ep.isOpenEnded && ep.endDate) {
        patternPayload.endDate = ep.endDate
      }
      try {
        let patternRes
        if (recurrencePatternId) {
          patternRes = await api.put('/api/recurrence-patterns/' + recurrencePatternId, patternPayload, { params: { entityId: entityId.value } })
        } else {
          patternRes = await api.post('/api/recurrence-patterns', patternPayload, { params: { entityId: entityId.value } })
        }
        const pdata = patternRes && patternRes.data && (patternRes.data.data || patternRes.data)
        if (pdata && pdata.id) {
          recurrencePatternId = pdata.id
        } else if (!recurrencePatternId) {
          showToast('error', '\u03a3\u03c6\u03ac\u03bb\u03bc\u03b1 pattern \u03b5\u03c0\u03b1\u03bd\u03ac\u03bb\u03b7\u03c8\u03b7\u03c2')
          editModal.value.saving = false; return
        }
      } catch (pe) {
        showToast('error', '\u03a3\u03c6\u03ac\u03bb\u03bc\u03b1 pattern: ' + (pe.response?.data?.error || pe.message || ''))
        editModal.value.saving = false; return
      }
    } else if (isPlanned && !ep.isRecurring) {
      // turned off recurring: detach (leave any old pattern orphaned;
      // backend FK is ON DELETE SET NULL, cleanup is a separate concern)
      recurrencePatternId = null
    }

    // S88: description — append notes for PLANNED (same convention as NewEntryView)
    let finalDescription = d.description
    if (isPlanned && ep.notes && ep.notes.trim()) {
      finalDescription = (finalDescription || '') + '\n[\u03a3\u03b7\u03bc.] ' + ep.notes.trim()
    }

    const payload = {
      docDate: d.docDate,
      description: finalDescription,
      amount: amountNum,
      type: d.type,
      category: d.category,
      subcategory: d.subcategory,
      account: d.subcategory || d.account,
      paymentMethod: d.paymentMethod,
      paymentStatus: d.paymentStatus,
      paymentDate: d.paymentDate || null
    }
    // S88: PLANNED extra fields (backend already has setters for these)
    if (isPlanned) {
      payload.entryMode           = 'PLANNED'
      payload.isRecurring         = ep.isRecurring
      payload.recurrencePatternId = recurrencePatternId
      payload.projectId           = ep.isOpEx ? null : (ep.projectId || null)
      payload.confidencePct       = Number(ep.confidence) || 100
    }
    const res = await api.put('/api/transactions/' + d.id, payload, {
      validateStatus: function (s) { return (s >= 200 && s < 300) || s === 400 }
    })
    // S93B: backend blocks amount edit on a paid txn -> open delete dialog.
    if (res.data && res.data.error === 'payment_present') {
      paidEditState.value = {
        visible: true,
        paymentId: res.data.paymentId,
        paymentAmount: res.data.paymentAmount,
        paymentDate: res.data.paymentDate || '',
        reopenData: { ...editModal.value.original },
        deleting: false
      }
      editModal.value.show = false
      editModal.value.saving = false
      return
    }
    // S94B: backend blocks amount edit on a legacy paid txn that has NO Payment row -> show info dialog.
    if (res.data && res.data.error === 'paid_no_payment_record') {
      paidNoPaymentState.value = {
        visible: true,
        paymentStatus: res.data.paymentStatus || '',
        amountPaid: Number(res.data.amountPaid) || 0,
        reopenData: { ...editModal.value.original }
      }
      editModal.value.show = false
      editModal.value.saving = false
      return
    }
    if (res.data && res.data.success !== false) {
      // Step 57-A.5: upload staged files (if any) AFTER successful PUT.
      // Continue-on-error pattern - txn is saved even if a file fails,
      // matching NewEntryView Phase 56-A.1 semantics.
      let uploadFailures = 0
      const stagedCount = editUploadedFiles.value.length
      if (stagedCount > 0) {
        for (let i = 0; i < stagedCount; i++) {
          try {
            const file = editUploadedFiles.value[i]
            const ext = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : ''
            let fileName = (editDriveFileNames.value[i] || '').trim()
            if (!fileName) {
              const baseDesc = (d.description || '').replace(/^\d+\s*-\s*/, '').substring(0, 50)
              fileName = (d.entityNumber || d.id) + ' - ' + baseDesc + (ext ? '.' + ext : '')
            }
            if (fileName && ext && !fileName.toLowerCase().endsWith('.' + ext)) {
              fileName += '.' + ext
            }
            const formData = new FormData()
            formData.append('file', file)
            formData.append('entityId', entityId.value)
            formData.append('transactionId', d.id)
            formData.append('customFileName', fileName)
            await api.post('/api/documents/upload', formData, {
              headers: { 'Content-Type': 'multipart/form-data' },
              timeout: 60000
            })
          } catch (fe) {
            console.warn('[TransactionsView] saveEdit upload failed:', fe)
            uploadFailures++
          }
        }
      }

      if (stagedCount === 0) {
        showToast('success', 'Η κίνηση αποθηκεύτηκε')
      } else if (uploadFailures === 0) {
        showToast('success', 'Η κίνηση αποθηκεύτηκε με ' + stagedCount + ' νέο' + (stagedCount === 1 ? '' : 'α') + ' αρχεί' + (stagedCount === 1 ? 'ο' : 'α'))
      } else {
        showToast('error', 'Η κίνηση αποθηκεύτηκε αλλά ' + uploadFailures + '/' + stagedCount + ' αρχεία απέτυχαν')
      }

      // Reset and close
      resetEditUploadedFiles()
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

// ───── Step 57-D.3: Route Delete click for virtual payment rows ─────
// Virtual payment rows in CASHFLOW MODE carry a real _paymentId from the
// backend cashflow event. Clicking Delete on such a row deletes ONLY that
// Payment record (not the parent transaction) via DELETE /api/payments/{id}.
// Backend recomputes parent's amountPaid/Remaining/Status accordingly.
//
// Standard-mode virtual rows have _paymentId=null (we cannot identify
// the specific Payment), so the Delete button is hidden via the v-if.
async function onDeleteAnyRow(t) {
  if (!t) return
  if (t._isPaymentRow && t._paymentId) {
    const ok = window.confirm(
      'Διαγραφή αυτής της πληρωμής;\n\n' +
      'Η συναλλαγή θα επανυπολογιστεί αυτόματα.\nΗ ενέργεια δεν αναιρείται.'
    )
    if (!ok) return
    try {
      const res = await api.delete('/api/payments/' + t._paymentId)
      if (res.data && res.data.success !== false) {
        showToast('success', 'Η πληρωμή διαγράφηκε')
        await loadTransactions()
      } else {
        showToast('error', (res.data && res.data.error) || 'Αποτυχία διαγραφής')
      }
    } catch (e) {
      console.error('onDeleteAnyRow payment delete failed:', e)
      showToast('error', e.response?.data?.error || 'Σφάλμα διαγραφής')
    }
  } else if (!t._isPaymentRow) {
    openDelete(t)
  }
  // else: virtual row without _paymentId -> button is hidden anyway, no-op
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


// ─── Universal Search helpers (legacy parity) ─────────────────────
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
    // Status label mapping for Greek search terms
    const statusMap = {
      'εξοφλημένη': 'paid',
      'εξοφλημενη': 'paid',
      'απλήρωτη': 'unpaid',
      'εκκρεμής': 'urgent',
      'εκκρεμεις': 'urgent',
      'εισπράχθηκε': 'received',
      'πληρωμένη': 'paid',
      'πληρωμενη': 'paid'
    }
    // Check if the search word is a Greek status term
    const mappedStatus = statusMap[word]

    const text = [
      String(t.entityNumber ?? t.id ?? ''),
      t.description ?? '', t.category ?? '', t.subcategory ?? '',
      t.counterparty ?? '', t.paymentMethod ?? '', t.paymentStatus ?? '',
      t.account ?? '', t.type ?? '',
      dateSearchFormats(t.docDate), dateSearchFormats(t.paymentDate),
      t.type === 'income' ? 'είσπραξη εισόδημα' : 'πληρωμή έξοδο',
      t.recordStatus ?? ''
    ].filter(Boolean).join(' ').toLowerCase()
    if (text.includes(word)) return true
    // Check mapped Greek status
    if (mappedStatus && (t.paymentStatus || '').toLowerCase() === mappedStatus) return true
    // Amount search
    const searchAmt = parseSearchAmount(word)
    if (searchAmt !== null) {
      const fields = [t.amount, t.amountPaid, t.amountRemaining]
      for (const f of fields) {
        const v = parseFloat(f || 0)
        if (v > 0 && Math.abs(v - searchAmt) < 0.005) return true
      }
    }
    return false
  })
}

// Client-side filtering: applies date range, type, status, AND search
// across the full augmented dataset (canonical + virtual payment rows).
// Everything is in-memory so this is instant - no backend round-trip.
// Phase 60-E: distinct values for column filters
function uniqueSortedField(field) {
  const values = new Set()
  for (const t of transactions.value) {
    if (t._isPaymentRow) continue  // skip virtual payment rows
    const v = t[field]
    if (v == null || v === '') values.add('')
    else values.add(String(v))
  }
  return Array.from(values).sort((a, b) => a.localeCompare(b, 'el'))
}
const categoryOptions    = computed(() => uniqueSortedField('category'))
const subcategoryOptions = computed(() => uniqueSortedField('subcategory'))
const methodOptions      = computed(() => uniqueSortedField('paymentMethod'))
function filteredOptions(allOptions) {
  const q = (filterMenuSearch.value || '').toLowerCase().trim()
  if (!q) return allOptions
  return allOptions.filter(o => (o || '').toLowerCase().includes(q))
}

const filteredTransactions = computed(() => {
  let list = transactions.value
  // Phase 1-F2.1: entry_mode filter — first thing, ensures PLANNED rows
  // never appear in the default cash flow view. Virtual payment rows
  // inherit their parent's entryMode so they follow the same gating.
  const mode = (selectedMode.value || 'ACTUAL').toUpperCase()
  if (mode !== 'ALL') {
    list = list.filter(t => {
      const tm = (t.entryMode || 'ACTUAL').toUpperCase()
      return tm === mode
    })
  }
  // Date range filter (against docDate, which for virtual rows = paymentDate)
  if (dateFrom.value) {
    list = list.filter(t => t.docDate && t.docDate >= dateFrom.value)
  }
  if (dateTo.value) {
    list = list.filter(t => t.docDate && t.docDate <= dateTo.value)
  }
  // Type filter (income / expense)
  if (selectedType.value) {
    list = list.filter(t => t.type === selectedType.value)
  }
  // Status filter (paid / unpaid / urgent / etc.)
  if (selectedStatus.value) {
    list = list.filter(t => t.paymentStatus === selectedStatus.value)
  }
  // Phase 60-E: column dropdown filters
  if (filterCategory.value.length > 0) {
    list = list.filter(t => filterCategory.value.includes(String(t.category || '')))
  }
  if (filterSubcategory.value.length > 0) {
    list = list.filter(t => filterSubcategory.value.includes(String(t.subcategory || '')))
  }
  if (filterMethod.value.length > 0) {
    list = list.filter(t => filterMethod.value.includes(String(t.paymentMethod || '')))
  }
  // Search filter (universal match across all fields)
  const q = (searchQuery.value || '').trim()
  if (q) {
    list = list.filter(t => universalMatch(t, q))
  }
  return list
})

// Client-side pagination over the filtered list. Updates total + pages
// reactively whenever the filtered list changes (filter, search, etc.).
const paginatedTransactions = computed(() => {
  const list = filteredTransactions.value
  total.value = list.length
  pages.value = Math.max(1, Math.ceil(list.length / perPage.value))
  // Clamp page in case current page index is now out of range after filter.
  if (page.value >= pages.value) page.value = 0
  const start = page.value * perPage.value
  return list.slice(start, start + perPage.value)
})

// Search is now fully client-side via filteredTransactions computed.
// No debounce needed - filtering 4752 rows in memory is instant.
function onSearchInput() {
  page.value = 0   // reset to first page when search changes
}

function clearSearch() {
  searchQuery.value = ''
  page.value = 0
}

const kpis = computed(() => {
  // Phase 1-F2.1: KPIs follow the same mode filter as the table.
  // We use filteredTransactions so totals reflect what the user is viewing
  // — otherwise the "ΣΥΝΟΛΟ ΚΙΝΗΣΕΩΝ" badge would lie when PLANNED rows
  // are hidden from the rows below.
  const txns = filteredTransactions.value.filter(t => !t._isPaymentRow)
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
// Phase 60-D: statusLabel accepts type for income-aware labels
function statusLabel(s, type) {
  if (type === 'income') {
    return { paid:'Εισπράχθηκε', received:'Εισπράχθηκε', unpaid:'Δεν εισπράχθηκε', urgent:'Σε εκκρεμότητα', partial:'Μερική' }[s] || s || '—'
  }
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

// Session #40 — parse URL query params for deep-linking from Dashboard reconciliation
const route = useRoute()
function onEntityChanged() {
  selectedEntity.value = localStorage.getItem('n2c_entity') || 'next2me'
  page.value = 0
  loadTransactions()
}

onMounted(() => {
  loadEditBankAccounts()
  loadEditConfig()
  const q = route.query || {}
  // entityId: UUID -> reverse map to 'next2me' / 'house' / 'next2megroup' key
  if (q.entityId) {
    const key = Object.keys(ENTITIES).find(k => ENTITIES[k] === q.entityId)
    if (key) selectedEntity.value = key
  }
  if (q.from) dateFrom.value = String(q.from)
  if (q.to)   dateTo.value   = String(q.to)
  loadTransactions()
  window.addEventListener('entity-changed', onEntityChanged)
})
onUnmounted(() => {
  window.removeEventListener('entity-changed', onEntityChanged)
})
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
          @input="onSearchInput"
          type="text"
          class="search-input"
          placeholder="Αναζήτηση: ID, περιγραφή, ποσό, κατηγορία, ημ/νία, status..." />
        <button v-if="searchQuery" class="search-clear" @click="clearSearch" title="Καθάρισμα">×</button>
      </div>
      <input v-model="dateFrom" type="date" class="f-input" />
      <input v-model="dateTo"   type="date" class="f-input" />
      <select v-model="selectedMode" class="f-select f-select-mode" :class="'mode-' + selectedMode.toLowerCase()">
        <option value="ACTUAL">💰 Πραγματικές</option>
        <option value="PLANNED">📋 Προγραμματισμένες</option>
        <option value="ALL">📊 Όλες</option>
      </select>
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
            <th class="th-filter">
              <!-- Phase 60-E: dropdown header for category -->
              <div class="th-filter-wrap" :class="{ 'has-filter': filterCategory.length > 0 }">
                <button class="th-btn" @click.stop="toggleFilterMenu('category')">
                  ΚΑΤΗΓΟΡΙΑ
                  <span v-if="filterCategory.length > 0" class="th-filter-count">{{ filterCategory.length }}</span>
                  <i class="fas fa-caret-down th-caret"></i>
                </button>
                <div v-if="openFilterMenu === 'category'" class="filter-menu" @click.stop>
                  <div class="filter-menu-search">
                    <input v-model="filterMenuSearch" type="text" placeholder="Αναζήτηση..." />
                  </div>
                  <div class="filter-menu-list">
                    <label v-for="opt in filteredOptions(categoryOptions)" :key="opt" class="filter-menu-item">
                      <input type="checkbox" :value="opt" v-model="filterCategory" />
                      <span class="filter-menu-label">{{ opt || '(κενό)' }}</span>
                    </label>
                  </div>
                  <div class="filter-menu-footer">
                    <button class="fm-btn-link" @click="filterCategory = [...categoryOptions]">Επιλογή Όλων</button>
                    <button class="fm-btn-link" @click="filterCategory = []">Καθαρισμός</button>
                    <button class="fm-btn-apply" @click="closeFilterMenu()">Εφαρμογή</button>
                  </div>
                </div>
              </div>
            </th>
            <th class="th-filter">
              <!-- Phase 60-E: dropdown header for subcategory -->
              <div class="th-filter-wrap" :class="{ 'has-filter': filterSubcategory.length > 0 }">
                <button class="th-btn" @click.stop="toggleFilterMenu('subcategory')">
                  ΥΠΟΚΑΤΗΓΟΡΙΑ
                  <span v-if="filterSubcategory.length > 0" class="th-filter-count">{{ filterSubcategory.length }}</span>
                  <i class="fas fa-caret-down th-caret"></i>
                </button>
                <div v-if="openFilterMenu === 'subcategory'" class="filter-menu" @click.stop>
                  <div class="filter-menu-search">
                    <input v-model="filterMenuSearch" type="text" placeholder="Αναζήτηση..." />
                  </div>
                  <div class="filter-menu-list">
                    <label v-for="opt in filteredOptions(subcategoryOptions)" :key="opt" class="filter-menu-item">
                      <input type="checkbox" :value="opt" v-model="filterSubcategory" />
                      <span class="filter-menu-label">{{ opt || '(κενό)' }}</span>
                    </label>
                  </div>
                  <div class="filter-menu-footer">
                    <button class="fm-btn-link" @click="filterSubcategory = [...subcategoryOptions]">Επιλογή Όλων</button>
                    <button class="fm-btn-link" @click="filterSubcategory = []">Καθαρισμός</button>
                    <button class="fm-btn-apply" @click="closeFilterMenu()">Εφαρμογή</button>
                  </div>
                </div>
              </div>
            </th>
            <th class="th-filter">
              <!-- Phase 60-E: dropdown header for method -->
              <div class="th-filter-wrap" :class="{ 'has-filter': filterMethod.length > 0 }">
                <button class="th-btn" @click.stop="toggleFilterMenu('method')">
                  ΤΡΟΠΟΣ
                  <span v-if="filterMethod.length > 0" class="th-filter-count">{{ filterMethod.length }}</span>
                  <i class="fas fa-caret-down th-caret"></i>
                </button>
                <div v-if="openFilterMenu === 'method'" class="filter-menu" @click.stop>
                  <div class="filter-menu-search">
                    <input v-model="filterMenuSearch" type="text" placeholder="Αναζήτηση..." />
                  </div>
                  <div class="filter-menu-list">
                    <label v-for="opt in filteredOptions(methodOptions)" :key="opt" class="filter-menu-item">
                      <input type="checkbox" :value="opt" v-model="filterMethod" />
                      <span class="filter-menu-label">{{ opt || '(κενό)' }}</span>
                    </label>
                  </div>
                  <div class="filter-menu-footer">
                    <button class="fm-btn-link" @click="filterMethod = [...methodOptions]">Επιλογή Όλων</button>
                    <button class="fm-btn-link" @click="filterMethod = []">Καθαρισμός</button>
                    <button class="fm-btn-apply" @click="closeFilterMenu()">Εφαρμογή</button>
                  </div>
                </div>
              </div>
            </th>
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
          <tr v-for="t in paginatedTransactions" :key="t.id"
              :class="[t.paymentStatus === 'urgent' ? 'row-urgent' : '', t._isPaymentRow ? 'row-payment' : '', (t.entryMode === 'PLANNED' ? 'row-planned' : '')]">
            <td class="id-col">
              #{{ t.entityNumber ?? t.id }}
              <span v-if="t.entryMode === 'PLANNED'" class="planned-pill" title="Προγραμματισμένη — δεν είναι πραγματική κίνηση">📋</span>
            </td>
            <td class="date-col">{{ fmtDate(t.docDate) }}</td>
            <td class="desc-col" :title="t.description">{{ (t.description || '—').substring(0,40) }}</td>
            <td><span class="cat-badge">{{ t.category || '—' }}</span></td>
            <td class="sub-col">{{ t.account || t.subcategory || '—' }}</td>
            <td class="method-col">{{ t.paymentMethod || '—' }}</td>
            <td class="ra green mono">{{ t.type === 'income'  ? fmt(t.amount) : '—' }}</td>
            <td class="ra mono" :class="t.type === 'expense' && isUnpaid(t) ? 'red' : 'white'">{{ t.type === 'expense' ? fmt(t.amount) : '—' }}</td>
            <td><span class="status-badge" :class="statusClass(t.paymentStatus)">{{ statusLabel(t.paymentStatus, t.type) }}</span></td>
            <td class="actions">
              <span class="act-slot act-slot-mark">
                <button
                  v-if="canMarkPaid(t) && !t._isPaymentRow"
                  class="btn-action btn-mark-paid-sm"
                  @click="openMarkPaid(t)">
                  ✓ {{ t.type === 'income' ? 'Καταγραφή Είσπραξης' : 'Εξόφληση' }}
                </button>
              </span>
              <span class="act-slot">
                <button
                  class="btn-action btn-attach-sm"
                  @click="openAttachments(t)"
                  :style="hasAttachments(t) ? {} : { opacity: 0.45 }">
                  📎
                </button>
              </span>
              <span class="act-slot">
                <button v-if="canModify" class="icon-btn" title="Επεξεργασία" @click="onEditAnyRow(t)">
                  <i class="fas fa-edit"></i>
                </button>
              </span>
              <span class="act-slot">
                <button v-if="canModify && (!t._isPaymentRow || t._paymentId)" class="icon-btn icon-danger" title="Διαγραφή" @click="onDeleteAnyRow(t)">
                  <i class="fas fa-trash"></i>
                </button>
              </span>
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
    <div v-if="paidEditState.visible" class="modal-backdrop" @click.self="cancelPaidEdit">
      <div class="modal paid-edit-modal">
        <div class="modal-header">
          <h3 class="edit-modal-title"><i class="fas fa-lock paid-edit-lock"></i> Η εγγραφή έχει εξόφληση</h3>
          <button class="modal-close" @click="cancelPaidEdit" :disabled="paidEditState.deleting">×</button>
        </div>
        <div class="modal-body">
          <p class="paid-edit-text">Για να αλλάξεις το ποσό, πρέπει πρώτα να διαγράψεις την πληρωμή που είναι καταχωρημένη πάνω σε αυτή την εγγραφή.</p>
          <div class="paid-edit-box">
            <div class="paid-edit-row"><span>Ποσό πληρωμής</span><strong>{{ Number(paidEditState.paymentAmount).toFixed(2) }} €</strong></div>
            <div class="paid-edit-row"><span>Ημερομηνία</span><strong>{{ paidEditState.paymentDate }}</strong></div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="cancelPaidEdit" :disabled="paidEditState.deleting">Άκυρο</button>
          <button class="btn-danger paid-edit-del" @click="confirmPaidEditDelete" :disabled="paidEditState.deleting">
            <span v-if="paidEditState.deleting"><span class="spinner-sm"></span> Διαγραφή...</span>
            <span v-else><i class="fas fa-trash"></i> Διαγραφή πληρωμής</span>
          </button>
        </div>
      </div>
    </div>

    <!-- S94B: paid_no_payment_record dialog (legacy paid txn without Payment row) -->
    <div v-if="paidNoPaymentState.visible" class="modal-backdrop" @click.self="cancelPaidNoPayment">
      <div class="modal paid-edit-modal">
        <div class="modal-header">
          <h3 class="edit-modal-title"><i class="fas fa-lock paid-edit-lock"></i> Δεν επιτρέπεται αλλαγή ποσού</h3>
          <button class="modal-close" @click="cancelPaidNoPayment">×</button>
        </div>
        <div class="modal-body">
          <p class="paid-edit-text">Η εγγραφή είναι ήδη εξοφλημένη χωρίς καταχωρημένη εγγραφή πληρωμής (legacy). Για να αλλάξεις το ποσό, πρέπει πρώτα να αλλάξει η κατάσταση σε unpaid από τον διαχειριστή.</p>
          <div class="paid-edit-box">
            <div class="paid-edit-row"><span>Κατάσταση</span><strong>{{ paidNoPaymentState.paymentStatus }}</strong></div>
            <div class="paid-edit-row"><span>Πληρωμένο ποσό</span><strong>{{ Number(paidNoPaymentState.amountPaid).toFixed(2) }} €</strong></div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="cancelPaidNoPayment">Άκυρο</button>
        </div>
      </div>
    </div>

    <div v-if="editModal.show" class="modal-backdrop" @click.self="closeEdit">
      <div class="modal">
        <div class="modal-header">
          <h3 class="edit-modal-title"><i class="fas fa-edit edit-modal-icon"></i> Επεξεργασία <span class="edit-modal-num">#{{ editModal.data.entityNumber ?? editModal.data.id }}</span></h3>
          <button class="modal-close" @click="closeEdit" :disabled="editModal.saving">×</button>
        </div>
        <div class="modal-body">
          <div class="form-grid">
            <!-- Step 57-A.3.1 layout -->
            <div class="form-field full">
              <label>Τύπος</label>
              <div class="edit-type-toggle">
                <button type="button"
                        :class="['edit-type-btn', 'expense', { active: editModal.data.type === 'expense' }]"
                        @click="editModal.data.type = 'expense'">
                  <i class="fas fa-arrow-up"></i> Πληρωμή
                </button>
                <button type="button"
                        :class="['edit-type-btn', 'income', { active: editModal.data.type === 'income' }]"
                        @click="editModal.data.type = 'income'">
                  <i class="fas fa-arrow-down"></i> Είσπραξη
                </button>
              </div>
            </div>
            <div class="form-field">
              <label>Ημ/νία</label>
              <input v-model="editModal.data.docDate" type="date" class="f-input" />
            </div>
            <div class="form-field" v-if="(editModal.data.entryMode || 'ACTUAL') !== 'PLANNED'">
              <label>Ημ/νία Πληρωμής</label>
              <input v-model="editModal.data.paymentDate" type="date" class="f-input" />
            </div>
            <div class="form-field">
              <label>Κατηγορία</label>
              <select v-model="editModal.data.category" class="f-select">
                <option value="">— Επιλέξτε —</option>
                <option v-for="c in editCategoriesWithFallback" :key="c.key" :value="c.key">
                  {{ c.value || c.key }}
                </option>
              </select>
            </div>
            <div class="form-field">
              <label>Υποκατηγορία</label>
              <select v-model="editModal.data.subcategory" class="f-select"
                      :disabled="!editModal.data.category">
                <option value="">{{ editModal.data.category ? '— Επιλέξτε —' : '— Επιλέξτε κατηγορία πρώτα —' }}</option>
                <option v-for="s in filteredEditSubcategories" :key="s.key" :value="s.key">
                  {{ s.value || s.key }}
                </option>
              </select>
            </div>
            <div class="form-field">
              <label>Ποσό (€)</label>
              <input v-model="editModal.data.amount" type="number" step="0.01" min="0" class="f-input" />
            </div>
            <div class="form-field">
              <label>Μέθοδος Πληρωμής</label>
              <select v-model="editModal.data.paymentMethod" class="f-input">
                <option value="">—</option>
                <option v-for="m in availableEditPaymentMethods" :key="m" :value="m">{{ m }}</option>
              </select>
            </div>
            <div class="form-field full">
              <label>Περιγραφή</label>
              <textarea v-model="editModal.data.description" class="f-input edit-desc-textarea" maxlength="500" rows="2"></textarea>
            </div>
            <!-- S88: PLANNED sections (only when editing a PLANNED transaction) -->
            <div v-if="(editModal.data.entryMode || 'ACTUAL') === 'PLANNED'" class="form-field full edit-planned-sections">
              <div class="edit-planned-block">
                <label class="edit-block-checkbox">
                  <input type="checkbox" v-model="editPlanned.isRecurring" />
                  <span class="edit-block-title">🔁 Είναι επαναλαμβανόμενη</span>
                </label>
                <div v-if="editPlanned.isRecurring" class="edit-block-body">
                  <div class="edit-planned-row">
                    <div class="edit-planned-col">
                      <label>Συχνότητα</label>
                      <select v-model="editPlanned.frequency" class="f-input">
                        <option v-for="f in editFrequencyOptions" :key="f.value" :value="f.value">{{ f.label }}</option>
                      </select>
                    </div>
                    <div class="edit-planned-col">
                      <label>Κάθε</label>
                      <div class="edit-interval-row">
                        <input v-model.number="editPlanned.intervalCount" type="number" min="1" max="12" class="f-input edit-interval-input" />
                        <span class="edit-interval-suffix">{{ editPlanned.frequency==='DAILY' ? 'ημέρες' : editPlanned.frequency==='WEEKLY' ? 'εβδομάδες' : editPlanned.frequency==='MONTHLY' ? 'μήνες' : editPlanned.frequency==='QUARTERLY' ? 'τρίμηνα' : 'χρόνια' }}</span>
                      </div>
                    </div>
                  </div>
                  <div class="edit-planned-row" v-if="editPlanned.frequency==='WEEKLY'">
                    <div class="edit-planned-col">
                      <label>Ημέρα εβδομάδας</label>
                      <select v-model.number="editPlanned.dayOfWeek" class="f-input">
                        <option v-for="dw in editDayOfWeekOptions" :key="dw.value" :value="dw.value">{{ dw.label }}</option>
                      </select>
                    </div>
                    <div class="edit-planned-col"></div>
                  </div>
                  <div class="edit-planned-row" v-if="['MONTHLY','QUARTERLY','YEARLY'].includes(editPlanned.frequency)">
                    <div class="edit-planned-col">
                      <label>Ημέρα μήνα (1-31)</label>
                      <input v-model.number="editPlanned.dayOfMonth" type="number" min="1" max="31" class="f-input" />
                    </div>
                    <div class="edit-planned-col"></div>
                  </div>
                  <div class="edit-planned-row">
                    <div class="edit-planned-col">
                      <label>Έναρξη</label>
                      <input v-model="editPlanned.startDate" type="date" class="f-input" />
                    </div>
                    <div class="edit-planned-col">
                      <label>Λήξη</label>
                      <input v-model="editPlanned.endDate" type="date" class="f-input" :disabled="editPlanned.isOpenEnded" />
                      <label class="edit-inline-check">
                        <input type="checkbox" v-model="editPlanned.isOpenEnded" />
                        <span>Αόριστη (χωρίς λήξη)</span>
                      </label>
                    </div>
                  </div>
                </div>
              </div>

              <div class="edit-planned-block" v-if="editProjects.length > 0">
                <div class="edit-block-title-row"><span class="edit-block-title">🎯 Project</span></div>
                <div class="edit-block-body">
                  <label class="edit-inline-check">
                    <input type="checkbox" v-model="editPlanned.isOpEx" />
                    <span>Αυτό είναι γενικό έξοδο εταιρείας (OpEx)</span>
                  </label>
                  <div class="edit-planned-col" style="margin-top:10px" v-if="!editPlanned.isOpEx">
                    <label>Project</label>
                    <select v-model="editPlanned.projectId" class="f-input" :disabled="editLoadingProjects">
                      <option value="">— Χωρίς project (γενικό OpEx) —</option>
                      <option v-for="p in editProjects" :key="p.id" :value="p.id">{{ p.name }}</option>
                    </select>
                  </div>
                </div>
              </div>

              <div class="edit-planned-block">
                <div class="edit-block-title-row"><span class="edit-block-title">🎬 Σενάριο</span></div>
                <div class="edit-block-body">
                  <div class="edit-scenario-options">
                    <button v-for="s in editScenarioOptions" :key="s.value" type="button"
                            :class="['edit-scenario-btn', {active: editPlanned.scenario===s.value}]"
                            :style="editPlanned.scenario===s.value ? {borderColor: s.color, background: s.color + '22', color: s.color} : {}"
                            @click="editPlanned.scenario=s.value">{{ s.label }}</button>
                  </div>
                  <span class="edit-hint">Το σενάριο αποθηκεύεται σε επόμενη φάση (Phase 2)</span>
                </div>
              </div>

              <div class="edit-planned-block">
                <div class="edit-block-title-row">
                  <span class="edit-block-title">📈 Βεβαιότητα</span>
                  <span class="edit-confidence-display">{{ editPlanned.confidence }}%</span>
                </div>
                <div class="edit-block-body">
                  <input v-model.number="editPlanned.confidence" type="range" min="0" max="100" step="5" class="edit-confidence-slider" />
                  <span class="edit-hint">100% = σίγουρο πώγιο, χαμηλότερο = πιθανή συναλλαγή</span>
                </div>
              </div>

              <div class="edit-planned-block">
                <div class="edit-block-title-row"><span class="edit-block-title">ℹ️ Παρατηρήσεις</span></div>
                <div class="edit-block-body">
                  <textarea v-model="editPlanned.notes" class="f-input" rows="2" placeholder="Προαιρετικά: σχόλια, υποθέσεις, follow-up..."></textarea>
                </div>
              </div>
            </div>
            <!-- END S88 PLANNED sections -->
            <div class="form-field full edit-pending-row" v-if="(editModal.data.entryMode || 'ACTUAL') !== 'PLANNED'">
              <label class="edit-pending-label">
                <input type="checkbox"
                       :checked="editModal.data.paymentStatus !== 'paid'"
                       @change="editModal.data.paymentStatus = $event.target.checked ? 'unpaid' : 'paid'" />
                <span>Εκκρεμεί πληρωμή</span>
              </label>
            </div>
            <div class="form-field full edit-attachments-block">
              <label class="edit-attach-label">
                Συνημμένα αρχεία
                <span v-if="!editAttachmentsLoading && editAttachments.length > 0" class="edit-attach-count">
                  {{ editAttachments.length }} αρχεί{{ editAttachments.length === 1 ? 'ο' : 'α' }}
                </span>
              </label>
              <div v-if="editAttachmentsLoading" class="edit-attach-state">
                <span class="spinner-sm"></span> Φόρτωση…
              </div>
              <div v-else-if="editAttachmentsError" class="edit-attach-state edit-attach-error">
                <i class="fas fa-exclamation-triangle"></i> {{ editAttachmentsError }}
              </div>
              <div v-else-if="editAttachments.length === 0" class="edit-attach-state edit-attach-empty">
                Δεν υπάρχουν συνημμένα αρχεία
              </div>
              <div v-else class="edit-attach-list">
                <div v-for="(file, idx) in editAttachments" :key="file.blobPath || idx" class="edit-attach-row">
                  <i class="fas fa-file-pdf edit-attach-icon"></i>
                  <span class="edit-attach-name" :title="file.fileName">{{ file.fileName }}</span>
                  <button type="button" class="edit-attach-btn preview"
                          @click="onPreviewEditAttachment(file)"
                          :disabled="editAttachmentDeletingIdx === idx"
                          title="Άνοιγμα">
                    <i class="fas fa-external-link-alt"></i>
                  </button>
                  <button type="button" class="edit-attach-btn delete"
                          @click="onDeleteEditAttachment(file, idx)"
                          :disabled="editAttachmentDeletingIdx === idx"
                          title="Διαγραφή">
                    <span v-if="editAttachmentDeletingIdx === idx" class="spinner-sm"></span>
                    <i v-else class="fas fa-times"></i>
                  </button>
                </div>
              </div>
            </div>
            <div class="form-field full edit-upload-block">
              <label class="edit-upload-area">
                <input type="file" multiple accept=".pdf,.jpg,.jpeg,.png" @change="onEditFileChange" style="display:none" />
                <div class="edit-upload-icon"><i class="fas fa-cloud-upload-alt"></i></div>
                <div class="edit-upload-text">
                  <span v-if="editUploadedFiles.length === 0">Πατήστε για upload αρχείων (πολλαπλά)</span>
                  <span v-else>+ Προσθήκη κι άλλων αρχείων</span>
                </div>
              </label>
              <div v-if="editUploadedFiles.length > 0" class="edit-upload-preview">
                <div v-for="(f, idx) in editUploadedFiles" :key="idx" class="edit-upload-preview-row">
                  <i class="fas fa-file-pdf edit-attach-icon"></i>
                  <span class="edit-upload-preview-name">{{ f.name }}</span>
                  <span class="edit-upload-preview-size">{{ Math.round(f.size/1024) }}KB</span>
                  <button type="button" class="edit-attach-btn delete" @click="removeEditFile(idx)" title="Αφαίρεση">
                    <i class="fas fa-times"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeEdit" :disabled="editModal.saving">
            <i class="fas fa-times"></i> Ακύρωση
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
            <strong>#{{ deleteConfirm.item?.entityNumber ?? deleteConfirm.item?.id }}</strong>;
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
/* S88: PLANNED edit modal blocks (mirrors NewEntryView styling) */
.edit-planned-sections { background: rgba(245,158,11,.05); border: 1px dashed rgba(245,158,11,.4); border-radius: 8px; padding: 14px; }
.edit-planned-block { background: rgba(255,255,255,.03); border: 1px solid rgba(148,163,184,.18); border-radius: 8px; padding: 12px 14px; margin-bottom: 10px; }
.edit-planned-block:last-child { margin-bottom: 0; }
.edit-block-checkbox { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.edit-block-title-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.edit-block-title { font-size: .92rem; font-weight: 700; }
.edit-block-body { margin-top: 10px; }
.edit-planned-row { display: flex; gap: 12px; margin-bottom: 10px; }
.edit-planned-row:last-child { margin-bottom: 0; }
.edit-planned-col { flex: 1; display: flex; flex-direction: column; gap: 6px; }
.edit-planned-col label { font-size: .8rem; opacity: .85; }
.edit-interval-row { display: flex; align-items: center; gap: 8px; }
.edit-interval-input { max-width: 80px; }
.edit-interval-suffix { font-size: .85rem; opacity: .7; }
.edit-inline-check { display: flex; align-items: center; gap: 8px; font-size: .85rem; cursor: pointer; margin-top: 6px; }
.edit-scenario-options { display: flex; gap: 8px; flex-wrap: wrap; }
.edit-scenario-btn { flex: 1; min-width: 100px; padding: 8px 12px; border-radius: 6px; border: 1px solid rgba(148,163,184,.3); background: transparent; cursor: pointer; font-size: .85rem; font-weight: 600; }
.edit-scenario-btn.active { font-weight: 700; }
.edit-confidence-display { font-size: 1rem; font-weight: 700; color: #f59e0b; }
.edit-confidence-slider { width: 100%; accent-color: #f59e0b; cursor: pointer; }
.edit-hint { display: block; font-size: .72rem; opacity: .6; margin-top: 6px; }

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
.white { color:var(--text-primary, #e2e8f0); }
.cat-badge { background:var(--bg-input); border:1px solid var(--border); padding:2px 8px; border-radius:var(--radius-sm); font-size:.75rem; white-space:nowrap; }
.status-badge { padding:2px 8px; border-radius:10px; font-size:.72rem; font-weight:600; white-space:nowrap; }
.badge-paid    { background:var(--success-bg); color:var(--success); }
.badge-unpaid  { background:var(--danger-bg);  color:var(--danger); }
.badge-urgent  { background:rgba(255,100,0,.15); color:#ff6400; }
.badge-partial { background:var(--warning-bg); color:var(--warning); }
.actions {
  white-space: nowrap;
  text-align: left;
  display: inline-grid;
  grid-template-columns: 92px 36px 36px 36px;
  gap: 6px;
  align-items: center;
  justify-content: start;
  width: auto;
  padding-left: 12px;
}
td.actions { vertical-align: middle; }
.act-slot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
}
.act-slot .icon-btn,
.act-slot .btn-action {
  margin: 0;
  width: 100%;
}
.act-slot .icon-btn {
  padding: 4px 0;
}
/* Mark-Paid slot wider to fit "✓ Εξόφληση" text */
.act-slot-mark .btn-mark-paid-sm {
  width: 100%;
  padding-left: 4px;
  padding-right: 4px;
  white-space: nowrap;
}
/* Mobile: shrink slot widths but keep the grid intact */
@media (max-width: 720px) {
  .actions {
    grid-template-columns: 76px 30px 30px 30px;
    gap: 4px;
  }
  .act-slot { min-height: 28px; }
}
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

.btn-action {
  padding: 4px 10px; border-radius: 5px; font-size: 0.78rem;
  font-weight: 600; cursor: pointer; border: 1px solid #2c3e50;
  background: transparent; transition: all 0.15s; white-space: nowrap;
}
.btn-mark-paid-sm { color: #4FC3A1; border-color: #4FC3A1; }
.btn-mark-paid-sm:hover { background: #4FC3A1; color: #0d1f2d; }
.btn-attach-sm { color: #9aa5b1; font-size: 0.85rem; }
.btn-attach-sm:hover { border-color: #4A9EFF; color: #4A9EFF; }

/* Cashflow payment rows: subtle visual distinction */
.row-payment {
  background: rgba(59, 130, 246, 0.04);
  border-left: 2px solid rgba(59, 130, 246, 0.4);
}
.row-payment td.id-col::before {
  content: "» ";
  opacity: 0.6;
}


/* ───── Step 57-A.1: Edit modal type toggle + pending row ───── */
.edit-type-toggle {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 4px;
}
.edit-type-btn {
  padding: 10px 16px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all .15s ease;
}
.edit-type-btn:hover { background: rgba(255, 255, 255, 0.08); }
.edit-type-btn.expense.active {
  background: rgba(239, 68, 68, 0.18);
  border-color: rgba(239, 68, 68, 0.6);
  color: #fca5a5;
}
.edit-type-btn.income.active {
  background: rgba(16, 185, 129, 0.18);
  border-color: rgba(16, 185, 129, 0.6);
  color: #6ee7b7;
}
.edit-pending-row {
  margin-top: 4px;
}
.edit-pending-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.85);
}
.edit-pending-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
}
/* ───── /Step 57-A.1 ───── */


/* ───── Step 57-A.3: Edit modal attachments section ───── */
.edit-attachments-block {
  margin-top: 4px;
}
.edit-attach-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.65);
  margin-bottom: 6px;
}
.edit-attach-count {
  color: rgba(255, 255, 255, 0.45);
  font-size: 12px;
}
.edit-attach-state {
  padding: 10px 12px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.03);
  color: rgba(255, 255, 255, 0.55);
  font-size: 13px;
  border: 1px dashed rgba(255, 255, 255, 0.08);
}
.edit-attach-state.edit-attach-error {
  color: #fca5a5;
  border-color: rgba(239, 68, 68, 0.4);
  background: rgba(239, 68, 68, 0.06);
}
.edit-attach-state.edit-attach-empty {
  color: rgba(255, 255, 255, 0.4);
  font-style: italic;
}
.edit-attach-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.edit-attach-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(59, 130, 246, 0.08);
  border: 1px solid rgba(59, 130, 246, 0.15);
}
.edit-attach-icon {
  color: #60a5fa;
  font-size: 14px;
  flex-shrink: 0;
}
.edit-attach-name {
  flex: 1;
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.edit-attach-btn {
  width: 28px;
  height: 28px;
  border-radius: 5px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  transition: all .15s ease;
  flex-shrink: 0;
}
.edit-attach-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}
.edit-attach-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.edit-attach-btn.preview:hover:not(:disabled) {
  border-color: rgba(96, 165, 250, 0.5);
  color: #60a5fa;
}
.edit-attach-btn.delete:hover:not(:disabled) {
  border-color: rgba(239, 68, 68, 0.5);
  background: rgba(239, 68, 68, 0.1);
  color: #fca5a5;
}
/* ───── /Step 57-A.3 ───── */


/* ───── Step 57-A.3.1: Header accent + textarea + footer ───── */
.edit-modal-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}
.edit-modal-icon {
  color: #60a5fa;
  font-size: 16px;
}
.edit-modal-num {
  color: #60a5fa;
  font-weight: 500;
  font-size: 16px;
}
.edit-desc-textarea {
  resize: vertical;
  min-height: 56px;
  font-family: inherit;
  line-height: 1.4;
}
/* Cancel button × icon spacing */
.btn-secondary > i.fa-times {
  margin-right: 4px;
  font-size: 11px;
}
/* ───── /Step 57-A.3.1 ───── */


/* ───── Step 57-A.4: Edit modal upload area ───── */
.edit-upload-block {
  margin-top: 4px;
}
.edit-upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 18px 16px;
  border: 1px dashed rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.02);
  cursor: pointer;
  transition: all .15s ease;
  text-align: center;
}
.edit-upload-area:hover {
  border-color: rgba(96, 165, 250, 0.5);
  background: rgba(59, 130, 246, 0.04);
}
.edit-upload-icon {
  color: #60a5fa;
  font-size: 22px;
  margin-bottom: 8px;
}
.edit-upload-text {
  color: rgba(255, 255, 255, 0.78);
  font-size: 13px;
  font-weight: 500;
}
.edit-upload-preview {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.edit-upload-preview-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.2);
}
.edit-upload-preview-name {
  flex: 1;
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.edit-upload-preview-size {
  color: rgba(255, 255, 255, 0.45);
  font-size: 11px;
  flex-shrink: 0;
}
/* ───── /Step 57-A.4 ───── */


/* ───── Step 57-A.4.1: Edit modal sizing (legacy parity) ───── */
/* Target ONLY the edit modal (which contains .edit-modal-title) so we don't
   affect the delete-confirm modal or any other future modals. */
.modal:has(.edit-modal-title) {
  max-width: 820px;
}
.modal:has(.edit-modal-title) .modal-header h3 {
  font-size: 1.15rem;
}
.modal:has(.edit-modal-title) .modal-body {
  padding: 22px 24px;
}
.modal:has(.edit-modal-title) .form-grid {
  gap: 16px 18px;
}
.modal:has(.edit-modal-title) .form-field label {
  font-size: 0.85rem;
  letter-spacing: 0.2px;
}
.modal:has(.edit-modal-title) .f-input,
.modal:has(.edit-modal-title) .f-select {
  font-size: 0.95rem;
  padding: 10px 12px;
}
.modal:has(.edit-modal-title) .edit-desc-textarea {
  min-height: 72px;
  font-size: 0.95rem;
  padding: 10px 12px;
}
.modal:has(.edit-modal-title) .edit-type-btn {
  padding: 12px 16px;
  font-size: 0.95rem;
}
.modal:has(.edit-modal-title) .edit-pending-label {
  font-size: 0.95rem;
}
.modal:has(.edit-modal-title) .edit-pending-label input[type="checkbox"] {
  width: 18px;
  height: 18px;
}
.modal:has(.edit-modal-title) .edit-attach-label {
  font-size: 0.85rem;
}
.modal:has(.edit-modal-title) .edit-attach-row {
  padding: 10px 14px;
}
.modal:has(.edit-modal-title) .edit-attach-name,
.modal:has(.edit-modal-title) .edit-upload-preview-name {
  font-size: 0.95rem;
}
.modal:has(.edit-modal-title) .edit-attach-btn {
  width: 32px;
  height: 32px;
  font-size: 13px;
}
.modal:has(.edit-modal-title) .edit-upload-area {
  padding: 22px 16px;
}
.modal:has(.edit-modal-title) .edit-upload-icon {
  font-size: 26px;
}
.modal:has(.edit-modal-title) .edit-upload-text {
  font-size: 0.95rem;
}
.modal:has(.edit-modal-title) .modal-footer {
  padding: 16px 24px;
}
.modal:has(.edit-modal-title) .btn-secondary,
.modal:has(.edit-modal-title) .btn-primary {
  font-size: 0.95rem;
  padding: 10px 18px;
}

/* Mobile: keep readability but cap at 100% */
@media (max-width: 640px) {
  .modal:has(.edit-modal-title) {
    max-width: 100%;
  }
  .modal:has(.edit-modal-title) .form-grid {
    grid-template-columns: 1fr;
  }
  .modal:has(.edit-modal-title) .modal-body {
    padding: 18px 16px;
  }
}
/* ───── /Step 57-A.4.1 ───── */


/* Phase 60-E: column dropdown filters */
.th-filter { position: relative; }
.th-filter-wrap { position: relative; display: inline-block; }
.th-btn {
  background: transparent;
  border: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
  padding: 4px 6px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 4px;
  transition: background 0.15s, color 0.15s;
}
.th-btn:hover { background: rgba(79, 195, 161, 0.12); color: #4FC3A1; }
.th-filter-wrap.has-filter .th-btn { color: #4FC3A1; }
.th-filter-count {
  display: inline-block;
  background: #4FC3A1;
  color: #0d1421;
  font-size: 0.72rem;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 8px;
  min-width: 16px;
  text-align: center;
}
.th-caret { font-size: 0.78rem; opacity: 0.7; }
.filter-menu {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 4px;
  min-width: 260px;
  max-width: 360px;
  background: #182230;
  border: 1px solid #2a3848;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.5);
  z-index: 50;
  font-weight: normal;
  font-size: 0.88rem;
  color: #c8d4e3;
}
.filter-menu-search {
  padding: 8px;
  border-bottom: 1px solid #2a3848;
}
.filter-menu-search input {
  width: 100%;
  padding: 6px 10px;
  background: #0d1421;
  border: 1px solid #2a3848;
  border-radius: 4px;
  color: #e8eef7;
  font-size: 0.85rem;
}
.filter-menu-list {
  max-height: 280px;
  overflow-y: auto;
  padding: 4px 0;
}
.filter-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  cursor: pointer;
  user-select: none;
}
.filter-menu-item:hover { background: rgba(79, 195, 161, 0.08); }
.filter-menu-item input[type="checkbox"] { cursor: pointer; }
.filter-menu-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.filter-menu-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
  padding: 8px;
  border-top: 1px solid #2a3848;
}
.fm-btn-link {
  background: transparent;
  border: none;
  color: #c8d4e3;
  cursor: pointer;
  font-size: 0.82rem;
  padding: 4px 6px;
  text-decoration: underline;
}
.fm-btn-link:hover { color: #4FC3A1; }
.fm-btn-apply {
  background: #4FC3A1;
  color: #0d1421;
  border: none;
  border-radius: 4px;
  padding: 6px 14px;
  font-weight: 600;
  font-size: 0.85rem;
  cursor: pointer;
}
.fm-btn-apply:hover { background: #6dd1b3; }

/* ── Phase 1-F2.1: Mode-aware filter & PLANNED row visuals ── */
.f-select-mode {
  font-weight: 600;
  border-width: 1.5px;
  transition: all .2s;
}
.f-select-mode.mode-actual {
  border-color: rgba(16, 185, 129, .45);
  color: #10b981;
  background: rgba(16, 185, 129, .06);
}
.f-select-mode.mode-planned {
  border-color: #f59e0b;
  color: #f59e0b;
  background: rgba(245, 158, 11, .1);
}
.f-select-mode.mode-all {
  border-color: var(--border);
  color: var(--text-primary);
}
.planned-pill {
  display: inline-block;
  margin-left: 4px;
  font-size: .78rem;
  vertical-align: middle;
  cursor: help;
}
.row-planned {
  background: rgba(245, 158, 11, .035) !important;
  border-left: 3px solid #f59e0b !important;
}
.row-planned td.id-col {
  color: #f59e0b;
  font-weight: 700;
}

.paid-edit-modal { max-width: 440px; }
.paid-edit-lock { color: #2E75B6; margin-right: 6px; }
/* S93B-DARK-THEME-FIX */
.paid-edit-text { font-size: 14px; line-height: 1.6; color: var(--text-primary); margin: 0 0 16px; }
.paid-edit-box { background: var(--bg-input); border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 12px 14px; }
.paid-edit-row { display: flex; justify-content: space-between; padding: 3px 0; font-size: 13px; }
.paid-edit-row span { color: var(--text-muted); }
.paid-edit-row strong { color: var(--text-primary); font-weight: 600; }
.btn-danger.paid-edit-del { background: #c0392b; border-color: #c0392b; color: #fff; }
.btn-danger.paid-edit-del:hover:not(:disabled) { background: #a93226; }
</style>