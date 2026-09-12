import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ToastProvider } from '../components/Toast/ToastProvider'
import { PostCommissionPage } from '../pages/PostCommissionPage'

function renderPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <PostCommissionPage />
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('PostCommissionPage', () => {
  it('shows the confirmation screen after posting a commission', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText(/project title/i), 'Community Garden Feedback')
    await user.click(screen.getByRole('button', { name: /post commission/i }))

    expect(await screen.findByText('Commission posted.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /view my commissions/i })).toBeInTheDocument()
  })

  it('lets the user start a new commission after posting one', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText(/project title/i), 'Community Garden Feedback')
    await user.click(screen.getByRole('button', { name: /post commission/i }))
    await screen.findByText('Commission posted.')

    await user.click(screen.getByRole('button', { name: /post another/i }))

    expect(screen.getByLabelText(/project title/i)).toHaveValue('')
  })
})
