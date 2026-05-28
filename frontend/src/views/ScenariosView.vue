<script setup>
// ScenariosView.vue — S97 Scenario management (Cash Planning spec §5.8 / Principle 3)
// Reads /api/scenarios (entity-scoped) and lets ADMIN edit revenue/expense
// adjustment percentages + color + name. Baseline is locked at 0/0.
// Pattern mirrors ProjectsView.vue (entity from localStorage, entity-changed listener).
import { ref, computed, onMounted, onUnmounted } from 'vue'
import api from '@/api'

const scenarios = ref([])
const loading = ref(false)
const error = ref('')
const saving = ref(false)

const isAdmin = computed(() => {
  try {
    const raw = localStorage.getItem('n2c_user')
    if (!raw) return false
    const u = JSON.parse(raw)
    return (u.role || '').toLowerCase() === 'admin'
  } catch { return false }
})

const ENTITIES = { next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972', house: 'dea1f32c-7b30-4981-b625-633da9dbe71e', polaris: '50317f44-9961-4fb4-add0-7a118e32dc14' }
function currentEntityId() {
  const key = localStorage.getItem('n2c_entity') || 'next2me'
  return ENTITIES[key] || ENTITIES.next2me
}

async function loadScenarios() {
  loading.value = true
  error.value = ''
  try {
    const res = await api.get('/api/scenarios', { params: { entityId: currentEntityId(), activeOnly: true } })
    if (res.data && res.data.success && Array.isArray(res.data.data)) {
      scenarios.value = res.data.data
    } else {
      scenarios.value = []
    }
  } catch (e) {
    console.error('loadScenarios error:', e)
    error.value = (e.response && e.response.data && e.response.data.error) || e.message || 'Σφάλμα.'
    scenarios.value = []
  } finally {
    loading.value = false
  }
}

function isBaseline(s) { return (s.scenarioType || '').toUpperCase() === 'BASELINE' || s.isDefault === true }

const showModal = ref(false)
const modalForm = ref({ id: '', name: '', scenarioType: '', revenueAdjustPct: 0, expenseAdjustPct: 0, color: '#6B7280', description: '' })

function openEdit(s) {
  if (!isAdmin.value) { window.alert('Μόνο διαχειριστές μπορούν να επεξεργαστούν σενάρια.'); return }
  if (isBaseline(s)) { window.alert('Το Baseline είναι κλειδωμένο στο 0% / 0% — είναι το σημείο αναφοράς.'); return }
  modalForm.value = {
    id: s.id,
    name: s.name || '',
    scenarioType: s.scenarioType || 'CUSTOM',
    revenueAdjustPct: Number(s.revenueAdjustPct) || 0,
    expenseAdjustPct: Number(s.expenseAdjustPct) || 0,
    color: s.color || '#6B7280',
    description: s.description || ''
  }
  showModal.value = true
}
function closeModal() { showModal.value = false }

async function saveScenario() {
  saving.value = true
  try {
    const payload = {
      name: modalForm.value.name,
      revenueAdjustPct: Number(modalForm.value.revenueAdjustPct) || 0,
      expenseAdjustPct: Number(modalForm.value.expenseAdjustPct) || 0,
      color: modalForm.value.color,
      description: modalForm.value.description
    }
    const res = await api.put('/api/scenarios/' + modalForm.value.id, payload)
    if (res.data && res.data.success) {
      showModal.value = false
      await loadScenarios()
    } else {
      window.alert((res.data && res.data.error) || 'Σφάλμα.')
    }
  } catch (e) {
    window.alert((e.response && e.response.data && e.response.data.error) || e.message || 'Σφάλμα.')
  } finally {
    saving.value = false
  }
}

function onEntityChanged() { loadScenarios() }
onMounted(() => {
  loadScenarios()
  window.addEventListener('entity-changed', onEntityChanged)
})
onUnmounted(() => {
  window.removeEventListener('entity-changed', onEntityChanged)
})
</script>

<template>
  <div class="scenarios-view">
    <div class="sv-header">
      <div>
        <h1 class="sv-title">Σενάρια</h1>
        <p class="sv-sub">Διαχείριση σεναρίων πρόβλεψης ανά εταιρεία (Baseline / Optimistic / Pessimistic / Custom).</p>
      </div>
      <button class="btn-reload" @click="loadScenarios" :disabled="loading">↻ Ανανέωση</button>
    </div>

    <div v-if="loading" class="sv-state">Φόρτωση σεναρίων…</div>
    <div v-else-if="error" class="sv-state sv-error">
      {{ error }}
      <button class="btn-retry" @click="loadScenarios">Δοκιμή ξανά</button>
    </div>
    <div v-else-if="scenarios.length === 0" class="sv-state">Δεν βρέθηκαν σενάρια για αυτή την εταιρεία.</div>

    <div v-else class="sv-grid">
      <div v-for="s in scenarios" :key="s.id" class="sv-card" :style="{ borderLeftColor: s.color || '#6B7280' }">
        <div class="sv-card-head">
          <span class="sv-card-name">{{ s.name }}</span>
          <span v-if="isBaseline(s)" class="sv-badge">ΠΡΟΕΠΙΛΟΓΗ</span>
        </div>
        <div class="sv-card-body">
          <div class="sv-row"><span>Έσοδα</span><strong :style="{color: (s.revenueAdjustPct>0?'#10b981':(s.revenueAdjustPct<0?'#ef4444':'#64748b'))}">{{ Number(s.revenueAdjustPct).toFixed(0) }}%</strong></div>
          <div class="sv-row"><span>Έξοδα</span><strong :style="{color: (s.expenseAdjustPct>0?'#ef4444':(s.expenseAdjustPct<0?'#10b981':'#64748b'))}">{{ Number(s.expenseAdjustPct).toFixed(0) }}%</strong></div>
        </div>
        <div class="sv-card-foot">
          <span v-if="isBaseline(s)" class="sv-locked">κλειδωμένο</span>
          <button v-else-if="isAdmin" class="btn-edit" @click="openEdit(s)">✎ Επεξεργασία</button>
        </div>
      </div>
    </div>

    <div v-if="showModal" class="sv-modal-overlay" @click.self="closeModal">
      <div class="sv-modal">
        <h2 class="sv-modal-title">Επεξεργασία σεναρίου</h2>
        <div class="sv-field">
          <label>Όνομα</label>
          <input v-model="modalForm.name" type="text" class="sv-input" />
        </div>
        <div class="sv-field-row">
          <div class="sv-field">
            <label>Προσαρμογή εσόδων (%)</label>
            <input v-model.number="modalForm.revenueAdjustPct" type="number" step="1" class="sv-input" />
          </div>
          <div class="sv-field">
            <label>Προσαρμογή εξόδων (%)</label>
            <input v-model.number="modalForm.expenseAdjustPct" type="number" step="1" class="sv-input" />
          </div>
        </div>
        <div class="sv-field">
          <label>Χρώμα</label>
          <input v-model="modalForm.color" type="color" class="sv-color" />
        </div>
        <div class="sv-field">
          <label>Περιγραφή</label>
          <textarea v-model="modalForm.description" rows="2" class="sv-input"></textarea>
        </div>
        <div class="sv-modal-foot">
          <button class="btn-cancel" @click="closeModal">Άκυρο</button>
          <button class="btn-save" @click="saveScenario" :disabled="saving">{{ saving ? 'Αποθήκευση…' : 'Αποθήκευση' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.scenarios-view { padding: 24px; }
.sv-header { display:flex; align-items:flex-start; justify-content:space-between; margin-bottom:20px; gap:16px; }
.sv-title { font-size:1.6rem; font-weight:700; color:#1e293b; margin:0; }
.sv-sub { font-size:.9rem; color:#64748b; margin:4px 0 0; font-style:italic; }
.btn-reload { padding:8px 16px; border:1px solid #d6dee8; border-radius:8px; background:#fff; color:#2E75B6; cursor:pointer; font-weight:600; white-space:nowrap; }
.btn-reload:disabled { opacity:.5; cursor:default; }
.sv-state { padding:40px; text-align:center; color:#64748b; }
.sv-error { color:#ef4444; }
.btn-retry { margin-left:12px; padding:6px 14px; border:1px solid #ef4444; border-radius:6px; background:#fff; color:#ef4444; cursor:pointer; }
.sv-grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(200px, 1fr)); gap:16px; }
.sv-card { background:#fff; border:1px solid #e2e8f0; border-left:4px solid #6B7280; border-radius:12px; padding:16px; }
.sv-card-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.sv-card-name { font-weight:700; color:#1e293b; font-size:1.05rem; }
.sv-badge { background:#eef4fb; color:#2E75B6; font-size:.7rem; font-weight:700; padding:2px 8px; border-radius:6px; }
.sv-card-body { display:flex; flex-direction:column; gap:6px; margin-bottom:12px; }
.sv-row { display:flex; align-items:center; justify-content:space-between; font-size:.9rem; color:#64748b; }
.sv-card-foot { border-top:1px solid #f1f5f9; padding-top:10px; }
.sv-locked { font-size:.8rem; color:#94a3b8; }
.btn-edit { padding:5px 12px; border:1px solid #d6dee8; border-radius:6px; background:#fff; color:#2E75B6; cursor:pointer; font-size:.85rem; font-weight:600; }
.sv-modal-overlay { position:fixed; inset:0; background:rgba(15,23,42,.45); display:flex; align-items:center; justify-content:center; z-index:1000; }
.sv-modal { background:#fff; border-radius:14px; padding:24px; width:min(440px, 92vw); }
.sv-modal-title { font-size:1.2rem; font-weight:700; color:#1e293b; margin:0 0 18px; }
.sv-field { margin-bottom:14px; }
.sv-field label { display:block; font-size:.85rem; color:#64748b; margin-bottom:5px; font-weight:600; }
.sv-field-row { display:flex; gap:12px; }
.sv-field-row .sv-field { flex:1; }
.sv-input { width:100%; padding:8px 10px; border:1px solid #d6dee8; border-radius:8px; font-size:.95rem; box-sizing:border-box; }
.sv-color { width:60px; height:38px; border:1px solid #d6dee8; border-radius:8px; cursor:pointer; }
.sv-modal-foot { display:flex; justify-content:flex-end; gap:10px; margin-top:8px; }
.btn-cancel { padding:8px 18px; border:1px solid #d6dee8; border-radius:8px; background:#fff; color:#64748b; cursor:pointer; }
.btn-save { padding:8px 18px; border:none; border-radius:8px; background:#2E75B6; color:#fff; cursor:pointer; font-weight:600; }
.btn-save:disabled { opacity:.6; cursor:default; }
</style>
