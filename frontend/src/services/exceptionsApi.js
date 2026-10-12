import { apiClient } from './apiClient'

// Story 19 can run in two modes:
// 1) Full mode: Spring Boot API at /api/exceptions.
// 2) Demo fallback: if the API is unavailable, use the same representative
//    exception records locally so the reviewer UI remains fully demonstrable.
//    Development only (npm run dev). A production build never falls back to made-up
//    records: it shows the real error, so a reviewer cannot "clear" something that was never saved.
const DEMO_FALLBACK_ALLOWED = import.meta.env?.DEV === true
const DEMO_ITEMS = [
  { id:'DEMO-1', flagType:'QUOTE_NOT_FOUND', sourceQuote:'The community strongly supports the proposed development.', sourceContext:'The transcript discusses mixed views about the development, but this exact quoted sentence does not appear in the source.', confidence:0.88, sourceRef:'community_transcript_04.txt', status:'PENDING', reviewerNote:'' },
  { id:'DEMO-2', flagType:'LOW_CONFIDENCE', sourceQuote:'Residents may prefer additional green space.', sourceContext:'The model detected a possible preference, but the available evidence is weak and requires human verification.', confidence:0.32, sourceRef:'community_transcript_07.txt', status:'PENDING', reviewerNote:'' },
  { id:'DEMO-3', flagType:'SOURCE_MISSING', sourceQuote:'Parking availability was identified as a major concern.', sourceContext:'The generated finding has no traceable transcript or source reference attached, so a reviewer must verify or reject it.', confidence:null, sourceRef:'unresolved_source_item_12', status:'PENDING', reviewerNote:'' },
  { id:'DEMO-4', flagType:'CONTESTED', sourceQuote:'The proposal has broad community agreement.', sourceContext:'Evidence contains conflicting statements: some participants support the proposal while others object to its scale and traffic impacts.', confidence:0.61, sourceRef:'community_transcript_11.txt', status:'PENDING', reviewerNote:'' },
]

let demoItems = DEMO_ITEMS.map(item => ({ ...item }))
let demoMode = false

function isTransportFailure(err) {
  return err?.isNetworkError || err?.status === 0 || err instanceof TypeError
}

async function withDemoFallback(apiCall, fallback) {
  if (demoMode) return fallback()
  try {
    return await apiCall()
  } catch (err) {
    if (!DEMO_FALLBACK_ALLOWED || !isTransportFailure(err)) throw err
    demoMode = true
    return fallback()
  }
}

function demoList(pendingOnly) {
  return demoItems.filter(item => !pendingOnly || item.status === 'PENDING').map(item => ({ ...item }))
}

function demoReview(id, status, note = '') {
  const item = demoItems.find(i => i.id === id)
  if (!item) throw new Error('Exception not found.')
  const cleanNote = note.trim()
  if (status === 'REJECTED' && !cleanNote) throw new Error('A reviewer note is required to reject an exception.')
  item.status = status
  item.reviewerNote = cleanNote
  item.reviewedAt = new Date().toISOString()
  return { ...item }
}

export const exceptionsApi = {
  list: (pendingOnly = false) => withDemoFallback(
    () => apiClient.get(`/exceptions?pendingOnly=${pendingOnly}`),
    () => demoList(pendingOnly),
  ),
  get: (id) => withDemoFallback(
    () => apiClient.get(`/exceptions/${encodeURIComponent(id)}`),
    () => ({ ...demoItems.find(i => i.id === id) }),
  ),
  clear: (id, note = '') => withDemoFallback(
    () => apiClient.patch(`/exceptions/${encodeURIComponent(id)}/clear`, { note }),
    () => demoReview(id, 'CLEARED', note),
  ),
  reject: (id, note = '') => withDemoFallback(
    () => apiClient.patch(`/exceptions/${encodeURIComponent(id)}/reject`, { note }),
    () => demoReview(id, 'REJECTED', note),
  ),
  isDemoMode: () => demoMode,
}
