import {
  account,
  commissions as seedCommissions,
  dashboardStats,
  groupCatalog,
  notifications as seedNotifications,
  recentActivity,
} from '../data/portalSeed'
import { apiClient, ApiError } from './apiClient'

// In-memory store standing in for the real backend, which doesn't have
// listing/dashboard endpoints yet (createCommission/suggestGroups/reports
// below are wired to real endpoints). Each function resolves `{ data }`
// like apiClient so swapping the rest over later is a one-line change.
const state = {
  commissions: [...seedCommissions],
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

// Real endpoints (see backend/src/main/java/com/vcnity/backend/report) --
// backed by MongoDB, seeded with synthetic content since the AI pipeline
// doesn't produce real report content yet.
export async function listReports() {
  const reports = await apiClient.get('/reports')
  return { data: reports }
}

export async function getReport(id) {
  try {
    const report = await apiClient.get(`/reports/${id}`)
    return { data: report }
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { data: null }
    }
    throw error
  }
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
