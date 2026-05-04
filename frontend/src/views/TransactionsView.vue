<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'   // Session #40
import api from '@/api'
import MarkPaidModal from '@/components/MarkPaidModal.vue'
import AttachmentsPopover from '@/components/AttachmentsPopover.vue'

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
let searchDebounceTimer = null

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

function stripPaymentPrefix(desc) {
      // Removes patterns like "Πληρωμή για #1234 — "
      // (Greek "Payment for #N — ") so the actual transaction description
      // can use the full display width.
      if (!desc) return desc
      return desc.replace(/^Πληρωμή για #\d+\s+—\s+/, '')
    }

    function mapCashflowEventToRow(ev) {
      const isPayment = ev.eventType === 'payment'
      const inflow = Number(ev.inflow || 0)
      // For display: use ev.entityNumber (the user-visible number, e.g. "#4747"
      // or "#101"), NOT ev.transactionId (internal DB id like #90129).
      // For payment rows, strip the "Payment for #N - " prefix from description
      // since the row is already marked with a "<<" badge in the id column;
      // the bare description fits cleanly within the 40-char display cap.
      const displayDesc = isPayment
        ? stripPaymentPrefix(ev.description)
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
        const cleanDesc = stripPaymentPrefix(r.description || '')
        const idLabel   = r.entityNumber != null ? r.entityNumber : r.id
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
function applyFilters() { page.value = 0 }
function goToPage(p)    { page.value = p }
function resetFilters() {
  dateFrom.value = ''; dateTo.value = ''
  selectedType.value = ''; selectedStatus.value = ''
  page.value = 0
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
    const payload = {
      docDate: d.docDate,
      description: d.description,
      amount: amountNum,
      type: d.type,
      category: d.category,
      subcategory: d.subcategory,
      account: d.subcategory || d.account,
      paymentMethod: d.paymentMethod,
      paymentStatus: d.paymentStatus,
      paymentDate: d.paymentDate || null
    }
    const res = await api.put('/api/transactions/' + d.id, payload)
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
const filteredTransactions = computed(() => {
  let list = transactions.value
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
  // Exclude payment rows from KPI totals (avoid double-counting)
  const txns = transactions.value.filter(t => !t._isPaymentRow)
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
  // entityId: UUID -> reverse map to 'next2me' / 'house' / 'polaris' key
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
          <tr v-for="t in paginatedTransactions" :key="t.id"
              :class="[t.paymentStatus === 'urgent' ? 'row-urgent' : '', t._isPaymentRow ? 'row-payment' : '']">
            <td class="id-col">#{{ t.entityNumber ?? t.id }}</td>
            <td class="date-col">{{ fmtDate(t.docDate) }}</td>
            <td class="desc-col" :title="t.description">{{ (t.description || '—').substring(0,40) }}</td>
            <td><span class="cat-badge">{{ t.category || '—' }}</span></td>
            <td class="sub-col">{{ t.account || t.subcategory || '—' }}</td>
            <td class="method-col">{{ t.paymentMethod || '—' }}</td>
            <td class="ra green mono">{{ t.type === 'income'  ? fmt(t.amount) : '—' }}</td>
            <td class="ra mono" :class="t.type === 'expense' && isUnpaid(t) ? 'red' : 'white'">{{ t.type === 'expense' ? fmt(t.amount) : '—' }}</td>
            <td><span class="status-badge" :class="statusClass(t.paymentStatus)">{{ statusLabel(t.paymentStatus) }}</span></td>
            <td class="actions">
              <button
                v-if="canMarkPaid(t) && !t._isPaymentRow"
                class="btn-action btn-mark-paid-sm"
                @click="openMarkPaid(t)">
                ✓ Εξόφληση
              </button>
              <button
                class="btn-action btn-attach-sm"
                @click="openAttachments(t)"
                :style="hasAttachments(t) ? {} : { opacity: 0.45 }">
                📎
              </button>
              <button v-if="canModify && !t._isPaymentRow" class="icon-btn" title="Επεξεργασία" @click="openEdit(t)">
                <i class="fas fa-edit"></i>
              </button>
              <button v-if="canModify && !t._isPaymentRow" class="icon-btn icon-danger" title="Διαγραφή" @click="openDelete(t)">
                <i class="fas fa-trash"></i>
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
            <div class="form-field">
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
            <div class="form-field full edit-pending-row">
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

</style>