import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/Toast/ToastProvider'
import { MyCommissionsPage } from '../pages/MyCommissionsPage'

function renderPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <MyCommissionsPage />
      </ToastProvider>
    </MemoryRouter>,
  )
}

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: async () => body,
    text: async () => JSON.stringify(body),
  }
}

describe('My commissions loads saved commissions', () => {
  it('shows commissions saved in the database, so they survive a page reload', async () => {
    global.fetch = vi.fn().mockResolvedValue(
      jsonResponse(200, {
        success: true,
        data: [{ id: 'c-1', title: 'TEST - Riverside shade and seating ideas', incentive: 998, tier: null, status: 'OPEN' }],
      }),
    )

    renderPage()

    expect(await screen.findByText('TEST - Riverside shade and seating ideas')).toBeInTheDocument()
    expect(screen.getByText('Skate Park Design Consultation')).toBeInTheDocument()
    expect(global.fetch.mock.calls[0][0]).toBe('/api/commissions')
  })

  it('still shows the sample commissions when the backend cannot be reached', async () => {
    global.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

    renderPage()

    expect(await screen.findByText('Skate Park Design Consultation')).toBeInTheDocument()
  })
})
