import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
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

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: async () => body,
    text: async () => JSON.stringify(body),
  }
}

const reportsListFixture = [
  { id: 'skate-park-design-consultation', project: 'Skate Park Design Consultation', group: '[Group 2]', reportingPeriod: '12–26 Aug 2026', status: 'FINAL' },
  { id: 'youth-space-ideas', project: 'Youth Space Ideas', group: '[Group 4]', reportingPeriod: 'In progress', status: 'DRAFT' },
]

const reportDetailFixture = {
  id: 'skate-park-design-consultation',
  title: 'Skate Park Design Consultation',
  subtitle: 'A community engagement report.',
  badges: ['AI-assisted', 'Human-reviewed'],
  preparedFor: '[Council 1]',
  date: '26 August 2026',
  file: 'vcnity-rpt-com-047-final',
  purpose: 'Test purpose.',
  stats: [
    { label: 'Participants', value: '47' },
    { label: 'Support for redesign proposal', value: '67%' },
    { label: 'Engagement hours contributed', value: '112' },
  ],
  findingsIntro: 'Intro text.',
  findings: [
    {
      id: 'finding-1',
      title: 'Safety and accessibility of the skate area',
      commissioningBody: '[Council 1]',
      deliveredBy: '[Group 2]',
      researchQuestion: 'Question?',
      method: 'Method.',
      analysis: 'Analysis.',
      deliverableStatus: 'Complete',
      sourceCount: 5,
      sources: [
        {
          id: 'SRV-014',
          type: 'Survey response',
          method: 'Online survey',
          quote: 'The ramp is cracked.',
          agreementPercent: 78,
          participantsAgreed: 37,
          participantsTotal: 47,
          flagged: false,
          ageRange: '14–22 years old',
          genderBreakdown: [{ label: 'male', percent: 100 }],
          culturalBackgrounds: ['Anglo-Australian'],
          whyItMatters: 'It matters.',
          themes: ['Safety hazard'],
        },
      ],
    },
  ],
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
    global.fetch = vi.fn().mockResolvedValue(jsonResponse(200, { success: true, data: reportsListFixture }))
    render(withProviders(<ReportsReceivedPage />))
    expect(await screen.findByText('Skate Park Design Consultation')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /view report/i })).toBeInTheDocument()
    expect(screen.getByText('Not yet available')).toBeInTheDocument()
  })

  it('ReportsReceivedPage shows an error toast when reports fail to load', async () => {
    global.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    render(withProviders(<ReportsReceivedPage />))
    expect(await screen.findByText(/unable to reach the server/i)).toBeInTheDocument()
  })

  it('ReportDetailPage shows the report findings', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse(200, { success: true, data: reportDetailFixture }))
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

  it('ReportDetailPage opens the Source Evidence panel from a finding with sources', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse(200, { success: true, data: reportDetailFixture }))
    const user = userEvent.setup()
    render(
      withProviders(
        <Routes>
          <Route path="/reports-received/:reportId" element={<ReportDetailPage />} />
        </Routes>,
        '/reports-received/skate-park-design-consultation',
      ),
    )
    await screen.findByText('1. Safety and accessibility of the skate area')

    await user.click(screen.getByRole('button', { name: /trace back to source/i }))

    expect(await screen.findByText('Source evidence')).toBeInTheDocument()
    expect(screen.getByText('SRV-014')).toBeInTheDocument()
  })

  it('ReportDetailPage shows a note when the backend withheld findings without linked evidence', async () => {
    global.fetch = vi.fn().mockResolvedValue(
      jsonResponse(200, { success: true, data: { ...reportDetailFixture, withheldFindingsCount: 2 } }),
    )
    render(
      withProviders(
        <Routes>
          <Route path="/reports-received/:reportId" element={<ReportDetailPage />} />
        </Routes>,
        '/reports-received/skate-park-design-consultation',
      ),
    )
    expect(await screen.findByText(/2 findings withheld pending linked evidence/i)).toBeInTheDocument()
  })

  it('ReportDetailPage handles an unknown report id gracefully', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValue(jsonResponse(404, { success: false, message: 'Report not found.' }))
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

  it('ReportDetailPage shows an error toast on a non-404 failure', async () => {
    global.fetch = vi.fn().mockResolvedValue(
      jsonResponse(500, { success: false, message: 'Something went wrong. Please try again.' }),
    )
    render(
      withProviders(
        <Routes>
          <Route path="/reports-received/:reportId" element={<ReportDetailPage />} />
        </Routes>,
        '/reports-received/skate-park-design-consultation',
      ),
    )
    expect(await screen.findByText('Something went wrong. Please try again.')).toBeInTheDocument()
    expect(screen.getByText(/isn.t available yet/i)).toBeInTheDocument()
  })

  it('MyAccountPage shows the profile details', async () => {
    render(withProviders(<MyAccountPage />))
    expect(await screen.findAllByText('Jordan Mitchell')).toHaveLength(2)
    expect(screen.getByText('jordan.mitchell@council1.nsw.gov.au')).toBeInTheDocument()
  })
})
