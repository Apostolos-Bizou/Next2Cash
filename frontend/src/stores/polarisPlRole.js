// polarisPlRole.js — the demo "ΠΡΟΒΟΛΗ ΩΣ" role of the Polaris P&L mockup port.
//
// Kept apart from polarisPl.js on purpose: App.vue (entry bundle, loaded by
// every user incl. the login page) needs the role for the topbar switcher,
// but must NOT pull in polarisPl.js and its demo payroll/actuals data. That
// data stays in the lazily-loaded chunk of the /polaris/* views.
// Phase 3 replaces this with the real entity_roles role.

import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePolarisPlRoleStore = defineStore('polarisPlRole', () => {
  let saved = null
  try { saved = localStorage.getItem('pl_role') } catch (e) { /* storage blocked */ }

  const role = ref(saved || 'admin')

  function setRole(r) {
    role.value = r
    try { localStorage.setItem('pl_role', r) } catch (e) { /* storage blocked */ }
  }

  return { role, setRole }
})
