<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import api from '@/api'

const ENTITIES = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  next2megroup: '50317f44-9961-4fb4-add0-7a118e32dc14',
}
const selectedEntity = ref(localStorage.getItem('n2c_entity') || 'next2me')
const entityId = computed(() => ENTITIES[selectedEntity.value])

// Config from backend
const categories     = ref([])
const allSubcats     = ref([])
const accounts       = ref([])
const paymentMethods = ref([])
const bankAccounts   = ref([])

async function loadConfig() {
  try {
    const res = await api.get('/api/config', { params: { entityId: entityId.value } })
    if (res.data.success) {
      categories.value     = res.data.categories     || []
      allSubcats.value     = res.data.subcategories  || []
      accounts.value       = res.data.accounts       || []
      paymentMethods.value = res.data.paymentMethods || []
    }
    const bankRes = await api.get('/api/bank-accounts', { params: { entityId: entityId.value } })
    if (bankRes.data.success) {
      bankAccounts.value = bankRes.data.accounts || []
    }
  } catch (e) { console.error('Config error:', e) }
}

// Form state
const type        = ref('expense')
// Phase 1-F1: entry mode (ACTUAL = past/today, PLANNED = future/budget)
const entryMode   = ref('ACTUAL')
// Phase 60-B: auto-uncheck pending when type=income
watch(type, (newType) => {
  if (newType === 'income') {
    isPending.value = false
    isUrgent.value = false
  }
})
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

// ─────────────────────────────────────────────────────────────
// Phase 1-F2: Mode B (PLANNED) — Cash Planning form extension
// ─────────────────────────────────────────────────────────────
// Επανάληψη (Recurrence)
const isRecurring    = ref(false)
const frequency      = ref('MONTHLY')   // DAILY / WEEKLY / MONTHLY / QUARTERLY / YEARLY
const dayOfMonth     = ref(1)            // for MONTHLY/QUARTERLY/YEARLY
const dayOfWeek      = ref(1)            // for WEEKLY (1=Mon..7=Sun)
const intervalCount  = ref(1)
const startDate      = ref(new Date().toISOString().split('T')[0])
const endDate        = ref('')
const isOpenEnded    = ref(true)         // true => endDate ignored

// Project (Phase 2 dependency — disabled placeholder; OpEx fallback)
const projectId      = ref('')
const isOpEx         = ref(true)

// Σενάριο (Scenario)
const scenario       = ref('BASELINE')   // BASELINE / OPTIMISTIC / PESSIMISTIC

// Βεβαιότητα (Confidence)
const confidence     = ref(100)

// Παρατηρήσεις (Notes — kept in description; this is just a UX helper field
// for the PLANNED variant. Submitted concatenated into description.)
const plannedNotes   = ref('')

// Reset day_of_month/day_of_week sensibly when frequency changes
watch(frequency, (newFreq) => {
  if (newFreq === 'WEEKLY' && (!dayOfWeek.value || dayOfWeek.value < 1)) {
    dayOfWeek.value = 1
  }
  if (['MONTHLY','QUARTERLY','YEARLY'].includes(newFreq)) {
    if (!dayOfMonth.value || dayOfMonth.value < 1) dayOfMonth.value = 1
  }
})

// When mode switches to PLANNED, default startDate to docDate
watch(entryMode, (newMode) => {
  if (newMode === 'PLANNED') {
    // PLANNED never has a paymentMethod/bank picked yet
    method.value = ''
    isPending.value = false
    isUrgent.value = false
    docStatus.value = ''
    if (!startDate.value) startDate.value = docDate.value
  }
})

// ── Autocomplete ──
const suggestions     = ref([])
const showSuggestions = ref(false)
let autocompleteTimer = null

async function onDescriptionInput() {
  const q = description.value.replace(/^\d+\s*-\s*/, "").trim()
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
  const descText = (t.description || '').replace(/^\d+\s*-\s*/, '')
  description.value = nextId.value + ' - ' + descText
  if (t.category)      category.value    = t.category
  if (t.paymentMethod) method.value      = t.paymentMethod
  if (t.type)          type.value        = t.type
  if (t.account) nextTick(() => { subcategory.value = t.account })
  showSuggestions.value = false
}

// Subcategories filtered by category
const subcategories = computed(() => {
  if (!category.value) return []
  return allSubcats.value.filter(s => s.parentKey === category.value)
})

watch(category, () => { subcategory.value = '' })

// Next transaction ID
async function loadNextId() {
  try {
    const res = await api.get('/api/transactions/next-number', {
      params: { entityId: entityId.value }
    })
    if (res.data.success) {
      nextId.value = res.data.nextNumber
      description.value = String(nextId.value) + " - "
    }
  } catch (e) { console.error('loadNextId error:', e) }
}

// Frequent entries
const frequentEntries = ['ΕΝΟΙΚΙΟ', 'MICROSOFT AZURE', 'ΠΑΠΑΝΙ', 'ΜΙΧΗΤΑ', 'ΜΑΛΑΜΙΤΣΗΣ', 'ΤΑΛΙΑΔΩΡΟΣ']

function applyFrequent(f) {
  description.value = nextId.value + ' - ' + f
}

// Multi-file upload (Phase 56-A)
const uploadedFiles = ref([])
const driveFileNames = ref([])

function buildDefaultName(file, indexAmongAll) {
  const ext = file.name.includes('.') ? file.name.substring(file.name.lastIndexOf('.')) : ''
  const baseDesc = (description.value || '').trim() || String(nextId.value)
  if (indexAmongAll === 0) return baseDesc + ext
  return baseDesc + ' (' + (indexAmongAll + 1) + ')' + ext
}

function onFileChange(e) {
  const incoming = Array.from(e.target.files || [])
  if (incoming.length === 0) return
  if (uploadedFiles.value.length === 0 && incoming.length > 0 && !description.value) {
    const firstName = incoming[0].name.replace(/\.[^.]+$/, '').replace(/[-_]/g, ' ')
    description.value = nextId.value + ' - ' + firstName
  }
  for (const file of incoming) {
    const idx = uploadedFiles.value.length
    uploadedFiles.value.push(file)
    driveFileNames.value.push(buildDefaultName(file, idx))
  }
  try { e.target.value = '' } catch (_) {}
}

function removeFile(idx) {
  uploadedFiles.value.splice(idx, 1)
  driveFileNames.value.splice(idx, 1)
}

// ─────────────────────────────────────────────────────────────
// Save — handles both ACTUAL (legacy) and PLANNED (new F2)
// ─────────────────────────────────────────────────────────────
async function save() {
  errorMsg.value = ''; successMsg.value = ''
  if (!category.value)  { errorMsg.value = 'Επιλέξτε Κατηγορία'; return }
  if (!amount.value || Number(amount.value) <= 0) { errorMsg.value = 'Συμπληρώστε Ποσό'; return }

  // PLANNED-specific validation
  if (entryMode.value === 'PLANNED') {
    if (isRecurring.value) {
      if (!startDate.value) { errorMsg.value = 'Συμπληρώστε Ημ/νία Έναρξης επανάληψης'; return }
      if (!isOpenEnded.value && endDate.value && endDate.value < startDate.value) {
        errorMsg.value = 'Η Λήξη πρέπει να είναι μετά την Έναρξη'; return
      }
      if (frequency.value === 'WEEKLY' && (dayOfWeek.value < 1 || dayOfWeek.value > 7)) {
        errorMsg.value = 'Ημέρα εβδομάδας πρέπει να είναι 1-7'; return
      }
      if (['MONTHLY','QUARTERLY','YEARLY'].includes(frequency.value)
          && (dayOfMonth.value < 1 || dayOfMonth.value > 31)) {
        errorMsg.value = 'Ημέρα μήνα πρέπει να είναι 1-31'; return
      }
    }
    if (confidence.value < 0 || confidence.value > 100) {
      errorMsg.value = 'Βεβαιότητα πρέπει να είναι 0-100'; return
    }
  }

  saving.value = true
  try {
    // ── Step 1: For PLANNED + recurring, create RecurrencePattern first ──
    let recurrencePatternId = null
    if (entryMode.value === 'PLANNED' && isRecurring.value) {
      const patternPayload = {
        frequency:      frequency.value,
        intervalCount:  Number(intervalCount.value) || 1,
        startDate:      startDate.value,
        timezone:       'Europe/Athens',
      }
      if (frequency.value === 'WEEKLY') {
        patternPayload.dayOfWeek = Number(dayOfWeek.value)
      } else if (['MONTHLY','QUARTERLY','YEARLY'].includes(frequency.value)) {
        patternPayload.dayOfMonth = Number(dayOfMonth.value)
      }
      if (!isOpenEnded.value && endDate.value) {
        patternPayload.endDate = endDate.value
      }
      try {
        const patternRes = await api.post(
          '/api/recurrence-patterns',
          patternPayload,
          { params: { entityId: entityId.value } }
        )
        if (patternRes.data.success && patternRes.data.data && patternRes.data.data.id) {
          recurrencePatternId = patternRes.data.data.id
        } else {
          errorMsg.value = 'Σφάλμα δημιουργίας pattern επανάληψης'
          saving.value = false
          return
        }
      } catch (pe) {
        const apiErr = pe.response && pe.response.data && pe.response.data.error
        errorMsg.value = 'Σφάλμα pattern: ' + (apiErr || pe.message || '')
        saving.value = false
        return
      }
    }

    // ── Step 2: Build transaction payload ──
    // For PLANNED, paymentMethod is always blank (no bank yet) and status is unpaid.
    const isPlanned = entryMode.value === 'PLANNED'

    // Description: for PLANNED, append notes if provided
    let finalDescription = description.value
    if (isPlanned && plannedNotes.value && plannedNotes.value.trim()) {
      finalDescription = (finalDescription || '') + '\n[Σημ.] ' + plannedNotes.value.trim()
    }

    const payload = {
      entityId:      entityId.value,
      type:          type.value,
      docDate:       isPlanned && isRecurring.value && startDate.value ? startDate.value : docDate.value,
      paymentDate:   isPlanned ? null : (payDate.value || docDate.value),
      category:      category.value,
      account:       subcategory.value,
      subcategory:   subcategory.value,
      amount:        Number(amount.value),
      paymentMethod: isPlanned ? null : method.value,
      description:   finalDescription,
      docStatus:     isPlanned ? null : docStatus.value,
      paymentStatus: isPlanned
                      ? 'unpaid'
                      : (isPending.value ? (isUrgent.value ? 'urgent' : 'unpaid') : 'paid'),
      amountPaid:    isPlanned ? 0 : (isPending.value ? 0 : Number(amount.value)),
      amountRemaining: isPlanned
                        ? Number(amount.value)
                        : (isPending.value ? Number(amount.value) : 0),
      recordStatus:  'active',
      // Phase 1-F1: entry mode
      entryMode:     entryMode.value,
    }

    // Phase 1-F2: extra fields for PLANNED
    if (isPlanned) {
      payload.isRecurring          = isRecurring.value
      payload.recurrencePatternId  = recurrencePatternId   // null if not recurring
      payload.projectId            = isOpEx.value ? null : (projectId.value || null)
      payload.confidencePct        = Number(confidence.value) || 100
      // scenarioId is left null for now (Phase 2 — needs forecast_scenarios table)
      // We just store the chosen label in description suffix for future migration.
    }

    const res = await api.post('/api/transactions', payload)
    if (res.data.success) {
      const newId = res.data.id

      // Attachments are not supported in PLANNED mode (per spec section 5.3).
      if (!isPlanned && uploadedFiles.value.length > 0) {
        let uploadFailures = 0
        for (let i = 0; i < uploadedFiles.value.length; i++) {
          try {
            saving.value = true
            const file = uploadedFiles.value[i]
            const ext = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : ''
            let fileName = (driveFileNames.value[i] || '').trim()
            if (!fileName) fileName = newId + ' - ' + (description.value.replace(/^\d+\s*-\s*/, '').substring(0,50)) + '.' + ext
            if (fileName && !fileName.toLowerCase().endsWith('.' + ext)) fileName += '.' + ext
            const formData = new FormData()
            formData.append('file', file)
            formData.append('entityId', entityId.value)
            formData.append('transactionId', newId)
            formData.append('customFileName', fileName)
            await api.post('/api/documents/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 })
            successMsg.value = '✅ Καταχωρήθηκε + αρχείο! ID: #' + newId
          } catch (fe) {
            console.warn('File upload failed for ' + (file && file.name) + ':', fe)
            uploadFailures++
            successMsg.value = '✅ Καταχωρήθηκε! ID: #' + newId + ' (αρχείο απέτυχε)'
          }
        }
        if (uploadFailures > 0) {
          alert('Προσοχή: ' + uploadFailures + ' αρχεία δεν ανέβηκαν. Η συναλλαγή δημιουργήθηκε κανονικά.')
        }
      } else {
        successMsg.value = isPlanned
          ? '✅ Καταχωρήθηκε προγραμματισμένη! ID: #' + newId + (isRecurring.value ? ' (επαναλαμβανόμενη)' : '')
          : '✅ Καταχωρήθηκε επιτυχώς! ID: #' + newId
      }
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

function hideDropdown() { window.setTimeout(() => { showSuggestions.value = false }, 200) }

function reset() {
  type.value = 'expense'; docDate.value = new Date().toISOString().split('T')[0]
  payDate.value = ''; category.value = ''; subcategory.value = ''
  amount.value = ''; method.value = ''; isPending.value = false
  isUrgent.value = false; description.value = ''; docStatus.value = ''
  uploadedFiles.value = []; driveFileNames.value = []; suggestions.value = []; showSuggestions.value = false
  entryMode.value = 'ACTUAL'  // Phase 1-F1: reset to default
  // Phase 1-F2 PLANNED state reset
  isRecurring.value = false
  frequency.value = 'MONTHLY'
  dayOfMonth.value = 1
  dayOfWeek.value = 1
  intervalCount.value = 1
  startDate.value = new Date().toISOString().split('T')[0]
  endDate.value = ''
  isOpenEnded.value = true
  projectId.value = ''
  isOpEx.value = true
  scenario.value = 'BASELINE'
  confidence.value = 100
  plannedNotes.value = ''
}

const docStatuses = [
  { value: 'bank',    label: 'Τράπεζα' },
  { value: 'receipt', label: 'Απόδειξη' },
  { value: 'cash',    label: 'Μετρητά' },
  { value: '',        label: 'Χωρίς' }
]

// Phase 1-F2: frequency options for PLANNED form
const frequencyOptions = [
  { value: 'DAILY',     label: 'Καθημερινά' },
  { value: 'WEEKLY',    label: 'Εβδομαδιαία' },
  { value: 'MONTHLY',   label: 'Μηνιαία' },
  { value: 'QUARTERLY', label: 'Τριμηνιαία' },
  { value: 'YEARLY',    label: 'Ετήσια' },
]

const dayOfWeekOptions = [
  { value: 1, label: 'Δευτέρα' },
  { value: 2, label: 'Τρίτη' },
  { value: 3, label: 'Τετάρτη' },
  { value: 4, label: 'Πέμπτη' },
  { value: 5, label: 'Παρασκευή' },
  { value: 6, label: 'Σάββατο' },
  { value: 7, label: 'Κυριακή' },
]

const scenarioOptions = [
  { value: 'BASELINE',    label: 'Baseline',    color: '#3b82f6' },
  { value: 'OPTIMISTIC',  label: 'Optimistic',  color: '#10b981' },
  { value: 'PESSIMISTIC', label: 'Pessimistic', color: '#ef4444' },
]

onMounted(async () => {
  await loadConfig()
  await loadNextId()
  window.addEventListener('entity-changed', async () => {
    selectedEntity.value = localStorage.getItem('n2c_entity') || 'next2me'
    await loadConfig()
    await loadNextId()
  })
})
</script>

<template>
  <div class="new-entry-page">
    <div class="entry-card">

      <!-- Phase 1-F1: Mode Switcher (ACTUAL / PLANNED) -->
      <div class="mode-switcher">
        <div class="mode-switcher-label">Τί είδος καταχώρησης;</div>
        <div class="mode-switcher-options">
          <button :class="['mode-btn', 'mode-actual', {active: entryMode==='ACTUAL'}]" @click="entryMode='ACTUAL'" type="button">
            <div class="mode-btn-icon">💰</div>
            <div class="mode-btn-title">Πραγματική</div>
            <div class="mode-btn-hint">Έχει γίνει ήδη</div>
          </button>
          <button :class="['mode-btn', 'mode-planned', {active: entryMode==='PLANNED'}]" @click="entryMode='PLANNED'" type="button">
            <div class="mode-btn-icon">📋</div>
            <div class="mode-btn-title">Προγραμματισμένη</div>
            <div class="mode-btn-hint">Θα γίνει στο μέλλον</div>
          </button>
        </div>
      </div>

      <!-- Header -->
      <div class="entry-header">
        <div class="entry-title">
          <i class="fas fa-plus-circle"></i> Νέα Καταχώριση
          <span v-if="entryMode==='PLANNED'" class="planned-badge">📋 Προγραμματισμένη</span>
        </div>
        <div class="entry-meta">
          <div class="meta-badge">
            <div class="meta-label">ΑΡ. ΚΑΤΑΧΩΡΗΣΗΣ</div>
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
          <label v-if="entryMode==='PLANNED'">Ημ/νία (μελλοντική) <span class="req">*</span></label>
          <label v-else>Ημ/νία Παραστατικού <span class="req">*</span></label>
          <input v-model="docDate" type="date" class="form-input" />
        </div>
        <div class="form-group" v-if="entryMode==='ACTUAL'">
          <label>{{ type === 'income' ? 'Ημ/νία Είσπραξης' : 'Ημ/νία Πληρωμής' }}</label>
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

      <!-- Amount & Method (Method hidden in PLANNED mode) -->
      <div class="form-row">
        <div class="form-group">
          <label>Ποσό (€) <span class="req">*</span></label>
          <input v-model="amount" type="number" step="0.01" min="0" placeholder="0.00" class="form-input" />
        </div>
        <div class="form-group" v-if="entryMode==='ACTUAL'">
          <label>Μέθοδος Πληρωμής</label>
          <select v-model="method" class="form-input">
            <option value="">— Επιλέξτε —</option>
            <option v-for="b in bankAccounts" :key="b.id" :value="b.accountLabel">{{ b.accountLabel }}</option>
          </select>
        </div>
      </div>

      <!-- ═════════════════════════════════════════════════════ -->
      <!--   PLANNED-ONLY SECTIONS (Phase 1-F2)                  -->
      <!-- ═════════════════════════════════════════════════════ -->
      <div v-if="entryMode==='PLANNED'" class="planned-sections">

        <!-- Επανάληψη -->
        <div class="planned-block">
          <label class="block-checkbox">
            <input type="checkbox" v-model="isRecurring" />
            <span class="block-title">🔁 Είναι επαναλαμβανόμενη</span>
          </label>

          <div v-if="isRecurring" class="block-body">
            <div class="form-row">
              <div class="form-group">
                <label>Συχνότητα</label>
                <select v-model="frequency" class="form-input">
                  <option v-for="f in frequencyOptions" :key="f.value" :value="f.value">{{ f.label }}</option>
                </select>
              </div>
              <div class="form-group">
                <label>Κάθε</label>
                <div class="interval-row">
                  <input v-model.number="intervalCount" type="number" min="1" max="12" class="form-input interval-input" />
                  <span class="interval-suffix">
                    {{ frequency==='DAILY' ? 'ημέρες'
                       : frequency==='WEEKLY' ? 'εβδομάδες'
                       : frequency==='MONTHLY' ? 'μήνες'
                       : frequency==='QUARTERLY' ? 'τρίμηνα'
                       : 'χρόνια' }}
                  </span>
                </div>
              </div>
            </div>

            <div class="form-row" v-if="frequency==='WEEKLY'">
              <div class="form-group">
                <label>Ημέρα εβδομάδας</label>
                <select v-model.number="dayOfWeek" class="form-input">
                  <option v-for="d in dayOfWeekOptions" :key="d.value" :value="d.value">{{ d.label }}</option>
                </select>
              </div>
              <div class="form-group"></div>
            </div>

            <div class="form-row" v-if="['MONTHLY','QUARTERLY','YEARLY'].includes(frequency)">
              <div class="form-group">
                <label>Ημέρα μήνα (1-31)</label>
                <input v-model.number="dayOfMonth" type="number" min="1" max="31" class="form-input" />
                <span class="hint">Αν δεν υπάρχει στον μήνα (πχ 31 στο Φεβ.) αυτόματα γίνεται η τελευταία μέρα</span>
              </div>
              <div class="form-group"></div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label>Έναρξη <span class="req">*</span></label>
                <input v-model="startDate" type="date" class="form-input" />
              </div>
              <div class="form-group">
                <label>Λήξη</label>
                <input v-model="endDate" type="date" class="form-input" :disabled="isOpenEnded" />
                <label class="checkbox-label" style="margin-top:6px">
                  <input type="checkbox" v-model="isOpenEnded" />
                  <span>Αόριστη (χωρίς λήξη)</span>
                </label>
              </div>
            </div>
          </div>
        </div>

        <!-- Project -->
        <div class="planned-block">
          <div class="block-title-row">
            <span class="block-title">🎯 Project</span>
            <span class="phase2-tag">Phase 2</span>
          </div>
          <div class="block-body">
            <label class="checkbox-label">
              <input type="checkbox" v-model="isOpEx" />
              <span>Αυτό είναι γενικό έξοδο εταιρείας (OpEx)</span>
            </label>
            <div class="form-group" style="margin-top:10px" v-if="!isOpEx">
              <label>Project</label>
              <select v-model="projectId" class="form-input" disabled>
                <option value="">— Διαθέσιμο σε Phase 2 —</option>
              </select>
              <span class="hint">Το dropdown θα ενεργοποιηθεί όταν δημιουργηθεί ο πίνακας projects (Phase 2). Προς το παρόν όλες οι προγραμματισμένες θεωρούνται OpEx.</span>
            </div>
          </div>
        </div>

        <!-- Σενάριο -->
        <div class="planned-block">
          <div class="block-title-row">
            <span class="block-title">🎬 Σενάριο</span>
          </div>
          <div class="block-body">
            <div class="scenario-options">
              <button
                v-for="s in scenarioOptions"
                :key="s.value"
                type="button"
                :class="['scenario-btn', {active: scenario===s.value}]"
                :style="scenario===s.value ? {borderColor: s.color, background: s.color + '22', color: s.color} : {}"
                @click="scenario=s.value">
                {{ s.label }}
              </button>
            </div>
          </div>
        </div>

        <!-- Βεβαιότητα -->
        <div class="planned-block">
          <div class="block-title-row">
            <span class="block-title">📈 Βεβαιότητα</span>
            <span class="confidence-display">{{ confidence }}%</span>
          </div>
          <div class="block-body">
            <input v-model.number="confidence" type="range" min="0" max="100" step="5" class="confidence-slider" />
            <span class="hint">100% = σίγουρο πάγιο, χαμηλότερο = πιθανή συναλλαγή</span>
          </div>
        </div>

        <!-- Παρατηρήσεις -->
        <div class="planned-block">
          <div class="block-title-row">
            <span class="block-title">ℹ️ Παρατηρήσεις</span>
          </div>
          <div class="block-body">
            <textarea v-model="plannedNotes" class="form-input textarea" rows="2"
              placeholder="Προαιρετικά: σχόλια, υποθέσεις, follow-up..."></textarea>
          </div>
        </div>

      </div>
      <!-- END PLANNED SECTIONS -->

      <!-- Pending (ACTUAL only) -->
      <div class="pending-row" v-if="entryMode==='ACTUAL' && type !== 'income'">
        <label class="checkbox-label">
          <input type="checkbox" v-model="isPending" />
          <span>Εκκρεμεί πληρωμή</span>
          <span class="hint-small">(θα εμφανιστεί στις Υποχρεώσεις)</span>
        </label>
        <div class="status-btns" v-if="isPending">
          <button :class="['status-btn', {active: !isUrgent}]" @click="isUrgent=false">ΑΠΛΗ ΕΩΣ</button>
          <button :class="['status-btn', 'orange', {active: isUrgent}]" @click="isUrgent=true">⏳ Εκκρεμής</button>
        </div>
      </div>

      <!-- Description with autocomplete -->
      <div class="form-group" style="position:relative">
        <label>Περιγραφή <span class="frequent-label">✓ Συχνές</span></label>
        <textarea v-model="description" class="form-input textarea"
          placeholder=""
          @input="onDescriptionInput"
          @blur="hideDropdown">
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

      <!-- File upload (ACTUAL only) -->
      <div class="form-group" v-if="entryMode==='ACTUAL'">
        <label>Συνημμένα</label>
        <label class="upload-area">
          <input type="file" multiple accept=".pdf,.jpg,.jpeg,.png" @change="onFileChange" style="display:none" />
          <div class="upload-icon"><i class="fas fa-cloud-upload-alt"></i></div>
          <div class="upload-text">
            <span v-if="uploadedFiles.length === 0">Πατήστε για upload αποδεικτικών</span>
            <span v-else>+ Προσθήκη κι άλλων αρχείων</span>
          </div>
          <div class="upload-hint">PDF, JPG, PNG — πολλαπλή επιλογή επιτρέπεται</div>
        </label>
        <div v-if="uploadedFiles.length > 0" class="file-preview">
          <div v-for="(f, idx) in uploadedFiles" :key="idx" class="file-preview-item">
            <div class="file-preview-row">
              <i class="fas fa-file-pdf file-icon"></i>
              <span class="file-name">{{ f.name }}</span>
              <span class="file-size">{{ Math.round(f.size/1024) }}KB</span>
              <button class="remove-file" @click="removeFile(idx)" type="button"><i class="fas fa-times"></i></button>
            </div>
            <div class="drive-row">
              <span class="drive-lbl"><i class="fas fa-arrow-right"></i> Drive:</span>
              <input type="text" v-model="driveFileNames[idx]" class="drive-input" />
            </div>
          </div>
        </div>
      </div>

      <!-- Doc status (ACTUAL only) -->
      <div class="doc-status-btns" v-if="entryMode==='ACTUAL'">
        <button v-for="s in docStatuses" :key="s.value"
          :class="['doc-btn', {active: docStatus===s.value}]" @click="docStatus=s.value">
          {{ s.label }}
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
.file-preview { margin-top:8px; background:var(--bg-input); border:1px solid var(--border); border-radius:var(--radius-md); padding:8px 12px; }
.file-preview-item { padding:8px 0; border-bottom:1px solid var(--border); }
.file-preview-item:last-child { border-bottom:none; padding-bottom:0; }
.file-preview-item:first-child { padding-top:0; }
.file-preview-row { display:flex; align-items:center; gap:8px; margin-bottom:6px; }
.file-icon { color:var(--danger); font-size:1rem; }
.file-name { flex:1; font-size:.82rem; color:var(--text-primary); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.file-size { font-size:.75rem; color:var(--text-muted); white-space:nowrap; }
.remove-file { background:none; border:none; color:var(--danger); cursor:pointer; font-size:.85rem; }
.drive-row { display:flex; align-items:center; gap:8px; }
.drive-lbl { font-size:.68rem; color:var(--success); font-weight:600; white-space:nowrap; }
.drive-input { flex:1; padding:6px 10px; font-size:.82rem; font-weight:500; background:var(--bg-input); border:1px solid var(--success); border-radius:var(--radius-sm); color:var(--success); font-family:var(--font); }
/* Phase 1-F1: Mode Switcher styles */
.mode-switcher { background: var(--bg-input); border: 1px solid var(--border); border-radius: var(--radius-md); padding: 14px 16px; margin-bottom: 18px; }
.mode-switcher-label { font-size: .72rem; color: var(--text-muted); letter-spacing: 1px; text-transform: uppercase; margin-bottom: 10px; font-weight: 600; }
.mode-switcher-options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
@media(max-width:600px) { .mode-switcher-options { grid-template-columns: 1fr; } }
.mode-btn { background: var(--bg-card); border: 2px solid var(--border); border-radius: var(--radius-md); padding: 14px 12px; cursor: pointer; text-align: center; font-family: var(--font); color: var(--text-muted); transition: all .2s; }
.mode-btn:hover { border-color: var(--accent); color: var(--text-primary); }
.mode-btn-icon { font-size: 1.4rem; margin-bottom: 6px; opacity: .7; }
.mode-btn-title { font-size: .92rem; font-weight: 700; margin-bottom: 2px; }
.mode-btn-hint { font-size: .72rem; color: var(--text-muted); }
.mode-btn.active { background: var(--accent-glow); border-color: var(--accent); color: var(--text-primary); }
.mode-btn.active .mode-btn-icon { opacity: 1; }
.mode-btn.mode-planned.active { background: rgba(245, 158, 11, .15); border-color: #f59e0b; }
.mode-btn.mode-planned.active .mode-btn-title { color: #f59e0b; }
.planned-badge { display: inline-flex; align-items: center; gap: 4px; background: rgba(245, 158, 11, .15); color: #f59e0b; border: 1px solid #f59e0b; padding: 2px 10px; border-radius: 12px; font-size: .72rem; font-weight: 600; margin-left: 10px; vertical-align: middle; }

/* ─── Phase 1-F2: PLANNED form blocks ─── */
.planned-sections { background: rgba(245, 158, 11, .04); border: 1px dashed rgba(245, 158, 11, .35); border-radius: var(--radius-md); padding: 14px 16px; margin-bottom: 18px; }
.planned-block { background: var(--bg-input); border: 1px solid var(--border); border-radius: var(--radius-md); padding: 12px 14px; margin-bottom: 10px; }
.planned-block:last-child { margin-bottom: 0; }
.block-checkbox { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.block-title-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.block-title { font-size: .92rem; font-weight: 700; color: var(--text-primary); }
.phase2-tag { background: rgba(107, 114, 128, .15); color: var(--text-muted); border: 1px solid var(--border); padding: 1px 8px; border-radius: 10px; font-size: .65rem; font-weight: 600; letter-spacing: .5px; text-transform: uppercase; }
.block-body { margin-top: 10px; }
.interval-row { display: flex; align-items: center; gap: 8px; }
.interval-input { max-width: 80px; }
.interval-suffix { font-size: .85rem; color: var(--text-muted); }
.scenario-options { display: flex; gap: 8px; flex-wrap: wrap; }
.scenario-btn { flex: 1; min-width: 110px; padding: 8px 14px; border-radius: var(--radius-sm); border: 1px solid var(--border); background: var(--bg-card); color: var(--text-muted); cursor: pointer; font-size: .85rem; font-weight: 600; font-family: var(--font); transition: all .2s; }
.scenario-btn:hover { color: var(--text-primary); }
.scenario-btn.active { font-weight: 700; }
.confidence-display { font-size: 1rem; font-weight: 700; color: #f59e0b; font-family: var(--font-mono); }
.confidence-slider { width: 100%; accent-color: #f59e0b; cursor: pointer; }
</style>
