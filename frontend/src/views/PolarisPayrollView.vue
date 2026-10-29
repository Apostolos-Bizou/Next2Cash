<script setup>
// PolarisPayrollView — "Μισθοδοσία & Προσωπικό" page of the Polaris P&L module.
//
// Phase 2 (Oct 2026): 1:1 port of the #pagePayroll block of
// docs/polaris-mockup-reference.html. Phase 3: rows come from
// /api/polaris/payroll/people and the role from /api/polaris/my-role:
//   admin  -> full table, editable (incl. office / name / role, so new rows can be filled in)
//   editor -> full table, read-only
//   viewer -> the mockup's aggregate card, from /api/polaris/payroll/summary (no names / salaries)
//   raises (Oct 2026): each row expands to its list of (ημερομηνία, ποσοστό) steps
//   from payroll_person_raises; the ΑΥΞΗΣΗ / ΙΣΧΥΣ ΑΠΟ columns stay as a shortcut
//   that writes the first step.
import { computed, ref } from 'vue'
import PolarisStatus from '@/components/PolarisStatus.vue'
import PolarisIntroBanner from '@/components/PolarisIntroBanner.vue'
import { usePolarisPlStore, fmtUSD, fmtInt, OFFICES } from '@/stores/polarisPl'
import '@/styles/polaris-pl.css'

const pl = usePolarisPlStore()

// expanded raise lists, by person id
const expanded = ref(new Set())
function toggleRaises(p) {
  const s = new Set(expanded.value)
  if (s.has(p.id)) s.delete(p.id); else s.add(p.id)
  expanded.value = s
}
const fmtPctSigned = n => (n > 0 ? '+' : '') + String(n).replace('.', ',') + '%'

const payrollEditNote = computed(() => pl.isAdmin
  ? 'Αθήνα + Μανίλα · κάθε άτομο με δική του αύξηση'
  : 'Αθήνα + Μανίλα · μόνο προβολή (ο editor δεν αλλάζει δομή/τύπους)')
</script>

<template>
  <div class="polaris-pl">
    <PolarisStatus :allowed="pl.hasAccess">

    <!-- one-time intro for the OPEX / raises features: roles that can write here, until dismissed (per user) -->
    <PolarisIntroBanner v-if="pl.canEdit" />

    <div class="page-head">
      <div>
        <h1>Μισθοδοσία &amp; Προσωπικό</h1>
        <div class="sub">Αθήνα + Μανίλα · ξεχωριστή αύξηση ανά άτομο</div>
      </div>
    </div>

    <!-- ===== Admin / Editor: full payroll table ===== -->
    <div class="section" v-if="pl.canEdit">
      <div class="sec-head">
        <div class="sec-title"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>Μισθοδοσία &amp; Προσωπικό</div>
        <div class="sec-note">{{ payrollEditNote }}</div>
        <div class="sec-actions"><button class="btn btn-add" :style="{ display: pl.isAdmin ? '' : 'none' }" @click="pl.addPerson()">+ Προσθήκη ατόμου</button></div>
      </div>
      <div class="sec-body">
        <div class="table-wrap">
          <table>
            <thead><tr>
              <th>Γραφείο</th><th>Όνομα / Θέση</th><th>Μισθός (έτος 1)</th><th>Αύξηση/έτος</th><th>Ισχύς από</th><th>Προβ. μισθός (ορίζοντας)</th><th></th>
            </tr></thead>
            <tbody>
              <tr v-if="pl.people.length === 0"><td colspan="7" style="color:var(--text-muted)">Δεν έχουν καταχωρηθεί άτομα ακόμα.</td></tr>
              <template v-for="(p, idx) in pl.people" :key="p.id">
              <tr>
                <td>
                  <select v-if="pl.isAdmin" class="cell-input" :value="p.officeCode" @change="pl.updatePerson(idx, 'officeCode', $event.target.value)">
                    <option v-for="o in OFFICES" :key="o.code" :value="o.code">{{ o.label }}</option>
                  </select>
                  <span v-else class="office-tag" :class="p.officeCode === 'ATHENS' ? 'athens' : 'manila'">{{ p.office }}</span>
                </td>
                <td v-if="pl.isAdmin">
                  <input class="cell-input" type="text" :value="p.name" @change="pl.updatePerson(idx, 'name', $event.target.value)">
                  <input class="cell-input" type="text" :value="p.role" style="margin-top:4px;font-size:12px;font-weight:500" @change="pl.updatePerson(idx, 'role', $event.target.value)">
                  <button class="raises-toggle" @click="toggleRaises(p)">{{ expanded.has(p.id) ? '▾' : '▸' }} Αυξήσεις ({{ p.raises.length }})</button>
                </td>
                <td v-else>
                  <div style="font-weight:600">{{ p.name }}</div>
                  <div style="font-size:11.5px;color:var(--text-muted)">{{ p.role }}</div>
                  <button class="raises-toggle" @click="toggleRaises(p)">{{ expanded.has(p.id) ? '▾' : '▸' }} Αυξήσεις ({{ p.raises.length }})</button>
                </td>
                <!-- payroll is entered in the currency it's paid in (₱ Μανίλα, $ Αθήνα); every rolled-up figure is in $ -->
                <td>
                  <input class="cell-input tnum" type="number" :value="p.salary" :disabled="!pl.isAdmin" @change="pl.updatePerson(idx, 'salary', $event.target.value)"> {{ pl.isManila(p) ? '₱' : '$' }}
                  <div class="fx-sub" v-if="pl.isManila(p)">≈ {{ fmtUSD(pl.phpToUsd(p.salary)) }}</div>
                </td>
                <td><input class="cell-input tnum" type="number" step="0.5" :value="p.raise" :disabled="!pl.isAdmin" @change="pl.updatePerson(idx, 'raise', $event.target.value)">%</td>
                <td><input class="cell-input" type="number" :value="p.effFrom" :disabled="!pl.isAdmin" @change="pl.updatePerson(idx, 'effFrom', $event.target.value)"></td>
                <td class="tnum" style="font-weight:600">{{ fmtUSD(pl.salaryUSD(p, pl.horizon)) }}<div class="fx-sub" v-if="pl.isManila(p)">≈ ₱{{ fmtInt(pl.projectedSalary(p, pl.horizon)) }} native</div></td>
                <td><button class="row-del" :style="{ visibility: pl.isAdmin ? 'visible' : 'hidden' }" @click="pl.removePerson(idx)" title="Αφαίρεση">✕</button></td>
              </tr>
              <!-- raise steps: each applies once on the step before it, flat until the next -->
              <tr v-if="expanded.has(p.id)" class="raises-row">
                <td colspan="7">
                  <div class="raises-panel">
                    <div class="sec-note" style="display:flex;align-items:center;justify-content:space-between;gap:12px">
                      <span>Βήματα αύξησης · κάθε ποσοστό εφαρμόζεται μία φορά πάνω στο προηγούμενο βήμα και μένει σταθερό μέχρι το επόμενο. Οι στήλες ΑΥΞΗΣΗ / ΙΣΧΥΣ ΑΠΟ γράφουν το πρώτο βήμα.</span>
                      <button v-if="pl.isAdmin" class="btn btn-add" @click="pl.addRaise(p)">+ Προσθήκη βήματος</button>
                    </div>
                    <table style="min-width:0;margin-top:8px">
                      <thead><tr><th>Ισχύει από (ημερομηνία)</th><th>Ποσοστό</th><th>Μισθός μετά το βήμα</th><th></th></tr></thead>
                      <tbody>
                        <tr v-if="p.raises.length === 0"><td colspan="4" style="color:var(--text-muted)">Χωρίς βήματα — ισχύει η απλή αύξηση των στηλών ΑΥΞΗΣΗ / ΙΣΧΥΣ ΑΠΟ.</td></tr>
                        <tr v-for="r in p.raises" :key="r.id">
                          <td><input class="cell-input" type="date" :value="r.date" :disabled="!pl.isAdmin" @change="pl.updateRaise(p, r, 'date', $event.target.value)"></td>
                          <td><input class="cell-input tnum" type="number" step="0.5" :value="r.pct" :disabled="!pl.isAdmin" @change="pl.updateRaise(p, r, 'pct', $event.target.value)">%</td>
                          <td class="tnum">{{ pl.isManila(p) ? '₱' : '$' }}{{ fmtInt(pl.salaryAtDate(p, r.date)) }} <span class="fx-sub" style="display:inline">({{ fmtPctSigned(r.pct) }})</span></td>
                          <td><button class="row-del" :style="{ visibility: pl.isAdmin ? 'visible' : 'hidden' }" @click="pl.removeRaise(p, r)" title="Αφαίρεση">✕</button></td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </td>
              </tr>
              </template>
            </tbody>
            <tfoot>
              <tr class="payroll-total-row">
                <td colspan="5">Σύνολο μισθοδοσίας (ορίζοντας)</td>
                <td class="tnum">{{ fmtUSD(pl.cur.payroll) }}</td><td></td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </div>

    <!-- ===== Viewer: aggregated-only card (GET /api/polaris/payroll/summary) ===== -->
    <div class="section" v-else>
      <div class="sec-head">
        <div class="sec-title"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"/></svg>Μισθοδοσία &amp; Προσωπικό</div>
        <div class="sec-note">συγκεντρωτικά — χωρίς ονόματα/μισθούς · βάση έτους 1</div>
      </div>
      <div class="sec-body locked-card">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="11" width="18" height="10" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
        <div>Το αναλυτικό προσωπικό είναι ορατό μόνο σε admin / editor.</div>
        <div class="big tnum">{{ fmtUSD(pl.summaryUsd.total) }}</div>
        <div class="agg-row">
          <div><div class="l">Αθήνα</div><div class="v tnum">{{ fmtUSD(pl.summaryUsd.athens) }}</div></div>
          <div><div class="l">Μανίλα</div><div class="v tnum">{{ fmtUSD(pl.summaryUsd.manila) }}</div></div>
          <div><div class="l">Άτομα</div><div class="v tnum">{{ pl.summaryUsd.headcount }}</div></div>
        </div>
      </div>
    </div>
    </PolarisStatus>
  </div>
</template>

<style scoped>
.raises-toggle{margin-top:6px;background:transparent;border:1px solid var(--border);border-radius:6px;padding:3px 9px;font-size:11.5px;font-weight:600;color:var(--text-secondary)}
.raises-toggle:hover{color:var(--text-primary);border-color:var(--border-focus)}
.raises-row td{padding-top:0}
.raises-panel{background:var(--bg-input);border:1px solid var(--border);border-radius:var(--radius-md);padding:12px 14px;margin:0 0 10px}
</style>
