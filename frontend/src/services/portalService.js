import {
  account,
  commissions as seedCommissions,
  dashboardStats,
  groupCatalog,
  notifications as seedNotifications,
  recentActivity,
} from '../data/portalSeed'
import { apiClient, ApiError } from './apiClient'
import { listFindings } from './findingsService'
import {
  COMMUNITY_REPORT_ID,
  buildCommunityReportDetail,
  buildCommunityReportSummary,
} from './communityReport'

// In-memory store standing in for the real backend, which doesn't have
// listing/dashboard endpoints yet (listCommissions/createCommission/suggestGroups/reports
// below are wired to real endpoints). Each function resolves `{ data }`
// like apiClient so swapping the rest over later is a one-line change.
// Commission drafts are kept in the browser (localStorage) so they survive a
// page refresh. There is no drafts endpoint on the backend yet; when one
// exists, only the four draft functions below need to change.
const DRAFTS_KEY = 'vcnity.commissionDrafts'

function loadDrafts() {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(DRAFTS_KEY) ?? '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function persistDrafts() {
  try {
    window.localStorage.setItem(DRAFTS_KEY, JSON.stringify(state.drafts))
  } catch {
    // Storage can be unavailable (private mode, blocked site data); drafts
    // then last until the page is closed, which is still better than nothing.
  }
}

const state = {
  commissions: [...seedCommissions],
  notifications: seedNotifications.map((item) => ({ ...item })),
  drafts: loadDrafts(),
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

function toCommissionRow(commission, fallback = {}) {
  return {
    id: commission.id,
    title: commission.title ?? fallback.title,
    assignedGroup: commission.assignedGroup ?? null,
    incentive: commission.incentive ?? fallback.incentive,
    tier: commission.tier ?? null,
    status: commission.status ?? 'OPEN',
  }
}

/**
 * Commissions saved in the database (GET /api/commissions, newest first),
 * followed by the sample commissions that the rest of the demo data refers to.
 * If the backend can't be reached, the sample list (plus anything posted in this
 * session) is shown instead, so the page still renders.
 */
export async function listCommissions() {
  let saved = []
  try {
    const data = await apiClient.get('/commissions')
    saved = Array.isArray(data) ? data.filter((item) => item?.id).map((item) => toCommissionRow(item)) : []
  } catch {
    saved = []
  }
  const savedIds = new Set(saved.map((item) => item.id))
  return { data: [...saved, ...state.commissions.filter((item) => !savedIds.has(item.id))] }
}

// Real endpoint (see backend/src/main/java/com/vcnity/backend/commission).
export async function createCommission(payload) {
  const commission = await apiClient.post('/commissions', payload)
  // Also keep it in this session's list, so it still shows if the list call fails.
  if (commission?.id) {
    state.commissions = [
      toCommissionRow(commission, payload),
      ...state.commissions.filter((item) => item.id !== commission.id),
    ]
  }
  return { data: commission }
}

export function listDrafts() {
  const sorted = [...state.drafts].sort((a, b) => (b.savedAt ?? '').localeCompare(a.savedAt ?? ''))
  return resolveAfterDelay(sorted)
}

export function getDraft(id) {
  return resolveAfterDelay(state.drafts.find((draft) => draft.id === id) ?? null)
}

/** Creates a draft, or updates it when `draft.id` is set. */
export function saveDraft(draft) {
  const id = draft.id ?? `draft-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
  const saved = { ...draft, id, savedAt: new Date().toISOString() }
  state.drafts = [saved, ...state.drafts.filter((item) => item.id !== id)]
  persistDrafts()
  return resolveAfterDelay(saved)
}

export function deleteDraft(id) {
  state.drafts = state.drafts.filter((draft) => draft.id !== id)
  persistDrafts()
  return resolveAfterDelay(id)
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
  // Seeded reports and the live "Community ideas" report load independently,
  // so one failing does not hide the other. `warnings` says what could not load.
  const [stored, findings] = await Promise.allSettled([apiClient.get('/reports'), listFindings()])
  if (stored.status === 'rejected' && findings.status === 'rejected') throw stored.reason

  const reports = []
  const warnings = []
  if (findings.status === 'fulfilled') {
    reports.push(buildCommunityReportSummary(findings.value.data ?? []))
  } else {
    warnings.push(`Community ideas could not be loaded: ${findings.reason?.message ?? 'unknown error'}`)
  }
  if (stored.status === 'fulfilled') {
    reports.push(...(Array.isArray(stored.value) ? stored.value : []))
  } else {
    warnings.push(stored.reason?.message ?? 'Could not load reports.')
  }
  return { data: reports, warnings }
}

export async function getReport(id) {
  if (id === COMMUNITY_REPORT_ID) {
    const { data } = await listFindings()
    return { data: buildCommunityReportDetail(data ?? []) }
  }
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
