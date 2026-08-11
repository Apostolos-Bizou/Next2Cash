import api from '@/api'

// Entity key → UUID (same mapping used across the app; S100 hardening keeps the
// authoritative filter on the backend). Current entity comes from localStorage.
const ENTITY_MAP = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house: 'dea1f32c-7b30-4981-b625-633da9dbe71e',
  next2megroup: '50317f44-9961-4fb4-add0-7a118e32dc14',
}

export function currentEntityId() {
  const key = localStorage.getItem('n2c_entity') || 'next2me'
  return ENTITY_MAP[key] || ENTITY_MAP.next2me
}

/** List dispatches for the current entity, optional date range + free-text q. */
export async function listDispatches({ from, to, q } = {}) {
  const params = { entityId: currentEntityId() }
  if (from) params.from = from
  if (to) params.to = to
  if (q) params.q = q
  const res = await api.get('/api/report-dispatches', { params })
  return res.data?.data || []
}

/** Single dispatch (includes transactionIds). */
export async function getDispatch(id) {
  const res = await api.get('/api/report-dispatches/' + id)
  return res.data?.data
}

/** Create a dispatch: renders + stores the PDF (+docs ZIP), then archives.
 *  10-minute timeout: a normal monthly send is 50-90 transactions whose
 *  attachments ZIP can be large — the global 3-minute default would abort the
 *  client while the server is still finishing (and succeeding). */
export async function createDispatch(payload) {
  const res = await api.post(
    '/api/report-dispatches?entityId=' + currentEntityId(), payload,
    { timeout: 600000 })
  return res.data?.data
}

/** Preview PDF (stream, no writes). Returns a Blob. */
export async function previewDispatch(payload) {
  const res = await api.post(
    '/api/report-dispatches/preview?entityId=' + currentEntityId(),
    payload, { responseType: 'blob' })
  return res.data
}

/** Download the stored PDF of a dispatch. Returns a Blob. */
export async function getDispatchPdf(id) {
  const res = await api.get('/api/report-dispatches/' + id + '/pdf', { responseType: 'blob' })
  return res.data
}

/** Download the attachments ZIP of a dispatch. Returns a Blob. */
export async function getDispatchDocuments(id) {
  const res = await api.get('/api/report-dispatches/' + id + '/documents', { responseType: 'blob' })
  return res.data
}

/** Delete a dispatch (ADMIN only — backend enforces). */
export async function deleteDispatch(id) {
  await api.delete('/api/report-dispatches/' + id)
}

/** Distinct recipients (most recent first) for autocomplete. */
export async function getRecipients() {
  const res = await api.get('/api/report-dispatches/recipients',
    { params: { entityId: currentEntityId() } })
  return res.data?.data || []
}

/** Batch "already sent" status for transaction ids → Set of dispatched ids. */
export async function getDispatchStatus(ids) {
  if (!ids || ids.length === 0) return new Set()
  try {
    const res = await api.get('/api/report-dispatches/dispatch-status',
      { params: { entityId: currentEntityId(), ids: ids.join(',') } })
    const data = res.data?.data || []
    return new Set(Array.isArray(data) ? data : [])
  } catch {
    return new Set() // best-effort: badges must never break the builder
  }
}

/**
 * Build the create/preview payload from the ReportBuilder sections.
 * Section is derived from the transaction SIGN (income → INCOME, else EXPENSE),
 * matching the backend's enforced rule. Deduped by id.
 */
export function buildDispatchPayload({ title, recipient, note, sentDate, items, includeDocs }) {
  const seen = new Set()
  const out = []
  for (const it of items) {
    const id = it.id
    if (seen.has(id)) continue
    seen.add(id)
    const section = (it.type === 'income' || it.amount >= 0) ? 'INCOME' : 'EXPENSE'
    out.push({ transactionId: id, section })
  }
  return {
    title, recipient, note: note || null, sentDate, items: out,
    includeDocs: includeDocs !== false, // default true
  }
}

/** Open a PDF Blob in a new browser tab (revokes the URL shortly after). */
export function openPdfBlob(blob) {
  const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }))
  window.open(url, '_blank')
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}

/** Trigger a browser download of a Blob under the given filename. */
export function downloadBlob(blob, filename, type) {
  const url = URL.createObjectURL(new Blob([blob], { type: type || 'application/octet-stream' }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}
