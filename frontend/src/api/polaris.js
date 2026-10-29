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
//   forecast/summary     GET admin+editor+viewer (computed aggregates: EBITDA,
//                        break-even, KPIs, per-year series -- never people/assumptions)
//   forecast/cost-milestones  GET admin+editor | writes admin   (OPEX forecast)
//   forecast/export           GET admin+editor (the full per-year P&L as .xlsx; never VIEWER)
//   payroll/raises            GET admin+editor | writes admin   (per-person raise steps)

import api from '@/api'

export const POLARIS_ENTITY_ID = 'baf68357-f959-43cf-9d8c-4170abe2ffc0'

const params = () => ({ params: { entityId: POLARIS_ENTITY_ID } })
const data = (res) => res.data?.data

export async function fetchMyRole() {
  return data(await api.get('/api/polaris/my-role', params()))?.role || null
}

/** Server-side forecast aggregates for the Dashboard block. years: 1 | 3 | 5. */
export async function fetchForecastSummary(years = 1) {
  return data(await api.get('/api/polaris/forecast/summary', { params: { entityId: POLARIS_ENTITY_ID, years } }))
}

/**
 * Excel export of the forecast P&L (30 Oct 2026). years: 1 | 3 | 5.
 * Returns { blob, filename } -- the filename comes from Content-Disposition.
 */
export async function fetchForecastExcel(years = 1) {
  const res = await api.get('/api/polaris/forecast/export', {
    params: { entityId: POLARIS_ENTITY_ID, years }, responseType: 'blob', timeout: 60000,
  })
  const disposition = res.headers['content-disposition'] || ''
  const m = disposition.match(/filename="?([^"]+)"?/)
  return { blob: res.data, filename: m ? m[1] : `Polaris_PL_Forecast_${years}y.xlsx` }
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
/** OPEX milestones (Oct 2026): GET admin+editor | writes admin. */
export const costMilestonesApi = crud('/api/polaris/forecast/cost-milestones')

/** Payroll raise steps (Oct 2026): list is per entity, create is per person; GET admin+editor | writes admin. */
export const raisesApi = {
  list: async () => data(await api.get('/api/polaris/payroll/raises', params())) || [],
  create: async (personId, body) => data(await api.post(`/api/polaris/payroll/people/${personId}/raises`, body, params())),
  update: async (id, body) => data(await api.put(`/api/polaris/payroll/raises/${id}`, body, params())),
  remove: async (id) => { await api.delete(`/api/polaris/payroll/raises/${id}`, params()) },
}
