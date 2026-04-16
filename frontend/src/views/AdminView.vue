<script setup>
import { ref } from 'vue'

const activeTab = ref('users')

const tabs = [
  { id: 'users', label: 'Χρήστες', icon: '👥' },
  { id: 'categories', label: 'Κατηγορίες', icon: '🏷' },
  { id: 'accounts', label: 'Λογαριασμοί', icon: '📋' },
  { id: 'banks', label: 'Τράπεζες', icon: '🏦' },
  { id: 'audit', label: 'Audit Log', icon: '🕐' },
]

// USERS
const users = ref([
  { id: 1, username: 'admin', name: 'Administrator', role: 'ADMIN', lastLogin: '15/04/26', active: true },
  { id: 2, username: 'sissy', name: 'Sissy', role: 'FINANCE', lastLogin: '14/04/26', active: true },
])
const newUser = ref({ username: '', name: '', password: '', role: 'VIEWER' })
const roles = ['ADMIN', 'FINANCE', 'VIEWER']

const addUser = () => {
  if (!newUser.value.username || !newUser.value.password) return
  users.value.push({ id: Date.now(), ...newUser.value, lastLogin: 'Ποτέ', active: true })
  newUser.value = { username: '', name: '', password: '', role: 'VIEWER' }
}

// CATEGORIES
const categories = ref([
  { id: 1, name: 'ΛΕΙΤΟΥΡΓΙΚΑ', subs: ['Ενοίκιο', 'Τηλέφωνα', 'Λογαριασμοί', 'Γραφείο', 'Έξοδα Κίνησης', 'Γεύματα Εργασίας', 'Leasing Αυτοκινήτου', 'Λοιπά'] },
  { id: 2, name: 'ΕΞΟΠΛΙΣΜΟΣ', subs: ['Η/Υ', 'Άδειες Χρήσης', 'Λογισμικά / ERP', 'Έπιπλα / Εξοπλισμός'] },
  { id: 3, name: 'ΑΠΑΣΧΟΛΗΣΗ', subs: ['Γεν. Διευθυντής', 'Finance', 'Operation 1', 'Operation 2', 'Νομική Υποστήριξη'] },
  { id: 4, name: 'ΛΟΙΠΑ', subs: ['Μέτοχος', 'Έκτακτα', 'Dn2Me-UK'] },
  { id: 5, name: 'ΕΙΣΠΡΑΞΕΙΣ', subs: ['ΚΑΓΚΕΛΑΡΗΣ', 'ΒΑΡΙΑΣ', 'HealthPass', 'Έσοδα'] },
  { id: 6, name: 'ΕΣΟΔΑ Β', subs: ['ΚΑΓΚΕΛΑΡΗΣ', 'ΒΑΡΙΑΣ'] },
])
const newCategory = ref('')
const newSub = ref({ name: '', parentId: null })

const addCategory = () => {
  if (!newCategory.value) return
  categories.value.push({ id: Date.now(), name: newCategory.value.toUpperCase(), subs: [] })
  newCategory.value = ''
}

// BANKS
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

// ACCOUNTS
const accounts = ref([
  'Λογαριασμός ΚΑΓΚΕΛΑΡΗΣ', 'Λογαριασμοί', 'Έξοδα Κίνησης', 'Λογισμικά/ERP',
  'Ενοίκιο', 'Τηλέφωνα', 'Άδειες Χρήσης', 'Operation 1', 'Finance', 'Dn2Me-UK'
])
const paymentMethods = ref(['Μετρητά', 'Τράπεζα', 'Απόδειξη', 'HSBC', 'Πειραιώς'])
const newAccount = ref('')
const newMethod = ref('')

// AUDIT LOG
const auditLog = ref([
  { date: '15/04/26 07:17', user: 'admin', action: 'LOGIN', details: 'Login successful' },
  { date: '15/04/26 06:29', user: 'admin', action: 'CREATE', details: '{"type":"income","amount":150}' },
  { date: '15/04/26 06:29', user: 'admin', action: 'UPLOAD', details: 'File: 4777 - ΕΣΟΔΑ ΠΡΟΣΩΠΙΚΑ' },
  { date: '15/04/26 06:26', user: 'admin', action: 'PAYMENT', details: '{"paymentId":62,"amount":12.4}' },
  { date: '14/04/26 10:02', user: 'sissy', action: 'CREATE', details: '{"type":"expense","amount":150}' },
  { date: '14/04/26 10:00', user: 'sissy', action: 'CREATE', details: '{"type":"expense","amount":300}' },
])

const actionClass = (a) => {
  if (a === 'LOGIN') return 'badge-blue'
  if (a === 'CREATE') return 'badge-green'
  if (a === 'PAYMENT') return 'badge-orange'
  if (a === 'UPLOAD') return 'badge-purple'
  return 'badge-gray'
}

const roleClass = (r) => {
  if (r === 'ADMIN') return 'role-red'
  if (r === 'FINANCE') return 'role-orange'
  return 'role-gray'
}

const fmt = (n) => new Intl.NumberFormat('el-GR', { minimumFractionDigits: 2 }).format(n) + ' €'
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
            <option v-for="r in roles" :key="r">{{ r }}</option>
          </select>
          <button class="btn-primary" @click="addUser">+ Δημιουργία</button>
        </div>
      </div>
      <div class="section-card">
        <h3>👥 Χρήστες Συστήματος</h3>
        <div class="user-list">
          <div v-for="u in users" :key="u.id" class="user-row">
            <div class="user-avatar">{{ u.name[0] }}</div>
            <div class="user-info">
              <div class="user-name">{{ u.name }}</div>
              <div class="user-meta">@{{ u.username }} · Τελευταίο login: {{ u.lastLogin }}</div>
            </div>
            <span class="role-badge" :class="roleClass(u.role)">{{ u.role }}</span>
            <button class="icon-btn">✏️</button>
          </div>
        </div>
      </div>
    </div>

    <!-- CATEGORIES TAB -->
    <div v-if="activeTab === 'categories'" class="tab-content">
      <div class="section-card">
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
            <button class="icon-btn">✏️</button>
            <button class="icon-btn red">🚫</button>
          </div>
        </div>
      </div>
      <div class="section-card">
        <h3>↳ Υποκατηγορίες</h3>
        <div v-for="cat in categories" :key="cat.id">
          <div class="subcat-header">{{ cat.name }}</div>
          <div v-for="sub in cat.subs" :key="sub" class="subcat-row">
            <span class="subcat-icon">↳</span>
            <span>{{ sub }}</span>
            <button class="icon-btn">✏️</button>
            <button class="icon-btn red">🚫</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ACCOUNTS TAB -->
    <div v-if="activeTab === 'accounts'" class="tab-content">
      <div class="section-card">
        <h3>📋 Νέος Λογαριασμός</h3>
        <div class="form-row">
          <input v-model="newAccount" placeholder="π.χ. ΚΑΓΚΕΛΑΡΗΣ" class="form-input" />
          <button class="btn-primary" @click="accounts.push(newAccount); newAccount=''">+ Προσθήκη</button>
        </div>
        <div class="simple-list">
          <div v-for="a in accounts" :key="a" class="simple-row">
            <span class="dot-blue">●</span> {{ a }}
            <button class="icon-btn ml-auto">✏️</button>
            <button class="icon-btn red">🚫</button>
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
            <button class="icon-btn ml-auto">✏️</button>
            <button class="icon-btn red">🚫</button>
          </div>
        </div>
      </div>
    </div>

    <!-- BANKS TAB -->
    <div v-if="activeTab === 'banks'" class="tab-content">
      <div class="section-card">
        <h3>🏦 Νέος Τραπεζικός Λογαριασμός</h3>
        <div class="form-row">
          <input v-model="newBank.name" placeholder="π.χ. Eurobank Τρεχούμενος" class="form-input" />
          <input v-model="newBank.bank" placeholder="π.χ. Eurobank" class="form-input" />
          <select v-model="newBank.type" class="form-select">
            <option value="checking">Τρεχούμενος</option>
            <option value="cash">Μετρητά</option>
            <option value="revolut">Revolut</option>
          </select>
          <input v-model="newBank.balance" type="number" placeholder="Αρχικό Υπόλοιπο" class="form-input" />
          <select v-model="newBank.currency" class="form-select">
            <option>EUR</option><option>USD</option><option>GBP</option>
          </select>
          <button class="btn-primary" @click="addBank">+ Δημιουργία</button>
        </div>
      </div>
      <div class="section-card">
        <h3>🏦 Τραπεζικοί Λογαριασμοί <span class="header-action">Ενημερώστε υπόλοιπο χειροκίνητα</span></h3>
        <div v-for="b in banks" :key="b.id" class="bank-row">
          <span class="bank-dot green">●</span>
          <div class="bank-info">
            <div class="bank-name">{{ b.name }}</div>
            <div class="bank-meta">{{ b.bank }} · {{ b.type }} · {{ b.currency }} · Ενημ: {{ b.updated }}</div>
          </div>
          <div class="bank-balance" :class="b.balance >= 0 ? 'green' : 'red'">{{ fmt(b.balance) }}</div>
          <input type="number" :value="b.balance" class="balance-input" @change="b.balance = +$event.target.value" />
          <button class="btn-check">✓</button>
        </div>
      </div>
    </div>

    <!-- AUDIT LOG TAB -->
    <div v-if="activeTab === 'audit'" class="tab-content">
      <div class="section-card">
        <div class="audit-header">
          <h3>🕐 Τελευταίες Ενέργειες</h3>
          <button class="btn-refresh">↻ Ανανέωση</button>
        </div>
        <table class="data-table">
          <thead>
            <tr>
              <th>ΗΜΕΡΟΜΗΝΙΑ</th>
              <th>ΧΡΗΣΤΗΣ</th>
              <th>ΕΝΕΡΓΕΙΑ</th>
              <th>ΛΕΠΤΟΜΕΡΕΙΕΣ</th>
            </tr>
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
.section-card h3 { margin: 0 0 16px; font-size: 0.95rem; color: #4FC3A1; }
.form-row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.form-input { background: #162B40; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; flex: 1; min-width: 140px; }
.form-select { background: #162B40; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; }
.btn-primary { background: #4FC3A1; color: #0f1e2e; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: 700; white-space: nowrap; }
.user-list { display: flex; flex-direction: column; gap: 8px; }
.user-row { display: flex; align-items: center; gap: 12px; padding: 10px; background: #162B40; border-radius: 8px; }
.user-avatar { width: 36px; height: 36px; border-radius: 50%; background: #2a4a6a; display: grid; place-items: center; font-weight: 700; color: #4FC3A1; }
.user-name { font-weight: 600; font-size: 0.9rem; }
.user-meta { font-size: 0.75rem; color: #8899aa; }
.user-info { flex: 1; }
.role-badge { padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 700; }
.role-red { background: rgba(239,83,80,0.15); color: #ef5350; }
.role-orange { background: rgba(255,152,0,0.15); color: #ff9800; }
.role-gray { background: rgba(136,153,170,0.15); color: #8899aa; }
.cat-row { margin-bottom: 4px; }
.cat-header { display: flex; align-items: center; gap: 8px; padding: 8px; background: #162B40; border-radius: 6px; }
.cat-dot { color: #4FC3A1; }
.cat-name { font-weight: 600; flex: 1; }
.cat-count { font-size: 0.75rem; color: #8899aa; }
.subcat-header { background: #162B40; padding: 6px 12px; font-size: 0.75rem; color: #8899aa; letter-spacing: 1px; margin-top: 8px; border-radius: 4px; }
.subcat-row { display: flex; align-items: center; gap: 8px; padding: 6px 16px; border-bottom: 1px solid #162B40; }
.subcat-icon { color: #4FC3A1; font-size: 0.8rem; }
.simple-list { margin-top: 12px; }
.simple-row { display: flex; align-items: center; gap: 8px; padding: 8px; border-bottom: 1px solid #162B40; }
.dot-blue { color: #29b6f6; }
.dot-green { color: #4FC3A1; }
.ml-auto { margin-left: auto; }
.bank-row { display: flex; align-items: center; gap: 12px; padding: 12px; background: #162B40; border-radius: 8px; margin-bottom: 6px; }
.bank-dot { font-size: 0.7rem; }
.bank-dot.green { color: #4FC3A1; }
.bank-info { flex: 1; }
.bank-name { font-weight: 600; }
.bank-meta { font-size: 0.75rem; color: #8899aa; }
.bank-balance { font-weight: 700; min-width: 100px; text-align: right; }
.balance-input { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 6px 10px; border-radius: 6px; width: 80px; text-align: right; }
.btn-check { background: #4FC3A1; color: #0f1e2e; border: none; padding: 6px 12px; border-radius: 6px; cursor: pointer; font-weight: 700; }
.header-action { font-size: 0.75rem; color: #8899aa; float: right; font-weight: 400; }
.audit-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.audit-header h3 { margin: 0; }
.btn-refresh { background: #1e3448; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 6px 12px; border-radius: 6px; cursor: pointer; }
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
.icon-btn.red { color: #ef5350; }
.green { color: #4FC3A1; }
.red { color: #ef5350; }
</style>
