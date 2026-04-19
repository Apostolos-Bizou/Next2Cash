<script setup>
import { ref, computed } from 'vue'
import api from '@/api'

// -----------------------------------------------------------------
// DocumentsView — ZIP Export for accountants
// Select entity + date range → download all documents as ZIP
// Backend: GET /api/documents/export?entityId=X&from=YYYY-MM-DD&to=YYYY-MM-DD
// -----------------------------------------------------------------

const ENTITIES = {
  'next2me': { id: '58202b71-4ddb-45c9-8e3c-39e816bde972', label: 'Next2me' },
  'house':   { id: 'dea1f32c-7b30-4981-b625-633da9dbe71e', label: 'House' },
  'polaris': { id: '50317f44-9961-4fb4-add0-7a118e32dc14', label: 'Polaris' }
}

// State
const selectedEntity = ref(localStorage.getItem('n2c_entity') || 'next2me')
const dateFrom = ref('')
const dateTo = ref('')
const downloading = ref(false)
const statusMsg = ref('')
const statusType = ref('')  // 'success' | 'error' | 'info'

// Set default date range: current month
const now = new Date()
const y = now.getFullYear()
const m = String(now.getMonth() + 1).padStart(2, '0')
dateFrom.value = y + '-' + m + '-01'
// Last day of current month
const lastDay = new Date(y, now.getMonth() + 1, 0).getDate()
dateTo.value = y + '-' + m + '-' + String(lastDay).padStart(2, '0')

// Quick presets
function setPreset(preset) {
  const today = new Date()
  const yr = today.getFullYear()
  const mn = today.getMonth()

  if (preset === 'thisMonth') {
    dateFrom.value = yr + '-' + String(mn + 1).padStart(2, '0') + '-01'
    const ld = new Date(yr, mn + 1, 0).getDate()
    dateTo.value = yr + '-' + String(mn + 1).padStart(2, '0') + '-' + String(ld).padStart(2, '0')
  } else if (preset === 'lastMonth') {
    const pm = mn === 0 ? 11 : mn - 1
    const py = mn === 0 ? yr - 1 : yr
    dateFrom.value = py + '-' + String(pm + 1).padStart(2, '0') + '-01'
    const ld = new Date(py, pm + 1, 0).getDate()
    dateTo.value = py + '-' + String(pm + 1).padStart(2, '0') + '-' + String(ld).padStart(2, '0')
  } else if (preset === 'thisYear') {
    dateFrom.value = yr + '-01-01'
    dateTo.value = yr + '-12-31'
  } else if (preset === 'lastYear') {
    dateFrom.value = (yr - 1) + '-01-01'
    dateTo.value = (yr - 1) + '-12-31'
  }
}

const canDownload = computed(() =>
  !downloading.value && selectedEntity.value && dateFrom.value && dateTo.value
)

async function downloadZip() {
  if (!canDownload.value) return
  downloading.value = true
  statusMsg.value = ''

  const entityId = ENTITIES[selectedEntity.value]?.id
  if (!entityId) {
    statusMsg.value = 'Δεν βρέθηκε η εταιρεία'
    statusType.value = 'error'
    downloading.value = false
    return
  }

  try {
    statusMsg.value = 'Δημιουργία ZIP αρχείου...'
    statusType.value = 'info'

    const res = await api.get('/api/documents/export', {
      params: { entityId, from: dateFrom.value, to: dateTo.value },
      responseType: 'blob',
      timeout: 120000  // 2 min for large ZIPs
    })

    if (res.status === 204 || !res.data || res.data.size === 0) {
      statusMsg.value = 'Δεν βρέθηκαν παραστατικά για αυτή την περίοδο'
      statusType.value = 'info'
      downloading.value = false
      return
    }

    // Extract filename from Content-Disposition header
    const disposition = res.headers['content-disposition'] || ''
    const fnMatch = disposition.match(/filename="?([^"]+)"?/)
    const filename = fnMatch ? fnMatch[1] : 'ACC_export.zip'

    // Trigger download
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)

    const sizeMB = (res.data.size / 1024 / 1024).toFixed(2)
    statusMsg.value = '✓ ' + filename + ' (' + sizeMB + ' MB) — κατεβαίνει!'
    statusType.value = 'success'

  } catch (err) {
    if (err.response?.status === 204) {
      statusMsg.value = 'Δεν βρέθηκαν παραστατικά για αυτή την περίοδο'
      statusType.value = 'info'
    } else if (err.response?.status === 403) {
      statusMsg.value = 'Δεν έχεις δικαίωμα λήψης αρχείων'
      statusType.value = 'error'
    } else {
      statusMsg.value = 'Σφάλμα κατά τη λήψη. Δοκιμάστε ξανά.'
      statusType.value = 'error'
    }
  }

  downloading.value = false
}
</script>

<template>
  <div class="zx-page">
    <div class="zx-container">

      <!-- Header -->
      <div class="zx-header">
        <h1>📁 Λήψη Παραστατικών</h1>
        <p class="zx-desc">Επίλεξε εταιρεία και περίοδο για να κατεβάσεις όλα τα παραστατικά σε ένα ZIP αρχείο.</p>
      </div>

      <!-- Entity selector -->
      <div class="zx-section">
        <label class="zx-label">Εταιρεία</label>
        <div class="zx-entity-btns">
          <button
            v-for="(ent, key) in ENTITIES"
            :key="key"
            class="zx-entity-btn"
            :class="{ active: selectedEntity === key }"
            @click="selectedEntity = key">
            {{ ent.label }}
          </button>
        </div>
      </div>

      <!-- Date range -->
      <div class="zx-section">
        <label class="zx-label">Περίοδος</label>
        <div class="zx-dates">
          <div class="zx-date-field">
            <label>Από</label>
            <input type="date" v-model="dateFrom" :disabled="downloading" />
          </div>
          <div class="zx-date-field">
            <label>Έως</label>
            <input type="date" v-model="dateTo" :disabled="downloading" />
          </div>
        </div>
        <div class="zx-presets">
          <button @click="setPreset('thisMonth')" :disabled="downloading">Τρέχων μήνας</button>
          <button @click="setPreset('lastMonth')" :disabled="downloading">Προηγούμενος</button>
          <button @click="setPreset('thisYear')" :disabled="downloading">Τρέχον έτος</button>
          <button @click="setPreset('lastYear')" :disabled="downloading">Προηγ. έτος</button>
        </div>
      </div>

      <!-- Download button -->
      <div class="zx-section">
        <button
          class="zx-download-btn"
          @click="downloadZip"
          :disabled="!canDownload">
          <span v-if="downloading">⏳ Δημιουργία ZIP...</span>
          <span v-else>⬇ Λήψη ZIP Παραστατικών</span>
        </button>
      </div>

      <!-- Status -->
      <div v-if="statusMsg" class="zx-status" :class="statusType">
        {{ statusMsg }}
      </div>

      <!-- Info box -->
      <div class="zx-info">
        <p>ℹ Το ZIP περιέχει όλα τα παραστατικά (PDF, JPG, PNG) που έχουν ανεβεί για την επιλεγμένη περίοδο.</p>
        <p>Αντί να ανοίγετε κάθε αρχείο ξεχωριστά, κατεβάστε όλα με ένα κλικ.</p>
      </div>

    </div>
  </div>
</template>

<style scoped>
.zx-page {
  min-height: 100vh;
  background: #0d1117;
  padding: 30px 20px;
  color: #e0e6ed;
}
.zx-container {
  max-width: 640px;
  margin: 0 auto;
}
.zx-header { margin-bottom: 30px; }
.zx-header h1 {
  font-size: 1.5rem; font-weight: 700; color: #fff; margin: 0 0 8px;
}
.zx-desc {
  font-size: 0.9rem; color: #7a8594; margin: 0;
}

.zx-section { margin-bottom: 24px; }
.zx-label {
  font-size: 0.75rem; color: #7a8594; text-transform: uppercase;
  letter-spacing: 0.5px; font-weight: 600; margin-bottom: 10px; display: block;
}

.zx-entity-btns { display: flex; gap: 10px; flex-wrap: wrap; }
.zx-entity-btn {
  flex: 1; min-width: 100px;
  padding: 10px 16px;
  background: #111a25; border: 1px solid #2c3e50; border-radius: 8px;
  color: #9aa5b1; font-size: 0.9rem; font-weight: 600;
  cursor: pointer; transition: all 0.15s;
}
.zx-entity-btn:hover { border-color: #4A9EFF; color: #e0e6ed; }
.zx-entity-btn.active {
  border-color: #4A9EFF; color: #4A9EFF; background: #0f1724;
}

.zx-dates { display: flex; gap: 14px; margin-bottom: 12px; }
.zx-date-field { flex: 1; }
.zx-date-field label {
  font-size: 0.75rem; color: #6c7a8a; margin-bottom: 4px; display: block;
}
.zx-date-field input {
  width: 100%; padding: 9px 12px;
  background: #111a25; border: 1px solid #2c3e50; border-radius: 6px;
  color: #e0e6ed; font-size: 0.88rem;
  outline: none; box-sizing: border-box;
}
.zx-date-field input:focus { border-color: #4A9EFF; }

.zx-presets { display: flex; gap: 8px; flex-wrap: wrap; }
.zx-presets button {
  padding: 5px 12px;
  background: transparent; border: 1px solid #2c3e50; border-radius: 5px;
  color: #7a8594; font-size: 0.78rem; cursor: pointer;
  transition: all 0.15s;
}
.zx-presets button:hover:not(:disabled) {
  border-color: #4FC3A1; color: #4FC3A1;
}
.zx-presets button:disabled { opacity: 0.4; cursor: not-allowed; }

.zx-download-btn {
  width: 100%; padding: 14px 20px;
  background: #4FC3A1; color: #0d1f2d; border: none; border-radius: 8px;
  font-size: 1rem; font-weight: 700; cursor: pointer;
  transition: background 0.15s;
}
.zx-download-btn:hover:not(:disabled) { background: #5dd4b0; }
.zx-download-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.zx-status {
  padding: 12px 16px; border-radius: 6px; font-size: 0.88rem; margin-bottom: 20px;
}
.zx-status.success { background: rgba(79,195,161,0.1); border: 1px solid rgba(79,195,161,0.3); color: #4FC3A1; }
.zx-status.error   { background: rgba(255,107,107,0.1); border: 1px solid rgba(255,107,107,0.3); color: #FF6B6B; }
.zx-status.info    { background: rgba(74,158,255,0.1);  border: 1px solid rgba(74,158,255,0.3);  color: #4A9EFF; }

.zx-info {
  padding: 14px 16px;
  background: #111a25; border: 1px solid #2c3e50; border-radius: 8px;
}
.zx-info p {
  font-size: 0.82rem; color: #6c7a8a; margin: 0 0 6px;
}
.zx-info p:last-child { margin: 0; }
</style>
