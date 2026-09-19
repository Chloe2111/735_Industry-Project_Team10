import { afterEach, describe, expect, it, vi } from 'vitest'
import { communityApi } from '../services/communityApi'

afterEach(() => vi.restoreAllMocks())

function mockJson(body, status = 200) {
  global.fetch = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: async () => body,
    text: async () => JSON.stringify(body),
  })
}

describe('communityApi', () => {
  it('loads community groups through the shared API client', async () => {
    const groups = [{ id: 'g1', name: 'Local Group' }]
    mockJson(groups)
    await expect(communityApi.getGroups()).resolves.toEqual(groups)
    expect(fetch).toHaveBeenCalledWith('/api/community/groups', expect.objectContaining({ method: 'GET' }))
  })

  it('creates a community post', async () => {
    const post = { groupId: 'g1', userId: 'u1', title: 'Update', content: 'Hello' }
    mockJson({ id: 'p1', ...post }, 201)
    await communityApi.createPost(post)
    expect(fetch).toHaveBeenCalledWith('/api/community/posts', expect.objectContaining({ method: 'POST', body: JSON.stringify(post) }))
  })
})
