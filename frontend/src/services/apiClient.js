const BASE_URL = import.meta.env?.VITE_API_BASE_URL ?? '/api'

/**
 * Error thrown for any non-2xx response or transport failure.
 *
 * `fieldErrors` carries per-field messages returned by the backend validator
 * ({ email: 'Email already registered' }) so the form can render them inline
 * instead of only surfacing a toast.
 */
export class ApiError extends Error {
  constructor(message, { status = 0, fieldErrors = {}, cause } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
    this.cause = cause
  }

  /** True when the request never reached the server (offline, DNS, CORS). */
  get isNetworkError() {
    return this.status === 0
  }
}

const NETWORK_MESSAGE =
  'Unable to reach the server. Check your connection and try again.'
const FALLBACK_MESSAGE = 'Something went wrong. Please try again.'

async function readBody(response) {
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    const text = await response.text().catch(() => '')
    return text ? { message: text } : {}
  }
  return response.json().catch(() => ({}))
}

async function request(path, { method = 'GET', body, signal, headers } = {}) {
  let response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      signal,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    })
  } catch (err) {
    // Let caller-initiated aborts propagate untouched so callers can ignore them.
    if (err?.name === 'AbortError') throw err
    throw new ApiError(NETWORK_MESSAGE, { cause: err })
  }

  const payload = await readBody(response)

  if (!response.ok) {
    throw new ApiError(payload.message || FALLBACK_MESSAGE, {
      status: response.status,
      fieldErrors: payload.errors ?? {},
    })
  }

  return payload.data ?? payload
}

export const apiClient = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  patch: (path, body, options) => request(path, { ...options, method: 'PATCH', body }),
  delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
}
