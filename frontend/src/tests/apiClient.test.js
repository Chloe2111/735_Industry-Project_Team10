import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiClient, ApiError } from '../services/apiClient'

function mockFetchOnce({ ok, status, body, contentType = 'application/json' }) {
  global.fetch = vi.fn().mockResolvedValue({
    ok,
    status,
    headers: { get: () => contentType },
    json: async () => body,
    text: async () => JSON.stringify(body),
  })
}

describe('apiClient', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('returns the data payload on success', async () => {
    mockFetchOnce({ ok: true, status: 200, body: { success: true, data: { id: '1' } } })

    const result = await apiClient.get('/commissions')

    expect(result).toEqual({ id: '1' })
  })

  it('throws an ApiError with field errors on a 400 response', async () => {
    mockFetchOnce({
      ok: false,
      status: 400,
      body: { success: false, message: 'Please fix the highlighted fields.', errors: { title: 'Required.' } },
    })

    await expect(apiClient.post('/commissions', {})).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      message: 'Please fix the highlighted fields.',
      fieldErrors: { title: 'Required.' },
    })
  })

  it('wraps a transport failure as a network ApiError', async () => {
    global.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

    const error = await apiClient.get('/commissions').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.isNetworkError).toBe(true)
  })

  it('sends a JSON body with POST requests', async () => {
    mockFetchOnce({ ok: true, status: 201, body: { success: true, data: { id: '2' } } })

    await apiClient.post('/commissions', { title: 'Test' })

    const [, options] = global.fetch.mock.calls[0]
    expect(options.method).toBe('POST')
    expect(options.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(options.body)).toEqual({ title: 'Test' })
  })
})
