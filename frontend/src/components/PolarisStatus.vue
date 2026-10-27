<script setup>
// PolarisStatus — loading / error / access wrapper for the Polaris P&L pages (Phase 3).
//
// Loads the shared polarisPl store on every activation (the views live under
// <KeepAlive>, so onActivated also fires on first mount) and on the sidebar's
// 'entity-changed' event, same pattern as the other Next2Cash views.
// Renders the page (default slot) only when the caller's real Polaris role
// (GET /api/polaris/my-role) is allowed to see it.
import { onActivated, onMounted, onUnmounted } from 'vue'
import { usePolarisPlStore } from '@/stores/polarisPl'

const props = defineProps({
  allowed: { type: Boolean, required: true },
  deniedText: { type: String, default: 'Δεν έχεις πρόσβαση σε αυτή τη σελίδα του Polaris P&L.' },
})

const pl = usePolarisPlStore()

function reload() { pl.load() }
onActivated(reload)
onMounted(() => window.addEventListener('entity-changed', reload))
onUnmounted(() => window.removeEventListener('entity-changed', reload))
</script>

<template>
  <div v-if="pl.saveError" class="save-error">
    <div class="dot"></div><div><b>Η αλλαγή δεν αποθηκεύτηκε</b> — {{ pl.saveError }}. Τα στοιχεία ξαναφορτώθηκαν από τον server.</div>
  </div>

  <div v-if="!pl.loaded && pl.loading" class="section">
    <div class="sec-body locked-card"><div>Φόρτωση…</div></div>
  </div>
  <div v-else-if="pl.loadError" class="section">
    <div class="sec-body locked-card">
      <div>Δεν ήταν δυνατή η φόρτωση των στοιχείων Polaris.</div>
      <div class="tnum" style="font-size:12.5px">{{ pl.loadError }}</div>
      <button class="btn" @click="reload">Δοκιμή ξανά</button>
    </div>
  </div>
  <div v-else-if="!pl.hasAccess || !props.allowed" class="section">
    <div class="sec-body locked-card">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="11" width="18" height="10" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
      <div>{{ pl.hasAccess ? props.deniedText : 'Δεν έχεις ρόλο στο Polaris (entity_roles). Επικοινώνησε με τον διαχειριστή.' }}</div>
    </div>
  </div>
  <slot v-else />
</template>

<style scoped>
.save-error{display:flex;gap:12px;align-items:flex-start;background:var(--danger-bg);border:1px solid rgba(239,68,68,.4);
  border-radius:var(--radius-md);padding:12px 16px;margin-bottom:18px;font-size:13.5px;color:var(--text-secondary)}
.save-error b{color:var(--danger)}
.save-error .dot{width:7px;height:7px;border-radius:50%;background:var(--danger);flex:0 0 7px;margin-top:6px}
</style>
