<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/stores/user'
import { listDispatches, getDispatchPdf, deleteDispatch, openPdfBlob } from '@/api/dispatches'

const userStore = useUserStore()
const isAdmin = computed(() => (userStore.profile?.role || '').toLowerCase() === 'admin')

const rows = ref([])
const loading = ref(false)
const error = ref('')
const q = ref('')
const from = ref('')
const to = ref('')
const busyId = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listDispatches({
      q: q.value.trim() || undefined,
      from: from.value || undefined,
      to: to.value || undefined,
    })
  } catch (e) {
    error.value = e.response?.status === 403
      ? 'Δεν έχετε πρόσβαση στο αρχείο αποστολών.'
      : 'Αποτυχία φόρτωσης αρχείου.'
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function viewPdf(row) {
  busyId.value = row.id
  try {
    const blob = await getDispatchPdf(row.id)
    openPdfBlob(blob)
  } catch (e) {
    alert(e.response?.status === 404 ? 'Το PDF δεν βρέθηκε.' : 'Αποτυχία λήψης PDF.')
  } finally {
    busyId.value = ''
  }
}

async function remove(row) {
  if (!confirm('Διαγραφή της αποστολής «' + row.title + '»; Οι κινήσεις επανέρχονται σε «μη απεσταλμένο».')) return
  busyId.value = row.id
  try {
    await deleteDispatch(row.id)
    rows.value = rows.value.filter(r => r.id !== row.id)
  } catch (e) {
    alert(e.response?.status === 403 ? 'Μόνο ο διαχειριστής μπορεί να διαγράψει.' : 'Αποτυχία διαγραφής.')
  } finally {
    busyId.value = ''
  }
}

const fmtDate = (d) => {
  if (!d) return '—'
  const p = String(d).split('-')
  return p.length === 3 ? p[2] + '/' + p[1] + '/' + p[0] : d
}

let onEntity
onMounted(() => {
  load()
  onEntity = () => load()
  window.addEventListener('entity-changed', onEntity)
})
onBeforeUnmount(() => { if (onEntity) window.removeEventListener('entity-changed', onEntity) })
</script>

<template>
  <div class="da-page">
    <div class="da-toolbar">
      <div class="da-title">Αρχείο Αποστολών</div>
      <div class="da-filters">
        <input v-model="q" class="da-input" placeholder="Αναζήτηση τίτλου, παραλήπτη, ID κίνησης..." @keyup.enter="load" />
        <input v-model="from" type="date" class="da-input" title="Από" />
        <input v-model="to" type="date" class="da-input" title="Έως" />
        <button class="da-search" @click="load">Αναζήτηση</button>
      </div>
    </div>

    <div v-if="loading" class="da-msg">Φόρτωση…</div>
    <div v-else-if="error" class="da-msg da-err">{{ error }}</div>
    <div v-else-if="rows.length === 0" class="da-msg">Καμία αποστολή.</div>

    <table v-else class="da-table">
      <thead>
        <tr>
          <th>ΤΙΤΛΟΣ</th>
          <th>ΠΑΡΑΛΗΠΤΗΣ</th>
          <th>ΗΜ. ΑΠΟΣΤΟΛΗΣ</th>
          <th>PDF</th>
          <th class="da-actions-col">ΕΝΕΡΓΕΙΕΣ</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td class="da-title-cell">{{ row.title }}</td>
          <td>{{ row.recipient }}</td>
          <td>{{ fmtDate(row.sentDate) }}</td>
          <td>
            <span v-if="row.hasPdf" class="da-pill da-pill-ok">PDF</span>
            <span v-else class="da-pill da-pill-no">—</span>
          </td>
          <td class="da-actions">
            <button class="da-btn" :disabled="!row.hasPdf || busyId === row.id" @click="viewPdf(row)">👁 Προβολή</button>
            <button v-if="isAdmin" class="da-btn da-del" :disabled="busyId === row.id" @click="remove(row)">🗑 Διαγραφή</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.da-page { padding: 20px 24px; background: #0d1e2e; min-height: 100vh; color: #c8d8e8; }
.da-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin-bottom: 18px; }
.da-title { font-size: 1.1rem; font-weight: 700; color: #e0e6ed; }
.da-filters { display: flex; gap: 8px; flex-wrap: wrap; }
.da-input { background: #152538; border: 1px solid #2a4a6a; color: #c8d8e8; padding: 8px 10px; border-radius: 6px; font-size: 0.82rem; outline: none; }
.da-input:focus { border-color: #4FC3A1; }
.da-search { background: #29b6f6; border: none; color: #0d1e2e; padding: 8px 16px; border-radius: 6px; font-weight: 700; cursor: pointer; }
.da-msg { padding: 30px; text-align: center; color: #8899aa; }
.da-err { color: #ef5350; }
.da-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; background: #1a2f45; border-radius: 10px; overflow: hidden; }
.da-table th { text-align: left; padding: 10px 14px; background: #152538; color: #6a8099; font-size: 0.7rem; font-weight: 700; letter-spacing: 0.05em; }
.da-table td { padding: 10px 14px; border-bottom: 1px solid #1e3448; }
.da-title-cell { font-weight: 600; color: #e0e6ed; }
.da-pill { padding: 2px 8px; border-radius: 10px; font-size: 0.68rem; font-weight: 700; }
.da-pill-ok { background: rgba(79,195,161,0.15); color: #4FC3A1; }
.da-pill-no { background: #223d57; color: #8899aa; }
.da-actions-col { text-align: right; }
.da-actions { display: flex; gap: 8px; justify-content: flex-end; }
.da-btn { background: #223d57; border: 1px solid #2a4a6a; color: #c8d8e8; padding: 5px 12px; border-radius: 6px; cursor: pointer; font-size: 0.78rem; }
.da-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.da-del { border-color: #ef5350; color: #ef5350; background: rgba(239,83,80,0.08); }
</style>
