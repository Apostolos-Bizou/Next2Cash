<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import '@/assets/theme.css'
import { useUserStore } from '@/stores/user'
import PdfPreviewModal from '@/components/PdfPreviewModal.vue'
import DispatchArchiveView from '@/views/DispatchArchiveView.vue'
import {
  listDispatches, getDispatch, createDispatch, previewDispatch, getDispatchPdf,
  deleteDispatch, getRecipients, buildDispatchPayload,
} from '@/api/dispatches'

const ENTITY_MAP = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house: 'dea1f32c-7b30-4981-b625-633da9dbe71e',
  next2megroup: '50317f44-9961-4fb4-add0-7a118e32dc14',
}
const entityId = () => ENTITY_MAP[localStorage.getItem('n2c_entity') || 'next2me'] || ENTITY_MAP.next2me

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canDispatch = computed(() => ['admin', 'user'].includes((userStore.profile?.role || '').toLowerCase()))

/* ── theme ── */
const theme = ref(localStorage.getItem('n2c_rb_theme')
  || (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'))
function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  try { localStorage.setItem('n2c_rb_theme', theme.value) } catch (e) { /* ignore */ }
}

/* ── tabs ── */
const activeTab = ref(route.path === '/dispatch-archive' && canDispatch.value ? 'arc' : 'report')
function go(v) {
  activeTab.value = v
  const target = v === 'arc' ? '/dispatch-archive' : '/report-builder'
  if (route.path !== target) router.push(target)
}
watch(() => route.path, (p) => {
  activeTab.value = (p === '/dispatch-archive' && canDispatch.value) ? 'arc' : 'report'
})

/* ── state ── */
const reportTitle = ref('')
const reportDesc = ref('')
const allTransactions = ref([])
const categoriesList = ref([])
const sections = reactive({ income: [], expense: [] })
const showSummary = ref(true)
const dispatchIndex = ref([])

/* ── formatting ── */
const fmtNum = (n) => Math.abs(n).toFixed(2).replace('.', ',').replace(/\B(?=(\d{3})+(?!\d))/g, '.')
const eur = (n) => (n < 0 ? '- ' : '+ ') + fmtNum(n) + ' €'
const plain = (n) => fmtNum(n) + ' €'
const grShort = (iso) => { const [y, m, d] = String(iso).split('-'); return `${d}/${m}/${y.slice(2)}` }
const grFull = (iso) => { const [y, m, d] = String(iso).split('-'); return `${d}/${m}/${y}` }
const docDisplay = (iso) => { if (!iso) return '—'; const [y, m, d] = String(iso).split('-'); return `${d}/${m}/${y}` }

/* ── mapped transactions (TX-like) ── */
const txList = computed(() => allTransactions.value
  .filter(t => (t.recordStatus || 'active') === 'active')
  .map(t => ({
    id: t.id,
    no: t.entityNumber != null ? t.entityNumber : t.id,
    d: docDisplay(t.docDate),
    t: t.description || '',
    cat: t.category || '',
    a: t.type === 'income' ? (Number(t.amount) || 0) : -(Number(t.amount) || 0),
    paid: t.paymentStatus === 'paid' || t.paymentStatus === 'received',
    m: t.paymentMethod || '',
  })))
const txById = computed(() => { const m = new Map(); txList.value.forEach(t => m.set(t.id, t)); return m })
const T = (id) => txById.value.get(id)
const where = (id) => Object.keys(sections).find(k => sections[k].includes(id))

/* ── sent index (from dispatch list + detail) ── */
const sentOf = (id) => dispatchIndex.value
  .filter(d => (d.transactionIds || []).includes(id))
  .sort((a, b) => (a.sentDate < b.sentDate ? 1 : -1))
const isSent = (id) => sentOf(id).length > 0
function sentInfo(id) {
  const s = sentOf(id)
  if (!s.length) return { sent: false }
  const l = s[0]
  return {
    sent: true, count: s.length, first: l,
    title: s.map(x => grShort(x.sentDate) + ' → ' + x.recipient).join(' · '),
  }
}

/* ── section views ── */
const sectionsView = computed(() => (['income', 'expense']).map(k => {
  const rows = sections[k].map(T).filter(Boolean)
  const total = rows.reduce((a, t) => a + t.a, 0)
  const un = rows.filter(t => !isSent(t.id)).length
  return { key: k, name: k === 'income' ? 'Εισπράξεις' : 'Έξοδα', rows, total, un }
}))
const totalCount = computed(() => sections.income.length + sections.expense.length)
const hint = computed(() => {
  const n = totalCount.value
  return `${2 + (showSummary.value ? 1 : 0)} ενότητες · ${n} ${n === 1 ? 'κίνηση' : 'κινήσεις'}`
})

/* ── summary ── */
const summary = computed(() => {
  const inc = sections.income.map(T).filter(Boolean)
  const exp = sections.expense.map(T).filter(Boolean)
  const sIn = inc.reduce((a, t) => a + t.a, 0)
  const sEx = exp.reduce((a, t) => a + t.a, 0)
  const net = sIn + sEx
  const all = [...inc, ...exp]
  const nS = all.filter(t => isSent(t.id)).length
  const abs = (x) => x.reduce((a, t) => a + Math.abs(t.a), 0)
  const eS = exp.filter(t => isSent(t.id)), eN = exp.filter(t => !isSent(t.id)), due = exp.filter(t => !t.paid)
  return {
    inc, exp, sIn, sEx, net, allLen: all.length, nS,
    eSsum: abs(eS), eNsum: abs(eN), eNlen: eN.length, dueSum: abs(due), dueLen: due.length,
  }
})

/* ── toast ── */
const toastState = reactive({ show: false, a: '', b: '', actLabel: '', hasAct: false })
let toastFn = null, toastTimer = null
function toast(a, b, act) {
  toastState.a = a; toastState.b = b
  toastState.hasAct = !!act
  if (act) { toastState.actLabel = act.label; toastFn = act.fn }
  toastState.show = true
  clearTimeout(toastTimer); toastTimer = setTimeout(hideToast, 9000)
}
function toastAct() { if (toastFn) { const f = toastFn; toastFn = null; hideToast(); f() } }
function hideToast() { toastState.show = false }

/* ── add / remove ── */
let lastRemoved = null
function removeRow(k, id) {
  const i = sections[k].indexOf(id); if (i < 0) return
  sections[k].splice(i, 1); lastRemoved = { k, id, i }
  const t = T(id)
  toast(`Αφαιρέθηκε: ${t.no} - ${t.t}`, `${eur(t.a)} · από «${k === 'income' ? 'Εισπράξεις' : 'Έξοδα'}»`,
    { label: 'Αναίρεση', fn: undo })
  if (picker.open) refreshPickSel()
}
function clearSection(k) {
  const rm = [...sections[k]]; sections[k].splice(0)
  lastRemoved = { k, bulk: rm }
  toast(`Καθαρίστηκε η ενότητα «${k === 'income' ? 'Εισπράξεις' : 'Έξοδα'}»`, `${rm.length} κινήσεις αφαιρέθηκαν`,
    { label: 'Αναίρεση', fn: undo })
}
function undo() {
  if (!lastRemoved) return
  if (lastRemoved.bulk) { sections[lastRemoved.k].splice(0, sections[lastRemoved.k].length, ...lastRemoved.bulk) }
  else sections[lastRemoved.k].splice(lastRemoved.i, 0, lastRemoved.id)
  lastRemoved = null; hideToast()
}
function toggleSummary() { showSummary.value = !showSummary.value }

/* ── picker ── */
const picker = reactive({ open: false, tab: 'all', q: '', cat: '', used: 'all', sent: 'all', pay: 'all' })
const selIds = ref([])
function openPicker(k) {
  selIds.value = []
  picker.open = true; picker.tab = k === 'income' ? 'in' : 'out'
  picker.q = ''; picker.cat = ''; picker.used = 'all'; picker.sent = 'all'; picker.pay = 'all'
}
function closePicker() { picker.open = false }
const pickerCats = computed(() => [...new Set(txList.value.map(t => t.cat).filter(Boolean))].sort())
const pickCounts = computed(() => ({
  all: txList.value.length,
  in: txList.value.filter(t => t.a > 0).length,
  out: txList.value.filter(t => t.a < 0).length,
}))
const pool = computed(() => txList.value.filter(t => {
  if (picker.tab === 'in' && t.a < 0) return false
  if (picker.tab === 'out' && t.a > 0) return false
  if (picker.cat && t.cat !== picker.cat) return false
  const used = !!where(t.id)
  if (picker.used === 'free' && used) return false
  if (picker.used === 'used' && !used) return false
  if (picker.sent === 'no' && isSent(t.id)) return false
  if (picker.sent === 'yes' && !isSent(t.id)) return false
  if (picker.pay === 'paid' && !t.paid) return false
  if (picker.pay === 'due' && t.paid) return false
  const q = picker.q.trim().toLowerCase()
  if (q && !`${t.no} ${t.t} ${t.id}`.toLowerCase().includes(q)) return false
  return true
}))
const isChecked = (id) => selIds.value.includes(id)
function toggle(id) {
  const w = where(id)
  if (w) { toast('Η κίνηση είναι ήδη στο report', `Στην ενότητα «${w === 'income' ? 'Εισπράξεις' : 'Έξοδα'}» — αφαίρεσέ την από εκεί`, null); return }
  selIds.value = isChecked(id) ? selIds.value.filter(x => x !== id) : [...selIds.value, id]
}
function refreshPickSel() { selIds.value = selIds.value.filter(id => !where(id)) }
function selectAllVisible() {
  const add = pool.value.filter(t => !where(t.id)).map(t => t.id)
  selIds.value = [...new Set([...selIds.value, ...add])]
}
function splitBySign(ids) { const inc = [], exp = []; ids.forEach(id => (T(id).a >= 0 ? inc : exp).push(id)); return { inc, exp } }
function routeLabel(inc, exp) {
  const p = []; if (inc.length) p.push(`${inc.length} στις Εισπράξεις`); if (exp.length) p.push(`${exp.length} στα Έξοδα`); return p.join(' · ')
}
const addBtnLabel = computed(() => {
  if (!selIds.value.length) return 'Προσθήκη'
  const { inc, exp } = splitBySign(selIds.value)
  return `Προσθήκη ${selIds.value.length} — ${routeLabel(inc, exp)}`
})
function addSelected() {
  const { inc, exp } = splitBySign(selIds.value)
  sections.income.push(...inc); sections.expense.push(...exp)
  const n = selIds.value.length; selIds.value = []; closePicker(); lastRemoved = null
  toast(`Προστέθηκαν ${n} ${n === 1 ? 'κίνηση' : 'κινήσεις'}`, routeLabel(inc, exp), null)
}

/* ── preview (real backend PDF) ── */
const previewBlob = ref(null)
const previewTitle = ref('Προεπισκόπηση PDF')
async function preview() {
  const items = [...sections.income, ...sections.expense].map(id => ({ id, amount: T(id).a }))
  if (!items.length) return
  try {
    const payload = buildDispatchPayload({
      title: reportTitle.value || 'Αναφορά', recipient: 'Προεπισκόπηση', note: reportDesc.value,
      sentDate: new Date().toISOString().slice(0, 10), items, includeDocs: false,
    })
    previewTitle.value = 'Προεπισκόπηση PDF — δεν έχει σταλεί ακόμα'
    previewBlob.value = await previewDispatch(payload)
  } catch (e) { alert(mapErr(e, 'Αποτυχία προεπισκόπησης')) }
}
async function openStoredPdf(id) {
  const d = dispatchIndex.value.find(x => x.id === id); if (!d) return
  try {
    previewTitle.value = d.title
    previewBlob.value = await getDispatchPdf(id)
  } catch (e) { alert(e.response?.status === 404 ? 'Το PDF δεν βρέθηκε.' : 'Αποτυχία λήψης PDF.') }
}

/* ── dispatch dialog ── */
const send = reactive({ open: false, title: '', rcp: '', date: '', note: '', scEx: true, scIn: false, includeDocs: true, busy: false })
const recipients = ref([])
function openDispatch() {
  if (!totalCount.value) return
  send.title = reportTitle.value; send.rcp = ''; send.note = ''
  send.date = new Date().toISOString().slice(0, 10)
  send.scEx = sections.expense.length > 0; send.scIn = sections.income.length > 0 && sections.expense.length === 0
  if (!send.scEx && !send.scIn) { send.scIn = sections.income.length > 0 }
  send.includeDocs = true
  getRecipients().then(r => { recipients.value = r }).catch(() => { recipients.value = [] })
  send.open = true
}
function scopeIds() {
  let i = []
  if (send.scEx) i = i.concat(sections.expense)
  if (send.scIn) i = i.concat(sections.income)
  return i
}
const sendSummaryText = computed(() => {
  const ids = scopeIds()
  if (!ids.length) return { warn: true, html: '⚠ Δεν έχεις επιλέξει τίποτα να σταλεί.' }
  const again = ids.filter(isSent).length
  let s = `Θα δημιουργηθεί PDF με ${ids.length} κινήσεις και θα αρχειοθετηθεί στις Αποστολές.`
  if (again) s += ` ⚠ Οι ${again} έχουν ξανασταλεί — προστίθεται νέα εγγραφή, δεν χάνεται η προηγούμενη.`
  return { warn: false, html: s }
})
const scExAmt = computed(() => `${sections.expense.length} · ${plain(sections.expense.reduce((a, id) => a + Math.abs(T(id).a), 0))}`)
const scInAmt = computed(() => `${sections.income.length} · ${plain(sections.income.reduce((a, id) => a + Math.abs(T(id).a), 0))}`)
async function confirmDispatch() {
  const ids = scopeIds()
  if (!ids.length) { toast('Δεν επιλέχθηκε τίποτα', 'Τσέκαρε Έξοδα ή Εισπράξεις', null); return }
  if (!send.title.trim()) { toast('Λείπει ο τίτλος', 'Χωρίς τίτλο δεν θα το βρίσκεις στο αρχείο', null); return }
  if (!send.rcp.trim()) { toast('Λείπει ο παραλήπτης', 'Γράψε πού το στέλνεις', null); return }
  send.busy = true
  try {
    const items = ids.map(id => ({ id, amount: T(id).a }))
    const payload = buildDispatchPayload({
      title: send.title.trim(), recipient: send.rcp.trim(), note: send.note.trim(),
      sentDate: send.date, items, includeDocs: send.includeDocs,
    })
    const res = await createDispatch(payload)
    send.open = false
    let msg = `Αρχειοθετήθηκε: «${send.title.trim()}»`, sub = `${ids.length} κινήσεις → ${send.rcp.trim()}`
    if (res && res.docsRequested && res.documentsFound > 0) {
      const att = res.documentsAttached || 0, miss = res.documentsFound - att
      if (att === 0) sub += ` · ⚠ παραστατικά ΔΕΝ μπήκαν (${miss} δεν βρέθηκαν)`
      else if (miss > 0) sub += ` · ⚠ ${miss}/${res.documentsFound} παραστατικά δεν μπήκαν`
      else sub += ` · ${att} παραστατικά`
    }
    await loadDispatchIndex()
    const newId = res?.id
    toast(msg, sub, newId ? { label: 'Άνοιγμα PDF', fn: () => openStoredPdf(newId) } : null)
  } catch (e) { alert(mapErr(e, 'Αποτυχία αποστολής')) } finally { send.busy = false }
}

/* ── detail ── */
const detail = reactive({ open: false, id: null })
const detailData = computed(() => {
  const d = dispatchIndex.value.find(x => x.id === detail.id)
  if (!d) return null
  const rows = (d.transactionIds || []).map(T).filter(Boolean)
  const sIn = rows.filter(t => t.a > 0).reduce((a, t) => a + t.a, 0)
  const sEx = rows.filter(t => t.a < 0).reduce((a, t) => a + t.a, 0)
  return { d, rows, sIn, sEx }
})
function openDetail(id) { detail.id = id; detail.open = true }
async function doDelete() {
  const d = dispatchIndex.value.find(x => x.id === detail.id); if (!d) return
  if (!confirm(`Διαγραφή της αποστολής «${d.title}»;`)) return
  try {
    await deleteDispatch(d.id); detail.open = false
    await loadDispatchIndex()
    toast(`Διαγράφηκε η αποστολή «${d.title}»`, `${(d.transactionIds || []).length} κινήσεις επανήλθαν σε «μη απεσταλμένο»`, null)
  } catch (e) { alert(e.response?.status === 403 ? 'Μόνο ο διαχειριστής μπορεί να διαγράψει.' : 'Αποτυχία διαγραφής.') }
}

/* ── archive enriched list ── */
const archiveList = computed(() => dispatchIndex.value.map(d => {
  const rows = (d.transactionIds || []).map(T).filter(Boolean)
  return {
    ...d, n: (d.transactionIds || []).length,
    sentDateFull: grFull(d.sentDate),
    sumIn: rows.filter(t => t.a > 0).reduce((a, t) => a + t.a, 0),
    sumEx: rows.filter(t => t.a < 0).reduce((a, t) => a + Math.abs(t.a), 0),
  }
}))

/* ── per-section ZIP (kept from before) ── */
let _jsZipPromise = null
const _loadJSZip = () => {
  if (_jsZipPromise) return _jsZipPromise
  _jsZipPromise = new Promise((resolve, reject) => {
    if (window.JSZip) return resolve(window.JSZip)
    const s = document.createElement('script')
    s.src = 'https://cdnjs.cloudflare.com/ajax/libs/jszip/3.10.1/jszip.min.js'
    s.onload = () => resolve(window.JSZip)
    s.onerror = () => reject(new Error('JSZip CDN load failed'))
    document.head.appendChild(s)
  })
  return _jsZipPromise
}
// Which transactions in the report carry attachments (transactions.blobFileIds).
const txDocsMap = computed(() => {
  const m = new Map(); allTransactions.value.forEach(t => m.set(t.id, (t.blobFileIds || '').trim())); return m
})
const reportHasDocs = computed(() =>
  [...sections.income, ...sections.expense].some(id => (txDocsMap.value.get(id) || '') !== ''))

// Core: fetch attachments of the given transaction ids and download them as one ZIP.
async function zipTransactionFiles(txnIds, zipName) {
  const ids = txnIds.filter(id => id > 0)
  if (!ids.length) { alert('Δεν υπάρχουν κινήσεις.'); return }
  const allFiles = []
  for (const id of ids) {
    try {
      const res = await api.get('/api/documents/by-transaction/' + id)
      ;(res.data?.data || []).forEach(f => { if (f.downloadUrl && f.fileName) allFiles.push({ url: f.downloadUrl, name: f.fileName, txnId: id }) })
    } catch (err) { console.warn('[zipTransactionFiles] tx#' + id + ' failed:', err) }
  }
  if (!allFiles.length) { alert('Δεν βρέθηκαν αρχεία για αυτές τις κινήσεις.'); return }
  let JSZip
  try { JSZip = await _loadJSZip() } catch { alert('Αποτυχία φόρτωσης JSZip.'); return }
  const zip = new JSZip(), usedNames = new Map()
  const results = await Promise.all(allFiles.map(async (f) => {
    try {
      const r = await fetch(f.url); if (!r.ok) throw new Error('HTTP ' + r.status)
      const blob = await r.blob(); let name = f.name; const count = usedNames.get(name) || 0
      if (count > 0) { const dot = name.lastIndexOf('.'); const base = dot > 0 ? name.substring(0, dot) : name; const ext = dot > 0 ? name.substring(dot) : ''; name = base + '_' + f.txnId + ext }
      usedNames.set(f.name, count + 1); zip.file(name, blob); return true
    } catch { return false }
  }))
  if (!results.filter(Boolean).length) { alert('Αποτυχία λήψης αρχείων.'); return }
  const zipBlob = await zip.generateAsync({ type: 'blob' })
  const url = URL.createObjectURL(zipBlob), a = document.createElement('a')
  a.href = url; a.download = zipName; document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url)
}
const today = () => new Date().toISOString().split('T')[0]
async function downloadSectionFiles(k) { await zipTransactionFiles(sections[k], `Report_${k}_${today()}.zip`) }
async function downloadAllFiles() { await zipTransactionFiles([...sections.income, ...sections.expense], `Report_all_${today()}.zip`) }

/* ── errors ── */
function mapErr(e, fallback) {
  const s = e.response?.status
  if (s === 400) return 'Μη έγκυρα δεδομένα: ' + (e.response?.data?.message || e.response?.data?.reason || 'ελέγξτε τις κινήσεις')
  if (s === 403) return 'Δεν έχετε δικαίωμα για αυτή την ενέργεια.'
  return fallback
}

/* ── loaders ── */
async function loadTransactions() {
  try {
    const res = await api.get('/api/transactions', { params: { entityId: entityId(), page: 0, perPage: 9999 } })
    const data = res.data?.data || res.data || []
    allTransactions.value = Array.isArray(data) ? data : []
  } catch { allTransactions.value = [] }
}
async function loadConfig() {
  try {
    const res = await api.get('/api/config/items', { params: { entityId: entityId() } })
    const items = res.data?.data || []
    categoriesList.value = items.filter(i => i.configType === 'category' && i.isActive !== false)
  } catch { categoriesList.value = [] }
}
async function loadDispatchIndex() {
  if (!canDispatch.value) { dispatchIndex.value = []; return }
  try {
    const list = await listDispatches()
    const detailed = await Promise.all(list.map(async (d) => {
      try { const full = await getDispatch(d.id); return { ...d, transactionIds: full?.transactionIds || [] } }
      catch { return { ...d, transactionIds: [] } }
    }))
    dispatchIndex.value = detailed
  } catch { dispatchIndex.value = [] }
}
async function reloadAll() { await loadTransactions(); loadConfig(); await loadDispatchIndex() }

let onEntity
onMounted(async () => {
  await reloadAll()
  onEntity = () => { reloadAll() }
  window.addEventListener('entity-changed', onEntity)
})
onBeforeUnmount(() => { if (onEntity) window.removeEventListener('entity-changed', onEntity) })
</script>

<template>
  <div class="rb-v4" :data-theme="theme">
    <div class="wrap">
      <!-- topbar (no brand — sidebar covers it) -->
      <div class="topbar">
        <div class="navtabs">
          <button class="navtab" :class="{ on: activeTab === 'report' }" @click="go('report')">Report Builder</button>
          <button v-if="canDispatch" class="navtab" :class="{ on: activeTab === 'arc' }" @click="go('arc')">
            Αποστολές<span class="n">{{ dispatchIndex.length }}</span></button>
        </div>
        <div class="topright">
          <button class="themebtn" @click="toggleTheme" :aria-pressed="theme === 'light'">
            {{ theme === 'dark' ? '☀️ Ανοιχτό' : '🌙 Σκούρο' }}</button>
        </div>
      </div>

      <!-- ══ REPORT BUILDER ══ -->
      <div v-show="activeTab === 'report'">
        <div class="head-card">
          <div class="field grow"><label for="rtitle">Τίτλος report</label>
            <input type="text" id="rtitle" v-model="reportTitle" placeholder="π.χ. Έξοδα Ιουλίου προς απόδοση"></div>
          <div class="field grow"><label for="rdesc">Περιγραφή <span style="text-transform:none">(προαιρετικά)</span></label>
            <input type="text" id="rdesc" v-model="reportDesc" placeholder="Σύντομη περιγραφή…"></div>
        </div>

        <div class="toolbar">
          <button class="btn btn-green" @click="openPicker('income')">+ Έσοδα</button>
          <button class="btn btn-red" @click="openPicker('expense')">+ Έξοδα</button>
          <button class="btn" @click="toggleSummary">{{ showSummary ? '− Σύνοψη' : '+ Σύνοψη' }}</button>
          <button v-if="canDispatch" class="btn" :disabled="!totalCount" @click="preview">📄 Προεπισκόπηση PDF</button>
          <button v-if="canDispatch" class="btn btn-sent" :disabled="!totalCount" @click="openDispatch">✉ Αποστολή &amp; αρχειοθέτηση</button>
          <button class="btn" :disabled="!reportHasDocs" @click="downloadAllFiles"
                  title="ZIP με τα παραστατικά όλων των κινήσεων του report">📥 Λήψη όλων των αρχείων</button>
          <span class="count-hint">{{ hint }}</span>
        </div>

        <div>
          <div v-for="s in sectionsView" :key="s.key" class="section" :class="s.key">
            <div class="sec-head">
              <div class="sec-title">{{ s.key === 'income' ? '▲' : '▼' }} {{ s.name }}
                <span class="sec-badge">{{ s.rows.length }} {{ s.rows.length === 1 ? 'κίνηση' : 'κινήσεις' }}</span>
                <span v-if="s.un" class="sec-badge warn">{{ s.un }} μη απεσταλμένα</span>
              </div>
              <div class="sec-actions">
                <button class="mini add" @click="openPicker(s.key)">+ Προσθήκη</button>
                <button v-if="s.rows.length" class="mini" @click="downloadSectionFiles(s.key)">📥 Αρχεία</button>
                <button v-if="s.rows.length" class="mini clear" @click="clearSection(s.key)">Καθαρισμός ενότητας</button>
              </div>
              <div class="sec-total">{{ eur(s.total) }}</div>
            </div>
            <table v-if="s.rows.length">
              <thead><tr><th class="c-id">#ID</th><th class="c-date">ΗΜ/ΝΙΑ</th><th>ΠΕΡΙΓΡΑΦΗ</th>
                <th class="c-stat">ΚΑΤΑΣΤΑΣΗ</th><th class="c-send">ΑΠΟΣΤΟΛΗ</th><th class="c-amt">ΠΟΣΟ</th><th class="c-x"></th></tr></thead>
              <tbody>
                <tr v-for="t in s.rows" :key="t.id">
                  <td class="c-id">{{ t.id }}</td><td class="c-date">{{ t.d }}</td>
                  <td>{{ t.t }} <span class="tag">{{ t.cat }}</span></td>
                  <td class="c-stat"><span class="st" :class="t.paid ? 'paid' : 'due'">{{ t.paid ? '✓ ΕΞΟΦΛΗΜΕΝΗ' : '⏳ ΕΚΚΡΕΜΗΣ' }}</span></td>
                  <td class="c-send">
                    <template v-if="!sentInfo(t.id).sent"><span class="st no">○ ΜΗ ΑΠΕΣΤΑΛΜΕΝΟ</span></template>
                    <template v-else>
                      <span class="st yes clickable" :class="{ multi: sentInfo(t.id).count > 1 }"
                            :title="sentInfo(t.id).title" @click="openDetail(sentInfo(t.id).first.id)">
                        ✉ ΑΠΕΣΤΑΛΜΕΝΟ<template v-if="sentInfo(t.id).count > 1"> {{ sentInfo(t.id).count }}×</template>
                        <small>{{ grShort(sentInfo(t.id).first.sentDate) }}</small></span>
                      <span class="rcp">{{ sentInfo(t.id).first.title }} → {{ sentInfo(t.id).first.recipient }}</span>
                    </template>
                  </td>
                  <td class="c-amt" :class="t.a < 0 ? 'amt-neg' : 'amt-pos'">{{ eur(t.a) }}</td>
                  <td class="c-x"><button class="xbtn" :aria-label="'Αφαίρεση ' + t.id" title="Αφαίρεση από το report" @click="removeRow(s.key, t.id)">✕</button></td>
                </tr>
              </tbody>
            </table>
            <div v-else class="empty"><b>Καμία κίνηση ακόμα</b>Πάτα «+ Προσθήκη» και διάλεξε ελεύθερα ό,τι θέλεις να στείλεις.</div>
          </div>

          <!-- Σύνοψη -->
          <div v-if="showSummary" class="section summary">
            <div class="sec-head"><div class="sec-title">▼ Σύνοψη</div>
              <div class="sec-actions"><button class="mini clear" @click="toggleSummary">Αφαίρεση ενότητας</button></div>
              <div class="sec-total" :style="{ color: summary.net < 0 ? 'var(--red)' : 'var(--green)' }">{{ eur(summary.net) }}</div></div>
            <div class="sum-body">
              <div class="sum-row"><span class="lbl">Σύνολο Εισπράξεων</span>
                <span class="cnt">{{ summary.inc.length }} {{ summary.inc.length === 1 ? 'κίνηση' : 'κινήσεις' }}</span>
                <span class="val amt-pos">{{ eur(summary.sIn) }}</span></div>
              <div class="sum-row"><span class="lbl">Σύνολο Εξόδων</span>
                <span class="cnt">{{ summary.exp.length }} {{ summary.exp.length === 1 ? 'κίνηση' : 'κινήσεις' }}</span>
                <span class="val amt-neg">{{ eur(summary.sEx) }}</span></div>
              <div class="sum-row net"><span class="lbl">Καθαρό Υπόλοιπο</span>
                <span class="val" :class="summary.net < 0 ? 'amt-neg' : 'amt-pos'">{{ eur(summary.net) }}</span></div>
              <div class="sum-block"><h4>Αποστολή</h4>
                <div class="sum-row sub"><span class="lbl">✉ Απεσταλμένα</span>
                  <span class="val" style="color:var(--sent)">{{ summary.nS }} από {{ summary.allLen }}</span></div>
                <div class="sum-row sub"><span class="lbl">○ Μη απεσταλμένα</span>
                  <span class="val" :style="{ color: (summary.allLen - summary.nS) ? 'var(--gold)' : 'var(--muted)' }">{{ summary.allLen - summary.nS }} από {{ summary.allLen }}</span></div>
                <div class="sum-row sub" style="border-top:1px solid var(--line-soft);margin-top:6px;padding-top:9px">
                  <span class="lbl">Έξοδα απεσταλμένα</span><span class="val" style="color:var(--sent)">{{ plain(summary.eSsum) }}</span></div>
                <div class="sum-row sub"><span class="lbl">Έξοδα μη απεσταλμένα</span>
                  <span class="val" :style="{ color: summary.eNlen ? 'var(--gold)' : 'var(--muted)' }">{{ plain(summary.eNsum) }}</span></div></div>
              <div class="sum-block"><h4>Πληρωμή</h4>
                <div class="sum-row sub"><span class="lbl">⏳ Εκκρεμείς πληρωμές στο report</span>
                  <span class="val" :style="{ color: summary.dueLen ? 'var(--gold)' : 'var(--muted)' }">{{ plain(summary.dueSum) }}</span></div></div>
            </div>
          </div>
        </div>

        <div class="legend"><h3>Πώς διαβάζεται</h3><ul>
          <li><span class="st inrep">✓ ΣΤΟ REPORT</span> είναι ήδη μέσα στο τρέχον report</li>
          <li><span class="st yes">✉ ΑΠΕΣΤΑΛΜΕΝΟ <small>31/07/26</small></span> έχει σταλεί παλαιότερα — δίπλα ο τίτλος και ο παραλήπτης</li>
          <li><span class="st yes multi">✉ ΑΠΕΣΤΑΛΜΕΝΟ 2×</span> σε περισσότερες από μία αποστολές — κλικ για ιστορικό</li>
          <li><span class="st no">○ ΜΗ ΑΠΕΣΤΑΛΜΕΝΟ</span> δεν έχει φύγει ποτέ</li>
          <li style="margin-top:12px;padding-top:10px;border-top:1px solid var(--line)">
            <b>Δύο ανεξάρτητοι άξονες:</b> <span class="st paid">✓ ΕΞΟΦΛΗΜΕΝΗ</span> / <span class="st due">⏳ ΕΚΚΡΕΜΗΣ</span>
            αφορούν την <b>πληρωμή</b>· το ΑΠΕΣΤΑΛΜΕΝΟ αφορά την <b>αποστολή</b>.</li>
        </ul></div>
      </div>

      <!-- ══ ΑΠΟΣΤΟΛΕΣ ══ -->
      <div v-show="activeTab === 'arc'">
        <DispatchArchiveView :dispatches="archiveList" @open-detail="openDetail" />
      </div>
    </div>

    <!-- PICKER -->
    <div class="overlay" :class="{ open: picker.open }" role="dialog" aria-modal="true">
      <div class="modal">
        <div class="m-head"><h2>Επιλογή κινήσεων</h2>
          <span class="sec-badge">Έσοδα → Εισπράξεις · Έξοδα → Έξοδα (αυτόματα)</span>
          <button class="m-close" @click="closePicker" aria-label="Κλείσιμο">✕</button></div>
        <div class="m-filters">
          <div class="field search-field"><label for="q">Αναζήτηση</label>
            <input type="text" id="q" v-model="picker.q" placeholder="Περιγραφή ή ID κίνησης…"></div>
          <div class="field"><label for="fcat">Κατηγορία</label>
            <select id="fcat" v-model="picker.cat"><option value="">Όλες οι κατηγορίες</option>
              <option v-for="c in pickerCats" :key="c" :value="c">{{ c }}</option></select></div>
          <div class="field"><label for="fused">Στο report</label>
            <select id="fused" v-model="picker.used"><option value="all">Όλες</option>
              <option value="free">Όσες δεν είναι μέσα</option><option value="used">Όσες είναι ήδη μέσα</option></select></div>
          <div class="field"><label for="fsent">Αποστολή</label>
            <select id="fsent" v-model="picker.sent"><option value="all">Όλες</option>
              <option value="no">Μόνο μη απεσταλμένα</option><option value="yes">Μόνο απεσταλμένα</option></select></div>
          <div class="field"><label for="fpay">Πληρωμή</label>
            <select id="fpay" v-model="picker.pay"><option value="all">Όλες</option>
              <option value="paid">Εξοφλημένες</option><option value="due">Εκκρεμείς</option></select></div>
        </div>
        <div class="tabs">
          <button v-for="tb in [['all','Όλες'],['in','Έσοδα'],['out','Έξοδα']]" :key="tb[0]"
                  class="tab" :class="{ active: picker.tab === tb[0] }" @click="picker.tab = tb[0]">
            {{ tb[1] }}<span class="n">{{ pickCounts[tb[0]] }}</span></button>
        </div>
        <div class="m-body"><table><thead><tr>
          <th class="c-chk"></th><th class="c-id">#ID</th><th class="c-date">ΗΜ/ΝΙΑ</th><th>ΠΕΡΙΓΡΑΦΗ</th>
          <th class="c-stat">ΚΑΤΑΣΤΑΣΗ</th><th class="c-send">ΑΠΟΣΤΟΛΗ</th><th class="c-amt">ΠΟΣΟ</th></tr></thead>
          <tbody>
            <tr v-for="t in pool" :key="t.id" class="pick-row" :class="{ used: !!where(t.id), checked: isChecked(t.id) }" @click="toggle(t.id)">
              <td class="c-chk"><div class="cbox">{{ (where(t.id) || isChecked(t.id)) ? '✓' : '' }}</div></td>
              <td class="c-id">{{ t.id }}</td><td class="c-date">{{ t.d }}</td>
              <td><div class="desc"><span>{{ t.t }}</span><span class="tag">{{ t.cat }}</span>
                <span v-if="where(t.id)" class="st inrep">✓ ΣΤΟ REPORT · {{ where(t.id) === 'income' ? 'Εισπράξεις' : 'Έξοδα' }}</span></div></td>
              <td class="c-stat"><span class="st" :class="t.paid ? 'paid' : 'due'">{{ t.paid ? '✓ ΕΞΟΦΛΗΜΕΝΗ' : '⏳ ΕΚΚΡΕΜΗΣ' }}</span></td>
              <td class="c-send">
                <template v-if="!sentInfo(t.id).sent"><span class="st no">○ ΜΗ ΑΠΕΣΤΑΛΜΕΝΟ</span></template>
                <template v-else><span class="st yes" :class="{ multi: sentInfo(t.id).count > 1 }" :title="sentInfo(t.id).title">
                  ✉ ΑΠΕΣΤΑΛΜΕΝΟ<template v-if="sentInfo(t.id).count > 1"> {{ sentInfo(t.id).count }}×</template>
                  <small>{{ grShort(sentInfo(t.id).first.sentDate) }}</small></span></template>
              </td>
              <td class="c-amt" :class="t.a < 0 ? 'amt-neg' : 'amt-pos'">{{ eur(t.a) }}</td>
            </tr>
            <tr v-if="pool.length === 0"><td colspan="7"><div class="empty"><b>Καμία κίνηση δεν ταιριάζει</b>Άλλαξε τα φίλτρα.</div></td></tr>
          </tbody></table></div>
        <div class="m-foot"><span>Επιλεγμένες: <b style="font-family:var(--mono);color:var(--cyan);font-size:17px">{{ selIds.length }}</b></span>
          <div class="foot-right"><button class="btn" @click="selectAllVisible">Επιλογή όλων στη λίστα</button>
            <button class="btn" @click="closePicker">Άκυρο</button>
            <button class="btn btn-primary" :disabled="!selIds.length" @click="addSelected">{{ addBtnLabel }}</button></div></div>
      </div>
    </div>

    <!-- DISPATCH -->
    <div class="overlay" :class="{ open: send.open }" role="dialog" aria-modal="true">
      <div class="modal small">
        <div class="m-head"><h2>Αποστολή &amp; αρχειοθέτηση</h2>
          <button class="m-close" @click="send.open = false" aria-label="Κλείσιμο">✕</button></div>
        <div class="m-pad">
          <div class="field"><label for="dtitle">Τίτλος αποστολής</label>
            <input type="text" id="dtitle" v-model="send.title" style="width:100%"></div>
          <div class="field"><label for="rcp">Παραλήπτης</label>
            <input type="text" id="rcp" v-model="send.rcp" list="rcps" placeholder="π.χ. Λογιστήριο" style="width:100%">
            <datalist id="rcps"><option v-for="r in recipients" :key="r" :value="r"></option></datalist></div>
          <div class="field"><label>Τι περιλαμβάνει</label>
            <div class="scope">
              <label><input type="checkbox" v-model="send.scEx">Έξοδα <span class="amt">{{ scExAmt }}</span></label>
              <label><input type="checkbox" v-model="send.scIn">Εισπράξεις <span class="amt">{{ scInAmt }}</span></label>
              <label><input type="checkbox" v-model="send.includeDocs">Να συμπεριληφθούν τα παραστατικά (ZIP)</label>
            </div></div>
          <div class="warn" v-html="sendSummaryText.html"></div>
          <div class="field"><label for="sdate">Ημερομηνία αποστολής</label>
            <input type="date" id="sdate" v-model="send.date" style="width:100%"></div>
          <div class="field"><label for="snote">Σημείωση <span style="text-transform:none">(προαιρετικά)</span></label>
            <textarea id="snote" v-model="send.note" placeholder="π.χ. με email, συνημμένα 4 παραστατικά"></textarea></div>
        </div>
        <div class="m-foot"><button class="btn" @click="send.open = false" :disabled="send.busy">Άκυρο</button>
          <div class="foot-right"><button class="btn btn-sent" :disabled="send.busy" @click="confirmDispatch">
            {{ send.busy ? 'Αποστολή…' : 'Δημιουργία PDF &amp; αρχειοθέτηση' }}</button></div></div>
      </div>
    </div>

    <!-- DISPATCH DETAIL -->
    <div class="overlay" :class="{ open: detail.open }" role="dialog" aria-modal="true">
      <div class="modal" v-if="detailData">
        <div class="m-head"><h2>{{ detailData.d.title }}</h2>
          <button class="m-close" @click="detail.open = false" aria-label="Κλείσιμο">✕</button></div>
        <div class="m-pad" style="padding-bottom:0"><div class="sum-block" style="margin-top:0">
          <div class="sum-row sub" style="padding-left:0"><span class="lbl" style="padding-left:0">✉ Παραλήπτης</span>
            <span class="val" style="color:var(--sent)">{{ detailData.d.recipient }}</span></div>
          <div class="sum-row sub" style="padding-left:0"><span class="lbl" style="padding-left:0">📅 Ημερομηνία αποστολής</span>
            <span class="val">{{ grFull(detailData.d.sentDate) }}</span></div>
          <div class="sum-row sub" v-if="detailData.d.note" style="padding-left:0"><span class="lbl" style="padding-left:0">Σημείωση</span>
            <span class="val" style="font-weight:400;font-family:inherit;font-size:13.5px">{{ detailData.d.note }}</span></div>
          <div class="sum-row sub" style="padding-left:0;border-top:1px solid var(--line-soft);margin-top:6px;padding-top:9px">
            <span class="lbl" style="padding-left:0">Σύνολα</span>
            <span class="val"><span v-if="detailData.sIn" class="amt-pos">{{ plain(detailData.sIn) }}</span><span v-if="detailData.sIn"> · </span><span class="amt-neg">{{ plain(Math.abs(detailData.sEx)) }}</span></span></div>
        </div></div>
        <div class="m-body"><table><thead><tr>
          <th class="c-id">#ID</th><th class="c-date">ΗΜ/ΝΙΑ</th><th>ΠΕΡΙΓΡΑΦΗ</th><th class="c-stat">ΚΑΤΑΣΤΑΣΗ</th><th class="c-amt">ΠΟΣΟ</th></tr></thead>
          <tbody><tr v-for="t in detailData.rows" :key="t.id">
            <td class="c-id">{{ t.id }}</td><td class="c-date">{{ t.d }}</td>
            <td>{{ t.t }} <span class="tag">{{ t.cat }}</span></td>
            <td class="c-stat"><span class="st" :class="t.paid ? 'paid' : 'due'">{{ t.paid ? '✓ ΕΞΟΦΛΗΜΕΝΗ' : '⏳ ΕΚΚΡΕΜΗΣ' }}</span></td>
            <td class="c-amt" :class="t.a < 0 ? 'amt-neg' : 'amt-pos'">{{ eur(t.a) }}</td></tr></tbody></table></div>
        <div class="m-foot">
          <button v-if="canDispatch" class="btn btn-red" @click="doDelete">🗑 Διαγραφή αποστολής</button>
          <div class="foot-right"><button class="btn" @click="detail.open = false">Κλείσιμο</button>
            <button v-if="detailData.d.hasPdf" class="btn btn-primary" @click="openStoredPdf(detailData.d.id)">📄 Άνοιγμα PDF</button></div></div>
      </div>
    </div>

    <!-- PDF PREVIEW (real backend PDF) -->
    <PdfPreviewModal :blob="previewBlob" :title="previewTitle" @close="previewBlob = null" />

    <!-- TOAST -->
    <div class="toast" :class="{ show: toastState.show }" role="status">
      <div class="toast-txt">{{ toastState.a }}<small>{{ toastState.b }}</small></div>
      <button v-if="toastState.hasAct" class="tbtn" @click="toastAct">{{ toastState.actLabel }}</button>
    </div>
  </div>
</template>
