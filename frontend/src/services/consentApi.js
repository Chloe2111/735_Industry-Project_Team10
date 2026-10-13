/**
 * consentApi.js
 *
 * Sub Task 22.3: connects the consent form to the backend.
 *
 * Matches your real apiClient.js: it exports an `apiClient` object
 * with .get/.post/.put/.patch/.delete, and BASE_URL already includes
 * `/api` — so paths here should NOT repeat the `/api` prefix, or the
 * real request would go to `/api/api/consent` instead of `/api/consent`.
 *
 * apiClient.post() already throws ApiError with a friendly, ready-to-
 * display `.message` on failure (network issue, validation error, or a
 * generic fallback) — so the page component doesn't need to build its
 * own error text, just show err.message.
 */

import { apiClient } from "./apiClient";

/**
 * Submits a consent record to the backend.
 *
 * payload shape (matches ConsentCaptureForm's onSubmit payload):
 *   { participantName, groupName, tier, consentGiven, notes }
 *
 * Returns the created consent record on success.
 * Throws ApiError on failure (see apiClient.js) — the caller
 * (ConsentCapturePage.jsx) catches this and shows err.message as a toast.
 */
export async function submitConsent(payload) {
  return apiClient.post("/consent", payload);
}
