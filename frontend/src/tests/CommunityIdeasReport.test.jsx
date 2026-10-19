import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/Toast/ToastProvider'
import { ReportsReceivedPage } from '../pages/ReportsReceivedPage'
import { ReportDetailPage } from '../pages/ReportDetailPage'
import {
  COMMUNITY_REPORT_ID,
  buildCommunityReportDetail,
  buildCommunityReportSummary,
  commissionReportId,
  groupByTheme,
  splitByCommission,
} from '../services/communityReport'
import { apiClient } from '../services/apiClient'
import { listFindings } from '../services/findingsService'

vi.mock('../services/findingsService', () => ({ listFindings: vi.fn() }))
vi.mock('../services/apiClient', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, apiClient: { ...actual.apiClient, get: vi.fn() } }
})

const finding = (itemId, sourceRef, theme, quote, confidence, extra = {}) => ({
  itemId, sourceRef, speakerCode: null, theme, quote, confidence, tier: 2, flags: [], ...extra,
})

const PUBLISHED = [
  finding('f1', 'sub-1', 'Safety after dark', 'Paths feel unsafe after dark', 0.88),
  finding('f2', 'sub-2', 'Safety after dark', 'more lighting along the park paths', 0.93),
  finding('f3', 'sub-2', 'Transport access', 'buses stop running too early', 0.64, { flags: ['lowConfidence'] }),
  finding('x1', 'sub-3', 'Restricted', 'Should never be shown', 0.9, { tier: 3 }),
]

const SEEDED_REPORT = { id: 'skate-park-design-consultation', project: 'Skate Park Design Consultation', group: '[Group 2]', reportingPeriod: '12–26 Aug 2026', status: 'FINAL' }

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ToastProvider>
        <Routes>
          <Route path="/reports-received" element={<ReportsReceivedPage />} />
          <Route path="/reports-received/:reportId" element={<ReportDetailPage />} />
        </Routes>
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('Community ideas report builder', () => {
  it('groups by theme, largest first, strongest quote first', () => {
    const groups = groupByTheme(PUBLISHED.slice(0, 3))
    expect(groups.map((g) => g.theme)).toEqual(['Safety after dark', 'Transport access'])
    expect(groups[0].items.map((i) => i.itemId)).toEqual(['f2', 'f1'])
  })

  it('is a Draft (not yet available) until an idea has been published', () => {
    expect(buildCommunityReportSummary([]).status).toBe('DRAFT')
    expect(buildCommunityReportDetail([])).toBeNull()
    expect(buildCommunityReportSummary(PUBLISHED).status).toBe('FINAL')
  })

  it('builds the report in the shape the report page renders, without tier 3 content', () => {
    const report = buildCommunityReportDetail(PUBLISHED)
    expect(report.stats).toEqual([
      { label: 'Ideas published', value: '2' },
      { label: 'Themes', value: '2' },
      { label: 'Supporting quotes', value: '3' },
      { label: 'Facilitator-reviewed', value: '1' },
    ])
    expect(report.findings.map((f) => f.title)).toEqual(['Safety after dark', 'Transport access'])
    expect(JSON.stringify(report)).not.toContain('Should never be shown')
    expect(report.findings[1].sources[0]).toMatchObject({ quote: 'buses stop running too early', flagged: true, aiConfidence: 64 })
  })
})

describe('Reports received with community ideas', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiClient.get.mockImplementation(async (path) => {
      if (path === '/reports') return [SEEDED_REPORT]
      throw new Error(`unexpected ${path}`)
    })
  })

  it('lists the live Community ideas report first, then stored reports', async () => {
    listFindings.mockResolvedValue({ data: PUBLISHED })
    renderAt('/reports-received')

    const row = (await screen.findByText('Community ideas')).closest('tr')
    expect(within(row).getByRole('link', { name: /View report/ })).toHaveAttribute('href', `/reports-received/${COMMUNITY_REPORT_ID}`)
    expect(screen.getByText('Skate Park Design Consultation')).toBeInTheDocument()
  })

  it('shows Community ideas as not yet available before anything is approved', async () => {
    listFindings.mockResolvedValue({ data: [] })
    renderAt('/reports-received')

    const row = (await screen.findByText('Community ideas')).closest('tr')
    expect(within(row).getByText('Not yet available')).toBeInTheDocument()
  })

  it('still shows stored reports, with a message, when community ideas cannot load', async () => {
    listFindings.mockRejectedValue(new Error('Findings are temporarily unavailable.'))
    renderAt('/reports-received')

    expect(await screen.findByText('Skate Park Design Consultation')).toBeInTheDocument()
    expect(await screen.findByText(/Community ideas could not be loaded: Findings are temporarily unavailable\./)).toBeInTheDocument()
  })

  it('opens the community report with themes and traceable, de-identified quotes', async () => {
    const user = userEvent.setup()
    listFindings.mockResolvedValue({ data: PUBLISHED })
    renderAt(`/reports-received/${COMMUNITY_REPORT_ID}`)

    expect(await screen.findByRole('heading', { name: 'Community ideas report' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /1\. Safety after dark/ })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Trace back to source/ }))
    const panel = screen.getByRole('dialog', { name: 'Source evidence' })
    expect(within(panel).getByText(/more lighting along the park paths/)).toBeInTheDocument()
    expect(within(panel).getByText('93% AI confidence')).toBeInTheDocument()

    await user.click(within(panel).getAllByRole('button', { name: /View detail/ })[0])
    expect(within(panel).getByRole('heading', { name: 'AI confidence' })).toBeInTheDocument()
  })
})

describe('Community ideas per commission', () => {
  const COMMISSION = { id: 'c-123', title: 'TEST - Riverside shade and seating ideas', status: 'OPEN' }
  const EMPTY_COMMISSION = { id: 'c-456', title: 'Library opening hours', status: 'OPEN' }
  const LINKED = [
    ...PUBLISHED,
    finding('f4', 'sub-4', 'Shade and seating', 'shaded benches along the garden path', 0.91, { commissionId: 'c-123' }),
    finding('f5', 'sub-5', 'Heat safety', 'volunteers left early because of the heat', 0.86, { commissionId: 'c-123' }),
    finding('f6', 'sub-6', 'Other', 'idea for a commission that was deleted', 0.9, { commissionId: 'gone' }),
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    apiClient.get.mockImplementation(async (path) => {
      if (path === '/reports') return [SEEDED_REPORT]
      if (path === '/commissions') return [COMMISSION, EMPTY_COMMISSION]
      throw new Error(`unexpected ${path}`)
    })
    listFindings.mockResolvedValue({ data: LINKED })
  })

  it('splits ideas by commission; unknown commissions fall back to the general report', () => {
    const { byCommission, general } = splitByCommission(LINKED, [COMMISSION])
    expect(byCommission.get('c-123').map((f) => f.itemId)).toEqual(['f4', 'f5'])
    expect(general.map((f) => f.itemId)).toEqual(['f1', 'f2', 'f3', 'x1', 'f6'])
  })

  it('lists one report per commission, Final once ideas are published and Not yet available before', async () => {
    renderAt('/reports-received')

    const linked = (await screen.findByText('TEST - Riverside shade and seating ideas')).closest('tr')
    expect(within(linked).getByRole('link', { name: /View report/ })).toHaveAttribute('href', `/reports-received/${commissionReportId('c-123')}`)
    const empty = screen.getByText('Library opening hours').closest('tr')
    expect(within(empty).getByText('Not yet available')).toBeInTheDocument()
    expect(screen.getByText('Community ideas')).toBeInTheDocument()
  })

  it("opens a commission's report with only that commission's ideas", async () => {
    renderAt(`/reports-received/${commissionReportId('c-123')}`)

    expect(await screen.findByRole('heading', { name: /TEST - Riverside shade and seating ideas — community ideas/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Shade and seating/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Safety after dark/ })).not.toBeInTheDocument()
  })

  it('keeps commission ideas out of the general Community ideas report', () => {
    const { general } = splitByCommission(LINKED, [COMMISSION])
    const report = buildCommunityReportDetail(general)
    expect(JSON.stringify(report)).not.toContain('shaded benches')
  })
})
