<script setup>
import { ref, computed } from 'vue'

const selectedKartela = ref(0)
const search = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const selectedStatus = ref('Όλες')
const selectedCategory = ref('Όλες κατηγορίες')

const karteles = ref([
  { id: 0, name: 'ΜΑΛΑΜΙΤΣΗΣ ΛΕΩΝΙΔΑΣ ΛΟΓΙΣΤΗΣ', count: 40, color: '#29b6f6' },
  { id: 1, name: 'MICROSOFT AZURE', count: 89, color: '#4FC3A1' },
  { id: 2, name: 'ΚΟΙΝΟΧΡΗΣΤΑ', count: 41, color: '#ff9800' },
  { id: 3, name: 'ΚΙΝΗΤΟ', count: 133, color: '#ef5350' },
  { id: 4, name: 'ΕΝΟΙΚΙΟ', count: 157, color: '#ab47bc' },
  { id: 5, name: 'ΔΕΗ', count: 40, color: '#29b6f6' },
  { id: 6, name: 'ΤΑΛΙΑΔΟΡΟΣ Dn2Me UK', count: 14, color: '#4FC3A1' },
  { id: 7, name: 'ΠΑΠΑΚΙ', count: 80, color: '#ff9800' },
  { id: 8, name: 'GOOGLE SERVER', count: 105, color: '#ef5350' },
  { id: 9, name: 'ΕΣΟΔΑ ΒΑΡΙΑΣ', count: 129, color: '#4FC3A1' },
  { id: 10, name: 'EPASS', count: 114, color: '#29b6f6' },
  { id: 11, name: 'APPLE', count: 7, color: '#8899aa' },
])

const transactions = ref([
  { id: -26, date: '05/04/2026', description: 'Πληρωμή #4656 — ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ...', category: 'Απασχόληση', method: 'Πειραιώς', amount: 186.00, paid: 186.00, balance: 0, paidDate: '05/04/2026', status: 'Εξοφλημένη' },
  { id: -28, date: '05/04/2026', description: 'Πληρωμή #4657 — ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ...', category: 'Απασχόληση', method: 'Πειραιώς', amount: 186.00, paid: 186.00, balance: 0, paidDate: '05/04/2026', status: 'Εξοφλημένη' },
  { id: -29, date: '05/04/2026', description: 'Πληρωμή #4658 — ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ...', category: 'Απασχόληση', method: 'Πειραιώς', amount: 186.00, paid: 186.00, balance: 0, paidDate: '05/04/2026', status: 'Εξοφλημένη' },
  { id: -30, date: '05/04/2026', description: 'Πληρωμή #4699 — ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ...', category: 'Απασχόληση', method: 'Πειραιώς', amount: 186.00, paid: 186.00, balance: 0, paidDate: '05/04/2026', status: 'Εξοφλημένη' },
  { id: 4700, date: '26/02/2026', description: '4700 - ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ ΛΟΓΙΣΤΙΚΑ 2ος...', category: 'Απασχόληση', method: 'Τράπεζα', amount: 186.00, paid: 186.00, balance: 0, paidDate: '05/04/2026', status: 'Εξοφλημένη' },
  { id: 4699, date: '30/01/2026', description: '4699 - ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ ΛΟΓΙΣΤΙΚΑ 1ος...', category: 'Απασχόληση', method: 'Τράπεζα', amount: 186.00, paid: 186.00, balance: 0, paidDate: '05/04/2026', status: 'Εξοφλημένη' },
  { id: 2793, date: '31/08/2020', description: 'ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ ΛΟΓΙΣΤΙΚΑ 8ος 2020', category: 'Απασχόληση', method: 'Μετρητά', amount: 650.00, paid: 0, balance: 650.00, paidDate: '—', status: 'Απλήρωτη' },
  { id: 2792, date: '31/08/2020', description: 'ΜΑΛΑΜΙΤΣΗΣ ΓΙΑ ΛΟΓΙΣΤΙΚΑ 8ος 2020', category: 'Απασχόληση', method: 'Τράπεζα', amount: 124.00, paid: 0, balance: 124.00, paidDate: '—', status: 'Απλήρωτη' },
])

const currentKartela = computed(() => karteles.value[selectedKartela.value])

const kpis = computed(() => ({
  total: { label: 'ΣΥΝΟΛΟ ΚΙΝΗΣΕΩΝ', amount: '5.702,00 €', count: '34 κινήσεις' },
  paid: { label: 'ΕΞΟΦΛΗΜΕΝΕΣ', amount: '4.154,00 €', count: '30 κινήσεις', color: 'green' },
  unpaid: { label: 'ΑΠΛΗΡΩΤΕΣ', amount: '1.548,00 €', count: '4 κινήσεις', color: 'red' },
  income: { label: 'ΕΙΣΠΡΑΞΕΙΣ', amount: '0,00 €', count: '0 κινήσεις', color: 'teal' },
  urgent: { label: 'ΕΚΚΡΕΜΕΙΣ', amount: '0,00 €', count: '0 κινήσεις', color: 'orange' },
}))

const fmt = (n) => new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2 }).format(n) + ' €'

const statusClass = (s) => {
  if (s === 'Εξοφλημένη') return 'badge-green'
  if (s === 'Απλήρωτη') return 'badge-red'
  if (s === 'Εκκρεμεί') return 'badge-orange'
  return 'badge-gray'
}
</script>

<template>
  <div class="karteles-page">
    <div class="karteles-layout">
      <!-- Sidebar -->
      <div class="karteles-sidebar">
        <div class="sidebar-header">
          <span>📋 Καρτέλες</span>
          <button class="btn-add">+</button>
        </div>
        <div
          v-for="k in karteles"
          :key="k.id"
          class="kartela-item"
          :class="{ active: selectedKartela === k.id }"
          @click="selectedKartela = k.id"
        >
          <span class="kartela-dot" :style="{ color: k.color }">📋</span>
          <span class="kartela-name">{{ k.name }}</span>
          <span class="kartela-count">{{ k.count }}</span>
        </div>
      </div>

      <!-- Main Content -->
      <div class="karteles-main">
        <div class="kartela-header">
          <h2>📋 {{ currentKartela.name }}</h2>
          <div class="header-actions">
            <button class="btn-action">📊 Excel</button>
            <button class="btn-action">📄 PDF</button>
            <button class="btn-action">✏️</button>
            <button class="btn-action red">🗑️</button>
          </div>
        </div>

        <!-- Filters -->
        <div class="filters-bar">
          <input v-model="search" class="filter-input flex-1" placeholder="Αναζήτηση ID, περιγραφή..." />
          <input v-model="dateFrom" type="date" class="filter-input" />
          <input v-model="dateTo" type="date" class="filter-input" />
          <select v-model="selectedStatus" class="filter-select">
            <option>Όλες</option>
            <option>Απλήρωτες</option>
            <option>Εξοφλημένες</option>
            <option>Εκκρεμείς</option>
          </select>
          <select v-model="selectedCategory" class="filter-select">
            <option>Όλες κατηγορίες</option>
            <option>Απασχόληση</option>
            <option>Λειτουργικά</option>
          </select>
        </div>

        <!-- KPI Cards -->
        <div class="kpi-row">
          <div v-for="(kpi, key) in kpis" :key="key" class="kpi-card" :class="kpi.color">
            <div class="kpi-label">{{ kpi.label }}</div>
            <div class="kpi-amount">{{ kpi.amount }}</div>
            <div class="kpi-count">{{ kpi.count }}</div>
          </div>
        </div>

        <!-- Table -->
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th><th>ΗΜ/ΝΙΑ</th><th>ΠΕΡΙΓΡΑΦΗ</th>
                <th>ΚΑΤΗΓΟΡΙΑ</th><th>ΜΕΘΟΔΟΣ</th>
                <th class="num">ΠΟΣΟ</th><th class="num">ΠΛΗΡΩΜΕΝΟ</th>
                <th class="num">ΥΠΟΛΟΙΠΟ</th><th>ΗΜ/ΝΙΑ ΠΛΗΡ.</th>
                <th>STATUS</th><th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in transactions" :key="t.id">
                <td class="id-col">{{ t.id }}</td>
                <td>{{ t.date }}</td>
                <td class="desc-col">{{ t.description }}</td>
                <td><span class="cat-badge">{{ t.category }}</span></td>
                <td>{{ t.method }}</td>
                <td class="num">{{ fmt(t.amount) }}</td>
                <td class="num green">{{ fmt(t.paid) }}</td>
                <td class="num" :class="t.balance > 0 ? 'red' : ''">{{ t.balance > 0 ? fmt(t.balance) : '0,00 €' }}</td>
                <td class="meta">{{ t.paidDate }}</td>
                <td><span class="badge" :class="statusClass(t.status)">{{ t.status }}</span></td>
                <td class="actions">
                  <button class="icon-btn">📎</button>
                  <button v-if="t.status === 'Απλήρωτη'" class="btn-exofli">✓ Εξόφληση</button>
                  <button v-else class="icon-btn green">✓</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.karteles-page { padding: 24px; color: #e0e6ed; height: calc(100vh - 60px); }
.karteles-layout { display: grid; grid-template-columns: 220px 1fr; gap: 20px; height: 100%; }
.karteles-sidebar { background: #1e3448; border-radius: 10px; padding: 12px; overflow-y: auto; }
.sidebar-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 4px 12px; font-size: 0.85rem; font-weight: 600; color: #4FC3A1; border-bottom: 1px solid #2a4a6a; margin-bottom: 8px; }
.btn-add { background: #4FC3A1; color: #0f1e2e; border: none; width: 24px; height: 24px; border-radius: 4px; cursor: pointer; font-weight: 700; }
.kartela-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.kartela-item:hover { background: #2a4a6a; }
.kartela-item.active { background: rgba(79,195,161,0.12); color: #4FC3A1; }
.kartela-name { flex: 1; }
.kartela-count { background: #2a4a6a; padding: 1px 6px; border-radius: 10px; font-size: 0.7rem; color: #8899aa; }
.karteles-main { overflow-y: auto; }
.kartela-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.kartela-header h2 { margin: 0; font-size: 1.1rem; color: #29b6f6; }
.header-actions { display: flex; gap: 8px; }
.btn-action { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 6px 12px; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-action.red { color: #ef5350; border-color: #ef5350; }
.filters-bar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.filter-input, .filter-select { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; }
.flex-1 { flex: 1; }
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-bottom: 16px; }
.kpi-card { background: #1e3448; border-radius: 8px; padding: 12px 14px; border-top: 3px solid #556677; }
.kpi-card.green { border-top-color: #4FC3A1; }
.kpi-card.red { border-top-color: #ef5350; }
.kpi-card.teal { border-top-color: #29b6f6; }
.kpi-card.orange { border-top-color: #ff9800; }
.kpi-label { font-size: 0.68rem; color: #8899aa; margin-bottom: 4px; }
.kpi-amount { font-size: 1rem; font-weight: 700; color: #fff; }
.kpi-card.green .kpi-amount { color: #4FC3A1; }
.kpi-card.red .kpi-amount { color: #ef5350; }
.kpi-card.teal .kpi-amount { color: #29b6f6; }
.kpi-card.orange .kpi-amount { color: #ff9800; }
.kpi-count { font-size: 0.7rem; color: #8899aa; }
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 0.83rem; }
.data-table th { background: #1a2f45; color: #8899aa; padding: 8px 12px; text-align: left; font-size: 0.72rem; border-bottom: 1px solid #2a4a6a; }
.data-table td { padding: 8px 12px; border-bottom: 1px solid #1e3448; }
.data-table tr:hover { background: #1e3448; }
.id-col { color: #8899aa; font-size: 0.8rem; }
.desc-col { max-width: 240px; }
.cat-badge { background: #2a4a6a; padding: 2px 8px; border-radius: 4px; font-size: 0.72rem; }
.num { text-align: right; font-family: monospace; }
.green { color: #4FC3A1; }
.red { color: #ef5350; }
.meta { color: #8899aa; font-size: 0.8rem; }
.badge { padding: 2px 8px; border-radius: 10px; font-size: 0.72rem; font-weight: 600; }
.badge-green { background: rgba(79,195,161,0.15); color: #4FC3A1; }
.badge-red { background: rgba(239,83,80,0.15); color: #ef5350; }
.badge-orange { background: rgba(255,152,0,0.15); color: #ff9800; }
.badge-gray { background: rgba(136,153,170,0.15); color: #8899aa; }
.actions { white-space: nowrap; }
.icon-btn { background: none; border: none; cursor: pointer; opacity: 0.6; }
.btn-exofli { background: rgba(79,195,161,0.15); color: #4FC3A1; border: 1px solid #4FC3A1; border-radius: 4px; padding: 2px 8px; font-size: 0.72rem; cursor: pointer; }
</style>
