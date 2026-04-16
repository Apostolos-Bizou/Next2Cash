<script setup>
import { ref, computed } from 'vue'

/* ── State ── */
const reportTitle = ref('')
const reportDesc = ref('')
const selectedCategory = ref('Όλες οι κατηγορίες')
const selectedSubcategory = ref('Όλες')
const dateFrom = ref('')
const dateTo = ref('')
const selectedMethod = ref('Όλες')
const displayMode = ref('Έσοδα + Έξοδα')
const groupByCategory = ref(false)

const sections = ref([
  {
    id: 1,
    type: 'income',
    label: 'Εισπράξεις',
    items: [
      { id: 4777,  date: '14/04/2026', desc: '4777 - ΕΣΟΔΑ ΠΡΟΣΩΠΙΚΑ ΤΡΑΠΕΖΑ',      category: 'Έσοδα Β',    amount: 150.00 },
      { id: -35,   date: '07/04/2026', desc: 'Πληρωμή #4747 — ΕΣΟΔΑ ΠΡΟΣΩΠΙΚΑ ΤΡΑΠΕΖΑ', category: 'Έσοδα Β', amount: 134.00 },
    ]
  },
  {
    id: 2,
    type: 'expense',
    label: 'Έξοδα',
    items: [
      { id: -62,   date: '14/04/2026', desc: 'Πληρωμή #4776 — ΠΑΠΑΚΙ ΑΓΟΡΑ DOMAIN Next2View.com', category: 'Εξοπλισμός', amount: -12.40 },
      { id: 4776,  date: '09/04/2026', desc: '4776 - ΠΑΠΑΚΙ ΑΓΟΡΑ DOMAIN Next2View.com',           category: 'Εξοπλισμός', amount: -12.40 },
      { id: 4775,  date: '09/04/2026', desc: '4775 - MICROSOFT AZURE 03ος 2026',                   category: 'Εξοπλισμός', amount: -112.42 },
      { id: -61,   date: '08/04/2026', desc: 'Πληρωμή #4732 — ΠΑΠΑΚΙ doctornexttome.gr ΤΙΜΟΛΟΓΙΟ 27-03-2026', category: 'Εξοπλισμός', amount: -36.08 },
      { id: 4748,  date: '07/04/2026', desc: '4748 - EPASS',                                       category: 'Λειτουργικά', amount: -50.00 },
      { id: -34,   date: '07/04/2026', desc: 'Πληρωμή #4738 — 4735 - ΠΑΠΑΚΙ HireBases.com ΤΙΜΟΛΟΓΙΟ 01-04-2026', category: 'Εξοπλισμός', amount: -12.40 },
      { id: 4732,  date: '06/04/2026', desc: '4732 - ΠΑΠΑΚΙ doctornexttome.gr ΤΙΜΟΛΟΓΙΟ 27-03-2026', category: 'Εξοπλισμός', amount: -36.08 },
      { id: -26,   date: '05/04/2026', desc: 'Πληρωμή #4656 — ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ ΛΟΓΙΣΤΙΚΑ 10ος 2025', category: 'Απασχόληση', amount: -186.00 },
    ]
  }
])

// Available items panel (right side)
const allItems = ref([
  { id: 4777,  date: '14/04/2026', desc: '4777 - ΕΣΟΔΑ ΠΡΟΣΩΠΙΚΑ ΤΡΑΠΕΖΑ',      category: 'Έσοδα Β',    amount: +150.00 },
  { id: -62,   date: '14/04/2026', desc: 'Πληρωμή #4776 — ΠΑΠΑΚΙ ΑΓΟΡΑ DOM...',  category: 'Εξοπλισμός', amount: -12.40  },
  { id: 4776,  date: '09/04/2026', desc: '4776 - ΠΑΠΑΚΙ ΑΓΟΡΑ DOMAIN Next2Vie...', category: 'Εξοπλισμός', amount: -12.40 },
  { id: 4775,  date: '09/04/2026', desc: '4775 - MICROSOFT AZURE 03ος 2026',      category: 'Εξοπλισμός', amount: -112.42 },
  { id: -61,   date: '08/04/2026', desc: 'Πληρωμή #4732 — ΠΑΠΑΚΙ doctornex...',  category: 'Εξοπλισμός', amount: -36.08  },
  { id: 4748,  date: '07/04/2026', desc: '4748 - EPASS',                          category: 'Λειτουργικά', amount: -50.00 },
  { id: -34,   date: '07/04/2026', desc: 'Πληρωμή #4738 — 4735 - ΠΑΠΑΚΙ Hi...',  category: 'Εξοπλισμός', amount: -12.40  },
  { id: -35,   date: '07/04/2026', desc: 'Πληρωμή #4747 — ΕΣΟΔΑ ΠΡΟΣΩΠΙΚΑ',      category: 'Έσοδα Β',    amount: +134.00 },
  { id: 4732,  date: '06/04/2026', desc: '4732 - ΠΑΠΑΚΙ doctornexttome.gr',       category: 'Εξοπλισμός', amount: -36.08  },
  { id: -26,   date: '05/04/2026', desc: 'Πληρωμή #4656 — ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ Λ...',  category: 'Απασχόληση', amount: -186.00 },
  { id: -28,   date: '05/04/2026', desc: 'Πληρωμή #4657 — ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ Λ...',  category: 'Απασχόληση', amount: -186.00 },
  { id: -29,   date: '05/04/2026', desc: 'Πληρωμή #4658 — ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ Λ...',  category: 'Απασχόληση', amount: -186.00 },
])

const itemFilter = ref('')
const itemFilterTab = ref('all') // all / income / expense / urgent

const selectedItems = ref([])

const categories = ['Όλες οι κατηγορίες','Λειτουργικά','Προβολή & Προώθηση','Ανάπτυξη Λογισμικού','Εξοπλισμός','Απασχόληση','Προσωπικό','Λοιπά','Εισπράξεις','Έσοδα','Έσοδα Β']
const subcategories = ['Όλες','Ενοίκιο','Τηλέφωνα','Άδειες Χρήσης','ΚΑΓΚΕΛΑΡΗΣ','ΒΑΡΙΑΣ','Finance','Dn2Me-UK']
const methods = ['Όλες','Μετρητά','Τράπεζα','Απόδειξη','HSBC','Πειραιώς','Πορτοφόλι','Revolut GBP','Revolut USD','Revolut EUR']
const displayModes = ['Έσοδα + Έξοδα','Μόνο Έσοδα','Μόνο Έξοδα']

/* ── Computed ── */
const sectionCount = computed(() => sections.value.reduce((s, sec) => s + sec.items.length, 0))
const totalIncome = computed(() => sections.value.filter(s => s.type === 'income').flatMap(s => s.items).reduce((s, i) => s + i.amount, 0))
const totalExpense = computed(() => sections.value.filter(s => s.type === 'expense').flatMap(s => s.items).reduce((s, i) => s + Math.abs(i.amount), 0))

const filteredItems = computed(() => {
  let items = allItems.value
  if (itemFilter.value) {
    const q = itemFilter.value.toLowerCase()
    items = items.filter(i => i.desc.toLowerCase().includes(q) || String(i.id).includes(q))
  }
  if (itemFilterTab.value === 'income')  items = items.filter(i => i.amount > 0)
  if (itemFilterTab.value === 'expense') items = items.filter(i => i.amount < 0)
  return items
})

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
  const itemsToAdd = allItems.value.filter(i => selectedItems.value.includes(i.id))
  itemsToAdd.forEach(item => {
    if (!lastSection.items.find(i => i.id === item.id)) {
      lastSection.items.push(item)
    }
  })
  selectedItems.value = []
}

/* ── Actions ── */
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

const applyFilters = () => {
  // In real implementation, this would filter from backend
  alert('Φίλτρα εφαρμόστηκαν! (Demo mode)')
}

const exportPDF = () => alert('Export PDF — θα συνδεθεί με backend στη φάση 2')
const exportExcel = () => alert('Export Excel — θα συνδεθεί με backend στη φάση 2')

const fmt = (n) => {
  const abs = Math.abs(n)
  const str = new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2 }).format(abs) + ' €'
  return n >= 0 ? '+ ' + str : '- ' + str
}

const isPanelOpen = ref(true)
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
            <option v-for="c in categories" :key="c">{{ c }}</option>
          </select>
        </div>

        <div class="form-group">
          <label>Υποκατηγορία</label>
          <select v-model="selectedSubcategory" class="rb-select">
            <option v-for="s in subcategories" :key="s">{{ s }}</option>
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
            <option v-for="m in methods" :key="m">{{ m }}</option>
          </select>
        </div>

        <div class="form-group">
          <label>Εμφάνιση</label>
          <select v-model="displayMode" class="rb-select">
            <option v-for="d in displayModes" :key="d">{{ d }}</option>
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
                  <td class="date-col">{{ item.date }}</td>
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
          <span class="panel-count">{{ allItems.length.toLocaleString() }}</span>
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
              <div class="panel-item-meta">{{ item.id }} · {{ item.date }} · {{ item.category }}</div>
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
