<template>
  <div class="budget-view">
    <!-- Header -->
    <div class="bv-header">
      <div>
        <div class="bv-title">Προϋπολογισμός</div>
        <div class="bv-sub">budget builder · στήσε στόχους ανά κατηγορία &amp; μήνα, σύγκρινε με τα πραγματικά</div>
      </div>
      <div class="bv-actions">
        <select v-model="selectedYear" class="bv-select">
          <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
        </select>
        <button v-if="activeTab === 'builder'" class="bv-btn" :disabled="seeding" @click="autoSeed">
          {{ seeding ? '...' : '✦ Auto-seed από πέρυσι' }}
        </button>
        <button v-if="activeTab === 'builder'" class="bv-btn" :disabled="seedingRecurring" @click="autoSeedFromRecurring" title="Γέμισε από επαναλαμβανόμενα έξοδα (μηνιαία × 12, ετήσια × 1)">
          {{ seedingRecurring ? '...' : '🔁 Auto-seed από Recurring' }}
        </button>
        <button v-if="activeTab === 'builder'" class="bv-btn bv-btn-primary" :disabled="saving" @click="saveBudget">
          {{ saving ? 'Αποθήκευση...' : '💾 Αποθήκευση' }}
        </button>
      </div>
    </div>

    <!-- Tabs -->
    <div class="bv-tabs">
      <button class="bv-tab" :class="{ active: activeTab === 'builder' }" @click="activeTab = 'builder'">Πίνακας Budget</button>
      <button class="bv-tab" :class="{ active: activeTab === 'vs' }" @click="activeTab = 'vs'">Budget vs Πραγματικά</button>
    </div>

    <div v-if="loading" class="bv-loading">Φόρτωση…</div>

    <!-- ─────────── TAB 1: BUILDER ─────────── -->
    <div v-else-if="activeTab === 'builder'">
      <!-- view toggle -->
      <div class="bv-viewtoggle">
        <span class="bv-vt-label">Προβολή:</span>
        <button class="bv-vt-btn" :class="{ active: viewMode === 'monthly' }" @click="viewMode = 'monthly'">Μηνιαία</button>
        <button class="bv-vt-btn" :class="{ active: viewMode === 'quarterly' }" @click="viewMode = 'quarterly'">Τριμηνιαία</button>
      </div>

      <!-- KPI cards -->
      <div class="bv-kpis">
        <div class="bv-kpi"><div class="bv-kpi-l">Σύνολο εσόδων</div><div class="bv-kpi-v bv-green">{{ fmt(totalIncome) }}</div></div>
        <div class="bv-kpi"><div class="bv-kpi-l">Σύνολο εξόδων</div><div class="bv-kpi-v bv-red">{{ fmt(totalExpense) }}</div></div>
        <div class="bv-kpi"><div class="bv-kpi-l">Καθαρό (budget)</div><div class="bv-kpi-v">{{ fmt(totalIncome - totalExpense) }}</div></div>
        <div class="bv-kpi"><div class="bv-kpi-l">Κατηγορίες</div><div class="bv-kpi-v">{{ allCategories.length }}</div></div>
      </div>

      <!-- EXPENSE + INCOME tables -->
      <div v-for="dir in ['expense', 'income']" :key="dir" class="bv-section">
        <div class="bv-section-head">{{ dir === 'expense' ? 'ΕΞΟΔΑ' : 'ΕΣΟΔΑ' }}</div>
        <div class="bv-table-wrap">
          <table class="bv-table">
            <thead>
              <tr>
                <th class="bv-cat-col">Κατηγορία / Υποκατηγορία</th>
                <template v-if="viewMode === 'monthly'">
                  <th v-for="(mn, mi) in MONTH_NAMES" :key="mi" class="bv-num">{{ mn }}</th>
                </template>
                <th v-for="q in [1,2,3,4]" :key="'q'+q" class="bv-num bv-q">Q{{ q }}</th>
                <th class="bv-num bv-year">Έτος</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="cat in categoriesFor(dir)" :key="dir + '|' + cat">
                <!-- category row -->
                <tr class="bv-cat-row">
                  <td class="bv-cat-col">
                    <span class="bv-chev" @click="toggleCat(dir, cat)">{{ isOpen(dir, cat) ? '▼' : '▶' }}</span>
                    {{ cat }}
                    <span class="bv-del" title="Διαγραφή κατηγορίας" @click="removeCat(dir, cat)">×</span>
                  </td>
                  <template v-if="viewMode === 'monthly'">
                    <td v-for="m in 12" :key="m" class="bv-num bv-bold">{{ fmtCell(catMonthTotal(dir, cat, m)) }}</td>
                  </template>
                  <td v-for="q in [1,2,3,4]" :key="'cq'+q" class="bv-num bv-bold bv-q">{{ fmtCell(catQuarterTotal(dir, cat, q)) }}</td>
                  <td class="bv-num bv-bold bv-year">{{ fmt(catYearTotal(dir, cat)) }}</td>
                </tr>
                <!-- subcategory rows -->
                <template v-if="isOpen(dir, cat)">
                  <tr v-for="sub in subcategoriesFor(dir, cat)" :key="dir + '|' + cat + '|' + sub" class="bv-sub-row">
                    <td class="bv-cat-col bv-sub-name">{{ sub || '(χωρίς υποκατηγορία)' }}<span class="bv-del" title="Διαγραφή υποκατηγορίας" @click="removeSub(dir, cat, sub)">×</span></td>
                    <template v-if="viewMode === 'monthly'">
                      <td v-for="m in 12" :key="m" class="bv-num">
                        <input type="text" class="bv-input" :value="getCell(dir, cat, sub, m)"
                               @input="setCell(dir, cat, sub, m, $event.target.value)" />
                      </td>
                    </template>
                    <td v-for="q in [1,2,3,4]" :key="'sq'+q" class="bv-num bv-q">{{ fmtCell(subQuarterTotal(dir, cat, sub, q)) }}</td>
                    <td class="bv-num bv-year">{{ fmtCell(subYearTotal(dir, cat, sub)) }}</td>
                  </tr>
                  <!-- add subcategory -->
                  <tr class="bv-add-row">
                    <td :colspan="viewMode === 'monthly' ? 17 : 6">
                      <span v-if="addSubFor !== dir + '|' + cat">
                        <button class="bv-mini-btn" @click="openAddSub(dir, cat)">+ υποκατηγορία</button>
                      </span>
                      <span v-else class="bv-add-inline">
                        <select v-model="addSubChoice" class="bv-select bv-select-sm">
                          <option value="">— επίλεξε —</option>
                          <option v-for="s in availableSubsFor(dir, cat)" :key="s" :value="s">{{ s }}</option>
                          <option value="__custom__">+ Νέα custom…</option>
                        </select>
                        <input v-if="addSubChoice === '__custom__'" v-model="addSubCustom" class="bv-input bv-input-wide" placeholder="όνομα υποκατηγορίας" />
                        <button class="bv-mini-btn" @click="confirmAddSub(dir, cat)">OK</button>
                        <button class="bv-mini-btn bv-ghost" @click="cancelAddSub">άκυρο</button>
                      </span>
                    </td>
                  </tr>
                </template>
              </template>

              <!-- total row -->
              <tr class="bv-total-row">
                <td class="bv-cat-col">Σύνολο {{ dir === 'expense' ? 'εξόδων' : 'εσόδων' }}</td>
                <template v-if="viewMode === 'monthly'">
                  <td v-for="m in 12" :key="m" class="bv-num bv-bold">{{ fmtCell(dirMonthTotal(dir, m)) }}</td>
                </template>
                <td v-for="q in [1,2,3,4]" :key="'tq'+q" class="bv-num bv-bold bv-q">{{ fmtCell(dirQuarterTotal(dir, q)) }}</td>
                <td class="bv-num bv-bold bv-year" :class="dir === 'expense' ? 'bv-red' : 'bv-green'">{{ fmt(dirYearTotal(dir)) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- add category -->
        <div class="bv-add-cat">
          <span v-if="addCatFor !== dir">
            <button class="bv-mini-btn" @click="openAddCat(dir)">+ Προσθήκη κατηγορίας</button>
          </span>
          <span v-else class="bv-add-inline">
            <select v-model="addCatChoice" class="bv-select bv-select-sm">
              <option value="">— επίλεξε —</option>
              <option v-for="c in availableCatsFor(dir)" :key="c" :value="c">{{ c }}</option>
              <option value="__custom__">+ Νέα custom…</option>
            </select>
            <input v-if="addCatChoice === '__custom__'" v-model="addCatCustom" class="bv-input bv-input-wide" placeholder="όνομα κατηγορίας" />
            <button class="bv-mini-btn" @click="confirmAddCat(dir)">OK</button>
            <button class="bv-mini-btn bv-ghost" @click="cancelAddCat">άκυρο</button>
          </span>
        </div>
      </div>

      <!-- ─────────── ΑΠΟΤΕΛΕΣΜΑ ανά μήνα (έσοδα − έξοδα) ─────────── -->
      <div class="bv-section bv-result-section">
        <div class="bv-section-label">ΑΠΟΤΕΛΕΣΜΑ (έσοδα − έξοδα)</div>
        <div class="bv-table-wrap">
          <table class="bv-table">
            <thead>
              <tr>
                <th class="bv-cat-col">Καθαρό ανά περίοδο</th>
                <template v-if="viewMode === 'monthly'">
                  <th v-for="(mn, mi) in MONTH_NAMES" :key="mi" class="bv-num">{{ mn }}</th>
                </template>
                <th v-for="q in [1,2,3,4]" :key="'rh'+q" class="bv-num bv-q">Q{{ q }}</th>
                <th class="bv-num bv-year">Έτος</th>
              </tr>
            </thead>
            <tbody>
              <tr class="bv-result-row">
                <td class="bv-cat-col">Αποτέλεσμα</td>
                <template v-if="viewMode === 'monthly'">
                  <td v-for="m in 12" :key="'rm'+m" class="bv-num bv-result-cell" :class="netMonthTotal(m) >= 0 ? 'bv-green' : 'bv-red'">{{ fmtSigned(netMonthTotal(m)) }}</td>
                </template>
                <td v-for="q in [1,2,3,4]" :key="'rq'+q" class="bv-num bv-q bv-result-cell" :class="netQuarterTotal(q) >= 0 ? 'bv-green' : 'bv-red'">{{ fmtSigned(netQuarterTotal(q)) }}</td>
                <td class="bv-num bv-year bv-result-cell" :class="netYearTotal() >= 0 ? 'bv-green' : 'bv-red'">{{ fmtSigned(netYearTotal()) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- ─────────── TAB 2: BUDGET vs ACTUAL ─────────── -->
    <div v-else>
      <div class="bv-vs-range">
        <span class="bv-vt-label">Περίοδος:</span>
        <select v-model="vsFromMonth" class="bv-select bv-select-sm">
          <option v-for="(mn, mi) in MONTH_NAMES" :key="mi" :value="mi + 1">{{ mn }}</option>
        </select>
        <span class="bv-vt-label">έως</span>
        <select v-model="vsToMonth" class="bv-select bv-select-sm">
          <option v-for="(mn, mi) in MONTH_NAMES" :key="mi" :value="mi + 1">{{ mn }}</option>
        </select>
      </div>

      <div class="bv-kpis bv-kpis-3">
        <div class="bv-kpi"><div class="bv-kpi-l">Budget (περίοδος)</div><div class="bv-kpi-v">{{ fmt(vsTotals.budget) }}</div></div>
        <div class="bv-kpi"><div class="bv-kpi-l">Πραγματικά</div><div class="bv-kpi-v">{{ fmt(vsTotals.actual) }}</div></div>
        <div class="bv-kpi"><div class="bv-kpi-l">Απόκλιση</div>
          <div class="bv-kpi-v" :class="varianceClass(vsTotals.budget, vsTotals.actual)">{{ fmtSigned(vsTotals.actual - vsTotals.budget) }}</div></div>
      </div>

      <div v-for="dir in ['expense', 'income']" :key="'vs' + dir" class="bv-section">
        <div class="bv-section-head">{{ dir === 'expense' ? 'ΕΞΟΔΑ' : 'ΕΣΟΔΑ' }}</div>
        <div class="bv-table-wrap">
          <table class="bv-table">
            <thead>
              <tr>
                <th class="bv-cat-col">Κατηγορία</th>
                <th class="bv-num">Budget</th>
                <th class="bv-num">Πραγματικά</th>
                <th class="bv-num">Απόκλιση €</th>
                <th class="bv-num">Απόκλιση %</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, ri) in vsRows(dir)" :key="'vsr' + dir + row.category" class="bv-sub-row">
                <td class="bv-cat-col"><span class="bv-rownum">{{ ri + 1 }}.</span> {{ row.category }}</td>
                <td class="bv-num">{{ fmt(row.budget) }}</td>
                <td class="bv-num">{{ fmt(row.actual) }}</td>
                <td class="bv-num" :class="varianceClass(row.budget, row.actual)">{{ fmtSigned(row.actual - row.budget) }}</td>
                <td class="bv-num" :class="varianceClass(row.budget, row.actual)">{{ pct(row.budget, row.actual) }}</td>
              </tr>
              <tr class="bv-total-row">
                <td class="bv-cat-col">Σύνολο {{ dir === 'expense' ? 'εξόδων' : 'εσόδων' }}</td>
                <td class="bv-num bv-bold">{{ fmt(vsDirTotal(dir).budget) }}</td>
                <td class="bv-num bv-bold">{{ fmt(vsDirTotal(dir).actual) }}</td>
                <td class="bv-num bv-bold" :class="varianceClass(vsDirTotal(dir).budget, vsDirTotal(dir).actual)">{{ fmtSigned(vsDirTotal(dir).actual - vsDirTotal(dir).budget) }}</td>
                <td class="bv-num bv-bold" :class="varianceClass(vsDirTotal(dir).budget, vsDirTotal(dir).actual)">{{ pct(vsDirTotal(dir).budget, vsDirTotal(dir).actual) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="bv-note">Θετική απόκλιση (πράσινο) = πάνω από budget. Αρνητική απόκλιση (κόκκινο) = κάτω από budget.</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, watch } from 'vue'
import api from '@/api'

const ENTITY_MAP = {
  'next2me': '58202b71-4ddb-45c9-8e3c-39e816bde972',
  'house': 'dea1f32c-7b30-4981-b625-633da9dbe71e',
  'polaris': '50317f44-9961-4fb4-add0-7a118e32dc14',
  'next2megroup': '50317f44-9961-4fb4-add0-7a118e32dc14',
}
const MONTH_NAMES = ['Ιαν', 'Φεβ', 'Μαρ', 'Απρ', 'Μάι', 'Ιουν', 'Ιουλ', 'Αυγ', 'Σεπ', 'Οκτ', 'Νοε', 'Δεκ']

const years = ['2024', '2025', '2026', '2027']
const selectedYear = ref('2026')
const activeTab = ref('builder')
const viewMode = ref('monthly')
const loading = ref(false)
const saving = ref(false)
const seeding = ref(false)
const seedingRecurring = ref(false)

// budget cells: key "dir|cat|sub|month" -> amount (number)
const cells = ref({})
// structure: ordered categories/subs per direction (so empty ones still show)
const structure = ref({ expense: {}, income: {} })  // { dir: { cat: [sub, sub...] } }
const openCats = ref({})

// actual transactions (for vs-actual tab + category dropdowns)
const allTransactions = ref([])

const vsFromMonth = ref(1)
const vsToMonth = ref(12)

function entityId() {
  const k = localStorage.getItem('n2c_entity') || 'next2me'
  return ENTITY_MAP[k] || ENTITY_MAP['polaris']
}

function cellKey(dir, cat, sub, m) { return dir + '|' + cat + '|' + (sub || '') + '|' + m }

// ── Load ──────────────────────────────────────────────────────────
async function loadBudget() {
  loading.value = true
  try {
    const eid = entityId()
    const res = await api.get('/api/budgets', { params: { entityId: eid, year: parseInt(selectedYear.value, 10) } })
    const data = res.data?.data || []
    const newCells = {}
    const struct = { expense: {}, income: {} }
    for (const line of data) {
      const dir = (line.direction === 'income') ? 'income' : 'expense'
      const cat = line.category
      const sub = line.subcategory || ''
      newCells[cellKey(dir, cat, sub, line.month)] = Number(line.amount) || 0
      if (!struct[dir][cat]) struct[dir][cat] = []
      if (!struct[dir][cat].includes(sub)) struct[dir][cat].push(sub)
    }
    cells.value = newCells
    structure.value = struct
  } catch (e) {
    cells.value = {}
    structure.value = { expense: {}, income: {} }
  } finally {
    loading.value = false
  }
}

async function loadTransactions() {
  try {
    const eid = entityId()
    const res = await api.get('/api/transactions', { params: { entityId: eid, page: 0, perPage: 9999 } })
    allTransactions.value = res.data?.data || res.data || []
  } catch (e) {
    allTransactions.value = []
  }
}

// ── Structure helpers ─────────────────────────────────────────────
function categoriesFor(dir) { return Object.keys(structure.value[dir] || {}) }
function subcategoriesFor(dir, cat) { return (structure.value[dir] && structure.value[dir][cat]) || [] }
const allCategories = computed(() => [...categoriesFor('expense'), ...categoriesFor('income')])

function isOpen(dir, cat) { return !!openCats.value[dir + '|' + cat] }
function toggleCat(dir, cat) { openCats.value[dir + '|' + cat] = !isOpen(dir, cat) }

// ── Cell get/set ──────────────────────────────────────────────────
function getCell(dir, cat, sub, m) {
  const v = cells.value[cellKey(dir, cat, sub, m)]
  return (v === undefined || v === 0) ? '' : v
}
function setCell(dir, cat, sub, m, raw) {
  const num = parseFloat(String(raw).replace(/\./g, '').replace(',', '.'))
  cells.value[cellKey(dir, cat, sub, m)] = isNaN(num) ? 0 : num
}

// ── Aggregations ──────────────────────────────────────────────────
function subMonth(dir, cat, sub, m) { return cells.value[cellKey(dir, cat, sub, m)] || 0 }
function subQuarterTotal(dir, cat, sub, q) {
  let s = 0; for (let m = (q - 1) * 3 + 1; m <= q * 3; m++) s += subMonth(dir, cat, sub, m); return s
}
function subYearTotal(dir, cat, sub) { let s = 0; for (let m = 1; m <= 12; m++) s += subMonth(dir, cat, sub, m); return s }

function catMonthTotal(dir, cat, m) { let s = 0; for (const sub of subcategoriesFor(dir, cat)) s += subMonth(dir, cat, sub, m); return s }
function catQuarterTotal(dir, cat, q) { let s = 0; for (let m = (q - 1) * 3 + 1; m <= q * 3; m++) s += catMonthTotal(dir, cat, m); return s }
function catYearTotal(dir, cat) { let s = 0; for (let m = 1; m <= 12; m++) s += catMonthTotal(dir, cat, m); return s }

function dirMonthTotal(dir, m) { let s = 0; for (const cat of categoriesFor(dir)) s += catMonthTotal(dir, cat, m); return s }
function dirQuarterTotal(dir, q) { let s = 0; for (const cat of categoriesFor(dir)) s += catQuarterTotal(dir, cat, q); return s }
function dirYearTotal(dir) { let s = 0; for (const cat of categoriesFor(dir)) s += catYearTotal(dir, cat); return s }
// Καθαρό αποτέλεσμα (έσοδα - έξοδα) ανά μήνα/τρίμηνο/έτος
function netMonthTotal(m) { return dirMonthTotal('income', m) - dirMonthTotal('expense', m) }
function netQuarterTotal(q) { return dirQuarterTotal('income', q) - dirQuarterTotal('expense', q) }
function netYearTotal() { return dirYearTotal('income') - dirYearTotal('expense') }

const totalIncome = computed(() => dirYearTotal('income'))
const totalExpense = computed(() => dirYearTotal('expense'))

// ── Add category / subcategory ────────────────────────────────────
const addCatFor = ref(null)
const addCatChoice = ref('')
const addCatCustom = ref('')
const addSubFor = ref(null)
const addSubChoice = ref('')
const addSubCustom = ref('')

// distinct categories/subs from actual transactions for this direction
function actualCatsFor(dir) {
  const set = new Set()
  for (const t of allTransactions.value) {
    const tdir = (t.type === 'income') ? 'income' : 'expense'
    if (tdir !== dir) continue
    if (t.category) set.add(t.category)
  }
  return [...set].sort((a, b) => a.localeCompare(b, 'el'))
}
function actualSubsFor(dir, cat) {
  const set = new Set()
  for (const t of allTransactions.value) {
    const tdir = (t.type === 'income') ? 'income' : 'expense'
    if (tdir !== dir) continue
    if (t.category !== cat) continue
    if (t.subcategory) set.add(t.subcategory)
  }
  return [...set].sort((a, b) => a.localeCompare(b, 'el'))
}
function availableCatsFor(dir) {
  const existing = new Set(categoriesFor(dir))
  return actualCatsFor(dir).filter(c => !existing.has(c))
}
function availableSubsFor(dir, cat) {
  const existing = new Set(subcategoriesFor(dir, cat))
  return actualSubsFor(dir, cat).filter(s => !existing.has(s))
}

function openAddCat(dir) { addCatFor.value = dir; addCatChoice.value = ''; addCatCustom.value = '' }
function cancelAddCat() { addCatFor.value = null }
function confirmAddCat(dir) {
  let name = addCatChoice.value === '__custom__' ? addCatCustom.value.trim() : addCatChoice.value
  if (!name) { cancelAddCat(); return }
  if (!structure.value[dir][name]) structure.value[dir][name] = []
  openCats.value[dir + '|' + name] = true
  cancelAddCat()
}
function openAddSub(dir, cat) { addSubFor.value = dir + '|' + cat; addSubChoice.value = ''; addSubCustom.value = '' }
function cancelAddSub() { addSubFor.value = null }
function confirmAddSub(dir, cat) {
  let name = addSubChoice.value === '__custom__' ? addSubCustom.value.trim() : addSubChoice.value
  if (!name) { cancelAddSub(); return }
  if (!structure.value[dir][cat]) structure.value[dir][cat] = []
  if (!structure.value[dir][cat].includes(name)) structure.value[dir][cat].push(name)
  cancelAddSub()
}

function removeCat(dir, cat) {
  const total = catYearTotal(dir, cat)
  if (total > 0 && !confirm('Διαγραφή κατηγορίας "' + cat + '"; Θα χαθούν τα ποσά της.')) return
  // wipe cells of all subs
  for (const sub of subcategoriesFor(dir, cat)) {
    for (let m = 1; m <= 12; m++) delete cells.value[cellKey(dir, cat, sub, m)]
  }
  delete structure.value[dir][cat]
  delete openCats.value[dir + '|' + cat]
}

function removeSub(dir, cat, sub) {
  const total = subYearTotal(dir, cat, sub)
  if (total > 0 && !confirm('Διαγραφή υποκατηγορίας "' + (sub || '(χωρίς όνομα)') + '"; Θα χαθούν τα ποσά της.')) return
  for (let m = 1; m <= 12; m++) delete cells.value[cellKey(dir, cat, sub, m)]
  const arr = structure.value[dir][cat] || []
  const idx = arr.indexOf(sub)
  if (idx >= 0) arr.splice(idx, 1)
}

// ── Auto-seed from Recurring patterns ──────────────────────────────
// Expands a recurrence pattern into the months it occurs in the given year.
// Returns { months: [1..12], amountPerCell }. Empty months[] = pattern doesn't occur in this year.
function expandPattern(pattern, txnAmount, year) {
  if (!pattern || !pattern.frequency) return { months: [], amountPerCell: 0 }
  const freq = pattern.frequency
  const interval = Number(pattern.intervalCount) || 1
  const startDate = pattern.startDate ? new Date(pattern.startDate + 'T00:00:00') : null
  const endDate = pattern.endDate ? new Date(pattern.endDate + 'T00:00:00') : null
  // Out-of-range guards
  if (endDate && endDate.getFullYear() < year) return { months: [], amountPerCell: 0 }
  if (startDate && startDate.getFullYear() > year) return { months: [], amountPerCell: 0 }

  if (freq === 'MONTHLY') {
    // Annualized: every month of the selected year, capped by end_date if it lands inside the year.
    if (interval === 1) {
      const endMonth = (endDate && endDate.getFullYear() === year) ? endDate.getMonth() + 1 : 12
      const months = []
      for (let m = 1; m <= endMonth; m++) months.push(m)
      return { months, amountPerCell: txnAmount }
    }
    // interval > 1: walk the rhythm from start_date
    if (!startDate) return { months: [], amountPerCell: 0 }
    const months = []
    let occY = startDate.getFullYear()
    let occM = startDate.getMonth() // 0-indexed
    while (occY <= year) {
      if (occY === year) months.push(occM + 1)
      occM += interval
      while (occM >= 12) { occM -= 12; occY += 1 }
    }
    return { months, amountPerCell: txnAmount }
  }

  if (freq === 'YEARLY') {
    if (!startDate) return { months: [], amountPerCell: 0 }
    const baseYear = startDate.getFullYear()
    if (baseYear > year) return { months: [], amountPerCell: 0 }
    if ((year - baseYear) % interval !== 0) return { months: [], amountPerCell: 0 }
    return { months: [startDate.getMonth() + 1], amountPerCell: txnAmount }
  }

  if (freq === 'QUARTERLY') {
    if (!startDate) return { months: [], amountPerCell: 0 }
    const months = []
    let occY = startDate.getFullYear()
    let occM = startDate.getMonth()
    const step = 3 * interval
    while (occY <= year) {
      if (occY === year) months.push(occM + 1)
      occM += step
      while (occM >= 12) { occM -= 12; occY += 1 }
    }
    return { months, amountPerCell: txnAmount }
  }

  if (freq === 'WEEKLY') {
    // Convert to monthly equivalent and fill all 12 months
    return { months: [1,2,3,4,5,6,7,8,9,10,11,12], amountPerCell: (txnAmount * 52 / 12) / interval }
  }
  if (freq === 'DAILY') {
    return { months: [1,2,3,4,5,6,7,8,9,10,11,12], amountPerCell: (txnAmount * 365 / 12) / interval }
  }

  return { months: [], amountPerCell: 0 }
}

async function autoSeedFromRecurring() {
  seedingRecurring.value = true
  try {
    const eid = entityId()
    const year = parseInt(selectedYear.value, 10)

    // Load recurring transactions + patterns in parallel
    const [txnRes, patRes] = await Promise.all([
      api.get('/api/transactions', { params: { entityId: eid, perPage: 10000 } }),
      api.get('/api/recurrence-patterns', { params: { entityId: eid } })
    ])
    const allTxns = Array.isArray(txnRes.data?.data) ? txnRes.data.data : (Array.isArray(txnRes.data) ? txnRes.data : [])
    const pats = Array.isArray(patRes.data?.data) ? patRes.data.data : (Array.isArray(patRes.data) ? patRes.data : [])

    // Only expense templates that are recurring, active, non-zero
    const templates = allTxns.filter(t =>
      t.isRecurring === true &&
      t.type === 'expense' &&
      (t.recordStatus || 'active').toLowerCase() !== 'void' &&
      Number(t.amount) > 0
    )

    const patternById = {}
    for (const p of pats) if (p && p.id) patternById[p.id] = p

    // Expand each template into seed entries
    const seedData = []  // { cat, sub, month, amount, description }
    const distinctDescriptions = new Set()
    for (const t of templates) {
      const pat = patternById[t.recurrencePatternId]
      if (!pat) continue
      const { months, amountPerCell } = expandPattern(pat, Number(t.amount), year)
      if (!months.length || amountPerCell <= 0) continue
      const cat = t.category || '(χωρίς κατηγορία)'
      const sub = t.subcategory || ''
      distinctDescriptions.add(t.description || cat)
      for (const m of months) {
        seedData.push({ cat, sub, month: m, amount: amountPerCell, description: t.description })
      }
    }

    if (seedData.length === 0) {
      alert('Δεν βρέθηκαν επαναλαμβανόμενα έξοδα για το έτος ' + year + '.')
      return
    }

    // Pre-compute targets per cell (sum patterns landing on same cat+sub+month)
    const targets = {}  // key -> { cat, sub, month, amount, currentValue }
    for (const s of seedData) {
      const k = cellKey('expense', s.cat, s.sub, s.month)
      if (!targets[k]) {
        targets[k] = { cat: s.cat, sub: s.sub, month: s.month, amount: 0, currentValue: Number(cells.value[k]) || 0 }
      }
      targets[k].amount += s.amount
    }
    const targetKeys = Object.keys(targets)
    const totalCells = targetKeys.length
    const occupiedCells = targetKeys.filter(k => targets[k].currentValue > 0).length
    const totalAmount = targetKeys.reduce((s, k) => s + targets[k].amount, 0)

    // Summary + confirm
    let summary =
      'Βρέθηκαν ' + distinctDescriptions.size + ' επαναλαμβανόμενα έξοδα.\n' +
      'Θα γεμιστούν ' + totalCells + ' κελιά / ' +
      Math.round(totalAmount).toLocaleString('el-GR') + '€ συνολικά για το ' + year + '.\n\n'
    if (occupiedCells > 0) {
      summary += occupiedCells + ' κελιά έχουν ήδη τιμές και θα ΑΝΤΙΚΑΤΑΣΤΑΘΟΥΝ.\n\n'
    }
    summary += 'OK = Συνέχεια   |   Άκυρο = Σταμάτησε'

    if (!confirm(summary)) return

    // Apply: ensure structure + open category + set cell (replace mode)
    for (const k of targetKeys) {
      const tgt = targets[k]
      if (!structure.value.expense[tgt.cat]) structure.value.expense[tgt.cat] = []
      if (!structure.value.expense[tgt.cat].includes(tgt.sub)) structure.value.expense[tgt.cat].push(tgt.sub)
      openCats.value['expense|' + tgt.cat] = true
      cells.value[k] = tgt.amount
    }

    alert('Έγιναν seed ' + totalCells + ' κελιά. Πάτησε 💾 Αποθήκευση για να αποθηκευτούν μόνιμα.')
  } catch (e) {
    console.error('autoSeedFromRecurring error:', e)
    alert('Σφάλμα auto-seed από recurring. Δες την κονσόλα του browser.')
  } finally {
    seedingRecurring.value = false
  }
}

// ── Auto-seed ─────────────────────────────────────────────────────
async function autoSeed() {
  seeding.value = true
  try {
    const eid = entityId()
    const sourceYear = parseInt(selectedYear.value, 10) - 1
    const res = await api.get('/api/budgets/seed', { params: { entityId: eid, sourceYear } })
    const seed = res.data?.data || []
    for (const line of seed) {
      const dir = (line.direction === 'income') ? 'income' : 'expense'
      const cat = line.category
      const avg = Number(line.monthlyAvg) || 0
      if (!structure.value[dir][cat]) structure.value[dir][cat] = []
      // seed at category level: store under a sub named after the category itself
      if (!structure.value[dir][cat].includes(cat)) structure.value[dir][cat].push(cat)
      for (let m = 1; m <= 12; m++) cells.value[cellKey(dir, cat, cat, m)] = avg
    }
    if (seed.length === 0) alert('Δεν βρέθηκαν πραγματικές κινήσεις για το έτος ' + sourceYear + '.')
  } catch (e) {
    alert('Σφάλμα auto-seed.')
  } finally {
    seeding.value = false
  }
}

// ── Save ──────────────────────────────────────────────────────────
async function saveBudget() {
  saving.value = true
  try {
    const eid = entityId()
    const lines = []
    for (const dir of ['expense', 'income']) {
      for (const cat of categoriesFor(dir)) {
        for (const sub of subcategoriesFor(dir, cat)) {
          for (let m = 1; m <= 12; m++) {
            const amt = subMonth(dir, cat, sub, m)
            if (amt && amt !== 0) lines.push({ category: cat, subcategory: sub, direction: dir, month: m, amount: amt })
          }
        }
      }
    }
    await api.post('/api/budgets', { entityId: eid, year: parseInt(selectedYear.value, 10), lines })
    alert('Ο προϋπολογισμός αποθηκεύτηκε.')
    await loadBudget()
  } catch (e) {
    alert('Σφάλμα αποθήκευσης (χρειάζεσαι δικαιώματα ADMIN).')
  } finally {
    saving.value = false
  }
}

// ── Budget vs Actual ──────────────────────────────────────────────
function budgetForCatInRange(dir, cat) {
  let s = 0
  for (const sub of subcategoriesFor(dir, cat)) {
    for (let m = vsFromMonth.value; m <= vsToMonth.value; m++) s += subMonth(dir, cat, sub, m)
  }
  return s
}
function actualForCatInRange(dir, cat) {
  let s = 0
  const yr = selectedYear.value
  for (const t of allTransactions.value) {
    const tdir = (t.type === 'income') ? 'income' : 'expense'
    if (tdir !== dir) continue
    if ((t.entryMode || 'ACTUAL').toUpperCase() !== 'ACTUAL') continue
    if (!t.docDate || t.docDate.substring(0, 4) !== yr) continue
    const m = parseInt(t.docDate.substring(5, 7), 10)
    if (m < vsFromMonth.value || m > vsToMonth.value) continue
    if ((t.category || '(χωρίς κατηγορία)') !== cat) continue
    s += Number(t.amount) || 0
  }
  return s
}
function vsRows(dir) {
  // union of budget categories + actual categories
  const cats = new Set(categoriesFor(dir))
  for (const c of actualCatsFor(dir)) cats.add(c)
  const rows = []
  for (const cat of cats) {
    const budget = budgetForCatInRange(dir, cat)
    const actual = actualForCatInRange(dir, cat)
    if (budget === 0 && actual === 0) continue
    rows.push({ category: cat, budget, actual })
  }
  rows.sort((a, b) => b.actual - a.actual)
  return rows
}
function vsDirTotal(dir) {
  let budget = 0, actual = 0
  for (const r of vsRows(dir)) { budget += r.budget; actual += r.actual }
  return { budget, actual }
}
const vsTotals = computed(() => {
  const e = vsDirTotal('expense')
  return { budget: e.budget, actual: e.actual }
})

// ── Formatting ────────────────────────────────────────────────────
function fmt(n) {
  const v = Math.round(Number(n) || 0)
  return '€' + v.toLocaleString('el-GR')
}
function fmtCell(n) {
  const v = Math.round(Number(n) || 0)
  return v === 0 ? '—' : v.toLocaleString('el-GR')
}
function fmtSigned(n) {
  const v = Math.round(Number(n) || 0)
  const sign = v > 0 ? '+' : ''
  return sign + '€' + v.toLocaleString('el-GR')
}
function pct(budget, actual) {
  if (!budget) return actual ? '—' : '0%'
  const p = ((actual - budget) / budget) * 100
  const sign = p > 0 ? '+' : ''
  return sign + p.toFixed(1).replace('.', ',') + '%'
}
function varianceClass(budget, actual) {
  const diff = actual - budget
  if (Math.abs(diff) < 0.5) return ''
  // Simple rule: positive variance = green, negative variance = red.
  return diff > 0 ? 'bv-green' : 'bv-red'
}

// ── Lifecycle ─────────────────────────────────────────────────────
function reloadAll() { loadBudget(); loadTransactions() }
onMounted(() => {
  reloadAll()
  window.addEventListener('storage', (e) => { if (e.key === 'n2c_entity') reloadAll() })
  window.addEventListener('entity-changed', () => { reloadAll() })
})
onActivated(() => { reloadAll() })

// reload budget when year changes
watch(selectedYear, () => { loadBudget() })
</script>

<style scoped>
.budget-view { padding: 24px; max-width: 100%; }
.bv-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 18px; }
.bv-title { font-size: 26px; font-weight: 800; color: #ffffff; }
.bv-sub { font-size: 13px; font-style: italic; color: #cbd5e1; margin-top: 2px; }
.bv-actions { display: flex; gap: 8px; align-items: center; }

.bv-select { height: 36px; padding: 0 10px; border: 1px solid #cbd5e1; border-radius: 8px; background: #fff !important; color: #0f172a !important; font-size: 14px; }
.bv-select-sm { height: 32px; font-size: 13px; }
.bv-btn { height: 36px; padding: 0 14px; border: 1px solid #cbd5e1; border-radius: 8px; background: #fff; color: #1e293b; font-size: 13px; cursor: pointer; }
.bv-btn:hover { background: #f1f5f9; }
.bv-btn-primary { border-color: #2563eb; color: #2563eb; font-weight: 600; }
.bv-btn:disabled { opacity: 0.6; cursor: default; }

.bv-tabs { display: flex; gap: 8px; margin-bottom: 18px; border-bottom: 1px solid #e2e8f0; }
.bv-tab { padding: 10px 16px; border: none; background: none; color: #e2e8f0; font-size: 15px; font-weight: 600; cursor: pointer; border-bottom: 2px solid transparent; }
.bv-tab.active { color: #ffffff; font-weight: 700; border-bottom-color: #60a5fa; }

.bv-loading { padding: 40px; text-align: center; color: #64748b; }

.bv-viewtoggle { display: flex; gap: 8px; align-items: center; margin-bottom: 14px; }
.bv-vt-label { font-size: 13px; color: #e2e8f0; font-weight: 600; }
.bv-vt-btn { height: 30px; padding: 0 12px; border: 1px solid #cbd5e1; border-radius: 8px; background: #fff; color: #475569; font-size: 12px; cursor: pointer; }
.bv-vt-btn.active { background: #dbeafe; color: #2563eb; border-color: #2563eb; font-weight: 600; }

.bv-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 18px; }
.bv-kpis-3 { grid-template-columns: repeat(3, 1fr); }
.bv-kpi { background: #f8fafc; border-radius: 8px; padding: 14px; }
.bv-kpi-l { font-size: 13px; color: #64748b; }
.bv-kpi-v { font-size: 24px; font-weight: 700; color: #1e293b; margin-top: 4px; }
.bv-green { color: #15803d !important; font-weight: 700; }
.bv-red { color: #b91c1c !important; font-weight: 700; }

.bv-section { margin-bottom: 24px; }
.bv-section-head { font-size: 14px; font-weight: 700; color: #ffffff; margin-bottom: 6px; letter-spacing: 0.5px; }
.bv-table-wrap { border: 1px solid #e2e8f0; border-radius: 12px; overflow-x: auto; background: #fff; }
.bv-table { width: 100%; border-collapse: collapse; font-size: 15px; background: #fff; }
.bv-table td { background: #fff; }
.bv-table th { background: #f1f5f9; padding: 11px 10px; font-weight: 700; color: #334155; text-align: right; white-space: nowrap; font-size: 14px; }
.bv-table th.bv-cat-col { text-align: left; position: sticky; left: 0; background: #f8fafc; min-width: 240px; padding-left: 16px; }
.bv-cat-col { text-align: left; padding-left: 16px; padding-right: 20px; }
.bv-num { text-align: right; white-space: nowrap; padding: 8px 10px; font-size: 15px; font-weight: 600; color: #0f172a; }
.bv-q { color: #2563eb; }
.bv-year { color: #000; font-weight: 700; }
.bv-bold { font-weight: 700; }

.bv-cat-row { background: #f8fafc; border-top: 1px solid #e2e8f0; }
.bv-cat-row .bv-cat-col { font-weight: 700; color: #0f172a; position: sticky; left: 0; background: #f8fafc; }
.bv-cat-row td { background: #f8fafc; color: #0f172a; }
.bv-chev { cursor: pointer; color: #64748b; margin-right: 6px; user-select: none; font-size: 11px; }
.bv-rownum { color: #94a3b8; font-weight: 600; margin-right: 6px; }
.bv-del { display: inline-block; margin-left: 8px; color: #cbd5e1; font-weight: 700; cursor: pointer; font-size: 16px; line-height: 1; border-radius: 4px; padding: 0 4px; }
.bv-del:hover { color: #fff; background: #dc2626; }

.bv-sub-row { border-top: 1px solid #f1f5f9; }
.bv-sub-row .bv-cat-col { position: sticky; left: 0; background: #fff; color: #0f172a; font-weight: 600; font-size: 15px; }
.bv-sub-name { padding-left: 28px; color: #1e293b; font-weight: 500; }

.bv-input { width: 60px; height: 30px; padding: 0 6px; border: 1px solid #cbd5e1; border-radius: 6px; text-align: right; font-size: 14px; font-weight: 600; color: #0f172a !important; background: #fff !important; }
.bv-input::placeholder { color: #94a3b8; font-weight: 400; }
.bv-input:focus { outline: none; border-color: #2563eb; }
.bv-input-wide { width: 180px; text-align: left; }

.bv-total-row { background: #e8edf4; border-top: 2px solid #94a3b8; }
.bv-total-row td { background: #e8edf4; }
.bv-total-row .bv-num { color: #000; font-weight: 800; font-size: 16px; }
.bv-total-row .bv-cat-col { font-weight: 700; color: #1e293b; position: sticky; left: 0; background: #f1f5f9; }
.bv-result-section { margin-top: 18px; }
.bv-result-row { background: #0f172a; border-top: 3px solid #334155; }
.bv-result-row td { background: #0f172a !important; padding: 14px 10px; }
.bv-result-row .bv-cat-col { font-weight: 800; color: #fff !important; font-size: 16px; position: sticky; left: 0; background: #0f172a !important; }
.bv-result-cell { font-weight: 800 !important; font-size: 18px !important; }
.bv-result-row .bv-green { color: #4ade80 !important; }
.bv-result-row .bv-red { color: #f87171 !important; }

.bv-add-row td { padding: 6px 8px 6px 28px; }
.bv-add-cat { margin-top: 10px; }
.bv-mini-btn { height: 28px; padding: 0 10px; border: 1px solid #cbd5e1; border-radius: 6px; background: #fff; color: #475569; font-size: 12px; cursor: pointer; }
.bv-mini-btn:hover { background: #f1f5f9; }
.bv-ghost { color: #94a3b8; }
.bv-add-inline { display: inline-flex; gap: 6px; align-items: center; flex-wrap: wrap; }

.bv-vs-range { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; }
.bv-note { font-size: 13px; color: #cbd5e1; font-style: italic; margin-top: 12px; }
</style>
