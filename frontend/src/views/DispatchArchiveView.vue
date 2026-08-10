<script setup>
import { ref, computed } from 'vue'

// Presentational component for the "Αποστολές" tab. The parent owns the data
// (dispatch index) and the shared detail modal; this component renders the
// filters + card list and emits open-detail. Faithful to the v4 mockup.
const props = defineProps({
  dispatches: { type: Array, default: () => [] }, // enriched: {id,title,recipient,sentDate,sentDateFull,note,transactionIds,n,sumIn,sumEx,hasPdf,hasDocs}
})
const emit = defineEmits(['open-detail'])

const q = ref('')
const from = ref('')
const to = ref('')

const plain = (n) => Math.abs(n).toFixed(2).replace('.', ',').replace(/\B(?=(\d{3})+(?!\d))/g, '.') + ' €'

const list = computed(() => {
  const needle = q.value.trim().toLowerCase()
  return [...props.dispatches]
    .sort((a, b) => (a.sentDate < b.sentDate ? 1 : -1))
    .filter(d => {
      if (from.value && d.sentDate < from.value) return false
      if (to.value && d.sentDate > to.value) return false
      if (needle) {
        const hay = `${d.title} ${d.recipient} ${d.note || ''} ${(d.transactionIds || []).join(' ')}`.toLowerCase()
        if (!hay.includes(needle)) return false
      }
      return true
    })
})

function clearFilters() { q.value = ''; from.value = ''; to.value = '' }
</script>

<template>
  <div>
    <div class="note"><b>Αρχείο αποστολών.</b> Κάθε φορά που στέλνεις ένα report, αποθηκεύεται εδώ με τον
      τίτλο που έγραψες, τον παραλήπτη, την ημερομηνία και το ίδιο το PDF. Κάνε κλικ σε μια αποστολή για να δεις
      τι ακριβώς περιείχε και να ανοίξεις ξανά το αρχείο.</div>

    <div class="arcfilters">
      <div class="field search-field"><label for="aq">Αναζήτηση</label>
        <input type="text" id="aq" v-model="q" placeholder="Τίτλος, παραλήπτης ή ID κίνησης…"></div>
      <div class="field"><label for="afrom">Από</label><input type="date" id="afrom" v-model="from"></div>
      <div class="field"><label for="ato">Έως</label><input type="date" id="ato" v-model="to"></div>
      <button class="btn" @click="clearFilters">Καθαρισμός</button>
    </div>

    <div class="arc">
      <div v-for="d in list" :key="d.id" class="card" tabindex="0" role="button"
           @click="emit('open-detail', d.id)"
           @keydown.enter.prevent="emit('open-detail', d.id)"
           @keydown.space.prevent="emit('open-detail', d.id)">
        <div class="main">
          <div class="t">{{ d.title }}</div>
          <div class="meta">
            <span>📅 <b>{{ d.sentDateFull }}</b></span>
            <span>✉ προς <b>{{ d.recipient }}</b></span>
            <span>{{ d.n }} {{ d.n === 1 ? 'κίνηση' : 'κινήσεις' }}</span>
          </div>
        </div>
        <div class="nums">
          <div v-if="d.sumIn"><div class="k">Εισπράξεις</div><div class="v amt-pos">{{ plain(d.sumIn) }}</div></div>
          <div v-if="d.sumEx"><div class="k">Έξοδα</div><div class="v amt-neg">{{ plain(d.sumEx) }}</div></div>
        </div>
        <span v-if="d.hasPdf" class="pdfchip">📄 PDF</span>
        <span v-if="d.hasDocs" class="docchip">📎 Παραστατικά</span>
      </div>

      <div v-if="list.length === 0" class="empty" style="background:var(--panel);border:1px solid var(--line);border-radius:8px">
        <b>Καμία αποστολή</b>{{ dispatches.length ? 'Άλλαξε τα φίλτρα αναζήτησης.' : 'Φτιάξε ένα report και πάτα «Αποστολή & αρχειοθέτηση».' }}
      </div>
    </div>
  </div>
</template>
