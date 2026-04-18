<script setup>
import { ref, watch, computed } from 'vue'
import api from '@/api'

// ──────────────────────────────────────────────────────────────
// CardFormModal — create or edit a card
//
// Props:
//   visible   : boolean — controls modal display
//   mode      : 'create' | 'edit'
//   card      : object (for edit mode — { id, configKey, configValue, parentKey, icon, sortOrder })
//   entityId  : UUID
//
// Emits:
//   close   — modal should close (user cancelled)
//   saved   — persisted successfully, with the server response as payload
// ──────────────────────────────────────────────────────────────

const props = defineProps({
  visible:  { type: Boolean, default: false },
  mode:     { type: String, default: 'create' }, // 'create' | 'edit'
  card:     { type: Object, default: null },
  entityId: { type: String, required: true }
})
const emit = defineEmits(['close', 'saved'])

// ─── Form state ───────────────────────────────────────────────
const name        = ref('')
const icon        = ref('')
const sortOrder   = ref(0)
const filterType  = ref('search')  // search | category | subcategory | counterparty
const filterValue = ref('')
const saving      = ref(false)
const errorMsg    = ref('')

// ─── Dropdown options ─────────────────────────────────────────
const filterOptions = [
  { value: 'search',       label: '🔍 Αναζήτηση στην περιγραφή' },
  { value: 'category',     label: '📁 Κατηγορία' },
  { value: 'subcategory',  label: '📂 Υποκατηγορία' },
  { value: 'counterparty', label: '👤 Αντισυμβαλλόμενος' }
]

const hintMap = {
  search:       'Λέξεις κλειδιά. Κόμμα για OR, κενό για AND. Π.χ. "ΔΕΗ,ΔΕΔΔΗΕ" ή "ΕΣΟΔΑ ΒΑΡΙΑΣ"',
  category:     'Ακριβές όνομα κατηγορίας. Π.χ. "ΛΕΙΤΟΥΡΓΙΚΑ"',
  subcategory:  'Ακριβές όνομα υποκατηγορίας. Π.χ. "ΕΝΟΙΚΙΟ"',
  counterparty: 'Ακριβές όνομα αντισυμβαλλόμενου. Π.χ. "ΜΑΛΑΜΙΤΣΗΣ"'
}

const hint = computed(() => hintMap[filterType.value] || '')

// ─── Populate form when opening in edit mode ──────────────────
watch(() => [props.visible, props.card, props.mode], () => {
  errorMsg.value = ''
  saving.value = false

  if (!props.visible) return

  if (props.mode === 'edit' && props.card) {
    name.value        = props.card.configValue || ''
    icon.value        = props.card.icon || ''
    sortOrder.value   = Number(props.card.sortOrder || 0)

    // Parse parentKey "type:value"
    const pk = props.card.parentKey || ''
    const idx = pk.indexOf(':')
    if (idx > 0) {
      filterType.value  = pk.substring(0, idx)
      filterValue.value = pk.substring(idx + 1)
    } else {
      filterType.value  = 'search'
      filterValue.value = pk
    }
  } else {
    // Create mode — reset
    name.value        = ''
    icon.value        = ''
    sortOrder.value   = 0
    filterType.value  = 'search'
    filterValue.value = ''
  }
}, { immediate: true })

// ─── Slugify helper for configKey auto-gen (ASCII-safe) ───────
function slugify(text) {
  return String(text || '')
    .trim()
    .toLowerCase()
    // strip Greek accents via NFD
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^ws-]/g, '')
    .replace(/s+/g, '_')
    .replace(/-+/g, '_')
    .substring(0, 80)
}

// ─── Validation ───────────────────────────────────────────────
const isValid = computed(() => {
  if (!name.value.trim()) return false
  if (!filterValue.value.trim()) return false
  return true
})

// ─── Actions ──────────────────────────────────────────────────
async function handleSave() {
  errorMsg.value = ''
  if (!isValid.value) {
    errorMsg.value = 'Συμπλήρωσε όνομα και τιμή κανόνα'
    return
  }

  saving.value = true
  try {
    const rule = `${filterType.value}:${filterValue.value.trim()}`

    if (props.mode === 'edit') {
      // PUT — partial update
      const payload = {
        configValue: name.value.trim(),
        parentKey:   rule,
        icon:        icon.value.trim() || null,
        sortOrder:   Number(sortOrder.value || 0)
      }
      const res = await api.put(`/api/config/cards/${props.card.id}`,
        payload,
        { params: { entityId: props.entityId } }
      )
      if (res.data?.success) {
        emit('saved', res.data.data)
      } else {
        errorMsg.value = res.data?.error || 'Αποτυχία αποθήκευσης'
      }
    } else {
      // POST — create
      const configKey = slugify(name.value) || ('card_' + Date.now())
      const payload = {
        configKey,
        configValue: name.value.trim(),
        parentKey:   rule,
        icon:        icon.value.trim() || null,
        sortOrder:   Number(sortOrder.value || 0)
      }
      const res = await api.post('/api/config/cards',
        payload,
        { params: { entityId: props.entityId } }
      )
      if (res.data?.success) {
        emit('saved', res.data.data)
      } else {
        errorMsg.value = res.data?.error || 'Αποτυχία δημιουργίας'
      }
    }
  } catch (e) {
    console.error('CardFormModal save error:', e)
    const serverMsg = e?.response?.data?.error || e?.response?.data?.message
    errorMsg.value = serverMsg || 'Σφάλμα σύνδεσης με τον server'
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  if (saving.value) return
  emit('close')
}

function onBackdropClick(ev) {
  // Close only if clicked outside the modal content
  if (ev.target.classList.contains('modal-backdrop')) {
    handleCancel()
  }
}
</script>

<template>
  <div v-if="visible" class="modal-backdrop" @click="onBackdropClick">
    <div class="modal-content" @click.stop>

      <div class="modal-header">
        <h3>{{ mode === 'edit' ? '✏️ Επεξεργασία καρτέλας' : '➕ Νέα καρτέλα' }}</h3>
        <button class="btn-close" @click="handleCancel" :disabled="saving">✕</button>
      </div>

      <div class="modal-body">

        <!-- Όνομα -->
        <div class="form-row">
          <label class="form-label">Όνομα καρτέλας <span class="req">*</span></label>
          <input v-model="name" class="form-input"
                 placeholder="π.χ. ΔΕΗ, ΜΑΛΑΜΙΤΣΗΣ, Ενοίκια"
                 :disabled="saving" maxlength="100" />
        </div>

        <!-- Icon + Sort order -->
        <div class="form-row-inline">
          <div class="form-col">
            <label class="form-label">Εικονίδιο (προαιρ.)</label>
            <input v-model="icon" class="form-input icon-input"
                   placeholder="📦 ή 💡 ή 🏠"
                   :disabled="saving" maxlength="8" />
          </div>
          <div class="form-col">
            <label class="form-label">Σειρά (προαιρ.)</label>
            <input v-model.number="sortOrder" type="number" class="form-input"
                   :disabled="saving" />
          </div>
        </div>

        <!-- Rule builder -->
        <div class="form-row">
          <label class="form-label">Κανόνας ταιριάσματος <span class="req">*</span></label>
          <select v-model="filterType" class="form-input" :disabled="saving">
            <option v-for="opt in filterOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>

        <div class="form-row">
          <input v-model="filterValue" class="form-input"
                 :placeholder="hint"
                 :disabled="saving" />
          <div class="form-hint">{{ hint }}</div>
        </div>

        <!-- Error -->
        <div v-if="errorMsg" class="form-error">⚠ {{ errorMsg }}</div>

      </div>

      <div class="modal-footer">
        <button class="btn-secondary" @click="handleCancel" :disabled="saving">Ακύρωση</button>
        <button class="btn-primary" @click="handleSave" :disabled="!isValid || saving">
          <span v-if="saving">Αποθήκευση...</span>
          <span v-else>{{ mode === 'edit' ? 'Αποθήκευση αλλαγών' : 'Δημιουργία' }}</span>
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.modal-content {
  background: #1a2f45;
  border-radius: 10px;
  width: 100%;
  max-width: 520px;
  max-height: 90vh;
  overflow-y: auto;
  color: #e0e6ed;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  border: 1px solid #2a4a6a;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #2a4a6a;
}
.modal-header h3 {
  margin: 0;
  font-size: 1.05rem;
  color: #4FC3A1;
  font-weight: 600;
}
.btn-close {
  background: transparent;
  border: none;
  color: #8899aa;
  font-size: 1.1rem;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 4px;
}
.btn-close:hover:not(:disabled) { background: #2a4a6a; color: #e0e6ed; }
.btn-close:disabled { opacity: 0.5; cursor: not-allowed; }

.modal-body { padding: 20px; }

.form-row { margin-bottom: 14px; }
.form-row-inline {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 14px;
}
.form-col { display: flex; flex-direction: column; }
.form-label {
  display: block;
  font-size: 0.78rem;
  color: #8899aa;
  margin-bottom: 4px;
  font-weight: 500;
}
.req { color: #e24b4a; }

.form-input {
  width: 100%;
  background: #0f1e2e;
  border: 1px solid #2a4a6a;
  color: #e0e6ed;
  padding: 9px 12px;
  border-radius: 6px;
  font-size: 0.88rem;
  box-sizing: border-box;
}
.form-input:focus {
  outline: none;
  border-color: #4FC3A1;
}
.form-input:disabled { opacity: 0.6; cursor: not-allowed; }
.icon-input { font-size: 1.05rem; }

.form-hint {
  font-size: 0.72rem;
  color: #6b7a8a;
  margin-top: 4px;
  line-height: 1.3;
}

.form-error {
  background: rgba(226, 75, 74, 0.12);
  color: #e24b4a;
  border: 1px solid rgba(226, 75, 74, 0.3);
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 0.82rem;
  margin-top: 8px;
}

.modal-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid #2a4a6a;
}

.btn-primary {
  background: #4FC3A1;
  color: #0f1e2e;
  border: none;
  padding: 9px 20px;
  border-radius: 6px;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  min-width: 140px;
}
.btn-primary:hover:not(:disabled) { background: #3da98a; }
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: transparent;
  color: #8899aa;
  border: 1px solid #2a4a6a;
  padding: 9px 20px;
  border-radius: 6px;
  font-size: 0.88rem;
  cursor: pointer;
}
.btn-secondary:hover:not(:disabled) { background: #1e3448; color: #e0e6ed; }
.btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
