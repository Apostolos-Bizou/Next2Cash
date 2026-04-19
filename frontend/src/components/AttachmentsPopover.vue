<script setup>
import { ref, watch } from 'vue'
import api from '@/api'
import UploadDocumentModal from '@/components/UploadDocumentModal.vue'

// -----------------------------------------------------------------
// AttachmentsPopover — list attachments for a transaction with
// Preview (open in new tab) and Download (forced download) buttons.
//
// Props:
//   visible        : boolean
//   transaction    : object — must have id, entityNumber, description
//
// Emits:
//   close          — user dismissed the popover
// -----------------------------------------------------------------

const props = defineProps({
  visible:     { type: Boolean, default: false },
  transaction: { type: Object,  default: null }
})
const emit = defineEmits(['close'])

// --- State --------------------------------------------------------
const loading     = ref(false)
const errorMsg    = ref('')
const attachments = ref([])          // [{ fileName, blobPath, sizeBytes, downloadUrl }]
const downloadingIdx = ref(-1)
const showUpload = ref(false)

function onUploadComplete() {
  showUpload.value = false
  loadAttachments()
}       // index of the file currently being downloaded

// --- Load attachments when popover opens --------------------------
watch(() => [props.visible, props.transaction?.id], async () => {
  if (!props.visible || !props.transaction?.id) {
    attachments.value = []
    errorMsg.value = ''
    return
  }
  await loadAttachments()
}, { immediate: true })

async function loadAttachments() {
  loading.value = true
  errorMsg.value = ''
  attachments.value = []
  try {
    const res = await api.get('/api/documents/by-transaction/' + props.transaction.id)
    if (res.data && res.data.success) {
      attachments.value = res.data.data || []
    } else {
      errorMsg.value = (res.data && res.data.error) || 'Αποτυχία φόρτωσης αρχείων.'
    }
  } catch (e) {
    console.error('loadAttachments error:', e)
    errorMsg.value = e.response?.data?.error || e.message || 'Σφάλμα σύνδεσης.'
  } finally {
    loading.value = false
  }
}

// --- Actions ------------------------------------------------------
function onClose() {
  emit('close')
}

function onBackdrop() {
  emit('close')
}

function onPreview(file) {
  // SAS URL opens the PDF/image directly in a new tab
  window.open(file.downloadUrl, '_blank', 'noopener,noreferrer')
}

async function onDownload(file, idx) {
  // Fetch blob and trigger a browser download with the correct filename.
  // Using window.open would open inline for PDFs instead of downloading,
  // and the browser would use the Azure hash as the filename.
  downloadingIdx.value = idx
  try {
    const resp = await fetch(file.downloadUrl)
    if (!resp.ok) throw new Error('HTTP ' + resp.status)
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = file.fileName || 'document'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) {
    console.error('download error:', e)
    alert('Σφάλμα λήψης: ' + (e.message || 'άγνωστο σφάλμα'))
  } finally {
    downloadingIdx.value = -1
  }
}

// --- Formatters ---------------------------------------------------
function fmtBytes(n) {
  const v = Number(n) || 0
  if (v < 1024) return v + ' B'
  if (v < 1024 * 1024) return (v / 1024).toFixed(1) + ' KB'
  return (v / 1024 / 1024).toFixed(2) + ' MB'
}

function fileIcon(fileName) {
  const ext = (fileName || '').split('.').pop().toLowerCase()
  if (['pdf'].includes(ext)) return 'PDF'
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext)) return 'IMG'
  if (['doc', 'docx'].includes(ext)) return 'DOC'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'XLS'
  if (['zip', 'rar', '7z'].includes(ext)) return 'ZIP'
  return 'FILE'
}

function iconColor(fileName) {
  const label = fileIcon(fileName)
  switch (label) {
    case 'PDF': return '#E74C3C'
    case 'IMG': return '#3498DB'
    case 'DOC': return '#2980B9'
    case 'XLS': return '#27AE60'
    case 'ZIP': return '#8E44AD'
    default:    return '#7F8C8D'
  }
}
</script>

<template>
  <div v-if="visible && transaction" class="ap-backdrop" @click.self="onBackdrop">
    <div class="ap-dialog">

      <!-- Header -->
      <div class="ap-header">
        <div class="ap-title">
          <span class="ap-paperclip">📎</span>
          <h3>Αρχεία #{{ transaction.entityNumber || transaction.id }}</h3>
        </div>
        <button class="ap-upload-btn" @click="showUpload = true">📎+ Νέο</button>
        <button class="ap-close" @click="onClose">×</button>
      </div>

      <!-- Subtitle: transaction description -->
      <div class="ap-subtitle">
        {{ transaction.entityNumber || transaction.id }} - {{ transaction.description || '—' }}
      </div>

      <!-- Content area -->
      <div class="ap-content">

        <!-- Loading -->
        <div v-if="loading" class="ap-loading">
          <div class="ap-spinner"></div>
          <span>Φόρτωση αρχείων...</span>
        </div>

        <!-- Error -->
        <div v-else-if="errorMsg" class="ap-error">
          ⚠ {{ errorMsg }}
          <button class="ap-retry" @click="loadAttachments">Δοκίμασε ξανά</button>
        </div>

        <!-- Empty -->
        <div v-else-if="attachments.length === 0" class="ap-empty">
          <div class="ap-empty-icon">📂</div>
          <div class="ap-empty-title">Δεν υπάρχουν συνημμένα</div>
          <div class="ap-empty-sub">Δεν έχουν ανέβει αρχεία για αυτή τη συναλλαγή.</div>
        </div>

        <!-- Attachments list -->
        <div v-else class="ap-list">
          <div
            v-for="(f, idx) in attachments"
            :key="f.blobPath"
            class="ap-card">

            <!-- File icon -->
            <div class="ap-fileicon" :style="{ background: iconColor(f.fileName) }">
              <span class="ap-fileicon-label">{{ fileIcon(f.fileName) }}</span>
            </div>

            <!-- File details + actions -->
            <div class="ap-filebody">
              <div class="ap-filemeta">
                ΑΡΧΕΙΟ {{ idx + 1 }} / {{ attachments.length }}
                <span v-if="f.sizeBytes" class="ap-filesize">· {{ fmtBytes(f.sizeBytes) }}</span>
              </div>
              <div class="ap-filename" :title="f.fileName">{{ f.fileName }}</div>
              <div class="ap-actions">
                <button
                  class="ap-btn ap-btn-preview"
                  @click="onPreview(f)"
                  title="Άνοιγμα σε νέα καρτέλα">
                  <span class="ap-btn-icon">👁</span>
                  <span class="ap-btn-label">Προβολή</span>
                </button>
                <button
                  class="ap-btn ap-btn-download"
                  @click="onDownload(f, idx)"
                  :disabled="downloadingIdx === idx"
                  title="Λήψη αρχείου">
                  <span class="ap-btn-icon">⬇</span>
                  <span v-if="downloadingIdx === idx" class="ap-btn-label">...</span>
                  <span v-else class="ap-btn-label">Λήψη</span>
                </button>
              </div>
            </div>

          </div>
        </div>

      </div>

    </div>
  

    <!-- Upload Modal -->
    <UploadDocumentModal
      :visible="showUpload"
      :transaction="transaction"
      @close="showUpload = false"
      @uploaded="onUploadComplete" />

  </div>
</template>

<style scoped>
/* Backdrop */
.ap-backdrop {
  position: fixed; inset: 0;
  background: rgba(10, 15, 25, 0.75);
  backdrop-filter: blur(3px);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
  padding: 20px;
}

/* Dialog */
.ap-dialog {
  background: #1a2332;
  border: 1px solid #2c3e50;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  width: 100%;
  max-width: 600px;
  max-height: 85vh;
  display: flex; flex-direction: column;
  color: #e0e6ed;
}

/* Header */
.ap-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20px 24px 10px;
}
.ap-title {
  display: flex; align-items: center; gap: 10px;
}
.ap-paperclip {
  font-size: 1.3rem; color: #4A9EFF;
}
.ap-title h3 {
  margin: 0; font-size: 1.15rem; font-weight: 700; color: #fff;
}
.ap-close {
  background: transparent; border: none;
  color: #9aa5b1; font-size: 1.6rem; line-height: 1;
  cursor: pointer; padding: 0 6px; border-radius: 4px;
}
.ap-close:hover { color: #fff; background: #2c3e50; }

/* Subtitle */
.ap-subtitle {
  padding: 0 24px 18px;
  color: #7a8594;
  font-size: 0.9rem;
  border-bottom: 1px solid #2c3e50;
}

/* Content */
.ap-content {
  flex: 1; overflow-y: auto;
  padding: 18px 24px 24px;
}

/* Loading */
.ap-loading {
  display: flex; align-items: center; justify-content: center; gap: 12px;
  padding: 40px 20px; color: #9aa5b1;
}
.ap-spinner {
  width: 20px; height: 20px;
  border: 2px solid #2c3e50; border-top-color: #4FC3A1;
  border-radius: 50%;
  animation: ap-spin 0.8s linear infinite;
}
@keyframes ap-spin { to { transform: rotate(360deg); } }

/* Error */
.ap-error {
  padding: 14px 16px;
  background: rgba(255, 107, 107, 0.1);
  border: 1px solid rgba(255, 107, 107, 0.3);
  border-radius: 6px;
  color: #FF6B6B; font-size: 0.88rem;
  display: flex; justify-content: space-between; align-items: center; gap: 10px;
}
.ap-retry {
  background: transparent; border: 1px solid #FF6B6B;
  color: #FF6B6B; padding: 4px 10px; border-radius: 4px;
  cursor: pointer; font-size: 0.82rem;
}
.ap-retry:hover { background: rgba(255, 107, 107, 0.15); }

/* Empty */
.ap-empty {
  text-align: center; padding: 40px 20px; color: #7a8594;
}
.ap-empty-icon { font-size: 2.5rem; margin-bottom: 12px; }
.ap-empty-title { font-size: 1rem; font-weight: 600; color: #9aa5b1; margin-bottom: 4px; }
.ap-empty-sub { font-size: 0.85rem; }

/* List */
.ap-list {
  display: flex; flex-direction: column; gap: 14px;
}

/* File card */
.ap-card {
  display: flex; gap: 14px;
  background: #111a25;
  border: 1px solid #2c3e50;
  border-radius: 10px;
  padding: 14px;
  transition: border-color 0.15s;
}
.ap-card:hover { border-color: #3a4a5e; }

/* File icon */
.ap-fileicon {
  width: 44px; height: 54px;
  border-radius: 4px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  font-weight: 700; color: #fff;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4);
}
.ap-fileicon-label {
  font-size: 0.72rem; letter-spacing: 0.5px;
}

/* File body */
.ap-filebody {
  flex: 1; min-width: 0;
  display: flex; flex-direction: column; gap: 5px;
}
.ap-filemeta {
  font-size: 0.72rem;
  color: #6c7a8a;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
}
.ap-filesize {
  text-transform: none;
  color: #566374;
  font-weight: 400;
}
.ap-filename {
  font-size: 0.95rem; font-weight: 600; color: #e0e6ed;
  line-height: 1.35;
  word-break: break-word;
}

/* Actions */
.ap-actions {
  display: flex; gap: 8px; margin-top: 6px;
}
.ap-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 7px 14px; border-radius: 6px;
  border: none; cursor: pointer;
  font-size: 0.85rem; font-weight: 600;
  transition: background 0.15s, transform 0.05s;
}
.ap-btn:active:not(:disabled) { transform: translateY(1px); }
.ap-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ap-btn-preview {
  background: #4A9EFF; color: #0d1f2d;
}
.ap-btn-preview:hover:not(:disabled) { background: #5fb0ff; }
.ap-btn-download {
  background: #2c3e50; color: #e0e6ed;
}
.ap-btn-download:hover:not(:disabled) { background: #3a4f66; }
.ap-btn-icon { font-size: 0.95rem; }
.ap-btn-label {
  text-decoration: underline;
  text-underline-offset: 2px;
}
.ap-upload-btn {
  background: transparent;
  border: 1px solid #4FC3A1;
  color: #4FC3A1;
  border-radius: 5px;
  padding: 3px 10px;
  font-size: 0.82rem;
  cursor: pointer;
  margin-right: 8px;
  transition: background 0.15s, color 0.15s;
}
.ap-upload-btn:hover {
  background: #4FC3A1;
  color: #0d1f2d;
}
</style>
