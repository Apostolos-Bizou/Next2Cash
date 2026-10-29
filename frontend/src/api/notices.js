// notices.js — per-user one-time in-app notices (Oct 2026).
//   GET  /api/notices/{key}          -> { dismissed }   any role
//   POST /api/notices/{key}/dismiss  -> sticks for this user, for good (admin/user/accountant)
import api from '@/api'

export async function fetchNoticeDismissed(key) {
  const res = await api.get(`/api/notices/${encodeURIComponent(key)}`)
  return !!res.data?.data?.dismissed
}

export async function dismissNotice(key) {
  await api.post(`/api/notices/${encodeURIComponent(key)}/dismiss`)
}
