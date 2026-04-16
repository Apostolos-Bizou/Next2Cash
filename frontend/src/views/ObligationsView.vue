<script setup>
import { ref } from 'vue'

const activeTab = ref('list')
const search = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const selectedStatus = ref('Απλήρωτες')
const selectedCategory = ref('Όλες κατηγορίες')
const selectedMethod = ref('Όλες μεθόδους')

const kpis = {
  total: { label: 'ΣΥΝΟΛΟ ΚΙΝΗΣΕΩΝ', amount: '235.597,98 €', count: '365 κινήσεις', color: 'white' },
  paid: { label: 'ΕΞΟΦΛΗΜΕΝΕΣ', amount: '0,00 €', count: '0 κιν.', color: 'green' },
  unpaid: { label: 'ΑΠΛΗΡΩΤΕΣ', amount: '227.887,23 €', count: '357 κινήσεις', color: 'red' },
  cash: { label: 'ΜΕΤΡΗΤΑ', amount: '126.814,58 €', count: '142 κιν.', color: 'orange', icon: '💰' },
  bank: { label: 'ΤΡΑΠΕΖΑ', amount: '101.762,26 €', count: '214 κιν.', color: 'blue', icon: '🏦' },
  partial: { label: 'ΜΕΡ. ΠΛΗΡΩΜΕΝΕΣ', amount: '0,00 €', count: '0 κιν.', color: 'gray' },
  urgent: { label: 'ΕΚΚΡΕΜΕΙΣ', amount: '7.710,75 €', count: '8 κιν.', color: 'orange', icon: '⚡' },
}

const obligations = ref([
  { id: 4775, date: '09/04/26', description: '4775 - MICROSOFT AZURE 03ος 2026', category: 'Εξοπλισμός', method: 'Πειραιώς', amount: 112.42, paid: 0, balance: 112.42, status: 'Εκκρεμεί' },
  { id: 4774, date: '01/04/26', description: '4774 - ΕNOIKIO 04ος 2026', category: 'Λειτουργικά', method: 'Πειραιώς', amount: 650.00, paid: 0, balance: 650.00, status: 'Απλήρωτη' },
  { id: 4741, date: '30/03/26', description: '4739 - ΚΙΝΗΤΟ 03ος 2026 ΤΙΜΟΛΟΓΙ...', category: 'Λειτουργικά', method: 'Πειραιώς', amount: 80.61, paid: 0, balance: 80.61, status: 'Εκκρεμεί' },
  { id: 4733, date: '27/03/26', description: '4733 - ΠΑΠΑΚΙ dn2me.gr ΤΙΜΟΛΟΓΙΟ...', category: 'Εξοπλισμός', method: 'Τράπεζα', amount: 36.88, paid: 0, balance: 36.88, status: 'Εκκρεμεί' },
  { id: 4773, date: '01/03/26', description: '4773 - ΕNOIKIO 03ος 2026', category: 'Λειτουργικά', method: 'Πειραιώς', amount: 650.00, paid: 0, balance: 650.00, status: 'Απλήρωτη' },
  { id: 4760, date: '26/02/26', description: '4760 - ΚΙΝΗΤΟ 02ος 2026', category: 'Λειτουργικά', method: 'Πειραιώς', amount: 80.61, paid: 0, balance: 80.61, status: 'Εκκρεμεί' },
  { id: 4772, date: '01/02/26', description: '4772 - ΕNOIKIO 02ος 2026', category: 'Λειτουργικά', method: 'Πειραιώς', amount: 650.00, paid: 0, balance: 650.00, status: 'Απλήρωτη' },
  { id: 4762, date: '29/01/26', description: '4762 - ΤΑΛΙΑΔΟΡΟΣ ΛΟΓΙΣΤΗΣ Dn2Me...', category: 'Λοιπά', method: 'Πειραιώς', amount: 297.50, paid: 0, balance: 297.50, status: 'Εκκρεμεί' },
  { id: 4761, date: '28/01/26', description: '4761 - ΤΑΛΙΑΔΟΡΟΣ ΛΟΓΙΣΤΗΣ Dn2Me...', category: 'Λοιπά', method: 'Πειραιώς', amount: 3000.00, paid: 0, balance: 3000.00, status: 'Εκκρεμεί' },
  { id: 4651, date: '01/01/26', description: 'ΕNOIKIO 01ος 2026', category: 'Λειτουργικά', method: 'Τράπεζα', amount: 650.00, paid: 0, balance: 650.00, status: 'Απλήρωτη' },
  { id: 4636, date: '18/12/25', description: 'ΤΑΛΙΑΔΟΡΟΣ ΛΟΓΙΣΤΗΣ Dn2Me UK', category: 'Λοιπά', method: 'Τράπεζα', amount: 2683.53, paid: 0, balance: 2683.53, status: 'Εκκρεμεί' },
])

const categories = [
  { name: 'Λειτουργικά', subs: ['Ενοίκιο'], color: '#29b6f6',
    months: [650, 650, 650, 650, 0, 0, 0, 0, 0, 0, 0, 0], total: 2600 },
  { name: 'Εξοπλισμός', subs: ['Άδειες Χρήσης'], color: '#4FC3A1',
    months: [0, 0, 0, 149.30, 0, 0, 0, 0, 0, 0, 0, 0], total: 149.30 },
  { name: 'Λοιπά', subs: ['Dn2Me-UK'], color: '#ef5350',
    months: [3000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], total: 3000 },
]

const months = ['Ιαν', 'Φεβ', 'Μαρ', 'Απρ', 'Μάι', 'Ιούν', 'Ιούλ', 'Αύγ', 'Σεπ', 'Οκτ', 'Νοέ', 'Δεκ']

const fmt = (n) => new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2 }).format(n) + ' €'

const statusClass = (s) => {
  if (s === 'Εκκρεμεί') return 'badge-orange'
  if (s === 'Απλήρωτη') return 'badge-red'
  return 'badge-green'
}
</script>

<template>
  <div class="obligations-page">
    <!-- KPI Cards -->
    <div class="kpi-grid">
      <div v-for="(kpi, key) in kpis" :key="key" class="kpi-card" :class="kpi.color">
        <div class="kpi-label">{{ kpi.icon }} {{ kpi.label }}</div>
        <div class="kpi-amount">{{ kpi.amount }}</div>
        <div class="kpi-count">{{ kpi.count }}</div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tabs">
      <button class="tab-btn" :class="{ active: activeTab === 'list' }" @click="activeTab = 'list'">≡ Λίστα</button>
      <button class="tab-btn" :class="{ active: activeTab === 'analysis' }" @click="activeTab = 'analysis'">⊞ Ανάλυση ανά Κατηγορία</button>
    </div>

    <!-- LIST TAB -->
    <div v-if="activeTab === 'list'">
      <div class="filters-bar">
        <input v-model="search" class="filter-input" placeholder="Αναζήτηση ID, περιγραφή..." />
        <input v-model="dateFrom" type="date" class="filter-input" />
        <input v-model="dateTo" type="date" class="filter-input" />
        <select v-model="selectedStatus" class="filter-select">
          <option>Απλήρωτες</option><option>Εκκρεμεί</option><option>Όλες</option>
        </select>
        <select v-model="selectedCategory" class="filter-select">
          <option>Όλες κατηγορίες</option><option>Λειτουργικά</option><option>Εξοπλισμός</option>
        </select>
        <select v-model="selectedMethod" class="filter-select">
          <option>Όλες μεθόδους</option><option>Πειραιώς</option><option>Τράπεζα</option>
        </select>
        <button class="btn-export">⬇ Export</button>
        <button class="btn-refresh">↻ Ανανέωση</button>
      </div>

      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th><th>ΗΜ/ΝΙΑ</th><th>ΠΕΡΙΓΡΑΦΗ</th>
              <th>ΚΑΤΗΓΟΡΙΑ</th><th>ΜΕΘΟΔΟΣ</th>
              <th class="num">ΠΟΣΟ</th><th class="num">ΠΛΗΡΩΜΕΝΟ</th>
              <th class="num">ΥΠΟΛΟΙΠΟ</th><th>STATUS</th><th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in obligations" :key="o.id">
              <td class="id-col">{{ o.id }}</td>
              <td>{{ o.date }}</td>
              <td class="desc-col">{{ o.description }}</td>
              <td><span class="cat-badge">{{ o.category }}</span></td>
              <td>{{ o.method }}</td>
              <td class="num red">{{ fmt(o.amount) }}</td>
              <td class="num">{{ fmt(o.paid) }}</td>
              <td class="num red">{{ fmt(o.balance) }}</td>
              <td><span class="badge" :class="statusClass(o.status)">⚡ {{ o.status }}</span></td>
              <td class="actions">
                <button class="icon-btn">📎</button>
                <button class="icon-btn">⚡</button>
                <button class="btn-exofli">✓ Εξόφληση</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ANALYSIS TAB -->
    <div v-if="activeTab === 'analysis'">
      <div class="analysis-filters">
        <select class="filter-select"><option>2026</option><option>2025</option></select>
        <select class="filter-select"><option>Απλήρωτες</option><option>Όλες</option></select>
        <select class="filter-select"><option>Μηνιαία Ανάλυση</option><option>Ετήσια Σύγκριση</option></select>
        <button class="btn-export ml-auto">📊 Export Excel</button>
      </div>

      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>Κατηγορία / Υποκατηγορία</th>
              <th v-for="m in months" :key="m" class="num">{{ m }}</th>
              <th class="num">Σύνολο</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="cat in categories" :key="cat.name">
              <tr class="cat-row">
                <td><span class="cat-dot" :style="{ color: cat.color }">●</span> <strong>{{ cat.name }}</strong></td>
                <td v-for="(m, i) in cat.months" :key="i" class="num" :class="m > 0 ? 'blue' : 'muted'">{{ m > 0 ? fmt(m) : '0,00 €' }}</td>
                <td class="num blue"><strong>{{ fmt(cat.total) }}</strong></td>
              </tr>
              <tr v-for="sub in cat.subs" :key="sub" class="sub-row">
                <td class="pl-20">{{ sub }}</td>
                <td v-for="(m, i) in cat.months" :key="i" class="num muted">{{ m > 0 ? fmt(m) : '0,00 €' }}</td>
                <td class="num muted">{{ fmt(cat.total) }}</td>
              </tr>
            </template>
            <tr class="total-row">
              <td><strong>ΓΕΝΙΚΟ ΣΥΝΟΛΟ</strong></td>
              <td v-for="(m, i) in months" :key="i" class="num">
                <strong>{{ fmt(categories.reduce((s, c) => s + c.months[i], 0)) }}</strong>
              </td>
              <td class="num green"><strong>{{ fmt(categories.reduce((s, c) => s + c.total, 0)) }}</strong></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.obligations-page { padding: 24px; color: #e0e6ed; }
.kpi-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 10px; margin-bottom: 20px; }
.kpi-card { background: #1e3448; border-radius: 8px; padding: 14px 16px; border-top: 3px solid #4FC3A1; }
.kpi-card.green { border-top-color: #4FC3A1; }
.kpi-card.red { border-top-color: #ef5350; }
.kpi-card.orange { border-top-color: #ff9800; }
.kpi-card.blue { border-top-color: #29b6f6; }
.kpi-card.white { border-top-color: #fff; }
.kpi-card.gray { border-top-color: #556677; }
.kpi-label { font-size: 0.68rem; color: #8899aa; letter-spacing: 0.5px; margin-bottom: 6px; }
.kpi-amount { font-size: 1rem; font-weight: 700; color: #fff; }
.kpi-card.green .kpi-amount { color: #4FC3A1; }
.kpi-card.red .kpi-amount { color: #ef5350; }
.kpi-card.orange .kpi-amount { color: #ff9800; }
.kpi-card.blue .kpi-amount { color: #29b6f6; }
.kpi-count { font-size: 0.72rem; color: #8899aa; margin-top: 2px; }
.tabs { display: flex; gap: 4px; margin-bottom: 16px; border-bottom: 1px solid #2a4a6a; }
.tab-btn { background: none; border: none; color: #8899aa; padding: 10px 16px; cursor: pointer; font-size: 0.9rem; border-bottom: 2px solid transparent; margin-bottom: -1px; }
.tab-btn.active { color: #4FC3A1; border-bottom-color: #4FC3A1; }
.filters-bar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.analysis-filters { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; flex-wrap: wrap; }
.filter-input, .filter-select { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; }
.btn-export, .btn-refresh { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 16px; border-radius: 6px; cursor: pointer; }
.ml-auto { margin-left: auto; }
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 0.83rem; }
.data-table th { background: #1a2f45; color: #8899aa; padding: 10px 12px; text-align: left; font-size: 0.72rem; border-bottom: 1px solid #2a4a6a; }
.data-table td { padding: 10px 12px; border-bottom: 1px solid #1e3448; }
.data-table tr:hover { background: #1e3448; }
.id-col { color: #8899aa; font-size: 0.8rem; }
.desc-col { max-width: 280px; }
.cat-badge { background: #2a4a6a; padding: 2px 8px; border-radius: 4px; font-size: 0.75rem; }
.num { text-align: right; font-family: monospace; }
.green { color: #4FC3A1; }
.red { color: #ef5350; }
.blue { color: #29b6f6; }
.muted { color: #556677; }
.badge { padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; }
.badge-orange { background: rgba(255,152,0,0.15); color: #ff9800; }
.badge-red { background: rgba(239,83,80,0.15); color: #ef5350; }
.badge-green { background: rgba(79,195,161,0.15); color: #4FC3A1; }
.actions { white-space: nowrap; }
.icon-btn { background: none; border: none; cursor: pointer; opacity: 0.6; }
.btn-exofli { background: rgba(79,195,161,0.15); color: #4FC3A1; border: 1px solid #4FC3A1; border-radius: 4px; padding: 2px 8px; font-size: 0.75rem; cursor: pointer; }
.cat-row { background: rgba(255,255,255,0.02); }
.sub-row td { padding-left: 24px; }
.pl-20 { padding-left: 24px !important; color: #8899aa; }
.total-row { background: #1a2f45; }
.cat-dot { margin-right: 6px; }
</style>
