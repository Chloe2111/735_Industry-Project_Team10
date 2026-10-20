import { apiClient } from './apiClient'

export const exceptionsApi = {
  list: (pendingOnly = false) => apiClient.get(`/exceptions?pendingOnly=${pendingOnly}`),
  get: (id) => apiClient.get(`/exceptions/${encodeURIComponent(id)}`),
  clear: (id, note = '') => apiClient.patch(`/exceptions/${encodeURIComponent(id)}/clear`, { note }),
  reject: (id, note = '') => apiClient.patch(`/exceptions/${encodeURIComponent(id)}/reject`, { note }),
  /**
   * For an "AI could not code" item: the facilitator codes the idea themselves.
   * Body: { theme, quote, note }. The quote must be copied exactly from the idea text.
   * The backend saves a finding with codedBy "facilitator" and marks the item cleared.
   */
  codeByFacilitator: (id, { theme, quote, note = '' }) =>
    apiClient.patch(`/exceptions/${encodeURIComponent(id)}/code`, { theme, quote, note }),
}
