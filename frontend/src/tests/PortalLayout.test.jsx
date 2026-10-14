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
          <Route element={<PortalLayout role="client" />}>
            <Route path="dashboard" element={<h1>Dashboard page</h1>} />
            <Route path="my-commissions" element={<h1>My commissions page</h1>} />
          </Route>
          <Route path="facilitator" element={<PortalLayout role="facilitator" />}>
            <Route path="exceptions-queue" element={<h1>Exceptions queue page</h1>} />
            <Route path="coding-findings" element={<h1>Coding findings page</h1>} />
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

  it('no longer shows the facilitator pages in the client nav', () => {
    renderLayout('/dashboard')
    expect(screen.queryByRole('link', { name: 'Exceptions queue' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Coding findings' })).not.toBeInTheDocument()
  })

  it('shows the facilitator portal with its own nav', async () => {
    const user = userEvent.setup()
    renderLayout('/facilitator/exceptions-queue')

    expect(screen.getByText('Facilitator Portal', { selector: '.portal-topnav__pill' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Post a commission' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Coding findings' }))
    expect(await screen.findByText('Coding findings page')).toBeInTheDocument()
  })

  it('switches role from the dropdown', async () => {
    const user = userEvent.setup()
    renderLayout('/dashboard')

    await user.selectOptions(screen.getByRole('combobox', { name: 'Switch role' }), 'facilitator')

    expect(await screen.findByText('Exceptions queue page')).toBeInTheDocument()
  })
})
