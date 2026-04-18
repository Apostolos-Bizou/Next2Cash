<script setup>
import { ref, watch, nextTick } from 'vue'

/**
 * ExportFilenameModal — Phase I (Session #13)
 *
 * Asks the user to confirm/edit the filename before downloading an export.
 * Pre-fills with a smart default (Kartela_<CARD>_DD-MM-YYYY) and shows
 * the chosen format (Excel/PDF) in the title.
 *
 * Events emitted:
 *   - confirm(filename: string)  → parent triggers actual download
 *   - cancel                     → parent dismisses without download
 */
const props = defineProps({
  show:     { type: Boolean, default: false },
  format:   { type: String,  default: 'excel' },  // 'excel' | 'pdf'
  cardName: { type: String,  default: '' },
  busy:     { type: Boolean, default: false }
})

const emit = defineEmits(['confirm', 'cancel'])

const filenameInput = ref(null)
const filename = ref('')

// ─── Build smart default filename ──────────────────────────────
function buildDefault() {
  const today = new Date()
  const dd = String(today.getDate()).padStart(2, '0')
  const mm = String(today.getMonth() + 1).padStart(2, '0')
  const yyyy = today.getFullYear()
  const sanitized = (props.cardName || 'EXPORT')
    .trim()
    .toUpperCase()
  return `Kartela_${sanitized}_${dd}-${mm}-${yyyy}`
}

// Re-fill + auto-select whenever modal opens
watch(() => props.show, async (visible) => {
  if (visible) {
    filename.value = buildDefault()
    await nextTick()
    if (filenameInput.value) {
      filenameInput.value.focus()
      filenameInput.value.select()
    }
  }
})

function handleConfirm() {
  const trimmed = (filename.value || '').trim()
  if (!trimmed) return
  emit('confirm', trimmed)
}

function handleCancel() {
  emit('cancel')
}

const formatLabel = () => props.format === 'pdf' ? 'PDF' : 'Excel'
const formatIcon  = () => props.format === 'pdf' ? '📄' : '📊'
const extensionHint = () => props.format === 'pdf' ? '.pdf' : '.xlsx'
</script>

<template>
  <div v-if="show" class="modal-backdrop" @click.self="handleCancel">
    <div class="export-dialog">

      <div class="export-header">
        <span class="export-icon">{{ formatIcon() }}</span>
        <h3>Εξαγωγή σε {{ formatLabel() }}</h3>
      </div>

      <div class="export-body">
        <p class="export-hint">
          Δώσε όνομα για το αρχείο (χωρίς κατάληξη):
        </p>

        <div class="filename-row">
          <input
            ref="filenameInput"
            v-model="filename"
            type="text"
            class="filename-input"
            :disabled="busy"
            @keyup.enter="handleConfirm"
            @keyup.esc="handleCancel"
            placeholder="Kartela_..."
          />
          <span class="filename-ext">{{ extensionHint() }}</span>
        </div>

        <p class="export-warn">
          ⓘ Ελληνικοί χαρακτήρες θα μετατραπούν σε λατινικούς αυτόματα
          (π.χ. ΜΑΛΑΜΙΤΣΗΣ → MALAMITSIS) για συμβατότητα με το filesystem.
        </p>
      </div>

      <div class="export-footer">
        <button
          class="btn-secondary"
          @click="handleCancel"
          :disabled="busy">
          Άκυρο
        </button>
        <button
          class="btn-primary-solid"
          @click="handleConfirm"
          :disabled="busy || !filename.trim()">
          <span v-if="busy">⏳ Λήψη...</span>
          <span v-else>⬇ Λήψη {{ formatLabel() }}</span>
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(3px);
}

.export-dialog {
  background: #1a2332;
  color: #e8ecf1;
  border-radius: 12px;
  width: 90%;
  max-width: 480px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
  border: 1px solid #2a3948;
  overflow: hidden;
}

.export-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 22px 12px;
  border-bottom: 1px solid #2a3948;
}
.export-icon { font-size: 1.4rem; }
.export-header h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: #fff;
}

.export-body {
  padding: 18px 22px;
}
.export-hint {
  margin: 0 0 12px 0;
  color: #b0bdcc;
  font-size: 0.9rem;
}

.filename-row {
  display: flex;
  align-items: stretch;
  gap: 0;
  margin-bottom: 10px;
}
.filename-input {
  flex: 1;
  background: #0f1820;
  color: #fff;
  border: 1px solid #2e75b6;
  border-right: none;
  padding: 10px 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 0.95rem;
  border-radius: 6px 0 0 6px;
  outline: none;
}
.filename-input:focus {
  border-color: #4a9cdf;
  box-shadow: 0 0 0 2px rgba(46, 117, 182, 0.25);
}
.filename-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.filename-ext {
  background: #2a3948;
  color: #b0bdcc;
  padding: 10px 14px;
  border: 1px solid #2e75b6;
  border-left: none;
  border-radius: 0 6px 6px 0;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 0.95rem;
  display: flex;
  align-items: center;
}

.export-warn {
  margin: 12px 0 0 0;
  color: #8899aa;
  font-size: 0.78rem;
  font-style: italic;
  line-height: 1.4;
}

.export-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 22px 18px;
  border-top: 1px solid #2a3948;
}
.btn-secondary,
.btn-primary-solid {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: opacity 0.15s, transform 0.1s;
}
.btn-secondary {
  background: #2a3948;
  color: #e8ecf1;
}
.btn-secondary:hover:not(:disabled) { background: #344656; }
.btn-primary-solid {
  background: #2e75b6;
  color: #fff;
}
.btn-primary-solid:hover:not(:disabled) { background: #3e87c9; }
.btn-primary-solid:disabled,
.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
