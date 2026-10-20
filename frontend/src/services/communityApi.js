import { apiClient } from './apiClient'

export const communityApi = {
  getGroups: (options) => apiClient.get('/community/groups', options),
  getGroup: (groupId, options) => apiClient.get(`/community/groups/${groupId}`, options),
  createGroup: (group) => apiClient.post('/community/groups', group),
  updateGroup: (groupId, group) => apiClient.put(`/community/groups/${groupId}`, group),
  deleteGroup: (groupId) => apiClient.delete(`/community/groups/${groupId}`),
  getFeed: (options) => apiClient.get('/community/posts', options),
  getGroupPosts: (groupId, options) => apiClient.get(`/community/posts/group/${groupId}`, options),
  createPost: (post) => apiClient.post('/community/posts', post),
  getMembers: (groupId, options) => apiClient.get(`/community/groups/${groupId}/members`, options),
  getMemberships: (userId, options) => apiClient.get(`/community/users/${userId}/memberships`, options),
  joinGroup: (groupId, userId) => apiClient.post(`/community/groups/${groupId}/members`, { userId }),
  leaveGroup: (groupId, userId) => apiClient.delete(`/community/groups/${groupId}/members/${userId}`),
}
