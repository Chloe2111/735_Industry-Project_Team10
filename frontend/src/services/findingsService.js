import { apiClient } from './apiClient'

// Story 23.2: real data from the persisted findings API (GET /api/findings).
//
// GET /api/intake/submissions is scoped to one group at a time -- there is no
// "all submissions" endpoint -- so we first list every classified group, then
// fetch that group's published submissions, then load findings per submission.
// Submissions still waiting in the exceptions queue are not published, so
// their findings never reach the client portal.
//
// A submission can belong to several groups, so the same submission id can
// come back from more than one group's list -- dedupe before fetching findings.
const PAGE_SIZE = 100 // backend maximum
const MAX_PAGES = 20 // safety stop so a bad hasNext can't loop forever

async function listFindingsForSource(sourceRef, options) {
  const findings = []
  for (let page = 0; page < MAX_PAGES; page += 1) {
    const query = new URLSearchParams({ sourceRef, page: String(page), size: String(PAGE_SIZE) })
    const result = await apiClient.get(`/findings?${query}`, options)
    findings.push(...(Array.isArray(result?.findings) ? result.findings : []))
    if (!result?.hasNext) break
  }
  return findings
}

async function listPublishedSubmissionIds(options) {
  const groups = await apiClient.get('/intake/group-tiers', options)
  const groupIds = (Array.isArray(groups) ? groups : []).map((group) => group?.groupId).filter(Boolean)

  const perGroup = await Promise.all(
    groupIds.map((groupId) =>
      apiClient.get(`/intake/submissions?${new URLSearchParams({ groupId })}`, options),
    ),
  )

  const ids = perGroup
    .flatMap((submissions) => (Array.isArray(submissions) ? submissions : []))
    .map((submission) => submission?.id)
    .filter(Boolean)
  return [...new Set(ids)]
}

export async function listFindings(options) {
  const sourceRefs = await listPublishedSubmissionIds(options)

  const perSource = await Promise.all(
    sourceRefs.map((sourceRef) => listFindingsForSource(sourceRef, options)),
  )
  // Same `{ data }` shape the mock returned, so CodingFindingsPage is unchanged.
  return { data: perSource.flat() }
}
