/**
 * intakeApi.js
 *
 * Story 24: connects the community submission form to the intake and
 * tier-classification endpoints.
 *
 * apiClient's BASE_URL already includes `/api`, so paths here must NOT
 * repeat it (same rule as consentApi.js). On failure apiClient throws an
 * ApiError whose `.message` is ready to show to the person.
 */

import { apiClient } from './apiClient'

/**
 * Submits feedback for a group.
 *
 * payload shape (must match SubmissionRequest.java on the backend):
 *   { groupIds: string[], text: string }
 *
 * There is no tier in the payload on purpose: the backend takes the tier
 * from the group's own record.
 *
 * Returns the receipt:
 *   { id, status, tier, tierLabel, groupIds, message, submittedAt }
 */
export function submitFeedback(payload) {
  return apiClient.post('/intake/submissions', payload)
}

/** Fetches the latest receipt for a submission, e.g. to see if it has been published yet. */
export function getSubmission(id) {
  return apiClient.get(`/intake/submissions/${encodeURIComponent(id)}`)
}

/** Published submissions of one group, newest first. */
export function listPublished(groupId) {
  return apiClient.get(`/intake/submissions?groupId=${encodeURIComponent(groupId)}`)
}

/** Every group that a person has classified, with its tier and history. */
export function listGroupTiers() {
  return apiClient.get('/intake/group-tiers')
}

/**
 * Sets or changes a group's tier.
 * body shape (must match TierRequest.java): { groupId, tier, setBy, reason }
 */
export function setGroupTier(body) {
  return apiClient.put('/intake/group-tiers', body)
}
