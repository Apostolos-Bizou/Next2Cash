<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import api from '@/api'
import CardFormModal from '@/components/CardFormModal.vue'
import ExportFilenameModal from '@/components/ExportFilenameModal.vue'

// ──────────────────────────────────────────────────────────────
// Phase H v2 — Karteles (user-defined cards with rule engine)
//
// Data flow:
//   1. On mount: GET /api/config/cards?entityId → sidebar list
//   2. On select: GET /api/config/cards/{id}/summary     (KPI block)
//                 GET /api/config/cards/{id}/transactions (table)
//
// CRUD:
//   - Create/Edit: CardFormModal (Phase 3)
//   - Delete: confirmation (Phase 4)
// ──────────────────────────────────────────────────────────────

// ─── Entity selection (match other views' convention) ─────────
const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14'
}
const entityKey = ref(localStorage.getItem('n2c_entity') || 'next2me')
const currentEntityId = computed(() => ENTITIES[entityKey.value])

// ─── State ────────────────────────────────────────────────────
const loadingList    = ref(false)
const loadingSummary = ref(false)
const loadingTxns    = ref(false)
const errorMsg       = ref('')

const cards          = ref([])        // [{ id, configKey, configValue, parentKey, icon, sortOrder }]
const selectedCardId = ref(null)
const selectedCard   = ref(null)      // full object after GET
const summary        = ref(null)      // { total, paid, unpaid, income, urgent, counts... }
const transactions   = ref([])        // from /cards/{id}/transactions

// ─── Filters (client-side on the current page of transactions) ─
const search       = ref('')
const dateFrom     = ref('')
const dateTo       = ref('')
const statusFilter = ref('all') // all | paid | unpaid | urgent | partial | received
const typeFilter   = ref('all') // all | income | expense
const viewFilter   = ref('all') // all | transactions | payments  (Phase K)

// ─── API ──────────────────────────────────────────────────────
async function loadCards() {
  loadingList.value = true
  errorMsg.value = ''
  try {
    const res = await api.get('/api/config/cards', {
      params: { entityId: currentEntityId.value }
    })
    if (res.data?.success) {
      cards.value = res.data.data || []

      if (cards.value.length === 0) {
        selectedCardId.value = null
        selectedCard.value = null
        summary.value = null
        transactions.value = []
      } else {
        // Keep selection if still present, else pick first
        const stillThere = selectedCardId.value &&
          cards.value.some(c => c.id === selectedCardId.value)
        if (!stillThere) {
          await selectCard(cards.value[0].id)
        }
      }
    } else {
      errorMsg.value = 'Αποτυχία φόρτωσης καρτέλων'
    }
  } catch (e) {
    console.error('loadCards error:', e)
    errorMsg.value = e?.response?.data?.error || 'Σφάλμα σύνδεσης με τον server'
  } finally {
    loadingList.value = false
  }
}

async function loadSummary(cardId) {
  loadingSummary.value = true
  try {
    const res = await api.get(`/api/config/cards/${cardId}/summary`, {
      params: { entityId: currentEntityId.value }
    })
    if (res.data?.success) {
      summary.value = res.data.data
    } else {
      summary.value = null
    }
  } catch (e) {
    console.error('loadSummary error:', e)
    summary.value = null
  } finally {
    loadingSummary.value = false
  }
}

async function loadTransactions(cardId) {
  loadingTxns.value = true
  try {
    const res = await api.get(`/api/config/cards/${cardId}/transactions`, {
      params: { entityId: currentEntityId.value, limit: 2000, offset: 0 }
    })
    if (res.data?.success) {
      selectedCard.value = res.data.card || null
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

async function selectCard(cardId) {
  selectedCardId.value = cardId
  // Reset local filters on selection change
  search.value = ''
  dateFrom.value = ''
  dateTo.value = ''
  statusFilter.value = 'all'
  typeFilter.value = 'all'
  viewFilter.value = 'all'
  // Fire in parallel
  await Promise.all([loadSummary(cardId), loadTransactions(cardId)])
}

// ─── Modal state ──────────────────────────────────────────────
const modalVisible = ref(false)
const modalMode    = ref('create') // 'create' | 'edit'
const modalCard    = ref(null)

function openCreateModal() {
  modalMode.value = 'create'
  modalCard.value = null
  modalVisible.value = true
}

function openEditModal() {
  if (!selectedCard.value) return
  modalMode.value = 'edit'
  modalCard.value = { ...selectedCard.value }
  modalVisible.value = true
}

async function onCardSaved(savedCard) {
  modalVisible.value = false
  // Reload sidebar list; if edit, keep selection (select by returned id)
  const keepId = modalMode.value === 'edit' ? selectedCardId.value : (savedCard?.id || null)
  await loadCards()
  if (keepId && cards.value.some(c => c.id === keepId)) {
    await selectCard(keepId)
  }
}

// ─── Export state + handlers ─────────────────────────────
const exportDialog   = ref(false)
const exportFormat   = ref('excel')  // 'excel' | 'pdf'
const exportBusy     = ref(false)
const exportErrorMsg = ref('')

function openExcelModal() {
  if (!selectedCard.value) return
  exportFormat.value = 'excel'
  exportErrorMsg.value = ''
  exportDialog.value = true
}

function openPdfModal() {
  if (!selectedCard.value) return
  exportFormat.value = 'pdf'
  exportErrorMsg.value = ''
  exportDialog.value = true
}

function closeExportDialog() {
  if (exportBusy.value) return
  exportDialog.value = false
  exportErrorMsg.value = ''
}

/**
 * Download handler. Called by ExportFilenameModal's @confirm event.
 * Overrides Accept header because api.js hardcodes application/json,
 * which would make the Spring controller return 406 for xlsx/pdf.
 */
async function runExport(userFilename) {
  if (!selectedCard.value || exportBusy.value) return
  exportBusy.value = true
  exportErrorMsg.value = ''

  const isPdf = exportFormat.value === 'pdf'
  const url = `/api/config/cards/${selectedCard.value.id}/export/${isPdf ? 'pdf' : 'excel'}`
  const ext = isPdf ? '.pdf' : '.xlsx'
  const acceptHeader = isPdf
    ? 'application/pdf'
    : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

  try {
    const res = await api.get(url, {
      params: { entityId: currentEntityId.value, filename: userFilename },
      responseType: 'blob',
      headers: { Accept: acceptHeader }
    })

    let downloadName = userFilename + ext
    const cd = res.headers['content-disposition'] || res.headers['Content-Disposition'] || ''
    const match = cd.match(/filename\s*=\s*"?([^";]+)"?/i)
    if (match && match[1]) downloadName = match[1].trim()

    if (isPdf) {
      // PDF: open in new tab → browser shows native print-preview dialog
      // with "Save as PDF" option, matching legacy UX.
      openPdfPreview(res.data, downloadName)
    } else {
      // Excel: keep direct download (spreadsheets have no preview)
      const blobUrl = window.URL.createObjectURL(res.data)
      const a = document.createElement('a')
      a.href = blobUrl
      a.download = downloadName
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(blobUrl)
    }

    exportDialog.value = false
  } catch (e) {
    console.error('export error:', e)
    let msg = 'Αποτυχία εξαγωγής'
    if (e?.response?.data instanceof Blob) {
      try {
        const text = await e.response.data.text()
        const json = JSON.parse(text)
        msg = json.error || json.message || msg
      } catch { /* default */ }
    } else if (e?.response?.data?.error) {
      msg = e.response.data.error
    } else if (e?.message) {
      msg = e.message
    }
    exportErrorMsg.value = msg
  } finally {
    exportBusy.value = false
  }
}

/**
 * Opens a PDF blob in a new browser tab and auto-triggers the native
 * print dialog (Ctrl+P-like UI with "Save as PDF" option).
 *
 * Uses a data URL fallback if the browser blocks the popup.
 * The filename is preserved via the URL fragment so if the user chooses
 * "Save as PDF" in the dialog, Chrome pre-fills the filename.
 */
function openPdfPreview(blob, filename) {
  const blobUrl = window.URL.createObjectURL(blob)
  // Open new tab with the PDF. Chrome/Edge will render it inline.
  const w = window.open(blobUrl, '_blank')

  if (!w) {
    // Popup blocked — fallback to direct download with helpful message
    exportErrorMsg.value = 'Το browser μπλόκαρε το popup. Ενεργοποίησε popups για next2cash.com ή θα κατέβει απευθείας.'
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(blobUrl)
    return
  }

  // Set a helpful title + auto-open print dialog once PDF has rendered.
  // 500ms is empirically enough for most PDFs < 2MB; longer PDFs may need more.
  w.document.title = filename
  setTimeout(() => {
    try {
      w.print()
    } catch (e) {
      console.warn('Auto-print failed (browser may have blocked):', e)
    }
  }, 600)

  // Clean up the blob URL after 60 seconds — enough time for user
  // to interact with the print dialog; longer keeps memory pinned.
  setTimeout(() => window.URL.revokeObjectURL(blobUrl), 60000)
}

// ─── Delete state + handlers ──────────────────────────────────
const deleteDialog   = ref(false)
const deleteTarget   = ref(null)  // card object being deleted
const deleting       = ref(false)
const deleteErrorMsg = ref('')

function confirmDelete() {
  if (!selectedCard.value) return
  deleteTarget.value = selectedCard.value
  deleteErrorMsg.value = ''
  deleteDialog.value = true
}

function closeDeleteDialog() {
  if (deleting.value) return
  deleteDialog.value = false
  deleteTarget.value = null
  deleteErrorMsg.value = ''
}

async function executeDelete() {
  if (!deleteTarget.value || deleting.value) return
  deleting.value = true
  deleteErrorMsg.value = ''
  try {
    const res = await api.delete(
      `/api/config/cards/${deleteTarget.value.id}`,
      { params: { entityId: currentEntityId.value } }
    )
    if (res.data?.success) {
      // Clear current selection and reload list
      selectedCardId.value = null
      selectedCard.value = null
      summary.value = null
      transactions.value = []
      deleteDialog.value = false
      deleteTarget.value = null
      await loadCards()
    } else {
      deleteErrorMsg.value = res.data?.error || 'Αποτυχία διαγραφής'
    }
  } catch (e) {
    console.error('delete error:', e)
    const serverMsg = e?.response?.data?.error || e?.response?.data?.message
    if (e?.response?.status === 404) {
      deleteErrorMsg.value = 'Η καρτέλα δεν βρέθηκε — πιθανόν να διαγράφηκε από αλλού. Η λίστα θα ανανεωθεί.'
      // Background refresh + close dialog after 2s
      setTimeout(async () => {
        deleteDialog.value = false
        deleteTarget.value = null
        selectedCardId.value = null
        selectedCard.value = null
        await loadCards()
      }, 2000)
    } else {
      deleteErrorMsg.value = serverMsg || 'Σφάλμα σύνδεσης με τον server'
    }
  } finally {
    deleting.value = false
  }
}

// ─── Entity switcher integration ──────────────────────────────
function onEntityChanged() {
  entityKey.value = localStorage.getItem('n2c_entity') || 'next2me'
  selectedCardId.value = null
  selectedCard.value = null
  summary.value = null
  transactions.value = []
  loadCards()
}

onMounted(() => {
  loadCards()
  window.addEventListener('entity-changed', onEntityChanged)
})
onUnmounted(() => {
  window.removeEventListener('entity-changed', onEntityChanged)
})

// ─── Derived / filtered data ──────────────────────────────────
const filteredTransactions = computed(() => {
  const q = (search.value || '').trim().toLowerCase()
  return transactions.value.filter(t => {
    // Phase K: view filter (transactions vs payments vs all)
    if (viewFilter.value === 'transactions' && t.recordSource === 'PAYMENT') return false
    if (viewFilter.value === 'payments'     && t.recordSource !== 'PAYMENT') return false
    if (typeFilter.value !== 'all' && t.type !== typeFilter.value) return false
    if (statusFilter.value !== 'all' && t.paymentStatus !== statusFilter.value) return false
    if (dateFrom.value && t.docDate < dateFrom.value) return false
    if (dateTo.value   && t.docDate > dateTo.value)   return false
    if (q) {
      const hay = [
        String(t.entityNumber ?? t.id ?? ''),
        t.description   ?? '',
        t.category      ?? '',
        t.subcategory   ?? '',
        t.paymentMethod ?? '',
        t.counterparty  ?? ''
      ].join(' ').toLowerCase()
      if (!hay.includes(q)) return false
    }
    return true
  })
})

// ─── Formatting helpers ───────────────────────────────────────
const fmtMoney = (n) => {
  const v = Number(n || 0)
  return new Intl.NumberFormat('el-GR', {
    minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(v) + ' €'
}
const fmtDate = (iso) => {
  if (!iso) return '—'
  const parts = String(iso).split('-')
  if (parts.length !== 3) return iso
  return `${parts[2]}/${parts[1]}/${parts[0]}`
}
const statusLabel = (s) => ({
  paid:     'Εξοφλημένη',
  received: 'Εισπράχθηκε',
  unpaid:   'Απλήρωτη',
  urgent:   'Επείγον',
  partial:  'Μερική'
}[s] || s || '—')
const statusClass = (s) => ({
  paid:     'badge-green',
  received: 'badge-green',
  unpaid:   'badge-red',
  urgent:   'badge-orange',
  partial:  'badge-orange'
}[s] || 'badge-gray')

// Pretty rule summary in the header
const ruleLabel = computed(() => {
  const pk = selectedCard.value?.parentKey
  if (!pk) return ''
  const idx = pk.indexOf(':')
  if (idx < 0) return pk
  const type = pk.substring(0, idx)
  const value = pk.substring(idx + 1)
  const typeText = {
    search: 'Αναζήτηση',
    category: 'Κατηγορία',
    subcategory: 'Υποκατηγορία',
    counterparty: 'Αντισυμβαλλόμενος'
  }[type] || type
  return `${typeText}: ${value}`
})
</script>

<template>
  <div class="karteles-page">
    <div class="karteles-layout">

      <!-- ═══════════════════════ Sidebar ═══════════════════════ -->
      <aside class="karteles-sidebar">
        <div class="sidebar-header">
          <span>📋 Καρτέλες <span class="sidebar-count">({{ cards.length }})</span></span>
          <button class="btn-plus" @click="openCreateModal" title="Νέα καρτέλα">+</button>
        </div>

        <div v-if="loadingList" class="side-msg">Φόρτωση...</div>
        <div v-else-if="errorMsg" class="side-msg error">{{ errorMsg }}</div>
        <div v-else-if="cards.length === 0" class="side-empty">
          <div class="side-empty-text">Δεν υπάρχουν καρτέλες ακόμα.</div>
          <button class="btn-primary btn-small" @click="openCreateModal">
            + Δημιούργησε την πρώτη
          </button>
        </div>

        <div
          v-for="c in cards"
          :key="c.id"
          class="kartela-item"
          :class="{ active: selectedCardId === c.id }"
          @click="selectCard(c.id)"
        >
          <span class="kartela-icon">{{ c.icon || '📂' }}</span>
          <span class="kartela-name" :title="c.configValue">{{ c.configValue }}</span>
        </div>
      </aside>

      <!-- ═══════════════════════ Main ═══════════════════════ -->
      <section class="karteles-main">

        <!-- Header -->
        <div v-if="selectedCard" class="kartela-header">
          <div class="kartela-title">
            <span class="kartela-title-icon">{{ selectedCard.icon || '📂' }}</span>
            <h2>{{ selectedCard.configValue }}</h2>
            <span class="rule-badge" :title="selectedCard.parentKey">{{ ruleLabel }}</span>
          </div>
          <div class="kartela-actions">
            <button
            class="btn-icon btn-export-excel"
            @click="openExcelModal"
            :disabled="!selectedCard"
            title="Εξαγωγή σε Excel">
            📊 Excel
          </button>
          <button
            class="btn-icon btn-export-pdf"
            @click="openPdfModal"
            :disabled="!selectedCard"
            title="Εξαγωγή σε PDF">
            📄 PDF
          </button>
          <button class="btn-icon" @click="openEditModal" title="Επεξεργασία">✏️ Edit</button>
            <button class="btn-icon btn-danger" @click="confirmDelete" title="Διαγραφή">🗑 Delete</button>
          </div>
        </div>

        <!-- Phase K: View filter chips -->
        <div class="view-chips" v-if="selectedCard">
          <button
            class="view-chip"
            :class="{ active: viewFilter === 'all' }"
            @click="viewFilter = 'all'">
            Όλα ({{ transactions.length }})
          </button>
          <button
            class="view-chip"
            :class="{ active: viewFilter === 'transactions' }"
            @click="viewFilter = 'transactions'">
            Κινήσεις ({{ transactions.filter(t => t.recordSource !== 'PAYMENT').length }})
          </button>
          <button
            class="view-chip view-chip-payments"
            :class="{ active: viewFilter === 'payments' }"
            @click="viewFilter = 'payments'">
            💳 Πληρωμές ({{ transactions.filter(t => t.recordSource === 'PAYMENT').length }})
          </button>
        </div>

        <!-- Filters -->
        <div class="filters-bar" v-if="selectedCard">
          <input v-model="search" class="filter-input flex-1"
                 placeholder="Αναζήτηση περιγραφής, κατηγορίας, αντισυμβαλλόμενου..." />
          <input v-model="dateFrom" type="date" class="filter-input" title="Από" />
          <input v-model="dateTo"   type="date" class="filter-input" title="Έως" />
          <select v-model="typeFilter" class="filter-select">
            <option value="all">Όλοι οι τύποι</option>
            <option value="income">Έσοδα</option>
            <option value="expense">Έξοδα</option>
          </select>
          <select v-model="statusFilter" class="filter-select">
            <option value="all">Όλες οι καταστάσεις</option>
            <option value="paid">Εξοφλημένες</option>
            <option value="unpaid">Απλήρωτες</option>
            <option value="urgent">Επείγουσες</option>
            <option value="partial">Μερικές</option>
            <option value="received">Εισπραχθέντα</option>
          </select>
        </div>

        <!-- KPI cards (server-side from /summary) -->
        <div v-if="selectedCard" class="kpi-row">
          <div v-if="loadingSummary" class="kpi-loading">Υπολογισμός KPIs...</div>
          <template v-else-if="summary">
            <div class="kpi-card kpi-neutral">
              <div class="kpi-label">Σύνολο</div>
              <div class="kpi-amount">{{ fmtMoney(summary.total) }}</div>
              <div class="kpi-count">{{ summary.countTotal }} κινήσεις</div>
            </div>
            <div class="kpi-card kpi-green">
              <div class="kpi-label">Εξοφλημένες</div>
              <div class="kpi-amount">{{ fmtMoney(summary.paid) }}</div>
              <div class="kpi-count">{{ summary.countPaid }} κινήσεις</div>
            </div>
            <div class="kpi-card kpi-red">
              <div class="kpi-label">Απλήρωτες</div>
              <div class="kpi-amount">{{ fmtMoney(summary.unpaid) }}</div>
              <div class="kpi-count">{{ summary.countUnpaid }} κινήσεις</div>
            </div>
            <div class="kpi-card kpi-blue">
              <div class="kpi-label">Εισπράξεις</div>
              <div class="kpi-amount">{{ fmtMoney(summary.income) }}</div>
              <div class="kpi-count">{{ summary.countIncome }} κινήσεις</div>
            </div>
            <div class="kpi-card kpi-payments">
              <div class="kpi-label">💳 Πληρωμές</div>
              <div class="kpi-amount">{{ fmtMoney(summary.paymentsTotal) }}</div>
              <div class="kpi-count">{{ summary.countPayments }} πληρωμές</div>
            </div>
            <div class="kpi-card kpi-orange">
              <div class="kpi-label">⚡ Εκκρεμείς</div>
              <div class="kpi-amount">{{ fmtMoney(summary.urgent) }}</div>
              <div class="kpi-count">{{ summary.countUrgent }} κινήσεις</div>
            </div>
          </template>
        </div>

        <!-- Transactions table -->
        <div v-if="selectedCard" class="table-wrap">
          <div v-if="loadingTxns" class="empty-state">Φόρτωση κινήσεων...</div>
          <div v-else-if="filteredTransactions.length === 0" class="empty-state">
            <span v-if="transactions.length === 0">Δεν υπάρχουν κινήσεις για αυτή την καρτέλα.</span>
            <span v-else>Καμία κίνηση δεν ταιριάζει στα φίλτρα.</span>
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
                <th>STATUS</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="t in filteredTransactions"
                :key="(t.recordSource || 'TXN') + '-' + t.id"
                :class="{ 'payment-row': t.recordSource === 'PAYMENT' }"
              >
                <td class="id-col">
                  <span v-if="t.recordSource === 'PAYMENT'" class="payment-id" :title="'Πληρωμή #' + (t.parentTransactionId || t.id)">-{{ t.id }}</span>
                  <span v-else>{{ t.entityNumber || t.id }}</span>
                </td>
                <td>{{ fmtDate(t.docDate) }}</td>
                <td class="desc-col" :title="t.description">
                  <span v-if="t.recordSource === 'PAYMENT'" class="payment-icon-inline">💳</span>
                  {{ t.description || '—' }}
                </td>
                <td><span class="cat-badge">{{ t.category || '—' }}</span></td>
                <td>{{ t.paymentMethod || '—' }}</td>
                <td class="num">{{ fmtMoney(t.amount) }}</td>
                <td class="num money-green">{{ fmtMoney(t.amountPaid) }}</td>
                <td class="num" :class="Number(t.amountRemaining) > 0 ? 'money-red' : ''">
                  {{ fmtMoney(t.amountRemaining) }}
                </td>
                <td>
                  <span class="badge" :class="statusClass(t.paymentStatus)">
                    {{ statusLabel(t.paymentStatus) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- No selection / empty repo -->
        <div v-if="!selectedCard && !loadingList && cards.length > 0" class="empty-state">
          Επίλεξε μια καρτέλα από τη λίστα αριστερά.
        </div>
        <div v-if="!selectedCard && !loadingList && cards.length === 0" class="empty-state-big">
          <div class="empty-big-icon">📋</div>
          <div class="empty-big-title">Δεν υπάρχουν καρτέλες ακόμα</div>
          <div class="empty-big-sub">Δημιούργησε την πρώτη σου καρτέλα για να ομαδοποιήσεις κινήσεις.</div>
          <button class="btn-primary" @click="openCreateModal">+ Νέα καρτέλα</button>
        </div>

      </section>
    </div>

    <!-- Create/Edit modal -->
    <CardFormModal
      :visible="modalVisible"
      :mode="modalMode"
      :card="modalCard"
      :entity-id="currentEntityId"
      @close="modalVisible = false"
      @saved="onCardSaved"
    />

    <!-- Export filename dialog -->
      <ExportFilenameModal
        :show="exportDialog"
        :format="exportFormat"
        :card-name="selectedCard?.configValue || ''"
        :busy="exportBusy"
        @confirm="runExport"
        @cancel="closeExportDialog"
      />
      <p v-if="exportErrorMsg" class="export-error-toast">⚠ {{ exportErrorMsg }}</p>

      <!-- Delete confirmation dialog -->
    <div v-if="deleteDialog" class="modal-backdrop" @click.self="closeDeleteDialog">
      <div class="delete-dialog">
        <div class="delete-header">
          <span class="delete-icon">⚠️</span>
          <h3>Διαγραφή καρτέλας</h3>
        </div>
        <div class="delete-body">
          <p>Σίγουρα θέλεις να διαγράψεις την καρτέλα:</p>
          <p class="delete-target">
            <span class="delete-target-icon">{{ deleteTarget?.icon || '📂' }}</span>
            <strong>{{ deleteTarget?.configValue }}</strong>
          </p>
          <p class="delete-warning">
            Οι κινήσεις παραμένουν στη βάση — διαγράφεται μόνο η ομαδοποίηση.
            Η καρτέλα θα εξαφανιστεί από τη λίστα.
          </p>
          <div v-if="deleteErrorMsg" class="form-error">⚠ {{ deleteErrorMsg }}</div>
        </div>
        <div class="delete-footer">
          <button class="btn-secondary" @click="closeDeleteDialog" :disabled="deleting">
            Ακύρωση
          </button>
          <button class="btn-danger-solid" @click="executeDelete" :disabled="deleting">
            <span v-if="deleting">Διαγραφή...</span>
            <span v-else>Διαγραφή</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ═══════════════════════ Layout ═══════════════════════ */
.karteles-page {
  padding: 24px;
  color: #e0e6ed;
  height: calc(100vh - 60px);
}
.karteles-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
  height: 100%;
}

/* ═══════════════════════ Sidebar ═══════════════════════ */
.karteles-sidebar {
  background: #1e3448;
  border-radius: 10px;
  padding: 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 4px 12px;
  font-size: 0.88rem;
  font-weight: 600;
  color: #4FC3A1;
  border-bottom: 1px solid #2a4a6a;
  margin-bottom: 8px;
}
.sidebar-count { color: #8899aa; font-weight: 400; font-size: 0.8rem; }
.btn-plus {
  background: #4FC3A1;
  color: #0f1e2e;
  border: none;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  font-size: 1.1rem;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}
.btn-plus:hover { background: #3da98a; }

.side-msg {
  padding: 10px 8px;
  font-size: 0.82rem;
  color: #8899aa;
}
.side-msg.error { color: #ef5350; }

.side-empty {
  padding: 20px 8px;
  text-align: center;
}
.side-empty-text {
  font-size: 0.82rem;
  color: #8899aa;
  margin-bottom: 12px;
}

.kartela-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: background 0.12s;
}
.kartela-item:hover { background: #2a4a6a; }
.kartela-item.active {
  background: rgba(79, 195, 161, 0.16);
  color: #4FC3A1;
}
.kartela-icon { font-size: 0.95rem; flex-shrink: 0; }
.kartela-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ═══════════════════════ Main panel ═══════════════════════ */
.karteles-main { overflow-y: auto; }

.kartela-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.kartela-title { display: flex; align-items: center; gap: 10px; min-width: 0; }
.kartela-title-icon { font-size: 1.3rem; }
.kartela-title h2 {
  margin: 0;
  font-size: 1.15rem;
  color: #29b6f6;
  font-weight: 600;
}
.rule-badge {
  background: #1e3448;
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 0.72rem;
  color: #8899aa;
  font-weight: 400;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
.kartela-actions { display: flex; gap: 8px; flex-shrink: 0; }
.btn-icon {
  background: #1e3448;
  border: 1px solid #2a4a6a;
  color: #e0e6ed;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 0.82rem;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.btn-icon:hover { background: #2a4a6a; }
.btn-icon.btn-danger:hover { background: #e24b4a; color: #fff; border-color: #e24b4a; }

/* ═══════════════════════ Filters ═══════════════════════ */
.filters-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.filter-input, .filter-select {
  background: #1e3448;
  border: 1px solid #2a4a6a;
  color: #e0e6ed;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 0.85rem;
}
.flex-1 { flex: 1; min-width: 220px; }

/* ═══════════════════════ KPI cards ═══════════════════════ */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
@media (max-width: 1200px) {
  .kpi-row { grid-template-columns: repeat(3, 1fr); }
}
.kpi-loading {
  grid-column: 1 / -1;
  padding: 20px;
  text-align: center;
  color: #8899aa;
  background: #1e3448;
  border-radius: 8px;
}
.kpi-card {
  background: #1e3448;
  border-radius: 8px;
  padding: 12px 14px;
  border-top: 3px solid #556677;
}
.kpi-card.kpi-neutral { border-top-color: #8899aa; }
.kpi-card.kpi-green   { border-top-color: #1d9e75; }
.kpi-card.kpi-red     { border-top-color: #e24b4a; }
.kpi-card.kpi-blue    { border-top-color: #2E75B6; }
.kpi-card.kpi-orange  { border-top-color: #ef9f27; }

.kpi-label {
  font-size: 0.68rem;
  color: #8899aa;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.kpi-amount {
  font-size: 1.02rem;
  font-weight: 700;
  color: #fff;
}
.kpi-card.kpi-green  .kpi-amount { color: #1d9e75; }
.kpi-card.kpi-red    .kpi-amount { color: #e24b4a; }
.kpi-card.kpi-blue   .kpi-amount { color: #2E75B6; }
.kpi-card.kpi-orange .kpi-amount { color: #ef9f27; }

.kpi-count {
  font-size: 0.7rem;
  color: #8899aa;
  margin-top: 2px;
}

/* ══════ Phase K: KPI payments tile ══════ */
.kpi-card.kpi-payments { border-top-color: #4FC3A1; }
.kpi-card.kpi-payments .kpi-amount { color: #4FC3A1; }

/* ══════ Phase K: View filter chips ══════ */
.view-chips {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.view-chip {
  background: #1e3448;
  border: 1px solid #2a4a6a;
  color: #8899aa;
  padding: 6px 14px;
  border-radius: 18px;
  font-size: 0.82rem;
  cursor: pointer;
  transition: all 0.15s;
}
.view-chip:hover {
  background: #2a4a6a;
  color: #e0e6ed;
}
.view-chip.active {
  background: #2E75B6;
  border-color: #2E75B6;
  color: #fff;
  font-weight: 600;
}
.view-chip.view-chip-payments.active {
  background: #4FC3A1;
  border-color: #4FC3A1;
  color: #0f1e2e;
}

/* ══════ Phase K: Payment row styling ══════ */
.data-table tr.payment-row {
  background: rgba(46, 117, 182, 0.08);
}
.data-table tr.payment-row:hover {
  background: rgba(46, 117, 182, 0.15);
}
.payment-icon-inline {
  color: #2E75B6;
  font-size: 1rem;
  margin-right: 6px;
  cursor: help;
}
.payment-id {
  color: #e24b4a;
  font-family: monospace;
  font-weight: 600;
  cursor: help;
}

/* ═══════════════════════ Table ═══════════════════════ */
.table-wrap {
  overflow-x: auto;
  background: #1a2f45;
  border-radius: 8px;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.83rem;
}
.data-table th {
  background: #1a2f45;
  color: #8899aa;
  padding: 10px 12px;
  text-align: left;
  font-size: 0.7rem;
  border-bottom: 1px solid #2a4a6a;
  letter-spacing: 0.3px;
}
.data-table td {
  padding: 8px 12px;
  border-bottom: 1px solid #1e3448;
}
.data-table tr:hover { background: #1e3448; }
.id-col { color: #8899aa; font-size: 0.8rem; }
.desc-col {
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cat-badge {
  background: #2a4a6a;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.72rem;
}
.num { text-align: right; font-family: monospace; }
.money-green { color: #1d9e75; }
.money-red   { color: #e24b4a; }

.badge {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 0.72rem;
  font-weight: 600;
}
.badge-green  { background: rgba(29, 158, 117, 0.15);  color: #1d9e75; }
.badge-red    { background: rgba(226, 75, 74, 0.15);   color: #e24b4a; }
.badge-orange { background: rgba(239, 159, 39, 0.15);  color: #ef9f27; }
.badge-gray   { background: rgba(136, 153, 170, 0.15); color: #8899aa; }

/* ═══════════════════════ Empty states ═══════════════════════ */
.empty-state {
  padding: 40px 20px;
  text-align: center;
  color: #8899aa;
  font-size: 0.9rem;
  background: #1a2f45;
  border-radius: 8px;
}
.empty-state-big {
  padding: 60px 20px;
  text-align: center;
  background: #1a2f45;
  border-radius: 8px;
}
.empty-big-icon { font-size: 3rem; margin-bottom: 10px; }
.empty-big-title {
  font-size: 1.1rem;
  color: #e0e6ed;
  margin-bottom: 6px;
  font-weight: 600;
}
.empty-big-sub {
  font-size: 0.88rem;
  color: #8899aa;
  margin-bottom: 18px;
}

.btn-primary {
  background: #4FC3A1;
  color: #0f1e2e;
  border: none;
  padding: 10px 18px;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
}
.btn-primary:hover { background: #3da98a; }
.btn-small {
  padding: 6px 12px;
  font-size: 0.8rem;
}

/* ═══════════════════════ Delete dialog ═══════════════════════ */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}
.delete-dialog {
  background: #1a2f45;
  border-radius: 10px;
  width: 100%;
  max-width: 440px;
  color: #e0e6ed;
  border: 1px solid #2a4a6a;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}
.delete-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-bottom: 1px solid #2a4a6a;
}
.delete-icon { font-size: 1.2rem; }
.delete-header h3 {
  margin: 0;
  font-size: 1rem;
  color: #e24b4a;
  font-weight: 600;
}
.delete-body {
  padding: 18px 20px;
  font-size: 0.88rem;
  line-height: 1.5;
}
.delete-body p { margin: 0 0 10px 0; }
.delete-target {
  background: #0f1e2e;
  padding: 10px 14px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 14px 0 !important;
  border-left: 3px solid #e24b4a;
}
.delete-target-icon { font-size: 1.1rem; }
.delete-target strong { color: #fff; font-weight: 600; }
.delete-warning {
  font-size: 0.8rem;
  color: #8899aa;
  font-style: italic;
}
.form-error {
  background: rgba(226, 75, 74, 0.12);
  color: #e24b4a;
  border: 1px solid rgba(226, 75, 74, 0.3);
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 0.82rem;
  margin-top: 8px;
}
.delete-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid #2a4a6a;
}
.btn-secondary {
  background: transparent;
  color: #8899aa;
  border: 1px solid #2a4a6a;
  padding: 9px 18px;
  border-radius: 6px;
  font-size: 0.86rem;
  cursor: pointer;
}
.btn-secondary:hover:not(:disabled) { background: #1e3448; color: #e0e6ed; }
.btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-danger-solid {
  background: #e24b4a;
  color: #fff;
  border: none;
  padding: 9px 20px;
  border-radius: 6px;
  font-size: 0.86rem;
  font-weight: 600;
  cursor: pointer;
  min-width: 120px;
}
.btn-danger-solid:hover:not(:disabled) { background: #c93d3c; }
.btn-danger-solid:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
