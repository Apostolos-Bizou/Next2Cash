<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import api from '@/api'

/* ── Entity mapping ── */
const ENTITY_MAP = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14',
}

/* ── State ── */
const reportTitle = ref('')
const reportDesc = ref('')
const selectedCategory = ref('all')
const selectedSubcategory = ref('all')
const dateFrom = ref('')
const dateTo = ref('')
const selectedMethod = ref('all')
const displayMode = ref('both')
const groupByCategory = ref(false)
const loading = ref(false)

const sections = ref([])
const allTransactions = ref([])

// Dynamic categories/subcategories from API
const categoriesList = ref([])
const subcategoriesList = ref([])

// Right panel
const itemFilter = ref('')
const itemFilterTab = ref('all')
const selectedItems = ref([])
const isPanelOpen = ref(true)

// Display mode options
const displayModes = [
  { value: 'both',    label: 'Έσοδα + Έξοδα' },
  { value: 'income',  label: 'Μόνο Έσοδα' },
  { value: 'expense', label: 'Μόνο Έξοδα' },
]

// Payment methods (static — same as legacy)
const methods = [
  { value: 'all',            label: 'Όλες' },
  { value: 'Μετρητά',       label: 'Μετρητά' },
  { value: 'Τράπεζα',       label: 'Τράπεζα' },
  { value: 'Απόδειξη',  label: 'Απόδειξη' },
  { value: 'HSBC',           label: 'HSBC' },
  { value: 'Πειραιώς',  label: 'Πειραιώς' },
  { value: 'Πορτοφόλι', label: 'Πορτοφόλι' },
  { value: 'Revolut GBP',   label: 'Revolut GBP' },
  { value: 'Revolut USD',   label: 'Revolut USD' },
  { value: 'Revolut EUR',   label: 'Revolut EUR' },
]

/* ── Fetch transactions ── */
async function loadTransactions() {
  const entityKey = localStorage.getItem('n2c_entity') || 'next2me'
  const entityId = ENTITY_MAP[entityKey]
  if (!entityId) return

  loading.value = true
  try {
    const res = await api.get('/api/transactions', {
      params: { entityId, page: 0, perPage: 9999 }
    })
    const data = res.data?.data || res.data || []
    allTransactions.value = (Array.isArray(data) ? data : []).filter(
      t => (t.recordStatus || 'active') === 'active'
    )
  } catch (err) {
    console.error('ReportBuilder: failed to load transactions', err)
    allTransactions.value = []
  } finally {
    loading.value = false
  }
}

/* ── Fetch categories from config ── */
async function loadConfig() {
  const entityKey = localStorage.getItem('n2c_entity') || 'next2me'
  const entityId = ENTITY_MAP[entityKey]
  if (!entityId) return

  try {
    const res = await api.get('/api/config', { params: { entityId } })
    const items = res.data || []
    categoriesList.value = items.filter(i => i.configType === 'category' && i.isActive !== false)
    subcategoriesList.value = items.filter(i => i.configType === 'subcategory' && i.isActive !== false)
  } catch (err) {
    console.error('ReportBuilder: failed to load config', err)
  }
}

/* ── Filtered items for right panel ── */
const panelItems = computed(() => {
  let items = allTransactions.value.map(t => ({
    id: t.id,
    date: t.docDate || '',
    desc: t.description || t.comments || '',
    category: t.category || '',
    subcategory: t.subcategory || '',
    amount: t.type === 'income' ? Number(t.amount) || 0 : -(Number(t.amount) || 0),
    type: t.type,
    paymentMethod: t.paymentMethod || '',
    paymentStatus: t.paymentStatus || 'unpaid',
    amountPaid: Number(t.amountPaid) || 0,
    amountRemaining: Number(t.amountRemaining) || 0,
    paymentDate: t.paymentDate || '',
  }))

  // Apply sidebar filters
  if (selectedCategory.value !== 'all') {
    items = items.filter(i => i.category === selectedCategory.value)
  }
  if (selectedSubcategory.value !== 'all') {
    items = items.filter(i => i.subcategory === selectedSubcategory.value)
  }
  if (dateFrom.value) {
    items = items.filter(i => i.date >= dateFrom.value)
  }
  if (dateTo.value) {
    items = items.filter(i => i.date <= dateTo.value)
  }
  if (selectedMethod.value !== 'all') {
    items = items.filter(i => i.paymentMethod === selectedMethod.value)
  }
  if (displayMode.value === 'income') {
    items = items.filter(i => i.type === 'income')
  } else if (displayMode.value === 'expense') {
    items = items.filter(i => i.type === 'expense')
  }

  // Sort by date desc
  items.sort((a, b) => (b.date || '').localeCompare(a.date || ''))

  return items
})

const filteredItems = computed(() => {
  let items = panelItems.value

  // Search filter
  if (itemFilter.value) {
    const q = itemFilter.value.toLowerCase()
    items = items.filter(i => i.desc.toLowerCase().includes(q) || String(i.id).includes(q))
  }

  // Tab filter
  if (itemFilterTab.value === 'income')  items = items.filter(i => i.amount > 0)
  if (itemFilterTab.value === 'expense') items = items.filter(i => i.amount < 0)
  if (itemFilterTab.value === 'urgent')  items = items.filter(i => i.paymentStatus === 'urgent' || i.paymentStatus === 'unpaid')

  return items
})

/* ── Computed ── */
const sectionCount = computed(() => sections.value.reduce((s, sec) => s + sec.items.length, 0))
const totalIncome = computed(() => sections.value.filter(s => s.type === 'income').flatMap(s => s.items).reduce((s, i) => s + Math.abs(i.amount), 0))
const totalExpense = computed(() => sections.value.filter(s => s.type === 'expense').flatMap(s => s.items).reduce((s, i) => s + Math.abs(i.amount), 0))

const isItemSelected = (id) => selectedItems.value.includes(id)

const toggleItem = (item) => {
  if (isItemSelected(item.id)) {
    selectedItems.value = selectedItems.value.filter(id => id !== item.id)
  } else {
    selectedItems.value.push(item.id)
  }
}

const addSelectedToReport = () => {
  if (selectedItems.value.length === 0) return
  if (sections.value.length === 0) {
    alert('Προσθέσε πρώτα ένα section (+ Έσοδα ή + Έξοδα)')
    return
  }
  const lastSection = sections.value[sections.value.length - 1]
  const itemsToAdd = panelItems.value.filter(i => selectedItems.value.includes(i.id))
  itemsToAdd.forEach(item => {
    if (!lastSection.items.find(i => i.id === item.id)) {
      lastSection.items.push({ ...item })
    }
  })
  selectedItems.value = []
}

/* ── Section Actions ── */
const addSection = (type) => {
  sections.value.push({
    id: Date.now(),
    type,
    label: type === 'income' ? 'Εισπράξεις' : type === 'expense' ? 'Έξοδα' : 'Σύνοψη',
    items: []
  })
}

const removeSection = (id) => {
  sections.value = sections.value.filter(s => s.id !== id)
}

/* ── Apply Filters (re-filter panel) ── */
const applyFilters = () => {
  // Filters are reactive via computed — this is just UX feedback
  selectedItems.value = []
}

/* ── Export stubs (will be activated in Step 2) ── */
const exportPDF = () => {
  if (sectionCount.value === 0) {
    alert('Δεν υπάρχουν κινήσεις στο report')
    return
  }
  // Collect ALL transactions from ALL sections
  const seenIds = new Set()
  const allTxns = []
  sections.value.forEach(sec => {
    sec.items.forEach(t => {
      const sid = String(t.id)
      if (!seenIds.has(sid)) {
        seenIds.add(sid)
        allTxns.push(t)
      }
    })
  })
  allTxns.sort((a, b) => (b.date || '').localeCompare(a.date || ''))

  const title = reportTitle.value || 'Custom Report'
  const today = new Date().toLocaleDateString('el-GR')
  const entityKey = localStorage.getItem('n2c_entity') || 'next2me'

  // Calculate KPIs
  const incData = allTxns.filter(t => t.type === 'income')
  const expData = allTxns.filter(t => t.type !== 'income')
  const totalInc = incData.reduce((s, t) => s + Math.abs(t.amount), 0)
  const totalExp = expData.reduce((s, t) => s + Math.abs(t.amount), 0)
  const expPaid = expData.filter(t => t.paymentStatus === 'paid' || t.paymentStatus === 'received')
  const expPaidAmt = expPaid.reduce((s, t) => s + Math.abs(t.amount), 0)
  const expUnpaid = expData.filter(t => t.paymentStatus === 'unpaid' || t.paymentStatus === 'urgent')
  const expUnpaidAmt = expUnpaid.reduce((s, t) => s + Math.abs(t.amountRemaining || t.amount), 0)
  const kpiBalance = totalInc - expPaidAmt - expUnpaidAmt
  const kpiCash = kpiBalance - expUnpaidAmt

  const statusLabel = { unpaid: 'Απλήρωτη', urgent: '⚡ Εκκρεμής', partial: 'Μερ. Πληρωμένη', paid: 'Εξοφλημένη', received: 'Εισπράχθηκε' }
  const statusColor = { unpaid: '#e74c3c', urgent: '#ff6400', partial: '#f39c12', paid: '#27ae60', received: '#27ae60' }

  const fmtPdf = (n) => new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Math.abs(n))
  const dispDate = (d) => { if (!d) return '—'; const parts = d.split('-'); return parts.length === 3 ? parts[2] + '/' + parts[1] + '/' + parts[0] : d }

  const rows = allTxns.map(t => {
    const sc = statusColor[t.paymentStatus] || '#666'
    const sl = statusLabel[t.paymentStatus] || t.paymentStatus || ''
    const isInc = t.type === 'income'
    const paid = isInc ? Math.abs(t.amount) : (t.amountPaid || 0)
    const rem = isInc ? 0 : (t.amountRemaining || 0)
    return `<tr>
      <td style="font-family:monospace;color:#666;font-size:9px">${t.id}</td>
      <td style="white-space:nowrap">${dispDate(t.date)}</td>
      <td style="max-width:200px">${t.desc || ''}</td>
      <td style="white-space:nowrap">${t.category || ''}</td>
      <td style="white-space:nowrap">${t.paymentMethod || '—'}</td>
      <td style="text-align:right;white-space:nowrap;font-family:monospace">${fmtPdf(t.amount)} €</td>
      <td style="text-align:right;white-space:nowrap;font-family:monospace;color:#27ae60">${paid > 0 ? fmtPdf(paid) + ' €' : '—'}</td>
      <td style="text-align:right;white-space:nowrap;font-family:monospace;color:${rem > 0 ? '#e74c3c' : '#27ae60'}">${fmtPdf(rem)} €</td>
      <td style="white-space:nowrap;font-size:9px">${dispDate(t.paymentDate) || '—'}</td>
      <td><span style="background:${sc}22;color:${sc};border:1px solid ${sc}44;padding:2px 8px;border-radius:10px;font-size:8px;font-weight:700;white-space:nowrap">${sl}</span></td>
    </tr>`
  }).join('')

  const html = `<!DOCTYPE html><html><head><meta charset="UTF-8"><title>Καρτέλα ${title}</title>
  <style>
    @import url('https://fonts.googleapis.com/css2?family=Montserrat:wght@400;500;600;700;800&display=swap');
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:'Montserrat',Arial,sans-serif;font-size:11px;color:#1a1a2e;background:#fff;padding:28px 32px}
    .header{display:flex;justify-content:space-between;align-items:center;padding-bottom:16px;border-bottom:2px solid #162B40;margin-bottom:20px}
    .logo-area{display:flex;align-items:center;gap:14px}
    .logo-text{display:flex;flex-direction:column}
    .logo-name{font-size:16px;font-weight:800;color:#162B40;letter-spacing:2px}
    .logo-sub{font-size:8px;font-weight:500;color:#2E75B6;letter-spacing:3px;margin-top:1px}
    .header-right{text-align:right}
    .doc-title{font-size:13px;font-weight:700;color:#162B40;text-transform:uppercase;letter-spacing:1px}
    .doc-date{font-size:9px;color:#888;margin-top:3px}
    .supplier-section{background:#f0f4f8;border-left:4px solid #2E75B6;padding:12px 16px;border-radius:0 6px 6px 0;margin-bottom:18px}
    .supplier-label{font-size:8px;font-weight:600;color:#2E75B6;text-transform:uppercase;letter-spacing:.1em;margin-bottom:4px}
    .supplier-name{font-size:20px;font-weight:800;color:#162B40}
    .summary{display:flex;gap:0;margin-bottom:20px;border:1px solid #e0e6ed;border-radius:8px;overflow:hidden}
    .sum-item{flex:1;padding:12px 8px;text-align:center;border-right:1px solid #e0e6ed}
    .sum-item:last-child{border-right:none}
    .sum-label{font-size:8px;font-weight:600;color:#888;text-transform:uppercase;letter-spacing:.08em;margin-bottom:5px}
    .sum-value{font-size:15px;font-weight:800;color:#162B40}
    table{width:100%;border-collapse:collapse;font-size:10px}
    thead tr{background:#162B40;color:#fff}
    thead th{padding:9px 8px;text-align:left;font-weight:600;font-size:9px;text-transform:uppercase;letter-spacing:.05em;white-space:nowrap}
    tbody tr{border-bottom:1px solid #f0f0f0}
    tbody tr:nth-child(even){background:#fafbfc}
    td{padding:8px;vertical-align:middle}
    .footer{margin-top:24px;display:flex;justify-content:space-between;padding-top:12px;border-top:1px solid #e0e6ed;font-size:8px;color:#aaa}
    @media print{body{padding:15px}@page{margin:1cm;size:A4 landscape}}
  </style></head><body>
  <div class="header">
    <div class="logo-area">
      <div class="logo-text"><div class="logo-name">CashControl</div><div class="logo-sub">N e x t 2 M e</div></div>
    </div>
    <div class="header-right"><div class="doc-title">ΚΑΡΤΕΛΑ ΠΡΟΜΗΘΕΥΤΗ</div><div class="doc-date">Εκτυπώθηκε: ${today}</div></div>
  </div>
  <div class="supplier-section"><div class="supplier-label">Προμηθευτής / Αντισυμβαλλόμενος</div><div class="supplier-name">${title}</div></div>
  <div class="summary">
    <div class="sum-item"><div class="sum-label">Εισπράξεις</div><div class="sum-value" style="color:#27ae60">${fmtPdf(totalInc)} €<div style="font-size:9px;font-weight:400;color:#888;margin-top:2px">${incData.length} κινήσεις</div></div></div>
    <div class="sum-item"><div class="sum-label">Εξοφλημένες</div><div class="sum-value" style="color:#2E75B6">${fmtPdf(expPaidAmt)} €<div style="font-size:9px;font-weight:400;color:#888;margin-top:2px">${expPaid.length} κινήσεις</div></div></div>
    <div class="sum-item"><div class="sum-label">Υπόλοιπο</div><div class="sum-value" style="color:${kpiBalance>=0?'#27ae60':'#e74c3c'}">${kpiBalance>=0?'+':'−'} ${fmtPdf(kpiBalance)} €</div></div>
    <div class="sum-item"><div class="sum-label">Εκκρεμότητες</div><div class="sum-value" style="color:${expUnpaidAmt>0?'#ff6400':'#27ae60'}">${fmtPdf(expUnpaidAmt)} €<div style="font-size:9px;font-weight:400;color:#888;margin-top:2px">${expUnpaid.length} κινήσεις</div></div></div>
    <div class="sum-item" style="background:#f0faf4"><div class="sum-label" style="color:#27ae60">Ταμειακά Διαθέσιμα</div><div class="sum-value" style="color:${kpiCash>=0?'#27ae60':'#e74c3c'}">${kpiCash>=0?'+':'−'} ${fmtPdf(kpiCash)} €</div></div>
  </div>
  <table><thead><tr><th>ID</th><th>Ημ/νία</th><th>Περιγραφή</th><th>Κατηγορία</th><th>Μέθοδος</th><th style="text-align:right">Ποσό</th><th style="text-align:right">Πληρωμένο</th><th style="text-align:right">Υπόλοιπο</th><th>Ημ/νία Πληρωμής</th><th>Status</th></tr></thead><tbody>${rows}</tbody></table>
  <div class="footer"><span>CashControl · Next2Me Financial System</span><span>Σύνολο: ${allTxns.length} κινήσεις · ${today}</span></div>
  </body></html>`

  const w = window.open('', '_blank', 'width=1100,height=800')
  w.document.write(html)
  w.document.close()
  setTimeout(() => w.print(), 500)
}

const exportExcel = () => alert('Export Excel — θα συνδεθεί στη φάση 2')

const fmt = (n) => {
  const abs = Math.abs(n)
  const str = new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2 }).format(abs) + ' €'
  return n >= 0 ? '+ ' + str : '- ' + str
}

const fmtDate = (d) => {
  if (!d) return '—'
  const parts = d.split('-')
  return parts.length === 3 ? parts[2] + '/' + parts[1] + '/' + parts[0] : d
}

/* ── Lifecycle ── */
onMounted(() => {
  loadTransactions()
  loadConfig()
  window.addEventListener('storage', (e) => {
    if (e.key === 'n2c_entity') { loadTransactions(); loadConfig() }
  })
  window.addEventListener('entity-changed', () => { loadTransactions(); loadConfig() })
})
</script>

<template>
  <div class="rb-page">

    <!-- ── Layout ── -->
    <div class="rb-layout" :class="{ 'panel-open': isPanelOpen }">

      <!-- ── LEFT: Sidebar Filters ── -->
      <div class="rb-sidebar">
        <div class="sidebar-title">Custom Report Builder</div>

        <div class="form-group">
          <label>ΤΙΤΛΟΣ REPORT</label>
          <input v-model="reportTitle" class="rb-input" placeholder="π.χ. Απολογισμός Επενδυτή Α..." />
        </div>

        <div class="form-group">
          <label>ΠΕΡΙΓΡΑΦΗ</label>
          <textarea v-model="reportDesc" class="rb-input rb-textarea" placeholder="Σύντομη περιγραφή report..."></textarea>
        </div>

        <div class="sidebar-section-title">ΦΙΛΤΡΑ ΚΙΝΗΣΕΩΝ</div>

        <div class="form-group">
          <label>Κατηγορία</label>
          <select v-model="selectedCategory" class="rb-select">
            <option value="all">Όλες οι κατηγορίες</option>
            <option v-for="c in categoriesList" :key="c.id" :value="c.name">{{ c.name }}</option>
          </select>
        </div>

        <div class="form-group">
          <label>Υποκατηγορία</label>
          <select v-model="selectedSubcategory" class="rb-select">
            <option value="all">Όλες</option>
            <option v-for="s in subcategoriesList" :key="s.id" :value="s.name">{{ s.name }}</option>
          </select>
        </div>

        <div class="form-group">
          <label>Περίοδος</label>
          <div class="date-row">
            <input v-model="dateFrom" type="date" class="rb-input" />
            <input v-model="dateTo"   type="date" class="rb-input" />
          </div>
        </div>

        <div class="form-group">
          <label>Μέθοδος Πληρωμής</label>
          <select v-model="selectedMethod" class="rb-select">
            <option v-for="m in methods" :key="m.value" :value="m.value">{{ m.label }}</option>
          </select>
        </div>

        <div class="form-group">
          <label>Εμφάνιση</label>
          <select v-model="displayMode" class="rb-select">
            <option v-for="d in displayModes" :key="d.value" :value="d.value">{{ d.label }}</option>
          </select>
        </div>

        <button class="btn-apply-filters" @click="applyFilters">
          ▼ Εφαρμογή Φίλτρων
        </button>

        <div class="form-group toggle-row">
          <label>Ομαδοποίηση ανά κατηγορία</label>
          <div class="toggle-switch" :class="{ active: groupByCategory }" @click="groupByCategory = !groupByCategory">
            <div class="toggle-thumb"></div>
          </div>
        </div>
      </div>

      <!-- ── CENTER: Report Builder ── -->
      <div class="rb-center">

        <!-- Toolbar -->
        <div class="rb-toolbar">
          <div class="toolbar-left">
            <button class="btn-add-section income" @click="addSection('income')">+ Έσοδα</button>
            <button class="btn-add-section expense" @click="addSection('expense')">+ Έξοδα</button>
            <button class="btn-add-section neutral" @click="addSection('summary')">+ Σύνοψη</button>
          </div>
          <div class="toolbar-right">
            <span class="report-stats">{{ sections.length }} sections · {{ sectionCount }} κινήσεις</span>
          </div>
        </div>

        <!-- Sections -->
        <div class="sections-area">
          <div
            v-for="section in sections"
            :key="section.id"
            class="report-section"
            :class="section.type"
          >
            <!-- Section Header -->
            <div class="section-header">
              <div class="section-header-left">
                <span class="section-arrow">{{ section.type === 'income' ? '▲' : '▼' }}</span>
                <span class="section-label">{{ section.label }}</span>
              </div>
              <div class="section-header-right">
                <button class="btn-add-item">+ Προσθήκη</button>
                <span class="section-badge">
                  📥 {{ section.items.length }} αρχεία
                </span>
                <span class="section-total" :class="section.type === 'income' ? 'income-col' : 'expense-col'">
                  {{ section.type === 'income'
                    ? '+ ' + new Intl.NumberFormat('el-GR',{minimumFractionDigits:2}).format(section.items.reduce((s,i)=>s+i.amount,0)) + ' €'
                    : '- ' + new Intl.NumberFormat('el-GR',{minimumFractionDigits:2}).format(Math.abs(section.items.reduce((s,i)=>s+i.amount,0))) + ' €'
                  }}
                </span>
                <span class="section-arrow-btn">↑</span>
                <button class="btn-remove-section" @click="removeSection(section.id)">🗑</button>
              </div>
            </div>

            <!-- Section Table -->
            <table class="section-table">
              <thead>
                <tr>
                  <th>#ID</th>
                  <th>ΗΜ/ΝΙΑ</th>
                  <th>ΠΕΡΙΓΡΑΦΗ</th>
                  <th>ΚΑΤΗΓΟΡΙΑ</th>
                  <th class="num">ΠΟΣΟ</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in section.items" :key="item.id">
                  <td class="id-col">{{ item.id }}</td>
                  <td class="date-col">{{ fmtDate(item.date) }}</td>
                  <td class="desc-col">{{ item.desc }}</td>
                  <td><span class="cat-badge">{{ item.category }}</span></td>
                  <td class="num" :class="item.amount >= 0 ? 'income-col' : 'expense-col'">
                    {{ item.amount >= 0 ? '+ ' : '- ' }}{{ new Intl.NumberFormat('el-GR',{minimumFractionDigits:2}).format(Math.abs(item.amount)) }} €
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Footer -->
        <div class="rb-footer">
          <span class="footer-stats">{{ sectionCount }} κινήσεις σε {{ sections.length }} sections — Έτοιμο</span>
          <div class="footer-actions">
            <button class="btn-footer" @click="exportExcel">📊 Αρχεία</button>
            <button class="btn-footer" @click="exportExcel">⊞ Excel</button>
            <button class="btn-footer accent" @click="exportPDF">📄 PDF</button>
          </div>
        </div>

      </div>

      <!-- ── RIGHT: Items Panel ── -->
      <div class="rb-panel" v-if="isPanelOpen">
        <div class="panel-header">
          <span class="panel-title-text">ΚΙΝΗΣΕΙΣ</span>
          <span class="panel-count">{{ filteredItems.length.toLocaleString() }}</span>
          <button class="panel-close" @click="isPanelOpen = false">✕</button>
        </div>

        <!-- Panel Search -->
        <input v-model="itemFilter" class="panel-search" placeholder="Αναζήτηση περιγραφής, ID..." />

        <!-- Panel Tabs -->
        <div class="panel-tabs">
          <button :class="['panel-tab', { active: itemFilterTab === 'all' }]"     @click="itemFilterTab = 'all'">Όλα</button>
          <button :class="['panel-tab', { active: itemFilterTab === 'income' }]"  @click="itemFilterTab = 'income'">Έσοδα</button>
          <button :class="['panel-tab', { active: itemFilterTab === 'expense' }]" @click="itemFilterTab = 'expense'">Έξοδα</button>
          <button :class="['panel-tab', { active: itemFilterTab === 'urgent' }]"  @click="itemFilterTab = 'urgent'">Εκκρεμή</button>
        </div>

        <!-- Panel Items -->
        <div class="panel-items">
          <div
            v-for="item in filteredItems"
            :key="item.id"
            class="panel-item"
            :class="{ selected: isItemSelected(item.id) }"
            @click="toggleItem(item)"
          >
            <input type="checkbox" :checked="isItemSelected(item.id)" @click.stop="toggleItem(item)" />
            <div class="panel-item-info">
              <div class="panel-item-desc">{{ item.desc }}</div>
              <div class="panel-item-meta">{{ item.id }} · {{ fmtDate(item.date) }} · {{ item.category }}</div>
            </div>
            <div class="panel-item-amount" :class="item.amount >= 0 ? 'income-col' : 'expense-col'">
              {{ item.amount >= 0 ? '+' : '' }}{{ new Intl.NumberFormat('el-GR',{minimumFractionDigits:2}).format(item.amount) }} €
            </div>
          </div>
        </div>

        <!-- Add to Report Button -->
        <div class="panel-add-btn-wrap">
          <button
            class="btn-add-to-report"
            :class="{ disabled: selectedItems.length === 0 }"
            @click="addSelectedToReport"
          >
            ✚ Προσθήκη
            <span v-if="selectedItems.length > 0"> {{ selectedItems.length }} εγγραφών</span>
            <span v-else> στο Report</span>
          </button>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
.rb-page { background: #0d1e2e; min-height: 100vh; color: #c8d8e8; }

.rb-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  height: 100vh;
  overflow: hidden;
}
.rb-layout.panel-open {
  grid-template-columns: 260px 1fr 320px;
}

/* ── Sidebar ── */
.rb-sidebar {
  background: #1a2f45;
  border-right: 1px solid #223d57;
  padding: 20px 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sidebar-title { font-size: 0.95rem; font-weight: 700; color: #e0e6ed; margin-bottom: 4px; }
.sidebar-section-title { font-size: 0.68rem; color: #4FC3A1; font-weight: 700; letter-spacing: 0.1em; margin-top: 8px; }

.form-group { display: flex; flex-direction: column; gap: 5px; }
.form-group label { font-size: 0.72rem; color: #8899aa; font-weight: 600; letter-spacing: 0.05em; }
.rb-input {
  background: #152538; border: 1px solid #2a4a6a; color: #c8d8e8;
  padding: 8px 10px; border-radius: 6px; font-size: 0.82rem; outline: none; width: 100%; box-sizing: border-box;
}
.rb-input:focus { border-color: #4FC3A1; }
.rb-textarea { min-height: 60px; resize: vertical; }
.rb-select {
  appearance: none; background: #152538; border: 1px solid #2a4a6a; color: #c8d8e8;
  padding: 8px 10px; border-radius: 6px; font-size: 0.82rem; outline: none; cursor: pointer; width: 100%;
}
.date-row { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.btn-apply-filters {
  background: #29b6f6; border: none; color: #0d1e2e;
  padding: 10px; border-radius: 8px; font-size: 0.85rem; font-weight: 700;
  cursor: pointer; width: 100%; margin-top: 4px;
}
.toggle-row { flex-direction: row; align-items: center; justify-content: space-between; }
.toggle-switch {
  width: 40px; height: 22px; background: #2a4a6a; border-radius: 11px;
  cursor: pointer; position: relative; transition: background 0.2s;
}
.toggle-switch.active { background: #4FC3A1; }
.toggle-thumb {
  position: absolute; top: 3px; left: 3px;
  width: 16px; height: 16px; border-radius: 50%;
  background: #fff; transition: transform 0.2s;
}
.toggle-switch.active .toggle-thumb { transform: translateX(18px); }

/* ── Center ── */
.rb-center {
  display: flex; flex-direction: column;
  background: #0d1e2e; overflow: hidden;
}
.rb-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 20px; background: #1a2f45; border-bottom: 1px solid #223d57;
  flex-shrink: 0;
}
.toolbar-left { display: flex; gap: 8px; }
.btn-add-section {
  padding: 6px 16px; border: none; border-radius: 6px;
  font-size: 0.82rem; font-weight: 600; cursor: pointer;
}
.btn-add-section.income  { background: rgba(79,195,161,0.15); color: #4FC3A1; border: 1px solid #4FC3A1; }
.btn-add-section.expense { background: rgba(239,83,80,0.15);  color: #ef5350; border: 1px solid #ef5350; }
.btn-add-section.neutral { background: rgba(41,182,246,0.15); color: #29b6f6; border: 1px solid #29b6f6; }
.report-stats { font-size: 0.78rem; color: #8899aa; }

.sections-area { flex: 1; overflow-y: auto; padding: 16px 20px; display: flex; flex-direction: column; gap: 16px; }

/* Sections */
.report-section { background: #1a2f45; border-radius: 10px; overflow: hidden; }
.report-section.income { border-left: 3px solid #4FC3A1; }
.report-section.expense { border-left: 3px solid #ef5350; }

.section-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: #152538;
}
.section-header-left { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 0.88rem; }
.section-header-right { display: flex; align-items: center; gap: 10px; }
.section-arrow { font-size: 0.8rem; }
.income .section-arrow { color: #4FC3A1; }
.expense .section-arrow { color: #ef5350; }
.section-badge { background: #1a2f45; padding: 3px 10px; border-radius: 10px; font-size: 0.72rem; color: #8899aa; }
.section-total { font-family: monospace; font-weight: 700; font-size: 0.9rem; }
.section-arrow-btn { color: #8899aa; cursor: pointer; font-size: 0.85rem; }
.btn-add-item { background: rgba(79,195,161,0.12); border: 1px solid #4FC3A1; color: #4FC3A1; padding: 3px 10px; border-radius: 4px; font-size: 0.75rem; cursor: pointer; }
.btn-remove-section { background: none; border: none; color: #ef5350; cursor: pointer; font-size: 0.9rem; }

.section-table { width: 100%; border-collapse: collapse; font-size: 0.82rem; }
.section-table th { color: #6a8099; padding: 8px 14px; font-size: 0.68rem; font-weight: 600; letter-spacing: 0.06em; border-bottom: 1px solid #223d57; text-align: left; }
.section-table td { padding: 8px 14px; border-bottom: 1px solid #1e3448; }
.section-table tbody tr:hover { background: #1e3a52; }
.id-col { color: #8899aa; font-size: 0.78rem; }
.date-col { color: #8899aa; white-space: nowrap; font-size: 0.78rem; }
.desc-col { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cat-badge { background: #2a4a6a; padding: 2px 8px; border-radius: 4px; font-size: 0.72rem; }
.num { text-align: right; font-family: monospace; }
.income-col  { color: #4FC3A1; }
.expense-col { color: #ef5350; }

/* Footer */
.rb-footer {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 20px; background: #1a2f45; border-top: 1px solid #223d57;
  flex-shrink: 0;
}
.footer-stats { font-size: 0.78rem; color: #8899aa; }
.footer-actions { display: flex; gap: 8px; }
.btn-footer {
  background: #152538; border: 1px solid #2a4a6a; color: #c8d8e8;
  padding: 6px 14px; border-radius: 6px; font-size: 0.82rem; cursor: pointer;
}
.btn-footer.accent { background: #4FC3A1; border-color: #4FC3A1; color: #0d1e2e; font-weight: 700; }

/* ── Right Panel ── */
.rb-panel {
  background: #1a2f45; border-left: 1px solid #223d57;
  display: flex; flex-direction: column; overflow: hidden;
}
.panel-header {
  display: flex; align-items: center; gap: 8px;
  padding: 12px 16px; background: #152538; border-bottom: 1px solid #223d57;
  flex-shrink: 0;
}
.panel-title-text { font-size: 0.8rem; font-weight: 700; color: #e0e6ed; }
.panel-count { background: #29b6f6; color: #0d1e2e; padding: 1px 8px; border-radius: 10px; font-size: 0.7rem; font-weight: 700; }
.panel-close { background: none; border: none; color: #8899aa; cursor: pointer; margin-left: auto; font-size: 0.9rem; }
.panel-search {
  background: #152538; border: none; border-bottom: 1px solid #223d57;
  color: #c8d8e8; padding: 10px 16px; font-size: 0.82rem; outline: none; flex-shrink: 0;
}
.panel-search::placeholder { color: #4a6a88; }
.panel-tabs {
  display: flex; border-bottom: 1px solid #223d57; flex-shrink: 0;
}
.panel-tab {
  flex: 1; background: none; border: none; color: #8899aa;
  padding: 8px; font-size: 0.78rem; cursor: pointer; border-bottom: 2px solid transparent;
}
.panel-tab.active { color: #29b6f6; border-bottom-color: #29b6f6; }

.panel-items { overflow-y: auto; flex: 1; }
.panel-item {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 12px; border-bottom: 1px solid #1e3448; cursor: pointer;
}
.panel-item:hover { background: #1e3a52; }
.panel-item.selected { background: rgba(79,195,161,0.06); }
.panel-item input[type="checkbox"] { flex-shrink: 0; accent-color: #4FC3A1; }
.panel-item-info { flex: 1; min-width: 0; }
.panel-item-desc { font-size: 0.78rem; color: #c8d8e8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.panel-item-meta { font-size: 0.68rem; color: #8899aa; margin-top: 2px; }
.panel-item-amount { font-size: 0.78rem; font-weight: 600; font-family: monospace; white-space: nowrap; flex-shrink: 0; }
.panel-add-btn-wrap {
  padding: 12px;
  border-top: 1px solid #223d57;
  flex-shrink: 0;
}
.btn-add-to-report {
  width: 100%;
  background: #4FC3A1;
  border: none;
  color: #0d1e2e;
  padding: 10px;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
}
.btn-add-to-report:hover { background: #3dab8a; }
.btn-add-to-report.disabled { background: #2a4a6a; color: #8899aa; cursor: not-allowed; }
.btn-add-to-report.disabled:hover { background: #2a4a6a; }
</style>
