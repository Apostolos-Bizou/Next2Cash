<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'

const props = defineProps({
  blob: { type: Object, default: null },   // a PDF Blob, or null when closed
  title: { type: String, default: 'Προεπισκόπηση PDF' },
})
const emit = defineEmits(['close'])

const url = ref('')

function revoke() {
  if (url.value) { URL.revokeObjectURL(url.value); url.value = '' }
}

watch(() => props.blob, (b) => {
  revoke()
  if (b) url.value = URL.createObjectURL(new Blob([b], { type: 'application/pdf' }))
}, { immediate: true })

onBeforeUnmount(revoke)

function download() {
  if (!url.value) return
  const a = document.createElement('a')
  a.href = url.value
  a.download = 'preview.pdf'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}
</script>

<template>
  <div v-if="blob" class="pv-overlay" @click.self="emit('close')">
    <div class="pv-modal">
      <div class="pv-head">
        <span class="pv-title">{{ title }}</span>
        <div class="pv-actions">
          <button class="pv-btn" @click="download">⬇ Λήψη</button>
          <button class="pv-close" @click="emit('close')">✕</button>
        </div>
      </div>
      <iframe v-if="url" :src="url" class="pv-frame" title="PDF preview"></iframe>
    </div>
  </div>
</template>

<style scoped>
.pv-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); z-index: 2000;
  display: flex; align-items: center; justify-content: center; padding: 24px; }
.pv-modal { background: #0d1e2e; border: 1px solid #223d57; border-radius: 10px;
  width: min(1100px, 96vw); height: 90vh; display: flex; flex-direction: column; overflow: hidden; }
.pv-head { display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: #1a2f45; border-bottom: 1px solid #223d57; }
.pv-title { font-weight: 700; color: #e0e6ed; font-size: 0.9rem; }
.pv-actions { display: flex; gap: 8px; align-items: center; }
.pv-btn { background: #4FC3A1; border: none; color: #0d1e2e; padding: 6px 12px;
  border-radius: 6px; font-weight: 700; cursor: pointer; font-size: 0.82rem; }
.pv-close { background: none; border: none; color: #8899aa; font-size: 1rem; cursor: pointer; }
.pv-frame { flex: 1; width: 100%; border: none; background: #fff; }
</style>
