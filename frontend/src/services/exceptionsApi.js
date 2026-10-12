import { apiClient } from './apiClient'

export const exceptionsApi = {
  list: (pendingOnly = false) => apiClient.get(`/exceptions?pendingOnly=${pendingOnly}`),
  get: (id) => apiClient.get(`/exceptions/${encodeURIComponent(id)}`),
  clear: (id, note = '') => apiClient.patch(`/exceptions/${encodeURIComponent(id)}/clear`, { note }),
  reject: (id, note = '') => apiClient.patch(`/exceptions/${encodeURIComponent(id)}/reject`, { note }),
}
