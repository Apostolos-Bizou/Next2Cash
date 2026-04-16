<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import api from '@/api'

const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14',
}
const selectedEntity = ref(localStorage.getItem('n2c_entity') || 'next2me')
const entityId = computed(() => ENTITIES[selectedEntity.value])

// ── Config from backend ───────────────────────────────────────────────
const categories     = ref([])
const allSubcats     = ref([])
const accounts       = ref([])
const paymentMethods = ref([])

async function loadConfig() {
  try {
    const res = await api.get('/api/config', { params: { entityId: entityId.value } })
    if (res.data.success) {
      categories.value     = res.data.categories     || []
      allSubcats.value     = res.data.subcategories  || []
      accounts.value       = res.data.accounts       || []
      paymentMethods.value = res.data.paymentMethods || []
    }
  } catch (e) { console.error('Config error:', e) }
}

// ── Form state ────────────────────────────────────────────────────────
const type        = ref('expense')
const docDate     = ref(new Date().toISOString().split('T')[0])
const payDate     = ref('')
const category    = ref('')
const subcategory = ref('')
const amount      = ref('')
const method      = ref('')
const isPending   = ref(false)
const isUrgent    = ref(false)
const description = ref('')
const docStatus   = ref('')
const saving      = ref(false)
const successMsg  = ref('')
const errorMsg    = ref('')
const nextId      = ref('...')

// ── Autocomplete ──────────────────────────────────────────────────────
const suggestions     = ref([])
const showSuggestions = ref(false)
let autocompleteTimer = null

async function onDescriptionInput() {
  const q = description.value.trim()
  if (q.length < 3) { suggestions.value = []; showSuggestions.value = false; return }
  clearTimeout(autocompleteTimer)
  autocompleteTimer = setTimeout(async () => {
    try {
      const res = await api.get('/api/transactions/search', {
        params: { entityId: entityId.value, q }
      })
      if (res.data.success) {
        suggestions.value = res.data.data.slice(0, 6)
        showSuggestions.value = suggestions.value.length > 0
      }
    } catch (e) {}
  }, 300)
}

function applySuggestion(t) {
  description.value = t.description || ''
  if (t.category)   category.value    = t.category
  if (t.account)    subcategory.value = t.account
  if (t.paymentMethod) method.value   = t.paymentMethod
  if (t.type)       type.value        = t.type
  showSuggestions.value = false
}

// ── Subcategories filtered by category ───────────────────────────────
const subcategories = computed(() => {
  if (!category.value) return []
  return allSubcats.value.filter(s => s.parentKey === category.value)
})

watch(category, () => { subcategory.value = '' })

// ── Next transaction ID ───────────────────────────────────────────────
async function loadNextId() {
  try {
    const res = await api.get('/api/transactions', {
      params: { entityId: entityId.value, page: 0, perPage: 1 }
    })
    if (res.data.success && res.data.data.length > 0) {
      nextId.value = res.data.data[0].id + 1
    }
  } catch (e) {}
}

// ── Frequent entries ──────────────────────────────────────────────────
const frequentEntries = ['ΕNOIKIO', 'MICROSOFT AZURE', 'ΠΑΠΑΚΙ', 'ΚΙΝΗΤΟ', 'ΜΑΛΑΜΙΤΣΗΣ', 'ΤΑΛΙΑΔΟΡΟΣ']

function applyFrequent(f) {
  description.value = nextId.value + ' - ' + f
}

// ── File upload (placeholder — Google Drive → Azure Blob later) ───────
const uploadedFile = ref(null)
function onFileChange(e) {
  const file = e.target.files[0]
  if (!file) return
  uploadedFile.value = file
  // Auto-fill description from filename
  const name = file.name.replace(/\.[^.]+$/, '').replace(/[-_]/g, ' ')
  if (!description.value) description.value = nextId.value + ' - ' + name
}

// ── Save ──────────────────────────────────────────────────────────────
async function save() {
  errorMsg.value = ''; successMsg.value = ''
  if (!category.value)  { errorMsg.value = 'Επιλέξτε Κατηγορία'; return }
  if (!amount.value || Number(amount.value) <= 0) { errorMsg.value = 'Συμπληρώστε Ποσό'; return }

  saving.value = true
  try {
    const payload = {
      entityId:      entityId.value,
      type:          type.value,
      docDate:       docDate.value,
      paymentDate:   payDate.value || docDate.value,
      category:      category.value,
      account:       subcategory.value,
      subcategory:   subcategory.value,
      amount:        Number(amount.value),
      paymentMethod: method.value,
      description:   description.value,
      docStatus:     docStatus.value,
      paymentStatus: isPending.value ? (isUrgent.value ? 'urgent' : 'unpaid') : 'paid',
      recordStatus:  'active',
    }

    const res = await api.post('/api/transactions', payload)
    if (res.data.success) {
      successMsg.value = '✅ Καταχωρήθηκε επιτυχώς! ID: #' + res.data.id
      reset()
      await loadNextId()
    } else {
      errorMsg.value = 'Σφάλμα αποθήκευσης'
    }
  } catch (e) {
    errorMsg.value = 'Σφάλμα σύνδεσης: ' + (e.message || '')
    console.error(e)
  } finally {
    saving.value = false
  }
}

function reset() {
  type.value = 'expense'; docDate.value = new Date().toISOString().split('T')[0]
  payDate.value = ''; category.value = ''; subcategory.value = ''
  amount.value = ''; method.value = ''; isPending.value = false
  isUrgent.value = false; description.value = ''; docStatus.value = ''
  uploadedFile.value = null; suggestions.value = []; showSuggestions.value = false
}

const docStatuses = ['Τράπεζα', 'Απόδειξη', 'Μετρητά', 'Χωρίς']

onMounted(async () => {
  await loadConfig()
  await loadNextId()
})
</script>

<template>
  <div class="new-entry-page">
    <div class="entry-card">

      <!-- Header -->
      <div class="entry-header">
        <div class="entry-title"><i class="fas fa-plus-circle"></i> Νέα Καταχώριση</div>
        <div class="entry-meta">
          <div class="meta-badge">
            <div class="meta-label">ΑΡ. ΚΑΤΑΧΩΡΙΣΗΣ</div>
            <div class="meta-value">#{{ nextId }}</div>
          </div>
          <div class="meta-badge">
            <div class="meta-label">ΠΟΣΟ</div>
            <div class="meta-value" :style="{color: type==='income'?'var(--success)':'var(--danger)'}">
              {{ amount ? Number(amount).toLocaleString('el-GR', {minimumFractionDigits:2}) + ' €' : '0,00 €' }}
            </div>
          </div>
        </div>
      </div>

      <!-- Success / Error -->
      <div v-if="successMsg" class="msg-success"><i class="fas fa-check-circle"></i> {{ successMsg }}</div>
      <div v-if="errorMsg"   class="msg-error"><i class="fas fa-exclamation-triangle"></i> {{ errorMsg }}</div>

      <!-- Type Toggle -->
      <div class="type-toggle">
        <button :class="['type-btn', 'expense', {active: type==='expense'}]" @click="type='expense'">
          <i class="fas fa-arrow-up"></i> Πληρωμή
        </button>
        <button :class="['type-btn', 'income', {active: type==='income'}]" @click="type='income'">
          <i class="fas fa-arrow-down"></i> Είσπραξη
        </button>
      </div>

      <!-- Dates -->
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

      <!-- Category -->
      <div class="form-row">
        <div class="form-group">
          <label>Κατηγορία <span class="req">*</span></label>
          <select v-model="category" class="form-input">
            <option value="">— Επιλέξτε —</option>
            <option v-for="c in categories" :key="c.key" :value="c.key">{{ c.value || c.key }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>Υποκατηγορία <span class="req">*</span></label>
          <select v-model="subcategory" class="form-input" :disabled="!category">
            <option value="">— Επιλέξτε κατηγορία πρώτα —</option>
            <option v-for="s in subcategories" :key="s.key" :value="s.key">{{ s.value || s.key }}</option>
          </select>
        </div>
      </div>

      <!-- Amount & Method -->
      <div class="form-row">
        <div class="form-group">
          <label>Ποσό (€) <span class="req">*</span></label>
          <input v-model="amount" type="number" step="0.01" min="0" placeholder="0.00" class="form-input" />
        </div>
        <div class="form-group">
          <label>Μέθοδος Πληρωμής</label>
          <select v-model="method" class="form-input">
            <option value="">— Επιλέξτε —</option>
            <option v-for="m in accounts" :key="m.key" :value="m.key">{{ m.value || m.key }}</option>
          </select>
        </div>
      </div>

      <!-- Pending -->
      <div class="pending-row">
        <label class="checkbox-label">
          <input type="checkbox" v-model="isPending" />
          <span>Εκκρεμεί πληρωμή</span>
          <span class="hint-small">(θα εμφανιστεί στις Υποχρεώσεις)</span>
        </label>
        <div class="status-btns" v-if="isPending">
          <button :class="['status-btn', {active: !isUrgent}]" @click="isUrgent=false">ΑΠΛΗΡΩΤΗ</button>
          <button :class="['status-btn', 'orange', {active: isUrgent}]" @click="isUrgent=true">⚡ Εκκρεμής</button>
        </div>
      </div>

      <!-- Description with autocomplete -->
      <div class="form-group" style="position:relative">
        <label>Περιγραφή <span class="frequent-label">★ Συχνές</span></label>
        <textarea v-model="description" class="form-input textarea"
          :placeholder="nextId + ' - '"
          @input="onDescriptionInput"
          @blur="setTimeout(() => showSuggestions=false, 200)">
        </textarea>
        <!-- Autocomplete dropdown -->
        <div v-if="showSuggestions" class="autocomplete-drop">
          <div v-for="s in suggestions" :key="s.id" class="autocomplete-item" @mousedown="applySuggestion(s)">
            <span class="ac-desc">{{ s.description }}</span>
            <span class="ac-meta">{{ s.category }} · {{ s.account }}</span>
          </div>
        </div>
      </div>

      <!-- Frequent entries -->
      <div class="frequent-entries">
        <button v-for="f in frequentEntries" :key="f" class="frequent-btn" @click="applyFrequent(f)">{{ f }}</button>
      </div>

      <!-- File upload -->
      <div class="form-group">
        <label>Συνημμένα</label>
        <label class="upload-area" :class="{uploaded: uploadedFile}">
          <input type="file" accept=".pdf,.jpg,.jpeg,.png" @change="onFileChange" style="display:none" />
          <div class="upload-icon"><i class="fas fa-cloud-upload-alt"></i></div>
          <div class="upload-text">{{ uploadedFile ? uploadedFile.name : 'Πατήστε για upload αποδεικτικού' }}</div>
          <div class="upload-hint">PDF, JPG, PNG — αυτόματη αποθήκευση στο Drive</div>
        </label>
      </div>

      <!-- Doc status -->
      <div class="doc-status-btns">
        <button v-for="s in docStatuses" :key="s"
          :class="['doc-btn', {active: docStatus===s}]" @click="docStatus=s">
          {{ s }}
        </button>
      </div>

      <!-- Actions -->
      <div class="action-btns">
        <button class="btn-reset" @click="reset"><i class="fas fa-undo"></i> Καθαρισμός</button>
        <button class="btn-save" @click="save" :disabled="saving">
          <i class="fas fa-save"></i> {{ saving ? 'Αποθήκευση...' : 'Αποθήκευση' }}
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.new-entry-page { padding: 0 24px 24px; max-width: 860px; }
.entry-card { background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-lg); padding:28px; }
.entry-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; }
.entry-title { font-size:1.1rem; font-weight:700; display:flex; align-items:center; gap:10px; }
.entry-title i { color:var(--accent); }
.entry-meta { display:flex; gap:12px; }
.meta-badge { background:var(--bg-input); border:1px solid var(--border); border-radius:var(--radius-md); padding:8px 16px; text-align:center; }
.meta-label { font-size:.65rem; color:var(--text-muted); letter-spacing:1px; margin-bottom:4px; }
.meta-value { font-size:1.05rem; font-weight:700; font-family:var(--font-mono); color:var(--accent); }
.msg-success { background:var(--success-bg); color:var(--success); padding:10px 14px; border-radius:var(--radius-md); margin-bottom:14px; }
.msg-error   { background:var(--danger-bg);  color:var(--danger);  padding:10px 14px; border-radius:var(--radius-md); margin-bottom:14px; }
.type-toggle { display:flex; margin-bottom:20px; border-radius:var(--radius-md); overflow:hidden; border:1px solid var(--border); }
.type-btn { flex:1; padding:12px; border:none; cursor:pointer; font-size:.95rem; font-weight:600; background:var(--bg-input); color:var(--text-muted); font-family:var(--font); transition:all .2s; display:flex; align-items:center; justify-content:center; gap:8px; }
.type-btn.expense.active { background:rgba(239,68,68,.15); color:var(--danger); }
.type-btn.income.active  { background:rgba(16,185,129,.15); color:var(--success); }
.form-row { display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-bottom:4px; }
@media(max-width:600px) { .form-row { grid-template-columns:1fr; } }
.form-group { margin-bottom:14px; }
.form-group label { display:block; color:var(--text-secondary); font-size:.8rem; margin-bottom:6px; font-weight:500; }
.form-input { width:100%; background:var(--bg-input); border:1px solid var(--border); color:var(--text-primary); padding:10px 12px; border-radius:var(--radius-sm); font-size:.9rem; font-family:var(--font); box-sizing:border-box; transition:border-color .2s; }
.form-input:focus { outline:none; border-color:var(--accent); box-shadow:0 0 0 3px var(--accent-glow); }
.form-input:disabled { opacity:.5; cursor:not-allowed; }
.textarea { min-height:80px; resize:vertical; }
.hint { display:block; font-size:.72rem; color:var(--text-muted); margin-top:4px; }
.req { color:var(--danger); }
.pending-row { display:flex; align-items:center; justify-content:space-between; background:var(--bg-input); border-radius:var(--radius-md); padding:12px 16px; margin-bottom:14px; border:1px solid var(--border); flex-wrap:wrap; gap:8px; }
.checkbox-label { display:flex; align-items:center; gap:8px; color:var(--text-primary); font-size:.9rem; cursor:pointer; }
.hint-small { color:var(--text-muted); font-size:.75rem; }
.status-btns { display:flex; gap:8px; }
.status-btn { padding:6px 14px; border-radius:var(--radius-sm); border:1px solid var(--border); cursor:pointer; font-size:.8rem; font-weight:600; background:var(--bg-card); color:var(--text-muted); font-family:var(--font); }
.status-btn.active { background:var(--bg-input); color:var(--text-primary); border-color:var(--accent); }
.status-btn.orange.active { background:rgba(255,100,0,.15); color:#ff6400; border-color:#ff6400; }
.frequent-label { color:var(--success); font-size:.75rem; margin-left:8px; }
.frequent-entries { display:flex; flex-wrap:wrap; gap:8px; margin-bottom:14px; }
.frequent-btn { background:var(--bg-input); border:1px solid var(--border); color:var(--text-muted); padding:4px 12px; border-radius:var(--radius-sm); cursor:pointer; font-size:.8rem; font-family:var(--font); transition:all .2s; }
.frequent-btn:hover { color:var(--accent); border-color:var(--accent); }
.autocomplete-drop { position:absolute; top:100%; left:0; right:0; background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-md); box-shadow:var(--shadow-lg); z-index:100; max-height:220px; overflow-y:auto; }
.autocomplete-item { padding:10px 14px; cursor:pointer; border-bottom:1px solid rgba(42,69,99,.3); transition:background .15s; }
.autocomplete-item:hover { background:var(--bg-card-hover); }
.autocomplete-item:last-child { border-bottom:none; }
.ac-desc { display:block; font-size:.88rem; font-weight:500; }
.ac-meta { display:block; font-size:.75rem; color:var(--text-muted); margin-top:2px; }
.upload-area { display:block; background:var(--bg-input); border:2px dashed var(--border); border-radius:var(--radius-md); padding:20px; text-align:center; cursor:pointer; transition:all .2s; }
.upload-area:hover,.upload-area.uploaded { border-color:var(--accent); }
.upload-icon { font-size:1.4rem; color:var(--accent); margin-bottom:6px; }
.upload-text { color:var(--text-primary); font-size:.88rem; }
.upload-hint { color:var(--text-muted); font-size:.72rem; margin-top:4px; }
.doc-status-btns { display:flex; gap:8px; margin-bottom:20px; flex-wrap:wrap; }
.doc-btn { flex:1; padding:8px; background:var(--bg-input); border:1px solid var(--border); color:var(--text-muted); border-radius:var(--radius-sm); cursor:pointer; font-size:.85rem; font-family:var(--font); transition:all .2s; }
.doc-btn.active { background:var(--accent-glow); color:var(--accent); border-color:var(--accent); }
.action-btns { display:flex; justify-content:flex-end; gap:12px; }
.btn-reset { background:var(--bg-input); border:1px solid var(--border); color:var(--text-secondary); padding:10px 24px; border-radius:var(--radius-md); cursor:pointer; font-size:.9rem; font-family:var(--font); }
.btn-save { background:var(--success); border:none; color:#fff; padding:10px 28px; border-radius:var(--radius-md); cursor:pointer; font-size:.9rem; font-weight:700; font-family:var(--font); transition:all .2s; }
.btn-save:hover:not(:disabled) { opacity:.9; transform:translateY(-1px); }
.btn-save:disabled { opacity:.5; cursor:not-allowed; }
</style>