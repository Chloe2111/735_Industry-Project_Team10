import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import OpenCommissions from '../components/Community/Commissions/OpenCommissions'

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: async () => body,
    text: async () => JSON.stringify(body),
  }
}

describe('Community Open Commissions', () => {
  it('shows commissions posted by clients, and opens the one clicked', async () => {
    global.fetch = vi.fn().mockResolvedValue(
      jsonResponse(200, {
        success: true,
        data: [
          { id: 'c-123', title: 'TEST - Riverside shade and seating ideas', incentive: 998, deadline: '4 weeks from posting', status: 'OPEN' },
          { id: 'c-old', title: 'Finished commission', incentive: 100, status: 'IN_PROGRESS' },
        ],
      }),
    )
    const onNavigate = vi.fn()
    const user = userEvent.setup()
    render(<OpenCommissions onNavigate={onNavigate} />)

    expect(await screen.findByText('TEST - Riverside shade and seating ideas')).toBeInTheDocument()
    expect(screen.getByText('$998')).toBeInTheDocument()
    expect(screen.queryByText('Finished commission')).not.toBeInTheDocument()
    expect(screen.getByText('Skate Park Design Consultation')).toBeInTheDocument()

    await user.click(screen.getAllByRole('button', { name: /View & participate/ })[0])
    expect(onNavigate).toHaveBeenCalledWith('details', expect.objectContaining({ id: 'c-123', source: 'client' }))
  })

  it('falls back to the sample commissions when the backend is down', async () => {
    global.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    render(<OpenCommissions onNavigate={vi.fn()} />)

    expect(await screen.findByText(/only sample commissions are shown/)).toBeInTheDocument()
    expect(screen.getByText('Skate Park Design Consultation')).toBeInTheDocument()
  })
})
