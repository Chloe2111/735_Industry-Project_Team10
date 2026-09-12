import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ToastProvider } from '../components/Toast/ToastProvider'
import { DashboardPage } from '../pages/DashboardPage'
import { MyCommissionsPage } from '../pages/MyCommissionsPage'
import { ReportsReceivedPage } from '../pages/ReportsReceivedPage'
import { ReportDetailPage } from '../pages/ReportDetailPage'
import { MyAccountPage } from '../pages/MyAccountPage'

function withProviders(ui, initialPath = '/') {
  return (
    <MemoryRouter initialEntries={[initialPath]}>
      <ToastProvider>{ui}</ToastProvider>
    </MemoryRouter>
  )
}

describe('portal pages render without crashing', () => {
  it('DashboardPage shows stats and recent activity', async () => {
    render(withProviders(<DashboardPage />))
    expect(await screen.findByText('Open commissions')).toBeInTheDocument()
    expect(await screen.findByText(/Report received — Skate Park Design Consultation/)).toBeInTheDocument()
  })

  it('MyCommissionsPage shows the commissions table', async () => {
    render(withProviders(<MyCommissionsPage />))
    expect(await screen.findByText('Skate Park Design Consultation')).toBeInTheDocument()
    expect(screen.getByText('Youth Space Ideas')).toBeInTheDocument()
  })

  it('ReportsReceivedPage shows report rows with the right actions', async () => {
    render(withProviders(<ReportsReceivedPage />))
    expect(await screen.findByText('Skate Park Design Consultation')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /view report/i })).toBeInTheDocument()
    expect(screen.getByText('Not yet available')).toBeInTheDocument()
  })

  it('ReportDetailPage shows the report findings', async () => {
    render(
      withProviders(
        <Routes>
          <Route path="/reports-received/:reportId" element={<ReportDetailPage />} />
        </Routes>,
        '/reports-received/skate-park-design-consultation',
      ),
    )
    expect(await screen.findByText('Skate Park Design Consultation')).toBeInTheDocument()
    expect(screen.getByText('1. Safety and accessibility of the skate area')).toBeInTheDocument()
    expect(screen.getByText('67%')).toBeInTheDocument()
  })

  it('ReportDetailPage handles an unknown report id gracefully', async () => {
    render(
      withProviders(
        <Routes>
          <Route path="/reports-received/:reportId" element={<ReportDetailPage />} />
        </Routes>,
        '/reports-received/does-not-exist',
      ),
    )
    expect(await screen.findByText(/isn.t available yet/i)).toBeInTheDocument()
  })

  it('MyAccountPage shows the profile details', async () => {
    render(withProviders(<MyAccountPage />))
    expect(await screen.findAllByText('Jordan Mitchell')).toHaveLength(2)
    expect(screen.getByText('jordan.mitchell@council1.nsw.gov.au')).toBeInTheDocument()
  })
})
