import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/Toast/ToastProvider'
import { PostCommissionPage } from '../pages/PostCommissionPage'

function mockFetchOnce({ ok, status, body }) {
  global.fetch = vi.fn().mockResolvedValue({
    ok,
    status,
    headers: { get: () => 'application/json' },
    json: async () => body,
    text: async () => JSON.stringify(body),
  })
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <PostCommissionPage />
      </ToastProvider>
    </MemoryRouter>,
  )
}

async function fillValidForm(user) {
  await user.type(screen.getByLabelText(/project title/i), 'Community Garden Feedback')
  await user.type(screen.getByLabelText(/incentive/i), '1500')
}

describe('PostCommissionPage', () => {
  it('blocks submission and shows inline errors when required fields are empty', async () => {
    const user = userEvent.setup()
    global.fetch = vi.fn()
    renderPage()

    await user.click(screen.getByRole('button', { name: /post commission/i }))

    expect(await screen.findByText('Project title is required.')).toBeInTheDocument()
    expect(screen.getByText(/enter an incentive amount greater than zero/i)).toBeInTheDocument()
    expect(global.fetch).not.toHaveBeenCalled()
  })

  it('clears a field error as soon as the user edits that field', async () => {
    const user = userEvent.setup()
    global.fetch = vi.fn()
    renderPage()

    await user.click(screen.getByRole('button', { name: /post commission/i }))
    expect(await screen.findByText('Project title is required.')).toBeInTheDocument()

    await user.type(screen.getByLabelText(/project title/i), 'A')

    expect(screen.queryByText('Project title is required.')).not.toBeInTheDocument()
  })

  it('posts to /api/commissions and shows the confirmation screen on success', async () => {
    const user = userEvent.setup()
    mockFetchOnce({
      ok: true,
      status: 201,
      body: { success: true, data: { id: 'c1', title: 'Community Garden Feedback', status: 'OPEN' } },
    })
    renderPage()

    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /post commission/i }))

    expect(await screen.findByText('Commission posted.')).toBeInTheDocument()

    const [url, options] = global.fetch.mock.calls[0]
    expect(url).toBe('/api/commissions')
    expect(options.method).toBe('POST')
    const body = JSON.parse(options.body)
    expect(body.title).toBe('Community Garden Feedback')
    expect(body.incentive).toBe(1500)
    expect(body.groups).toEqual(['Youth groups'])
  })

  it('shows a server-side field error and stays on the form when the API returns 400', async () => {
    const user = userEvent.setup()
    mockFetchOnce({
      ok: false,
      status: 400,
      body: {
        success: false,
        message: 'Please fix the highlighted fields.',
        errors: { tier: 'Tier must be one of: Tier 1, Tier 2, Tier 3.' },
      },
    })
    renderPage()

    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /post commission/i }))

    expect(await screen.findByText('Tier must be one of: Tier 1, Tier 2, Tier 3.')).toBeInTheDocument()
    expect(screen.queryByText('Commission posted.')).not.toBeInTheDocument()
  })

  it('shows an error toast on a network failure', async () => {
    const user = userEvent.setup()
    global.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    renderPage()

    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /post commission/i }))

    expect(await screen.findByText(/unable to reach the server/i)).toBeInTheDocument()
    expect(screen.queryByText('Commission posted.')).not.toBeInTheDocument()
  })

  it('lets the user start a new commission after posting one', async () => {
    const user = userEvent.setup()
    mockFetchOnce({
      ok: true,
      status: 201,
      body: { success: true, data: { id: 'c1', title: 'Community Garden Feedback', status: 'OPEN' } },
    })
    renderPage()

    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /post commission/i }))
    await screen.findByText('Commission posted.')

    await user.click(screen.getByRole('button', { name: /post another/i }))

    expect(screen.getByLabelText(/project title/i)).toHaveValue('')
  })
})
