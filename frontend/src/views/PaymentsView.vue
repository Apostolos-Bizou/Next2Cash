<script setup>
import { ref } from 'vue'

const search = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const selectedStatus = ref('Όλα')
const selectedCategory = ref('Όλες')

const statuses = ['Όλα', 'Εκκρεμεί', 'Απλήρωτη', 'Πληρώθηκε']
const categories = ['Όλες', 'Λειτουργικά', 'Εξοπλισμός', 'Απασχόληση', 'Λοιπά']

const payments = ref([
  { id: 4776, date: '09/04/26', description: 'ΠΑΠΑΚΙ ΑΓΟΡΑ DOMAIN Next2Cash', category: 'Εξοπλισμός', method: 'Πειραιώς', amount: 12.40, paid: 0, balance: 12.40, status: 'Εκκρεμεί', progress: 0 },
  { id: 4775, date: '09/04/26', description: 'MICROSOFT AZURE 03ος 2026', category: 'Εξοπλισμός', method: 'Πειραιώς', amount: 112.42, paid: 0, balance: 112.42, status: 'Εκκρεμεί', progress: 0 },
  { id: 4774, date: '01/04/26', description: 'ΕNOIKIO 04ος 2026', category: 'Λειτουργικά', method: 'Πειραιώς', amount: 650.00, paid: 650.00, balance: 0, status: 'Απλήρωτη', progress: 100 },
  { id: 4773, date: '01/03/26', description: 'ΕNOIKIO 03ος 2026', category: 'Λειτουργικά', method: 'Πειραιώς', amount: 650.00, paid: 650.00, balance: 0, status: 'Απλήρωτη', progress: 100 },
  { id: 4748, date: '07/04/26', description: 'EPASS', category: 'Λειτουργικά', method: 'Πειραιώς', amount: 50.00, paid: 0, balance: 50.00, status: 'Εκκρεμεί', progress: 0 },
  { id: 4761, date: '28/01/26', description: 'ΤΑΛΙΑΔΟΡΟΣ ΛΟΓΙΣΤΗΣ Dn2Me UK', category: 'Λοιπά', method: 'Πειραιώς', amount: 3000.00, paid: 0, balance: 3000.00, status: 'Εκκρεμεί', progress: 0 },
  { id: 4762, date: '29/01/26', description: 'ΤΑΛΙΑΔΟΡΟΣ ΛΟΓΙΣΤΗΣ Dn2Me UK', category: 'Λοιπά', method: 'Τράπεζα', amount: 297.50, paid: 0, balance: 297.50, status: 'Εκκρεμεί', progress: 0 },
  { id: 4732, date: '06/04/26', description: 'ΠΑΠΑΚΙ doctornexttome.gr', category: 'Εξοπλισμός', method: 'Τράπεζα', amount: 36.88, paid: 0, balance: 36.88, status: 'Εκκρεμεί', progress: 0 },
])

const summary = {
  total: 4404,
  totalAmount: 1240289.17,
  unpaid: 359,
  unpaidAmount: 227887.23,
  partial: 0,
  partialAmount: 0,
  paid: 4035,
  paidAmount: 1004642.71,
  receipts: 368,
  receiptsAmount: 1045604.13
}

const fmt = (n) => new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2 }).format(n) + ' €'

const statusClass = (s) => {
  if (s === 'Εκκρεμεί') return 'badge-orange'
  if (s === 'Απλήρωτη') return 'badge-red'
  return 'badge-green'
}
</script>

<template>
  <div class="plirome-page">
    <!-- Summary Cards -->
    <div class="summary-cards">
      <div class="sum-card">
        <div class="sum-label">{{ summary.total }} πληρωμές — Σύνολο Κινήσεων</div>
        <div class="sum-amount white">{{ fmt(summary.totalAmount) }}</div>
      </div>
      <div class="sum-card red">
        <div class="sum-label">{{ summary.unpaid }} κινήσεις — Απλήρωτες</div>
        <div class="sum-amount red">{{ fmt(summary.unpaidAmount) }}</div>
      </div>
      <div class="sum-card orange">
        <div class="sum-label">{{ summary.partial }} κινήσεις — Μερ. Πληρωμένες</div>
        <div class="sum-amount orange">{{ fmt(summary.partialAmount) }}</div>
      </div>
      <div class="sum-card green">
        <div class="sum-label">{{ summary.paid }} κινήσεις — Πληρωμένες</div>
        <div class="sum-amount green">{{ fmt(summary.paidAmount) }}</div>
      </div>
      <div class="sum-card teal">
        <div class="sum-label">{{ summary.receipts }} εισπράξεις — Εισπραχθείσες</div>
        <div class="sum-amount teal">{{ fmt(summary.receiptsAmount) }}</div>
      </div>
    </div>

    <!-- Filters -->
    <div class="filters-bar">
      <input v-model="search" class="filter-input" placeholder="Αναζήτηση ID, περιγραφή..." />
      <input v-model="dateFrom" type="date" class="filter-input" />
      <input v-model="dateTo" type="date" class="filter-input" />
      <select v-model="selectedStatus" class="filter-select">
        <option v-for="s in statuses" :key="s">{{ s }}</option>
      </select>
      <select v-model="selectedCategory" class="filter-select">
        <option v-for="c in categories" :key="c">{{ c }}</option>
      </select>
      <button class="btn-export">⬇ Export</button>
      <button class="btn-refresh">↻ Ανανέωση</button>
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
            <th>ΜΕΘΟΔΟΣ</th>
            <th class="num">ΠΟΣΟ</th>
            <th class="num">ΠΛΗΡΩΜΕΝΟ</th>
            <th class="num">ΥΠΟΛΟΙΠΟ</th>
            <th>STATUS</th>
            <th>ΠΡΟΟΔΟΣ</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in payments" :key="p.id">
            <td class="id-col">{{ p.id }}</td>
            <td>{{ p.date }}</td>
            <td class="desc-col">{{ p.description }}</td>
            <td><span class="cat-badge">{{ p.category }}</span></td>
            <td>{{ p.method }}</td>
            <td class="num red">{{ fmt(p.amount) }}</td>
            <td class="num green">{{ fmt(p.paid) }}</td>
            <td class="num red">{{ fmt(p.balance) }}</td>
            <td><span class="badge" :class="statusClass(p.status)">⚡ {{ p.status }}</span></td>
            <td>
              <div class="progress-bar">
                <div class="progress-fill" :style="{ width: p.progress + '%' }"></div>
              </div>
              <span class="progress-text">{{ p.progress }}%</span>
            </td>
            <td class="actions">
              <button class="icon-btn">📎</button>
              <button class="icon-btn">✏️</button>
              <button class="icon-btn green-btn">✓ Εξόφληση</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.plirome-page { padding: 24px; color: #e0e6ed; }
.summary-cards { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
.sum-card { background: #1e3448; border-radius: 8px; padding: 16px 20px; flex: 1; min-width: 180px; border-top: 3px solid #4FC3A1; }
.sum-card.red { border-top-color: #ef5350; }
.sum-card.orange { border-top-color: #ff9800; }
.sum-card.green { border-top-color: #4FC3A1; }
.sum-card.teal { border-top-color: #29b6f6; }
.sum-label { font-size: 0.75rem; color: #8899aa; margin-bottom: 8px; }
.sum-amount { font-size: 1.2rem; font-weight: 700; }
.sum-amount.white { color: #fff; }
.sum-amount.red { color: #ef5350; }
.sum-amount.orange { color: #ff9800; }
.sum-amount.green { color: #4FC3A1; }
.sum-amount.teal { color: #29b6f6; }
.filters-bar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.filter-input, .filter-select { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; }
.btn-export, .btn-refresh { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 16px; border-radius: 6px; cursor: pointer; }
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.data-table th { background: #1a2f45; color: #8899aa; padding: 10px 12px; text-align: left; font-size: 0.72rem; letter-spacing: 0.5px; border-bottom: 1px solid #2a4a6a; }
.data-table td { padding: 10px 12px; border-bottom: 1px solid #1e3448; }
.data-table tr:hover { background: #1e3448; }
.id-col { color: #8899aa; font-size: 0.8rem; }
.desc-col { max-width: 240px; }
.cat-badge { background: #2a4a6a; padding: 2px 8px; border-radius: 4px; font-size: 0.75rem; }
.num { text-align: right; font-family: monospace; }
.green { color: #4FC3A1; }
.red { color: #ef5350; }
.badge { padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; }
.badge-orange { background: rgba(255,152,0,0.15); color: #ff9800; }
.badge-red { background: rgba(239,83,80,0.15); color: #ef5350; }
.badge-green { background: rgba(79,195,161,0.15); color: #4FC3A1; }
.progress-bar { background: #2a4a6a; border-radius: 4px; height: 6px; width: 80px; display: inline-block; vertical-align: middle; }
.progress-fill { background: #4FC3A1; height: 6px; border-radius: 4px; }
.progress-text { font-size: 0.75rem; color: #8899aa; margin-left: 6px; }
.actions { white-space: nowrap; }
.icon-btn { background: none; border: none; cursor: pointer; font-size: 0.85rem; opacity: 0.6; }
.icon-btn:hover { opacity: 1; }
.green-btn { background: rgba(79,195,161,0.15); color: #4FC3A1; border: 1px solid #4FC3A1; border-radius: 4px; padding: 2px 8px; font-size: 0.75rem; cursor: pointer; }
</style>
