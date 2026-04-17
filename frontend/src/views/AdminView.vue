<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'

const activeTab = ref('users')

const tabs = [
  { id: 'users', label: 'Χρήστες', icon: '👥' },
  { id: 'categories', label: 'Κατηγορίες', icon: '🏷' },
  { id: 'accounts', label: 'Λογαριασμοί', icon: '📋' },
  { id: 'banks', label: 'Τράπεζες', icon: '🏦' },
  { id: 'audit', label: 'Audit Log', icon: '🕐' },
]

// ═══════════════════════════════════════════════════════════════
//  USERS — REAL API (Deploy 3B-lite, 17 Apr 2026)
// ═══════════════════════════════════════════════════════════════
const users = ref([])
const usersLoading = ref(false)
const usersError = ref(null)

// UI role labels (Greek) vs backend values (English lowercase)
const ROLE_LABELS = {
  admin:      'Διαχειριστής',
  user:       'Χρήστης',
  accountant: 'Λογιστής',
  viewer:     'Θεατής'
}

const ROLE_CLASSES = {
  admin:      'role-red',
  user:       'role-blue',
  accountant: 'role-orange',
  viewer:     'role-gray'
}

async function fetchUsers() {
  usersLoading.value = true
  usersError.value = null
  try {
    const res = await api.get('/api/admin/users')
    if (res.data.success) {
      users.value = res.data.data
    } else {
      usersError.value = res.data.error || 'Σφάλμα φόρτωσης'
    }
  } catch (e) {
    console.error('fetchUsers error:', e)
    usersError.value = e.response?.data?.error || 'Σφάλμα σύνδεσης'
  } finally {
    usersLoading.value = false
  }
}

function formatLastLogin(iso) {
  if (!iso) return 'Ποτέ'
  try {
    const d = new Date(iso)
    return d.toLocaleDateString('el-GR', { day: '2-digit', month: '2-digit', year: '2-digit' })
  } catch {
    return '—'
  }
}

function userInitial(u) {
  const name = u.displayName || u.username || '?'
  return name.charAt(0).toUpperCase()
}

const newUser = ref({ username: '', name: '', password: '', role: 'viewer' })

// Placeholder — real create TODO in next deploy
const addUser = () => {
  alert('Η δημιουργία χρηστών θα ενεργοποιηθεί σε επόμενο deploy.')
}

// ═══════════════════════════════════════════════════════════════
//  CATEGORIES — still mock (will be replaced in future deploy)
// ═══════════════════════════════════════════════════════════════
const categories = ref([
  { id: 1, name: 'ΛΕΙΤΟΥΡΓΙΚΑ', subs: ['Ενοίκιο', 'Τηλέφωνα', 'Λογαριασμοί', 'Γραφείο', 'Έξοδα Κίνησης', 'Γεύματα Εργασίας', 'Leasing Αυτοκινήτου', 'Λοιπά'] },
  { id: 2, name: 'ΕΞΟΠΛΙΣΜΟΣ', subs: ['Η/Υ', 'Άδειες Χρήσης', 'Λογισμικά / ERP', 'Έπιπλα / Εξοπλισμός'] },
  { id: 3, name: 'ΑΠΑΣΧΟΛΗΣΗ', subs: ['Γεν. Διευθυντής', 'Finance', 'Operation 1', 'Operation 2', 'Νομική Υποστήριξη'] },
  { id: 4, name: 'ΛΟΙΠΑ', subs: ['Μέτοχος', 'Έκτακτα', 'Dn2Me-UK'] },
  { id: 5, name: 'ΕΙΣΠΡΑΞΕΙΣ', subs: ['ΚΑΓΚΕΛΑΡΗΣ', 'ΒΑΡΙΑΣ', 'HealthPass', 'Έσοδα'] },
  { id: 6, name: 'ΕΣΟΔΑ Β', subs: ['ΚΑΓΚΕΛΑΡΗΣ', 'ΒΑΡΙΑΣ'] },
])
const newCategory = ref('')

const addCategory = () => {
  if (!newCategory.value) return
  categories.value.push({ id: Date.now(), name: newCategory.value.toUpperCase(), subs: [] })
  newCategory.value = ''
}

// BANKS (still mock)
const banks = ref([
  { id: 1, name: 'Πειραιώς', bank: 'Πειραιώς', type: 'checking', balance: 248.97, currency: 'EUR', updated: '15/04/26' },
  { id: 2, name: 'Μετρητά', bank: 'Ταμείο', type: 'cash', balance: 0.00, currency: 'EUR', updated: '05/04/26' },
  { id: 3, name: 'Πορτοφόλι', bank: 'Ταμείο', type: 'cash', balance: 0.00, currency: 'EUR', updated: '05/04/26' },
  { id: 4, name: 'Revolut GBP', bank: 'Revolut', type: 'revolut', balance: 0.00, currency: 'GBP', updated: '05/04/26' },
  { id: 5, name: 'Revolut USD', bank: 'Revolut', type: 'revolut', balance: 0.00, currency: 'USD', updated: '05/04/26' },
  { id: 6, name: 'Revolut EUR', bank: 'Revolut', type: 'revolut', balance: 2.78, currency: 'EUR', updated: '05/04/26' },
])
const newBank = ref({ name: '', bank: '', type: 'checking', balance: 0, currency: 'EUR' })

const addBank = () => {
  if (!newBank.value.name) return
  banks.value.push({ id: Date.now(), ...newBank.value, updated: new Date().toLocaleDateString('el-GR') })
  newBank.value = { name: '', bank: '', type: 'checking', balance: 0, currency: 'EUR' }
}

// ACCOUNTS (still mock)
const accounts = ref([
  'Λογαριασμός ΚΑΓΚΕΛΑΡΗΣ', 'Λογαριασμοί', 'Έξοδα Κίνησης', 'Λογισμικά/ERP',
  'Ενοίκιο', 'Τηλέφωνα', 'Άδειες Χρήσης', 'Operation 1', 'Finance', 'Dn2Me-UK'
])
const paymentMethods = ref(['Μετρητά', 'Τράπεζα', 'Απόδειξη', 'HSBC', 'Πειραιώς'])
const newAccount = ref('')
const newMethod = ref('')

// AUDIT LOG (still mock)
const auditLog = ref([
  { date: '15/04/26 07:17', user: 'admin', action: 'LOGIN', details: 'Login successful' },
  { date: '15/04/26 06:29', user: 'admin', action: 'CREATE', details: '{"type":"income","amount":150}' },
  { date: '15/04/26 06:29', user: 'admin', action: 'UPLOAD', details: 'File: 4777 - ΕΣΟΔΑ ΠΡΟΣΩΠΙΚΑ' },
  { date: '15/04/26 06:26', user: 'admin', action: 'PAYMENT', details: '{"paymentId":62,"amount":12.4}' },
])

const actionClass = (a) => {
  if (a === 'LOGIN') return 'badge-blue'
  if (a === 'CREATE') return 'badge-green'
  if (a === 'PAYMENT') return 'badge-orange'
  if (a === 'UPLOAD') return 'badge-purple'
  return 'badge-gray'
}

const fmt = (n) => new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2 }).format(n) + ' €'

// Fetch users on mount
onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div class="admin-page">
    <!-- Tabs -->
    <div class="tabs">
      <button v-for="tab in tabs" :key="tab.id" class="tab-btn" :class="{ active: activeTab === tab.id }" @click="activeTab = tab.id">
        {{ tab.icon }} {{ tab.label }}
        <span v-if="tab.id === 'users'" class="tab-count">{{ users.length }}</span>
        <span v-if="tab.id === 'categories'" class="tab-count">{{ categories.length }}</span>
        <span v-if="tab.id === 'banks'" class="tab-count">{{ banks.length }}</span>
      </button>
    </div>

    <!-- USERS TAB -->
    <div v-if="activeTab === 'users'" class="tab-content">
      <div class="section-card">
        <h3>👥 Νέος Χρήστης</h3>
        <div class="form-row">
          <input v-model="newUser.username" placeholder="Username *" class="form-input" />
          <input v-model="newUser.name" placeholder="Εμφανιζόμενο Όνομα" class="form-input" />
          <input v-model="newUser.password" type="password" placeholder="Κωδικός *" class="form-input" />
          <select v-model="newUser.role" class="form-select">
            <option value="admin">Διαχειριστής</option>
            <option value="user">Χρήστης</option>
            <option value="accountant">Λογιστής</option>
            <option value="viewer">Θεατής</option>
          </select>
          <button class="btn-primary" @click="addUser">+ Δημιουργία</button>
        </div>
        <div class="note-info">
          ℹ Η δημιουργία χρηστών θα ενεργοποιηθεί σε επόμενο deploy. Σήμερα μόνο προβολή.
        </div>
      </div>

      <div class="section-card">
        <h3>
          👥 Χρήστες Συστήματος
          <button class="btn-refresh-sm" @click="fetchUsers" :disabled="usersLoading">
            {{ usersLoading ? '⟳' : '↻' }} Ανανέωση
          </button>
        </h3>

        <div v-if="usersLoading" class="loading-state">
          ⟳ Φόρτωση χρηστών...
        </div>

        <div v-else-if="usersError" class="error-state">
          ❌ {{ usersError }}
        </div>

        <div v-else-if="users.length === 0" class="empty-state">
          Δεν υπάρχουν χρήστες.
        </div>

        <div v-else class="user-list">
          <div v-for="u in users" :key="u.id" class="user-row" :class="{ 'inactive': !u.isActive }">
            <div class="user-avatar">{{ userInitial(u) }}</div>
            <div class="user-info">
              <div class="user-name">
                {{ u.displayName || u.username }}
                <span v-if="!u.isActive" class="inactive-badge">Ανενεργός</span>
              </div>
              <div class="user-meta">
                @{{ u.username }} · Τελευταίο login: {{ formatLastLogin(u.lastLogin) }}
              </div>
            </div>
            <span class="role-badge" :class="ROLE_CLASSES[u.role] || 'role-gray'">
              {{ ROLE_LABELS[u.role] || u.role }}
            </span>
            <button class="icon-btn" title="Επεξεργασία (coming soon)" @click="alert('Η επεξεργασία θα ενεργοποιηθεί σε επόμενο deploy.')">✏️</button>
          </div>
        </div>
      </div>
    </div>

    <!-- CATEGORIES TAB (mock) -->
    <div v-if="activeTab === 'categories'" class="tab-content">
      <div class="section-card">
        <div class="note-warning">⚠ Mock data — real API σε επόμενο deploy</div>
        <h3>🏷 Νέα Κατηγορία</h3>
        <div class="form-row">
          <input v-model="newCategory" placeholder="π.χ. ΛΕΙΤΟΥΡΓΙΚΑ" class="form-input" />
          <button class="btn-primary" @click="addCategory">+ Προσθήκη</button>
        </div>
      </div>
      <div class="section-card">
        <h3>🏷 Κατηγορίες</h3>
        <div v-for="cat in categories" :key="cat.id" class="cat-row">
          <div class="cat-header">
            <span class="cat-dot">●</span>
            <span class="cat-name">{{ cat.name }}</span>
            <span class="cat-count">{{ cat.subs.length }} υποκατηγορίες</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ACCOUNTS TAB (mock) -->
    <div v-if="activeTab === 'accounts'" class="tab-content">
      <div class="section-card">
        <div class="note-warning">⚠ Mock data — real API σε επόμενο deploy</div>
        <h3>📋 Νέος Λογαριασμός</h3>
        <div class="form-row">
          <input v-model="newAccount" placeholder="π.χ. ΚΑΓΚΕΛΑΡΗΣ" class="form-input" />
          <button class="btn-primary" @click="accounts.push(newAccount); newAccount=''">+ Προσθήκη</button>
        </div>
        <div class="simple-list">
          <div v-for="a in accounts" :key="a" class="simple-row">
            <span class="dot-blue">●</span> {{ a }}
          </div>
        </div>
      </div>
      <div class="section-card">
        <h3>💳 Μέθοδοι Πληρωμής</h3>
        <div class="form-row">
          <input v-model="newMethod" placeholder="π.χ. Paypal" class="form-input" />
          <button class="btn-primary" @click="paymentMethods.push(newMethod); newMethod=''">+ Προσθήκη</button>
        </div>
        <div class="simple-list">
          <div v-for="m in paymentMethods" :key="m" class="simple-row">
            <span class="dot-green">●</span> {{ m }}
          </div>
        </div>
      </div>
    </div>

    <!-- BANKS TAB (mock) -->
    <div v-if="activeTab === 'banks'" class="tab-content">
      <div class="section-card">
        <div class="note-warning">⚠ Mock data — real API σε επόμενο deploy</div>
        <h3>🏦 Νέος Τραπεζικός Λογαριασμός</h3>
        <div class="form-row">
          <input v-model="newBank.name" placeholder="π.χ. Eurobank" class="form-input" />
          <input v-model="newBank.bank" placeholder="Τράπεζα" class="form-input" />
          <select v-model="newBank.type" class="form-select">
            <option value="checking">Τρεχούμενος</option>
            <option value="cash">Μετρητά</option>
            <option value="revolut">Revolut</option>
          </select>
          <button class="btn-primary" @click="addBank">+ Δημιουργία</button>
        </div>
      </div>
      <div class="section-card">
        <h3>🏦 Τραπεζικοί Λογαριασμοί</h3>
        <div v-for="b in banks" :key="b.id" class="bank-row">
          <span class="bank-dot green">●</span>
          <div class="bank-info">
            <div class="bank-name">{{ b.name }}</div>
            <div class="bank-meta">{{ b.bank }} · {{ b.type }} · {{ b.currency }}</div>
          </div>
          <div class="bank-balance" :class="b.balance >= 0 ? 'green' : 'red'">{{ fmt(b.balance) }}</div>
        </div>
      </div>
    </div>

    <!-- AUDIT LOG TAB (mock) -->
    <div v-if="activeTab === 'audit'" class="tab-content">
      <div class="section-card">
        <div class="note-warning">⚠ Mock data — real API σε επόμενο deploy</div>
        <h3>🕐 Τελευταίες Ενέργειες</h3>
        <table class="data-table">
          <thead>
            <tr><th>ΗΜΕΡΟΜΗΝΙΑ</th><th>ΧΡΗΣΤΗΣ</th><th>ΕΝΕΡΓΕΙΑ</th><th>ΛΕΠΤΟΜΕΡΕΙΕΣ</th></tr>
          </thead>
          <tbody>
            <tr v-for="log in auditLog" :key="log.date">
              <td class="meta">{{ log.date }}</td>
              <td>{{ log.user }}</td>
              <td><span class="badge" :class="actionClass(log.action)">{{ log.action }}</span></td>
              <td class="meta">{{ log.details }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; color: #e0e6ed; }
.tabs { display: flex; gap: 4px; margin-bottom: 24px; border-bottom: 1px solid #2a4a6a; padding-bottom: 0; flex-wrap: wrap; }
.tab-btn { background: none; border: none; color: #8899aa; padding: 10px 16px; cursor: pointer; font-size: 0.9rem; border-bottom: 2px solid transparent; margin-bottom: -1px; }
.tab-btn.active { color: #4FC3A1; border-bottom-color: #4FC3A1; }
.tab-btn:hover { color: #e0e6ed; }
.tab-count { background: #2a4a6a; color: #8899aa; font-size: 0.7rem; padding: 1px 6px; border-radius: 10px; margin-left: 6px; }
.tab-content { display: flex; flex-direction: column; gap: 20px; }
.section-card { background: #1e3448; border-radius: 10px; padding: 20px; }
.section-card h3 { margin: 0 0 16px; font-size: 0.95rem; color: #4FC3A1; display: flex; align-items: center; justify-content: space-between; }
.form-row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.form-input { background: #162B40; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; flex: 1; min-width: 140px; }
.form-select { background: #162B40; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; }
.btn-primary { background: #4FC3A1; color: #0f1e2e; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: 700; white-space: nowrap; }
.btn-refresh-sm { background: #162B40; border: 1px solid #2a4a6a; color: #8899aa; padding: 4px 10px; border-radius: 4px; cursor: pointer; font-size: 0.75rem; }
.btn-refresh-sm:hover:not(:disabled) { color: #4FC3A1; border-color: #4FC3A1; }
.btn-refresh-sm:disabled { opacity: 0.5; cursor: not-allowed; }
.note-info { background: rgba(41,182,246,0.08); border-left: 3px solid #29b6f6; padding: 8px 12px; margin-top: 12px; border-radius: 4px; font-size: 0.8rem; color: #8899aa; }
.note-warning { background: rgba(255,152,0,0.08); border-left: 3px solid #ff9800; padding: 8px 12px; margin-bottom: 16px; border-radius: 4px; font-size: 0.8rem; color: #ff9800; font-weight: 600; }
.loading-state, .empty-state { padding: 24px; text-align: center; color: #8899aa; }
.error-state { padding: 16px; background: rgba(239,83,80,0.1); color: #ef5350; border-radius: 6px; border-left: 3px solid #ef5350; }
.user-list { display: flex; flex-direction: column; gap: 8px; }
.user-row { display: flex; align-items: center; gap: 12px; padding: 10px; background: #162B40; border-radius: 8px; transition: opacity 0.2s; }
.user-row.inactive { opacity: 0.5; }
.user-avatar { width: 36px; height: 36px; border-radius: 50%; background: #2a4a6a; display: grid; place-items: center; font-weight: 700; color: #4FC3A1; }
.user-name { font-weight: 600; font-size: 0.9rem; display: flex; align-items: center; gap: 8px; }
.inactive-badge { background: rgba(136,153,170,0.2); color: #8899aa; font-size: 0.65rem; padding: 2px 6px; border-radius: 4px; font-weight: 500; }
.user-meta { font-size: 0.75rem; color: #8899aa; }
.user-info { flex: 1; min-width: 0; }
.role-badge { padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 700; }
.role-red { background: rgba(239,83,80,0.15); color: #ef5350; }
.role-blue { background: rgba(41,182,246,0.15); color: #29b6f6; }
.role-orange { background: rgba(255,152,0,0.15); color: #ff9800; }
.role-gray { background: rgba(136,153,170,0.15); color: #8899aa; }
.cat-row { margin-bottom: 4px; }
.cat-header { display: flex; align-items: center; gap: 8px; padding: 8px; background: #162B40; border-radius: 6px; }
.cat-dot { color: #4FC3A1; }
.cat-name { font-weight: 600; flex: 1; }
.cat-count { font-size: 0.75rem; color: #8899aa; }
.simple-list { margin-top: 12px; }
.simple-row { display: flex; align-items: center; gap: 8px; padding: 8px; border-bottom: 1px solid #162B40; }
.dot-blue { color: #29b6f6; }
.dot-green { color: #4FC3A1; }
.bank-row { display: flex; align-items: center; gap: 12px; padding: 12px; background: #162B40; border-radius: 8px; margin-bottom: 6px; }
.bank-dot { font-size: 0.7rem; }
.bank-dot.green { color: #4FC3A1; }
.bank-info { flex: 1; }
.bank-name { font-weight: 600; }
.bank-meta { font-size: 0.75rem; color: #8899aa; }
.bank-balance { font-weight: 700; min-width: 100px; text-align: right; }
.data-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.data-table th { background: #162B40; color: #8899aa; padding: 8px 12px; text-align: left; font-size: 0.72rem; letter-spacing: 0.5px; }
.data-table td { padding: 8px 12px; border-bottom: 1px solid #162B40; }
.meta { color: #8899aa; font-size: 0.8rem; }
.badge { padding: 2px 8px; border-radius: 4px; font-size: 0.72rem; font-weight: 700; }
.badge-blue { background: rgba(41,182,246,0.15); color: #29b6f6; }
.badge-green { background: rgba(79,195,161,0.15); color: #4FC3A1; }
.badge-orange { background: rgba(255,152,0,0.15); color: #ff9800; }
.badge-purple { background: rgba(171,71,188,0.15); color: #ab47bc; }
.badge-gray { background: rgba(136,153,170,0.15); color: #8899aa; }
.icon-btn { background: none; border: none; cursor: pointer; opacity: 0.6; }
.icon-btn:hover { opacity: 1; }
.green { color: #4FC3A1; }
.red { color: #ef5350; }
</style>
