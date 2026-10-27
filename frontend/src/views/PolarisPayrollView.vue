<script setup>
// PolarisPayrollView — "Μισθοδοσία & Προσωπικό" page of the Polaris P&L module.
//
// Phase 2 (Oct 2026): 1:1 port of the #pagePayroll block of
// docs/polaris-mockup-reference.html. admin/editor see the full table (only
// admin can edit), viewer sees the aggregated card. Demo data + demo role from
// the shared polarisPl store; Phase 3 wires /api/polaris/payroll + entity_roles.
import { computed } from 'vue'
import PolarisConceptBanner from '@/components/PolarisConceptBanner.vue'
import { usePolarisPlStore, fmtUSD, fmtInt } from '@/stores/polarisPl'
import '@/styles/polaris-pl.css'

const pl = usePolarisPlStore()

const payrollEditNote = computed(() => pl.isAdmin
  ? 'Αθήνα + Μανίλα · κάθε άτομο με δική του αύξηση'
  : 'Αθήνα + Μανίλα · μόνο προβολή (ο editor δεν αλλάζει δομή/τύπους)')
</script>

<template>
  <div class="polaris-pl">
    <PolarisConceptBanner />

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
              <tr v-for="(p, idx) in pl.people" :key="idx">
                <td><span class="office-tag" :class="p.office === 'Αθήνα' ? 'athens' : 'manila'">{{ p.office }}</span></td>
                <td>
                  <div style="font-weight:600">{{ p.name }}</div>
                  <div style="font-size:11.5px;color:var(--text-muted)">{{ p.role }}</div>
                </td>
                <!-- payroll is entered in the currency it's paid in (₱ Μανίλα, € Αθήνα); every rolled-up figure is in $ -->
                <td>
                  <input class="cell-input tnum" type="number" :value="p.salary" :disabled="!pl.isAdmin" @change="pl.updatePerson(idx, 'salary', $event.target.value)"> {{ pl.isManila(p) ? '₱' : '€' }}
                  <div class="fx-sub">≈ {{ fmtUSD(pl.isManila(p) ? pl.phpToUsd(p.salary) : pl.eurToUsd(p.salary)) }}</div>
                </td>
                <td><input class="cell-input tnum" type="number" step="0.5" :value="p.raise" :disabled="!pl.isAdmin" @change="pl.updatePerson(idx, 'raise', $event.target.value)">%</td>
                <td><input class="cell-input" type="number" :value="p.effFrom" :disabled="!pl.isAdmin" @change="pl.updatePerson(idx, 'effFrom', $event.target.value)"></td>
                <td class="tnum" style="font-weight:600">{{ fmtUSD(pl.salaryUSD(p, pl.horizon)) }}<div class="fx-sub">≈ {{ pl.isManila(p) ? '₱' : '€' }}{{ fmtInt(pl.projectedSalary(p, pl.horizon)) }} native</div></td>
                <td><button class="row-del" :style="{ visibility: pl.isAdmin ? 'visible' : 'hidden' }" @click="pl.removePerson(idx)" title="Αφαίρεση">✕</button></td>
              </tr>
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

    <!-- ===== Viewer: aggregated-only card ===== -->
    <div class="section" v-else>
      <div class="sec-head">
        <div class="sec-title"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"/></svg>Μισθοδοσία &amp; Προσωπικό</div>
        <div class="sec-note">συγκεντρωτικά — χωρίς ονόματα/μισθούς</div>
      </div>
      <div class="sec-body locked-card">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="11" width="18" height="10" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
        <div>Το αναλυτικό προσωπικό είναι ορατό μόνο σε admin / editor.</div>
        <div class="big tnum">{{ fmtUSD(pl.cur.payroll) }}</div>
        <div class="agg-row">
          <div><div class="l">Αθήνα</div><div class="v tnum">{{ fmtUSD(pl.athensTotal) }}</div></div>
          <div><div class="l">Μανίλα</div><div class="v tnum">{{ fmtUSD(pl.manilaTotal) }}</div></div>
          <div><div class="l">Άτομα</div><div class="v tnum">{{ pl.staffCount }}</div></div>
        </div>
      </div>
    </div>
  </div>
</template>
