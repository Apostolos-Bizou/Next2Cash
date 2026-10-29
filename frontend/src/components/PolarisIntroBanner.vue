<script setup>
// PolarisIntroBanner — one-time instructions for the OPEX forecast + multi-raise
// payroll features (Oct 2026). Shown on the Forecast and Payroll pages to roles
// that can write there (the parent gates it with pl.canEdit) until the user
// closes it; the dismissal is persisted per user (POST /api/notices/{key}/dismiss),
// so it never comes back after logout/login or on another device.
import { ref, onMounted } from 'vue'
import { fetchNoticeDismissed, dismissNotice } from '@/api/notices'

// v2 (30 Oct 2026): the wording changed materially (step rule, 9 categories), so the
// banner shows once more to users who had dismissed the first version.
const NOTICE_KEY = 'polaris-opex-raises-intro-v2'

const visible = ref(false)   // hidden until the server says "not dismissed", so there is no flash for returning users
const busy = ref(false)

onMounted(async () => {
  try { visible.value = !(await fetchNoticeDismissed(NOTICE_KEY)) } catch { visible.value = false }
})

async function dismiss() {
  if (busy.value) return
  busy.value = true
  try { await dismissNotice(NOTICE_KEY); visible.value = false } catch { /* leave it visible; next click retries */ } finally { busy.value = false }
}
</script>

<template>
  <div v-if="visible" class="intro-banner" role="status">
    <div class="intro-banner__body">
      <div class="intro-banner__title">Νέο: Πρόβλεψη Λειτουργικών Εξόδων &amp; πολλαπλές αυξήσεις μισθού</div>
      <p>Στη Μισθοδοσία, κάθε άτομο μπορεί τώρα να έχει πάνω από μία μελλοντική αύξηση — άνοιξε τη γραμμή του για να προσθέσεις ημερομηνία + ποσοστό.</p>
      <p>Στις Προβλέψεις, η ενότητα «Πρόβλεψη Λειτουργικών Εξόδων» έχει 9 κατηγορίες: Consultants, Audit, Legal, IT/Computer, Marketing/Διαφήμιση, Ταξίδια, PR, Λοιπά, Σύνδεσμοι. Ο Αστρολάβος δίνει ένα συνδυασμένο ποσό για Consultants+Audit+Legal και ένα για Ταξίδια+PR+Λοιπά+Σύνδεσμοι — το ίδιο ποσό εμφανίζεται ως αναφορά δίπλα σε κάθε υποκατηγορία· τον επιμερισμό τον κάνεις εσύ.</p>
      <p><b>Σημαντικό:</b> κάθε κατηγορία ξεκινάει στα $0 μέχρι να προσθέσεις τον πρώτο στόχο, και κάθε στόχος ισχύει ΒΗΜΑΤΙΚΑ από τον μήνα του και μετά, όπως οι αυξήσεις μισθών (όχι γραμμική παρεμβολή ανάμεσα σε στόχους). Αν θες να αντανακλάται και το ΣΗΜΕΡΙΝΟ πραγματικό κόστος (όχι μόνο το μέλλον), πρόσθεσε πρώτα έναν στόχο κοντά στο σήμερα με το τρέχον ετήσιο run-rate της κατηγορίας, και μετά τους μελλοντικούς στόχους σου πάνω σε αυτόν. Χωρίς αυτό το πρώτο βήμα, η κατηγορία θα παραμείνει $0 στο EBITDA μέχρι τον πρώτο μελλοντικό στόχο.</p>
      <p>Στις Παραδοχές προστέθηκαν οι «Εργοδοτικές εισφορές» (% επί των μισθών προσωπικού, όχι επί ενοικίων) — αφαιρούνται από το EBITDA μαζί με τη μισθοδοσία και τα OPEX. Με το κουμπί «Εξαγωγή σε Excel» κατεβάζεις το P&L του επιλεγμένου ορίζοντα σε .xlsx.</p>
      <p>Το Ενοίκιο και το Medical/3rd-party δεν έχουν ξεχωριστή πρόβλεψη — είναι ήδη μέσα στη Μισθοδοσία και στο κόστος ανά μέλος αντίστοιχα, ώστε να μην μετρηθούν διπλά.</p>
    </div>
    <button class="intro-banner__close" :disabled="busy" @click="dismiss" aria-label="Κλείσιμο" title="Κλείσιμο — δεν θα εμφανιστεί ξανά">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6 6 18M6 6l12 12"/></svg>
    </button>
  </div>
</template>

<style scoped>
.intro-banner{display:flex;gap:14px;align-items:flex-start;background:var(--gold-bg);border:1px solid rgba(245,180,62,.45);
  border-left:4px solid var(--gold);border-radius:var(--radius-md);padding:14px 16px;margin-bottom:18px;font-size:13.5px;color:var(--text-secondary)}
.intro-banner__body{flex:1;min-width:0}
.intro-banner__title{font-weight:700;color:var(--gold);font-size:14.5px;margin-bottom:8px}
.intro-banner p{margin:0 0 8px;line-height:1.5}
.intro-banner p:last-child{margin-bottom:0}
.intro-banner b{color:var(--text-primary)}
.intro-banner__close{flex:0 0 30px;width:30px;height:30px;border-radius:8px;display:flex;align-items:center;justify-content:center;
  color:var(--text-secondary);background:transparent;border:1px solid var(--border);cursor:pointer}
.intro-banner__close:hover{color:var(--text-primary);border-color:var(--border-focus)}
.intro-banner__close svg{width:16px;height:16px}
</style>
