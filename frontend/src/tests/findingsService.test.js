import { describe, expect, it, vi, beforeEach } from 'vitest'
import { apiClient } from '../services/apiClient'
import { listFindings } from '../services/findingsService'

vi.mock('../services/apiClient', () => ({
  apiClient: { get: vi.fn() },
}))

const finding = (itemId, sourceRef) => ({
  itemId, sourceRef, speakerCode: null, theme: 'Transport', quote: 'We need buses.',
  confidence: 0.9, tier: 2, flags: [],
})

/**
 * Stands in for the real backend contract: GET /intake/submissions requires
 * groupId and GET /findings requires sourceRef -- both 400 without one, same
 * as IntakeService.listPublished and FindingController actually enforce.
 * Fed a request missing either, this throws, just like the real API would.
 */
function fakeBackend({ groups = [], submissionsByGroup = {}, findingsBySource = {} }) {
  return vi.fn(async (path) => {
    if (path === '/intake/group-tiers') return groups

    if (path.startsWith('/intake/submissions')) {
      const groupId = new URLSearchParams(path.split('?')[1]).get('groupId')
      if (!groupId) throw new Error('groupId is required.')
      return submissionsByGroup[groupId] ?? []
    }

    if (path.startsWith('/findings')) {
      const params = new URLSearchParams(path.split('?')[1])
      const sourceRef = params.get('sourceRef')
      if (!sourceRef) throw new Error('sourceRef is required.')
      const page = Number(params.get('page'))
      const pages = findingsBySource[sourceRef] ?? [{ findings: [], hasNext: false }]
      return { ...pages[page], page, size: Number(params.get('size')) }
    }

    throw new Error(`unexpected path ${path}`)
  })
}

describe('findingsService.listFindings (Story 23.2)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists groups, then that group\'s published submissions, then findings per submission', async () => {
    apiClient.get = fakeBackend({
      groups: [{ groupId: 'youth-group-a' }],
      submissionsByGroup: { 'youth-group-a': [{ id: 'sub-1' }, { id: 'sub-2' }] },
      findingsBySource: {
        'sub-1': [{ findings: [finding('f1', 'sub-1')], hasNext: false }],
        'sub-2': [{ findings: [finding('f2', 'sub-2')], hasNext: false }],
      },
    })

    const { data } = await listFindings()

    expect(data.map((f) => f.itemId).sort()).toEqual(['f1', 'f2'])
  })

  it('dedupes a submission shared by several groups before fetching its findings', async () => {
    const getFindings = vi.fn(async () => ({ findings: [finding('f1', 'sub-1')], hasNext: false }))
    apiClient.get = vi.fn(async (path) => {
      if (path === '/intake/group-tiers') return [{ groupId: 'group-a' }, { groupId: 'group-b' }]
      if (path.startsWith('/intake/submissions')) return [{ id: 'sub-1' }] // same submission, both groups
      if (path.startsWith('/findings')) return getFindings(path)
      throw new Error(`unexpected path ${path}`)
    })

    const { data } = await listFindings()

    expect(data.map((f) => f.itemId)).toEqual(['f1'])
    expect(getFindings).toHaveBeenCalledTimes(1) // not once per group
  })

  it('follows hasNext to load every page for a source', async () => {
    apiClient.get = fakeBackend({
      groups: [{ groupId: 'youth-group-a' }],
      submissionsByGroup: { 'youth-group-a': [{ id: 'sub-1' }] },
      findingsBySource: {
        'sub-1': [
          { findings: [finding('f1', 'sub-1')], hasNext: true },
          { findings: [finding('f2', 'sub-1')], hasNext: false },
        ],
      },
    })

    const { data } = await listFindings()

    expect(data.map((f) => f.itemId)).toEqual(['f1', 'f2'])
  })

  it('returns no findings when no group is classified yet, without calling /findings', async () => {
    apiClient.get = fakeBackend({ groups: [] })

    const { data } = await listFindings()

    expect(data).toEqual([])
  })

  it('would fail against the real contract if groupId were ever omitted (regression guard)', async () => {
    const backend = fakeBackend({ groups: [{ groupId: 'youth-group-a' }] })
    apiClient.get = backend

    await listFindings()

    const submissionCalls = backend.mock.calls.filter(([path]) => path.startsWith('/intake/submissions'))
    expect(submissionCalls.every(([path]) => new URLSearchParams(path.split('?')[1]).has('groupId'))).toBe(true)
  })

  it('passes API errors through so the page shows its error state', async () => {
    apiClient.get = vi.fn().mockRejectedValueOnce(new Error('Unable to reach the server.'))

    await expect(listFindings()).rejects.toThrow('Unable to reach the server.')
  })
})
