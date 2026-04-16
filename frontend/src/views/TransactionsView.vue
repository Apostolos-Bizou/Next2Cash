<script setup>
import { ref, computed } from 'vue'

const search = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const selectedCategory = ref('Όλες')
const selectedType = ref('Όλοι')

const categories = ['Όλες', 'Λειτουργικά', 'Εξοπλισμός', 'Απασχόληση', 'Λοιπά']
const types = ['Όλοι', 'Έσοδο', 'Έξοδο']

const transactions = ref([
  { id: 4776, date: '09/04/26', description: 'ΠΑΠΑΚΙ ΑΓΟΡΑ DOMAIN Next2Cash', category: 'Εξοπλισμός', subcategory: 'Άδειες Χρήσης', income: null, expense: 12.40, balance: -194685.04 },
  { id: 4775, date: '09/04/26', description: 'MICROSOFT AZURE 03ος 2026', category: 'Εξοπλισμός', subcategory: 'Άδειες Χρήσης', income: null, expense: 112.42, balance: -194672.64 },
  { id: 4748, date: '07/04/26', description: 'EPASS', category: 'Λειτουργικά', subcategory: 'Έξοδα Κίνησης', income: null, expense: 50.00, balance: -194560.22 },
  { id: 4747, date: '02/04/26', description: 'ΕΣΟΔΑ ΠΡΟΣΩΠΙΚΑ ΤΡΑΠΕΖΑ', category: 'Έσοδα Β', subcategory: 'ΚΑΓΚΕΛΑΡΗΣ', income: 134.00, expense: null, balance: -194497.82 },
  { id: 4746, date: '02/04/26', description: 'ΕΣΟΔΑ ΠΡΟΣΩΠΙΚΑ ΤΡΑΠΕΖΑ', category: 'Έσοδα Β', subcategory: 'ΚΑΓΚΕΛΑΡΗΣ', income: 50.00, expense: null, balance: -194613.74 },
  { id: 4744, date: '02/04/26', description: 'ΒΑΡΙΑΣ ΣΙΜΟΣ ΜΕΤΡΗΤΑ', category: 'Έσοδα Β', subcategory: 'ΒΑΡΙΑΣ', income: 1116.00, expense: null, balance: -194663.74 },
  { id: 4774, date: '01/04/26', description: 'ΕNOIKIO 04ος 2026', category: 'Λειτουργικά', subcategory: 'Ενοίκιο', income: null, expense: 650.00, balance: -194666.44 },
  { id: 4773, date: '01/03/26', description: 'ΕNOIKIO 03ος 2026', category: 'Λειτουργικά', subcategory: 'Ενοίκιο', income: null, expense: 650.00, balance: -194816.44 },
])

const kpis = computed(() => ({
  kiniseis: transactions.value.length,
  pliromesTotal: transactions.value.filter(t => t.expense).reduce((s, t) => s + t.expense, 0),
  pliromeno: 8450.00,
  eispraxeis: transactions.value.filter(t => t.income).reduce((s, t) => s + t.income, 0),
  ypóloipo: -194685.04,
  aplirotes: 3
}))

const fmt = (n) => n ? new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2 }).format(n) + ' €' : '—'
</script>

<template>
  <div class="kiniseis-page">
    <!-- KPI Bar -->
    <div class="kpi-bar">
      <div class="kpi-item">
        <span class="kpi-label">ΚΙΝΗΣΕΙΣ</span>
        <span class="kpi-value">{{ kpis.kiniseis }}</span>
      </div>
      <div class="kpi-item red">
        <span class="kpi-label">ΠΛΗΡΩΜΕΣ</span>
        <span class="kpi-value">{{ fmt(kpis.pliromesTotal) }}</span>
      </div>
      <div class="kpi-item green">
        <span class="kpi-label">ΠΛΗΡΩΜΕΝΟ</span>
        <span class="kpi-value">{{ fmt(kpis.pliromeno) }}</span>
      </div>
      <div class="kpi-item green">
        <span class="kpi-label">ΕΙΣΠΡΑΞΕΙΣ</span>
        <span class="kpi-value">{{ fmt(kpis.eispraxeis) }}</span>
      </div>
      <div class="kpi-item red">
        <span class="kpi-label">ΥΠΟΛΟΙΠΟ</span>
        <span class="kpi-value">{{ fmt(kpis.ypóloipo) }}</span>
      </div>
      <div class="kpi-item orange">
        <span class="kpi-label">ΑΠΛΗΡΩΤΕΣ</span>
        <span class="kpi-value">{{ kpis.aplirotes }}</span>
      </div>
    </div>

    <!-- Filters -->
    <div class="filters-bar">
      <input v-model="search" class="filter-input" placeholder="Αναζήτηση..." />
      <input v-model="dateFrom" type="date" class="filter-input" />
      <input v-model="dateTo" type="date" class="filter-input" />
      <select v-model="selectedCategory" class="filter-select">
        <option v-for="c in categories" :key="c">{{ c }}</option>
      </select>
      <select v-model="selectedType" class="filter-select">
        <option v-for="t in types" :key="t">{{ t }}</option>
      </select>
      <button class="btn-export">⬇ Export</button>
      <button class="btn-new">+ Νέα</button>
    </div>

    <!-- Table -->
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>ΗΜ/ΝΙΑ</th>
            <th>ΠΕΡΙΓΡΑΦΗ</th>
            <th>ΚΑΤΗΓΟΡΙΑ</th>
            <th>ΥΠΟΚΑΤΗΓΟΡΙΑ</th>
            <th class="num">ΕΙΣΠΡΑΞΗ</th>
            <th class="num">ΠΛΗΡΩΜΗ</th>
            <th class="num">ΥΠΟΛΟΙΠΟ</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in transactions" :key="t.id">
            <td class="id-col">{{ t.id }}</td>
            <td>{{ t.date }}</td>
            <td class="desc-col">{{ t.description }}</td>
            <td><span class="cat-badge">{{ t.category }}</span></td>
            <td>{{ t.subcategory }}</td>
            <td class="num green">{{ t.income ? fmt(t.income) : '—' }}</td>
            <td class="num red">{{ t.expense ? fmt(t.expense) : '—' }}</td>
            <td class="num" :class="t.balance < 0 ? 'red' : 'green'">{{ fmt(t.balance) }}</td>
            <td class="actions">
              <button class="icon-btn">✏️</button>
              <button class="icon-btn">🗑️</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.kiniseis-page { padding: 24px; color: #e0e6ed; }
.kpi-bar { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
.kpi-item { background: #1e3448; border-radius: 8px; padding: 14px 20px; min-width: 140px; border-left: 3px solid #4FC3A1; }
.kpi-item.red { border-left-color: #ef5350; }
.kpi-item.green { border-left-color: #4FC3A1; }
.kpi-item.orange { border-left-color: #ff9800; }
.kpi-label { display: block; font-size: 0.7rem; color: #8899aa; letter-spacing: 1px; margin-bottom: 4px; }
.kpi-value { font-size: 1.1rem; font-weight: 700; color: #fff; }
.filters-bar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.filter-input, .filter-select { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; }
.btn-export { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 16px; border-radius: 6px; cursor: pointer; }
.btn-new { background: #4FC3A1; border: none; color: #162B40; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: 700; }
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
.data-table th { background: #1a2f45; color: #8899aa; padding: 10px 12px; text-align: left; font-size: 0.75rem; letter-spacing: 0.5px; border-bottom: 1px solid #2a4a6a; }
.data-table td { padding: 10px 12px; border-bottom: 1px solid #1e3448; }
.data-table tr:hover { background: #1e3448; }
.id-col { color: #8899aa; font-size: 0.8rem; }
.desc-col { max-width: 280px; }
.cat-badge { background: #2a4a6a; padding: 2px 8px; border-radius: 4px; font-size: 0.78rem; }
.num { text-align: right; font-family: monospace; }
.green { color: #4FC3A1; }
.red { color: #ef5350; }
.actions { text-align: center; }
.icon-btn { background: none; border: none; cursor: pointer; font-size: 0.85rem; opacity: 0.6; }
.icon-btn:hover { opacity: 1; }
</style>
