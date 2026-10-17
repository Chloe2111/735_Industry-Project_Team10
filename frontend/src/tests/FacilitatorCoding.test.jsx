import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import ExceptionsQueue, { ideaSentences } from '../components/Exceptions/ExceptionsQueue'
import { ToastProvider } from '../components/Toast/ToastProvider'
import { exceptionsApi } from '../services/exceptionsApi'
import { buildCommunityReportDetail } from '../services/communityReport'
import { isRenderableFinding } from '../pages/CodingFindingsPage'

vi.mock('../services/exceptionsApi', () => ({
  exceptionsApi: { list: vi.fn(), clear: vi.fn(), reject: vi.fn(), codeByFacilitator: vi.fn() },
}))

const IDEA = 'Idea title: Lit evening paths\nIdea: Add lighting along the park paths. It would help after dark.\nProblem it solves: Paths feel unsafe after dark.'

const item = {
  // Matches how intake raises it (PR #23): the de-identified text is in sourceQuote,
  // and sourceContext holds the instruction for the reviewer.
  id: 'EX-9', flagType: 'AI_COULD_NOT_CODE', sourceQuote: IDEA,
  sourceContext: 'The AI could not code this community submission (it failed or found no themes).', confidence: null,
  sourceRef: 'sub-9', status: 'PENDING', sourceType: 'COMMUNITY_SUBMISSION', sourceId: 'sub-9',
}

function renderQueue(communitySubmissions = null) {
  return render(<ToastProvider><ExceptionsQueue communitySubmissions={communitySubmissions} /></ToastProvider>)
}

describe('Facilitator codes an idea the AI could not code', () => {
  beforeEach(() => vi.clearAllMocks())

  it('splits the idea into exact sentences without the form labels', () => {
    const sentences = ideaSentences(IDEA)
    expect(sentences).toEqual([
      'Lit evening paths',
      'Add lighting along the park paths.',
      'It would help after dark.',
      'Paths feel unsafe after dark.',
    ])
    sentences.forEach((sentence) => expect(IDEA).toContain(sentence))
  })

  it('shows the idea, and only allows approval once a theme and a sentence are chosen', async () => {
    const user = userEvent.setup()
    exceptionsApi.list.mockResolvedValue([item])
    renderQueue()

    expect(await screen.findByText(/The AI could not code this idea/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Clear for verification' })).not.toBeInTheDocument()
    const approve = screen.getByRole('button', { name: 'Approve with this theme' })
    expect(approve).toBeDisabled()

    await user.type(screen.getByPlaceholderText(/A short theme/), 'Safety after dark')
    expect(approve).toBeDisabled()
    await user.click(screen.getByRole('radio', { name: 'Paths feel unsafe after dark.' }))
    expect(approve).toBeEnabled()
  })

  it('approves with the facilitator coding, then publishes the submission', async () => {
    const user = userEvent.setup()
    exceptionsApi.list.mockResolvedValueOnce([item]).mockResolvedValueOnce([])
    exceptionsApi.codeByFacilitator.mockResolvedValue({ ...item, status: 'CLEARED' })
    const communitySubmissions = { getStatus: vi.fn().mockResolvedValue({ status: 'PENDING_REVIEW' }), publish: vi.fn().mockResolvedValue({ status: 'PUBLISHED' }) }
    renderQueue(communitySubmissions)

    await user.type(await screen.findByPlaceholderText(/A short theme/), 'Safety after dark')
    await user.click(screen.getByRole('radio', { name: 'Paths feel unsafe after dark.' }))
    await user.type(screen.getByPlaceholderText(/Why you chose this theme/), 'Clear safety concern about lighting.')
    await user.click(screen.getByRole('button', { name: 'Approve & publish' }))

    await waitFor(() => expect(exceptionsApi.codeByFacilitator).toHaveBeenCalledWith('EX-9', {
      theme: 'Safety after dark',
      quote: 'Paths feel unsafe after dark.',
      note: 'Clear safety concern about lighting.',
    }))
    expect(communitySubmissions.publish).toHaveBeenCalledWith('sub-9')
  })

  it('can still reject the idea with a reason', async () => {
    const user = userEvent.setup()
    exceptionsApi.list.mockResolvedValueOnce([item]).mockResolvedValueOnce([])
    exceptionsApi.reject.mockResolvedValue({ ...item, status: 'REJECTED' })
    renderQueue()

    await user.type(await screen.findByPlaceholderText(/Why you chose this theme/), 'Off-topic for this commission.')
    await user.click(screen.getByRole('button', { name: 'Reject' }))
    await waitFor(() => expect(exceptionsApi.reject).toHaveBeenCalledWith('EX-9', 'Off-topic for this commission.'))
    expect(exceptionsApi.codeByFacilitator).not.toHaveBeenCalled()
  })
})

describe('Facilitator-coded findings in the client report', () => {
  const aiFinding = { itemId: 'f1', sourceRef: 'sub-1', speakerCode: null, theme: 'Safety after dark', quote: 'more lighting along the park paths', confidence: 0.9, tier: 2, flags: [] }
  const humanFinding = { itemId: 'f9', sourceRef: 'sub-9', speakerCode: null, theme: 'Safety after dark', quote: 'Paths feel unsafe after dark.', confidence: null, tier: 2, flags: [], codedBy: 'facilitator' }

  it('accepts a facilitator-coded finding without an AI confidence, but still rejects tier 3', () => {
    expect(isRenderableFinding(humanFinding)).toBe(true)
    expect(isRenderableFinding({ ...humanFinding, tier: 3 })).toBe(false)
    expect(isRenderableFinding({ ...aiFinding, confidence: null })).toBe(false)
  })

  it('shows "Coded by facilitator" instead of an AI confidence and counts it as reviewed', () => {
    const report = buildCommunityReportDetail([aiFinding, humanFinding])
    const theme = report.findings[0]
    const human = theme.sources.find((s) => s.quote === humanFinding.quote)
    expect(human).toMatchObject({ codedByFacilitator: true, aiConfidence: null })
    expect(theme.analysis).toMatch(/average confidence 90%/)
    expect(theme.analysis).toMatch(/1 quote was coded by a facilitator/)
    expect(report.stats.find((s) => s.label === 'Facilitator-reviewed').value).toBe('1')
  })
})
