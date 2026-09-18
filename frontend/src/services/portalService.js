import {
  account,
  commissions as seedCommissions,
  dashboardStats,
  groupCatalog,
  notifications as seedNotifications,
  recentActivity,
  reportDetails,
  reports as seedReports,
} from '../data/portalSeed'
import { apiClient } from './apiClient'

// In-memory store standing in for the real backend, which doesn't have
// listing/report endpoints yet (createCommission below is the one function
// that's wired to a real endpoint). Each function resolves `{ data }` like
// apiClient so swapping the rest over later is a one-line change per function.
const state = {
  commissions: [...seedCommissions],
  reports: [...seedReports],
  notifications: seedNotifications.map((item) => ({ ...item })),
}

const LATENCY_MS = 250

function resolveAfterDelay(data) {
  return new Promise((resolve) => {
    setTimeout(() => resolve({ data }), LATENCY_MS)
  })
}

export function getDashboardSummary() {
  return resolveAfterDelay({ stats: dashboardStats, activity: recentActivity })
}

export function listCommissions() {
  return resolveAfterDelay(state.commissions)
}

// Real endpoint (see backend/src/main/java/com/vcnity/backend/commission) --
// unlike the rest of this file, not backed by the in-memory mock store.
export async function createCommission(payload) {
  const commission = await apiClient.post('/commissions', payload)
  return { data: commission }
}

// Stands in for a real AI group-matching endpoint, which doesn't exist yet
// -- always returns the same top 4 catalog groups regardless of the
// questionnaire answers passed in, since there's no matching engine to
// vary the result meaningfully. Swap for a real call once one exists.
export function suggestGroups(answers) {
  void answers
  return resolveAfterDelay(groupCatalog.slice(0, 4))
}

export function listReports() {
  return resolveAfterDelay(state.reports)
}

export function getReport(id) {
  return resolveAfterDelay(reportDetails[id] ?? null)
}

export function getAccount() {
  return resolveAfterDelay(account)
}

export function listNotifications() {
  return resolveAfterDelay(state.notifications)
}

export function markAllNotificationsRead() {
  state.notifications = state.notifications.map((item) => ({ ...item, unread: false }))
  return resolveAfterDelay(state.notifications)
}
