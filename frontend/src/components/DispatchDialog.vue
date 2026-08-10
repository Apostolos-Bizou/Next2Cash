<script setup>
import { ref, watch, onMounted } from 'vue'
import { getRecipients } from '@/api/dispatches'

const props = defineProps({
  open: { type: Boolean, default: false },
  defaultTitle: { type: String, default: '' },
  itemCount: { type: Number, default: 0 },
  busy: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'submit'])

const title = ref('')
const recipient = ref('')
const note = ref('')
const sentDate = ref('')
const includeDocs = ref(true)
const recipients = ref([])
const error = ref('')

function todayIso() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate())
}

watch(() => props.open, (v) => {
  if (v) {
    title.value = props.defaultTitle || ''
    recipient.value = ''
    note.value = ''
    sentDate.value = todayIso()
    includeDocs.value = true
    error.value = ''
    loadRecipients()
  }
})

async function loadRecipients() {
  try { recipients.value = await getRecipients() } catch { recipients.value = [] }
}

onMounted(() => { if (props.open) loadRecipients() })

function submit() {
  error.value = ''
  if (!title.value.trim()) { error.value = 'Ο τίτλος είναι υποχρεωτικός.'; return }
  if (!recipient.value.trim()) { error.value = 'Ο παραλήπτης είναι υποχρεωτικός.'; return }
  if (props.itemCount === 0) { error.value = 'Δεν υπάρχουν κινήσεις στο report.'; return }
  emit('submit', {
    title: title.value.trim(),
    recipient: recipient.value.trim(),
    note: note.value.trim(),
    sentDate: sentDate.value,
    includeDocs: includeDocs.value,
  })
}
</script>

<template>
  <div v-if="open" class="dd-overlay" @click.self="emit('close')">
    <div class="dd-modal">
      <div class="dd-head">
        <span class="dd-title">📤 Αποστολή Αναφοράς</span>
        <button class="dd-close" @click="emit('close')">✕</button>
      </div>

      <div class="dd-body">
        <label class="dd-label">ΤΙΤΛΟΣ ΑΝΑΦΟΡΑΣ</label>
        <input v-model="title" class="dd-input" placeholder="π.χ. Απόδοση Δαπανών Ιανουαρίου" />

        <label class="dd-label">ΠΑΡΑΛΗΠΤΗΣ</label>
        <input v-model="recipient" class="dd-input" list="dd-recipients" placeholder="π.χ. Λεωνίδας" />
        <datalist id="dd-recipients">
          <option v-for="r in recipients" :key="r" :value="r" />
        </datalist>

        <label class="dd-label">ΗΜΕΡΟΜΗΝΙΑ ΑΠΟΣΤΟΛΗΣ</label>
        <input v-model="sentDate" type="date" class="dd-input" />

        <label class="dd-label">ΣΗΜΕΙΩΣΗ (προαιρετικό)</label>
        <textarea v-model="note" class="dd-input dd-textarea" placeholder="Σημείωση προς παραλήπτη..."></textarea>

        <label class="dd-check">
          <input type="checkbox" v-model="includeDocs" />
          <span>Να συμπεριληφθούν τα παραστατικά (ZIP)</span>
        </label>

        <div class="dd-count">{{ itemCount }} κινήσεις θα σταλούν</div>
        <div v-if="error" class="dd-error">{{ error }}</div>
      </div>

      <div class="dd-foot">
        <button class="dd-cancel" @click="emit('close')" :disabled="busy">Άκυρο</button>
        <button class="dd-submit" @click="submit" :disabled="busy">
          {{ busy ? 'Αποστολή…' : '📤 Αποστολή & Αρχειοθέτηση' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dd-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); z-index: 2000;
  display: flex; align-items: center; justify-content: center; padding: 24px; }
.dd-modal { background: #1a2f45; border: 1px solid #223d57; border-radius: 10px;
  width: min(460px, 96vw); display: flex; flex-direction: column; overflow: hidden; }
.dd-head { display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px; background: #152538; border-bottom: 1px solid #223d57; }
.dd-title { font-weight: 700; color: #e0e6ed; font-size: 0.9rem; }
.dd-close { background: none; border: none; color: #8899aa; font-size: 1rem; cursor: pointer; }
.dd-body { padding: 16px; display: flex; flex-direction: column; gap: 6px; }
.dd-label { font-size: 0.68rem; color: #8899aa; font-weight: 600; letter-spacing: 0.05em; margin-top: 8px; }
.dd-input { background: #152538; border: 1px solid #2a4a6a; color: #c8d8e8;
  padding: 8px 10px; border-radius: 6px; font-size: 0.85rem; outline: none; width: 100%; box-sizing: border-box; }
.dd-input:focus { border-color: #4FC3A1; }
.dd-textarea { min-height: 60px; resize: vertical; }
.dd-check { display: flex; align-items: center; gap: 8px; margin-top: 12px; cursor: pointer; font-size: 0.82rem; color: #c8d8e8; }
.dd-check input { accent-color: #4FC3A1; width: 16px; height: 16px; }
.dd-count { margin-top: 12px; font-size: 0.78rem; color: #4FC3A1; font-weight: 600; }
.dd-error { margin-top: 6px; font-size: 0.78rem; color: #ef5350; }
.dd-foot { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 16px;
  background: #152538; border-top: 1px solid #223d57; }
.dd-cancel { background: #223d57; border: none; color: #c8d8e8; padding: 8px 14px;
  border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.dd-submit { background: #4FC3A1; border: none; color: #0d1e2e; padding: 8px 16px;
  border-radius: 6px; font-weight: 700; cursor: pointer; font-size: 0.82rem; }
.dd-submit:disabled, .dd-cancel:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
