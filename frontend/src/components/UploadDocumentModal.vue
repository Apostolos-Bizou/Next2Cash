<script setup>
import { ref, watch, computed } from 'vue'
import api from '@/api'

// -----------------------------------------------------------------
// UploadDocumentModal — multi-file upload panel, full parity
// with legacy ATLAS UX (doc-status buttons, per-file rename preview,
// 10MB limit, PDF+JPG+JPEG+PNG).
//
// Props:
//   visible     : Boolean
//   transaction : Object — must have id, description, entityNumber, paymentMethod
//
// Emits:
//   close     — user dismissed / backdrop click
//   uploaded  — at least one file uploaded; parent should refresh list
// -----------------------------------------------------------------

const props = defineProps({
  visible:     { type: Boolean, default: false },
  transaction: { type: Object,  default: null }
})
const emit = defineEmits(['close', 'uploaded'])

// --- State --------------------------------------------------------
const selectedFiles = ref([])
const docStatus     = ref('')
const uploading     = ref(false)
const progressByIdx = ref({})
const errorMsg      = ref('')
const fileInputRef  = ref(null)

// --- Constants ----------------------------------------------------
const MAX_BYTES = 10 * 1024 * 1024
const ACCEPT    = '.pdf,.jpg,.jpeg,.png'
const DOC_STATUS_META = {
  bank:    { label: 'Τραπεζικό αποδεικτικό',      color: '#4FC3A1', icon: '🏦' },
  receipt: { label: 'Απόδειξη',                    color: '#4A9EFF', icon: '🧾' },
  cash:    { label: 'Μετρητά (χωρίς αποδεικτικό)', color: '#FFD93D', icon: '💵' },
  none:    { label: 'Χωρίς παραστατικό',       color: '#9aa5b1', icon: '✕' }
}

// --- Reset when opening -------------------------------------------
watch(() => props.visible, (v) => {
  if (v) {
    selectedFiles.value = []
    docStatus.value = ''
    progressByIdx.value = {}
    errorMsg.value = ''
    uploading.value = false
  }
})

// --- File selection -----------------------------------------------
function onBrowse() {
  fileInputRef.value && fileInputRef.value.click()
}

function onFileSelect(e) {
  const files = Array.from(e.target.files || [])
  addFiles(files)
  e.target.value = ''
}

function onDrop(e) {
  e.preventDefault()
  if (uploading.value) return
  const files = Array.from(e.dataTransfer.files || [])
  addFiles(files)
}

function addFiles(files) {
  errorMsg.value = ''
  for (const f of files) {
    const lower = (f.name || '').toLowerCase()
    const okExt = lower.endsWith('.pdf') || lower.endsWith('.jpg')
               || lower.endsWith('.jpeg') || lower.endsWith('.png')
    if (!okExt) {
      errorMsg.value = 'Δεκτά αρχεία: PDF, JPG, JPEG, PNG (' + f.name + ' απορρίφθηκε)'
      continue
    }
    if (f.size > MAX_BYTES) {
      errorMsg.value = f.name + ': ξεπερνά τα 10 MB'
      continue
    }
    selectedFiles.value.push(f)
  }
  if (!docStatus.value && selectedFiles.value.length > 0) {
    autoDetectDocStatus()
  }
}

function removeFile(idx) {
  if (uploading.value) return
  selectedFiles.value.splice(idx, 1)
  const next = {}
  selectedFiles.value.forEach((_, i) => {
    const srcIdx = i >= idx ? i + 1 : i
    if (progressByIdx.value[srcIdx] !== undefined) {
      next[i] = progressByIdx.value[srcIdx]
    }
  })
  progressByIdx.value = next
}

// --- Doc status ---------------------------------------------------
function setDocStatus(s) {
  if (uploading.value) return
  docStatus.value = s
}

function autoDetectDocStatus() {
  const pm = (props.transaction?.paymentMethod || '').trim()
  if (pm === 'Τράπεζα' || pm === 'Revolut') docStatus.value = 'bank'
  else if (pm === 'Μετρητά')                 docStatus.value = 'cash'
  else                                                                            docStatus.value = 'receipt'
}

// --- Filename preview (visual only — backend generates real filename) -
function autoFilename(idx) {
  const f = selectedFiles.value[idx]
  if (!f) return ''
  const ext = (f.name.split('.').pop() || 'pdf').toLowerCase()
  const desc = (props.transaction?.description || '')
    .replace(/^\d+\s*-\s*/, '')
    .substring(0, 50)
    .replace(/[\/\\:*?"<>|]/g, '_')
    .trim()
  const id = props.transaction?.entityNumber || props.transaction?.id || ''
  return (id ? id + ' - ' : '') + (desc || 'document') + '.' + ext
}

// --- Helpers ------------------------------------------------------
function fmtBytes(n) {
  const v = Number(n) || 0
  if (v < 1024) return v + ' B'
  if (v < 1024 * 1024) return (v / 1024).toFixed(1) + ' KB'
  return (v / 1024 / 1024).toFixed(2) + ' MB'
}

function fileIcon(f) {
  const ext = ((f?.name || '').split('.').pop() || '').toLowerCase()
  if (ext === 'pdf') return { label: 'PDF', color: '#E74C3C' }
  if (['jpg','jpeg','png'].includes(ext)) return { label: 'IMG', color: '#3498DB' }
  return { label: 'FILE', color: '#7F8C8D' }
}

function mapServerError(code) {
  const M = {
    file_missing:          'Δεν επιλέχθηκε αρχείο',
    file_too_large:        'Το αρχείο ξεπερνά τα 10 MB',
    unsupported_file_type: 'Δεκτά αρχεία: PDF, JPG, JPEG, PNG',
    transaction_not_found: 'Η συναλλαγή δεν βρέθηκε'
  }
  return M[code] || ('Σφάλμα: ' + code)
}

// --- Upload -------------------------------------------------------
async function startUpload() {
  if (!props.transaction?.id || selectedFiles.value.length === 0) return
  if (uploading.value) return

  uploading.value = true
  errorMsg.value = ''
  let successCount = 0

  for (let i = 0; i < selectedFiles.value.length; i++) {
    const f = selectedFiles.value[i]
    progressByIdx.value = { ...progressByIdx.value, [i]: { state: 'pending' } }
    try {
      const form = new FormData()
      form.append('file', f)
      const res = await api.post(
        '/api/documents/upload?transactionId=' + props.transaction.id,
        form
      )
      if (res.data?.success) {
        progressByIdx.value = { ...progressByIdx.value, [i]: { state: 'done' } }
        successCount++
      } else {
        const code = res.data?.error || 'unknown'
        progressByIdx.value = {
          ...progressByIdx.value,
          [i]: { state: 'error', msg: mapServerError(code) }
        }
      }
    } catch (err) {
      const status = err.response?.status
      const serverErr = err.response?.data?.error
      let msg = 'Σφάλμα σύνδεσης'
      if (status === 403) msg = 'Δεν έχεις δικαίωμα upload'
      else if (status === 404) msg = 'Η συναλλαγή δεν βρέθηκε'
      else if (status === 400 && serverErr) msg = mapServerError(serverErr)
      progressByIdx.value = {
        ...progressByIdx.value,
        [i]: { state: 'error', msg }
      }
    }
  }

  uploading.value = false

  if (successCount === selectedFiles.value.length) {
    emit('uploaded')
    emit('close')
  } else if (successCount > 0) {
    errorMsg.value = successCount + ' από ' + selectedFiles.value.length + ' αρχεία ανέβηκαν. Δες τα λάθη παρακάτω.'
    emit('uploaded')
  } else {
    errorMsg.value = 'Κανένα αρχείο δεν ανέβηκε. Δοκίμασε ξανά ή αφαίρεσε τα προβληματικά.'
  }
}

// --- Close handlers -----------------------------------------------
function onClose() {
  if (uploading.value) return
  emit('close')
}
function onBackdrop() { onClose() }
function onDragOver(e) { e.preventDefault() }

const canSubmit = computed(() =>
  !uploading.value && selectedFiles.value.length > 0 && props.transaction?.id
)
</script>

<template>
  <div v-if="visible && transaction" class="ud-backdrop" @click.self="onBackdrop">
    <div class="ud-dialog">

      <!-- Header -->
      <div class="ud-header">
        <div class="ud-title">
          <span class="ud-icon">📎</span>
          <h3>Νέο παραστατικό</h3>
        </div>
        <button class="ud-close" @click="onClose" :disabled="uploading">×</button>
      </div>

      <!-- Subtitle -->
      <div class="ud-subtitle">
        #{{ transaction.entityNumber || transaction.id }}
        <span v-if="transaction.description"> — {{ transaction.description }}</span>
      </div>

      <!-- Drop zone -->
      <div
        class="ud-dropzone"
        :class="{ disabled: uploading }"
        @click="onBrowse"
        @drop="onDrop"
        @dragover="onDragOver">
        <div class="ud-cloud">☁</div>
        <div class="ud-dz-title">Πάτησε ή σύρε αρχεία</div>
        <div class="ud-dz-sub">PDF, JPG, JPEG, PNG — max 10 MB ανά αρχείο</div>
        <input
          ref="fileInputRef"
          type="file"
          :accept="ACCEPT"
          multiple
          style="display:none"
          @change="onFileSelect" />
      </div>

      <!-- File list -->
      <div v-if="selectedFiles.length" class="ud-filelist">
        <div
          v-for="(f, i) in selectedFiles"
          :key="i"
          class="ud-fileitem">
          <div class="ud-fileicon" :style="{ background: fileIcon(f).color }">
            {{ fileIcon(f).label }}
          </div>
          <div class="ud-filebody">
            <div class="ud-filename">{{ f.name }}</div>
            <div class="ud-filemeta">
              {{ fmtBytes(f.size) }}
              <span class="ud-arrow">→</span>
              <span class="ud-autoname">{{ autoFilename(i) }}</span>
            </div>
            <div v-if="progressByIdx[i]" class="ud-progress" :class="progressByIdx[i].state">
              <span v-if="progressByIdx[i].state === 'pending'">Ανέβασμα...</span>
              <span v-else-if="progressByIdx[i].state === 'done'">✓ OK</span>
              <span v-else-if="progressByIdx[i].state === 'error'">✕ {{ progressByIdx[i].msg }}</span>
            </div>
          </div>
          <button
            class="ud-remove"
            :disabled="uploading"
            @click="removeFile(i)">×</button>
        </div>
      </div>

      <!-- Doc status -->
      <div v-if="selectedFiles.length" class="ud-docstatus">
        <div class="ud-section-label">Τύπος παραστατικού</div>
        <div class="ud-docstatus-btns">
          <button
            v-for="(meta, key) in DOC_STATUS_META"
            :key="key"
            class="ud-ds-btn"
            :class="{ active: docStatus === key }"
            :style="docStatus === key ? { borderColor: meta.color, color: meta.color } : {}"
            :disabled="uploading"
            @click="setDocStatus(key)">
            <span class="ud-ds-icon">{{ meta.icon }}</span>
            <span>{{ meta.label.split(' ')[0] }}</span>
          </button>
        </div>
      </div>

      <!-- Error -->
      <div v-if="errorMsg" class="ud-error">⚠ {{ errorMsg }}</div>

      <!-- Footer -->
      <div class="ud-footer">
        <button class="ud-btn-cancel" @click="onClose" :disabled="uploading">
          Ακύρωση
        </button>
        <button
          class="ud-btn-upload"
          @click="startUpload"
          :disabled="!canSubmit">
          <span v-if="uploading">Ανέβασμα...</span>
          <span v-else>⬆ Ανέβασμα ({{ selectedFiles.length }})</span>
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.ud-backdrop {
  position: fixed; inset: 0;
  background: rgba(10, 15, 25, 0.75);
  backdrop-filter: blur(3px);
  display: flex; align-items: center; justify-content: center;
  z-index: 1100;
  padding: 20px;
}
.ud-dialog {
  background: #1a2332; border: 1px solid #2c3e50;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.5);
  width: 100%; max-width: 600px;
  max-height: 88vh; overflow-y: auto;
  color: #e0e6ed;
}
.ud-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18px 22px 10px;
}
.ud-title { display: flex; align-items: center; gap: 10px; }
.ud-title h3 { margin: 0; font-size: 1.1rem; font-weight: 700; color: #fff; }
.ud-icon { font-size: 1.2rem; color: #4A9EFF; }
.ud-close {
  background: transparent; border: none; color: #9aa5b1;
  font-size: 1.6rem; cursor: pointer; padding: 0 6px;
}
.ud-close:hover:not(:disabled) { color: #fff; }
.ud-close:disabled { opacity: 0.4; cursor: not-allowed; }

.ud-subtitle {
  padding: 0 22px 16px;
  color: #7a8594; font-size: 0.88rem;
  border-bottom: 1px solid #2c3e50;
}

.ud-dropzone {
  margin: 18px 22px;
  border: 2px dashed #3a4a5e;
  border-radius: 10px;
  padding: 28px 20px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.ud-dropzone:hover { border-color: #4A9EFF; background: rgba(74,158,255,0.05); }
.ud-dropzone.disabled { opacity: 0.5; cursor: not-allowed; }
.ud-cloud { font-size: 2rem; color: #6c7a8a; margin-bottom: 6px; }
.ud-dz-title { font-size: 0.95rem; color: #e0e6ed; font-weight: 500; }
.ud-dz-sub { font-size: 0.78rem; color: #6c7a8a; margin-top: 4px; }

.ud-filelist {
  margin: 0 22px 14px;
  display: flex; flex-direction: column; gap: 10px;
}
.ud-fileitem {
  display: flex; gap: 12px;
  background: #111a25;
  border: 1px solid #2c3e50;
  border-radius: 8px;
  padding: 10px 12px;
}
.ud-fileicon {
  width: 38px; height: 46px; border-radius: 4px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  font-size: 0.68rem; font-weight: 700; color: #fff;
  letter-spacing: 0.5px;
}
.ud-filebody { flex: 1; min-width: 0; }
.ud-filename {
  font-size: 0.88rem; color: #e0e6ed; font-weight: 500;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ud-filemeta {
  font-size: 0.72rem; color: #7a8594; margin-top: 3px;
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
}
.ud-arrow { color: #4FC3A1; }
.ud-autoname { color: #4FC3A1; font-weight: 500; }
.ud-progress { margin-top: 4px; font-size: 0.78rem; }
.ud-progress.pending { color: #FFC107; }
.ud-progress.done    { color: #4FC3A1; font-weight: 600; }
.ud-progress.error   { color: #FF6B6B; }
.ud-remove {
  background: transparent; border: none; color: #7a8594;
  font-size: 1.3rem; cursor: pointer; padding: 0 6px;
  align-self: flex-start;
}
.ud-remove:hover:not(:disabled) { color: #FF6B6B; }
.ud-remove:disabled { opacity: 0.4; cursor: not-allowed; }

.ud-docstatus { padding: 0 22px 14px; }
.ud-section-label {
  font-size: 0.75rem; color: #7a8594; margin-bottom: 8px;
  text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600;
}
.ud-docstatus-btns {
  display: flex; gap: 8px; flex-wrap: wrap;
}
.ud-ds-btn {
  flex: 1; min-width: 110px;
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  padding: 8px 10px;
  background: #111a25;
  border: 1px solid #2c3e50;
  border-radius: 6px;
  color: #9aa5b1;
  font-size: 0.82rem; font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.ud-ds-btn:hover:not(:disabled) { border-color: #4A9EFF; }
.ud-ds-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ud-ds-btn.active { background: #0f1724; }
.ud-ds-icon { font-size: 0.9rem; }

.ud-error {
  margin: 0 22px 12px;
  padding: 9px 12px;
  background: rgba(255,107,107,0.1);
  border: 1px solid rgba(255,107,107,0.3);
  border-radius: 5px;
  color: #FF6B6B; font-size: 0.85rem;
}

.ud-footer {
  display: flex; justify-content: flex-end; gap: 10px;
  padding: 14px 22px 20px;
  border-top: 1px solid #2c3e50;
}
.ud-btn-cancel, .ud-btn-upload {
  padding: 9px 20px; border: none; border-radius: 6px;
  font-size: 0.9rem; font-weight: 600; cursor: pointer;
}
.ud-btn-cancel {
  background: transparent; color: #9aa5b1;
  border: 1px solid #2c3e50;
}
.ud-btn-cancel:hover:not(:disabled) { background: #2c3e50; color: #fff; }
.ud-btn-upload {
  background: #4A9EFF; color: #0d1f2d;
}
.ud-btn-upload:hover:not(:disabled) { background: #5fb0ff; }
.ud-btn-cancel:disabled, .ud-btn-upload:disabled {
  opacity: 0.5; cursor: not-allowed;
}
</style>
