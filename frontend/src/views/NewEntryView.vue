<script setup>
import { ref, computed } from 'vue'

const type = ref('expense')
const docDate = ref(new Date().toISOString().split('T')[0])
const payDate = ref('')
const category = ref('')
const subcategory = ref('')
const amount = ref('')
const method = ref('')
const isPending = ref(true)
const isUrgent = ref(false)
const description = ref('')
const entryNo = ref(4778)

const categories = {
  'ΛΕΙΤΟΥΡΓΙΚΑ': ['Ενοίκιο', 'Τηλέφωνα', 'Λογαριασμοί', 'Γραφείο', 'Έξοδα Διαχείρισης', 'Γεύματα Εργασίας', 'Leasing Αυτοκινήτου', 'Έξοδα Κίνησης', 'Λοιπά'],
  'ΕΞΟΠΛΙΣΜΟΣ': ['Εγκατάσταση / Διαμόρφωση χώρων', 'Έπιπλα / Εξοπλισμός', 'Η/Υ', 'Άδειες Χρήσης', 'Λογισμικά / ERP', 'Πάγιος Εξοπλισμός'],
  'ΑΠΑΣΧΟΛΗΣΗ': ['Γεν. Διευθυντής', 'PA', 'Operation 1', 'Operation 2', 'Finance', 'Medical 1', 'Medical 2', 'Νομική Υποστήριξη'],
  'ΠΡΟΣΩΠΙΚΟ': ['Γραμματεία', 'Big Data', 'Γραφίστας', 'Developer', 'Εισφορές Δημοσίου'],
  'ΛΟΙΠΑ': ['Μέτοχος', 'Έκτακτα', 'Dn2Me-UK'],
  'ΕΙΣΠΡΑΞΕΙΣ': ['Έσοδα', 'HealthPass', 'Emergency', 'ΚΑΓΚΕΛΑΡΗΣ', 'ΒΑΡΙΑΣ'],
  'ΕΣΟΔΑ': ['Πωλήσεις', 'Παροχή Υπηρεσιών'],
  'ΕΣΟΔΑ Β': ['ΚΑΓΚΕΛΑΡΗΣ', 'ΒΑΡΙΑΣ'],
}

const subcategories = computed(() => {
  return category.value ? (categories[category.value] || []) : []
})

const methods = ['Πειραιώς', 'Μετρητά', 'Πορτοφόλι', 'Revolut GBP', 'Revolut USD', 'Revolut EUR', 'Τράπεζα', 'Απόδειξη', 'HSBC']

const docStatus = ref('')
const docStatuses = [
  { label: 'Τράπεζα', icon: '🏦' },
  { label: 'Απόδειξη', icon: '🧾' },
  { label: 'Μετρητά', icon: '💵' },
  { label: 'Χωρίς', icon: '✕' },
]

const frequentEntries = [
  'ΕNOIKIO',
  'MICROSOFT AZURE',
  'ΠΑΠΑΚΙ',
  'ΚΙΝΗΤΟ',
  'ΜΑΛΑΜΙΤΣΗΣ',
  'ΤΑΛΙΑΔΟΡΟΣ',
]

const reset = () => {
  type.value = 'expense'
  docDate.value = new Date().toISOString().split('T')[0]
  payDate.value = ''
  category.value = ''
  subcategory.value = ''
  amount.value = ''
  method.value = ''
  isPending.value = true
  isUrgent.value = false
  description.value = ''
  docStatus.value = ''
}

const save = () => {
  if (!category.value || !amount.value) {
    alert('Συμπλήρωσε Κατηγορία και Ποσό')
    return
  }
  alert('✅ Η καταχώριση αποθηκεύτηκε! (Demo mode)')
  reset()
}
</script>

<template>
  <div class="new-entry-page">
    <div class="entry-card">
      <div class="entry-header">
        <div class="entry-title">
          <span class="entry-icon">➕</span>
          <span>Νέα Καταχώριση</span>
        </div>
        <div class="entry-meta">
          <div class="meta-badge">
            <div class="meta-label">ΑΡ. ΚΑΤΑΧΩΡΙΣΗΣ</div>
            <div class="meta-value">{{ entryNo }}</div>
          </div>
          <div class="meta-badge">
            <div class="meta-label">ΠΟΣΟ</div>
            <div class="meta-value">{{ amount ? Number(amount).toLocaleString('el-GR', {minimumFractionDigits:2}) + ' €' : '0,00 €' }}</div>
          </div>
        </div>
      </div>

      <!-- Type Toggle -->
      <div class="type-toggle">
        <button :class="['type-btn', 'expense', { active: type === 'expense' }]" @click="type = 'expense'">
          ↓ Πληρωμή
        </button>
        <button :class="['type-btn', 'income', { active: type === 'income' }]" @click="type = 'income'">
          ↑ Είσπραξη
        </button>
      </div>

      <!-- Dates Row -->
      <div class="form-row">
        <div class="form-group">
          <label>Ημ/νία Παραστατικού <span class="req">*</span></label>
          <input v-model="docDate" type="date" class="form-input" />
        </div>
        <div class="form-group">
          <label>Ημ/νία Πληρωμής</label>
          <input v-model="payDate" type="date" class="form-input" />
          <span class="hint">Κενή = ίδια με παραστατικού</span>
        </div>
      </div>

      <!-- Category Row -->
      <div class="form-row">
        <div class="form-group">
          <label>Κατηγορία <span class="req">*</span></label>
          <select v-model="category" class="form-input" @change="subcategory = ''">
            <option value="">— Επιλέξτε —</option>
            <option v-for="c in Object.keys(categories)" :key="c">{{ c }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>Υποκατηγορία <span class="req">*</span></label>
          <select v-model="subcategory" class="form-input" :disabled="!category">
            <option value="">— Επιλέξτε κατηγορία πρώτα —</option>
            <option v-for="s in subcategories" :key="s">{{ s }}</option>
          </select>
        </div>
      </div>

      <!-- Amount & Method Row -->
      <div class="form-row">
        <div class="form-group">
          <label>Ποσό (€) <span class="req">*</span></label>
          <input v-model="amount" type="number" step="0.01" placeholder="0.00" class="form-input" />
        </div>
        <div class="form-group">
          <label>Μέθοδος Πληρωμής</label>
          <select v-model="method" class="form-input">
            <option value="">— Επιλέξτε —</option>
            <option v-for="m in methods" :key="m">{{ m }}</option>
          </select>
        </div>
      </div>

      <!-- Pending & Urgent -->
      <div class="pending-row">
        <label class="checkbox-label">
          <input type="checkbox" v-model="isPending" />
          <span>Εκκρεμεί πληρωμή</span>
          <span class="hint-small">(θα εμφανιστεί στις Υποχρεώσεις)</span>
        </label>
        <div class="status-btns">
          <button :class="['status-btn', 'gray', { active: !isUrgent }]" @click="isUrgent = false">ΑΠΛΗΡΩΤΗ</button>
          <button :class="['status-btn', 'orange', { active: isUrgent }]" @click="isUrgent = true">⚡ Εκκρεμής</button>
        </div>
      </div>

      <!-- Description -->
      <div class="form-group">
        <label>Περιγραφή <span class="frequent-label">★ Συχνές</span></label>
        <textarea v-model="description" class="form-input textarea" :placeholder="entryNo + ' - '"></textarea>
      </div>

      <!-- Frequent Entries -->
      <div class="frequent-entries">
        <button v-for="f in frequentEntries" :key="f" class="frequent-btn" @click="description = entryNo + ' - ' + f">
          {{ f }}
        </button>
      </div>

      <!-- Doc Status -->
      <div class="form-group">
        <label>Συνημμένα</label>
        <div class="upload-area">
          <div class="upload-icon">☁</div>
          <div class="upload-text">Πατήστε για upload αποδεικτικού</div>
          <div class="upload-hint">PDF, JPG, PNG — αυτόματη αποθήκευση στο Drive</div>
        </div>
      </div>

      <!-- Doc Status Buttons -->
      <div class="doc-status-btns">
        <button v-for="s in docStatuses" :key="s.label" :class="['doc-btn', { active: docStatus === s.label }]" @click="docStatus = s.label">
          {{ s.icon }} {{ s.label }}
        </button>
      </div>

      <!-- Action Buttons -->
      <div class="action-btns">
        <button class="btn-reset" @click="reset">↺ Καθαρισμός</button>
        <button class="btn-save" @click="save">💾 Αποθήκευση</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.new-entry-page { padding: 24px; max-width: 900px; margin: 0 auto; }
.entry-card { background: #1e3448; border-radius: 12px; padding: 28px; }
.entry-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.entry-title { display: flex; align-items: center; gap: 10px; font-size: 1.1rem; font-weight: 700; color: #fff; }
.entry-meta { display: flex; gap: 12px; }
.meta-badge { background: #162B40; border: 1px solid #2a4a6a; border-radius: 8px; padding: 8px 16px; text-align: center; }
.meta-label { font-size: 0.65rem; color: #8899aa; letter-spacing: 1px; margin-bottom: 4px; }
.meta-value { font-size: 1.1rem; font-weight: 700; color: #4FC3A1; }
.type-toggle { display: flex; margin-bottom: 20px; border-radius: 8px; overflow: hidden; }
.type-btn { flex: 1; padding: 12px; border: none; cursor: pointer; font-size: 0.95rem; font-weight: 600; background: #162B40; color: #8899aa; }
.type-btn.expense.active { background: rgba(239,83,80,0.2); color: #ef5350; }
.type-btn.income.active { background: rgba(79,195,161,0.2); color: #4FC3A1; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; color: #8899aa; font-size: 0.8rem; margin-bottom: 6px; }
.form-input { width: 100%; background: #162B40; border: 1px solid #2a4a6a; color: #e0e6ed; padding: 10px 12px; border-radius: 6px; font-size: 0.9rem; box-sizing: border-box; }
.form-input:focus { outline: none; border-color: #4FC3A1; }
.textarea { min-height: 80px; resize: vertical; }
.hint { display: block; font-size: 0.72rem; color: #556677; margin-top: 4px; }
.req { color: #ef5350; }
.pending-row { display: flex; align-items: center; justify-content: space-between; background: #162B40; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px; }
.checkbox-label { display: flex; align-items: center; gap: 8px; color: #e0e6ed; font-size: 0.9rem; cursor: pointer; }
.hint-small { color: #8899aa; font-size: 0.75rem; }
.status-btns { display: flex; gap: 8px; }
.status-btn { padding: 6px 14px; border-radius: 6px; border: 1px solid #2a4a6a; cursor: pointer; font-size: 0.8rem; font-weight: 600; background: #1e3448; color: #8899aa; }
.status-btn.orange.active { background: rgba(255,152,0,0.2); color: #ff9800; border-color: #ff9800; }
.status-btn.gray.active { background: rgba(136,153,170,0.2); color: #8899aa; border-color: #8899aa; }
.frequent-entries { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.frequent-btn { background: #162B40; border: 1px solid #2a4a6a; color: #8899aa; padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 0.8rem; }
.frequent-btn:hover { color: #4FC3A1; border-color: #4FC3A1; }
.frequent-label { color: #4FC3A1; font-size: 0.75rem; margin-left: 8px; }
.upload-area { background: #162B40; border: 2px dashed #2a4a6a; border-radius: 8px; padding: 24px; text-align: center; cursor: pointer; margin-bottom: 12px; }
.upload-area:hover { border-color: #4FC3A1; }
.upload-icon { font-size: 1.5rem; margin-bottom: 8px; color: #4FC3A1; }
.upload-text { color: #e0e6ed; font-size: 0.9rem; }
.upload-hint { color: #8899aa; font-size: 0.75rem; margin-top: 4px; }
.doc-status-btns { display: flex; gap: 8px; margin-bottom: 20px; }
.doc-btn { flex: 1; padding: 8px; background: #162B40; border: 1px solid #2a4a6a; color: #8899aa; border-radius: 6px; cursor: pointer; font-size: 0.85rem; }
.doc-btn.active { background: rgba(79,195,161,0.15); color: #4FC3A1; border-color: #4FC3A1; }
.action-btns { display: flex; justify-content: flex-end; gap: 12px; }
.btn-reset { background: #162B40; border: 1px solid #2a4a6a; color: #8899aa; padding: 10px 24px; border-radius: 8px; cursor: pointer; font-size: 0.9rem; }
.btn-save { background: #4FC3A1; border: none; color: #0f1e2e; padding: 10px 24px; border-radius: 8px; cursor: pointer; font-size: 0.9rem; font-weight: 700; }
</style>
