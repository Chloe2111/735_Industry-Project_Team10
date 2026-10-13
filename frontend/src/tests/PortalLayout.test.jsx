import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ToastProvider } from '../components/Toast/ToastProvider'
import { PortalLayout } from '../components/Layout/PortalLayout'

function renderLayout(initialPath = '/dashboard') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <ToastProvider>
        <Routes>
          <Route element={<PortalLayout />}>
            <Route path="dashboard" element={<h1>Dashboard page</h1>} />
            <Route path="my-commissions" element={<h1>My commissions page</h1>} />
          </Route>
        </Routes>
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('PortalLayout', () => {
  it('renders the breadcrumb for the current page', () => {
    renderLayout('/dashboard')
    expect(screen.getByText('Dashboard page')).toBeInTheDocument()
    expect(screen.getByText('Council Portal')).toBeInTheDocument()
  })

  it('navigates to another page when a nav link is clicked', async () => {
    const user = userEvent.setup()
    renderLayout('/dashboard')

    await user.click(screen.getByRole('link', { name: 'My commissions' }))

    expect(await screen.findByText('My commissions page')).toBeInTheDocument()
  })
})
