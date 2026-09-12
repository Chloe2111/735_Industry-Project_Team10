import {
  account,
  commissions as seedCommissions,
  dashboardStats,
  notifications as seedNotifications,
  recentActivity,
  reportDetails,
  reports as seedReports,
} from '../data/portalSeed'

// In-memory store standing in for the real backend, which has no commission
// or report endpoints yet. Each function resolves `{ data }` like apiClient
// so swapping to a real `apiClient.get/post` call later is a one-line change.
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

function slugify(title) {
  return title
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')
}

export function getDashboardSummary() {
  return resolveAfterDelay({ stats: dashboardStats, activity: recentActivity })
}

export function listCommissions() {
  return resolveAfterDelay(state.commissions)
}

export function createCommission(payload) {
  const commission = {
    id: slugify(payload.title) || `commission-${state.commissions.length + 1}`,
    title: payload.title,
    assignedGroup: null,
    incentive: payload.incentive,
    tier: payload.tier,
    status: 'OPEN',
  }
  state.commissions = [commission, ...state.commissions]
  return resolveAfterDelay(commission)
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
