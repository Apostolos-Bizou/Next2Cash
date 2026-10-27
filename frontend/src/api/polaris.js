// polaris.js — API client for the Polaris P&L module (Phase 3, Oct 2026).
//
// Thin wrappers over the Phase 1 backend. Every call is scoped to the Polaris
// entity (?entityId=), which is the only entity these endpoints accept.
// Access is decided server-side by entity_roles:
//   my-role                         admin / editor / viewer (no row -> 403)
//   payroll/summary      GET admin+editor+viewer (totals per office, no names)
//   payroll/people       GET admin+editor        | writes admin
//   payroll/milestones   GET admin               | writes admin
//   forecast/assumptions GET admin               | writes admin
//   forecast/actuals     GET admin+editor+viewer | writes admin+editor

import api from '@/api'

export const POLARIS_ENTITY_ID = 'baf68357-f959-43cf-9d8c-4170abe2ffc0'

const params = () => ({ params: { entityId: POLARIS_ENTITY_ID } })
const data = (res) => res.data?.data

export async function fetchMyRole() {
  return data(await api.get('/api/polaris/my-role', params()))?.role || null
}

export async function fetchPayrollSummary() {
  return data(await api.get('/api/polaris/payroll/summary', params())) || []
}

function crud(path) {
  return {
    list: async () => data(await api.get(path, params())) || [],
    create: async (body) => data(await api.post(path, body, params())),
    update: async (id, body) => data(await api.put(`${path}/${id}`, body, params())),
    remove: async (id) => { await api.delete(`${path}/${id}`, params()) },
  }
}

export const peopleApi = crud('/api/polaris/payroll/people')
export const milestonesApi = crud('/api/polaris/payroll/milestones')
export const assumptionsApi = crud('/api/polaris/forecast/assumptions')
export const actualsApi = crud('/api/polaris/forecast/actuals')
