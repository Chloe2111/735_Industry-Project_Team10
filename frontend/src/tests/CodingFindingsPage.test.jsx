import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ToastProvider } from '../components/Toast/ToastProvider'
import { CodingFindingsPage, isRenderableFinding } from '../pages/CodingFindingsPage'
import { listFindings } from '../services/findingsService'

vi.mock('../services/findingsService', () => ({
  listFindings: vi.fn(),
}))

function withProviders(ui) {
  return (
    <MemoryRouter>
      <ToastProvider>{ui}</ToastProvider>
    </MemoryRouter>
  )
}

const validFinding = {
  itemId: 'finding_001',
  sourceRef: 'transcript_014',
  speakerCode: 'PERSON_003',
  theme: 'Access to community activities',
  quote: 'We need affordable transport.',
  confidence: 0.92,
  tier: 2,
  flags: [],
}

describe('isRenderableFinding — Story 23.4 guard', () => {
  it('accepts a valid finding, including a null speakerCode and empty flags', () => {
    expect(isRenderableFinding(validFinding)).toBe(true)
    expect(isRenderableFinding({ ...validFinding, speakerCode: null })).toBe(true)
  })

  it.each([
    ['tier 3 (culturally restricted, must never render)', { ...validFinding, tier: 3 }],
    ['a missing tier', { ...validFinding, tier: undefined }],
    ['a non-numeric tier', { ...validFinding, tier: '2' }],
    ['an out-of-range confidence', { ...validFinding, confidence: 1.5 }],
    ['a non-finite confidence', { ...validFinding, confidence: Number.NaN }],
    ['a blank theme', { ...validFinding, theme: '' }],
    ['a blank quote', { ...validFinding, quote: '' }],
    ['a missing itemId', { ...validFinding, itemId: undefined }],
    ['flags that are not an array', { ...validFinding, flags: 'quoteNotGrounded' }],
    ['null', null],
  ])('rejects a finding with %s', (_label, finding) => {
    expect(isRenderableFinding(finding)).toBe(false)
  })
})

describe('CodingFindingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows a loading state before the findings resolve', () => {
    listFindings.mockReturnValue(new Promise(() => {})) // never resolves
    render(withProviders(<CodingFindingsPage />))
    expect(screen.getByText(/loading coded findings/i)).toBeInTheDocument()
  })

  it('renders valid findings and never renders an invalid (e.g. tier 3) one', async () => {
    listFindings.mockResolvedValue({
      data: [
        validFinding,
        { ...validFinding, itemId: 'finding_restricted', tier: 3, quote: 'Should never appear on screen.' },
      ],
    })
    render(withProviders(<CodingFindingsPage />))

    expect(await screen.findByText('Access to community activities')).toBeInTheDocument()
    expect(screen.queryByText('Should never appear on screen.')).not.toBeInTheDocument()
  })

  it('shows "Unknown speaker" when speakerCode is null', async () => {
    listFindings.mockResolvedValue({ data: [{ ...validFinding, speakerCode: null }] })
    render(withProviders(<CodingFindingsPage />))
    expect(await screen.findByText('Unknown speaker')).toBeInTheDocument()
  })

  it('shows an error state and toast when the fetch fails', async () => {
    listFindings.mockRejectedValue(new Error('Could not load coded findings. Please try again.'))
    render(withProviders(<CodingFindingsPage />))
    expect(await screen.findAllByText(/could not load coded findings/i)).toHaveLength(2) // inline state + toast
  })

  it('shows the empty-state message when there are no findings', async () => {
    listFindings.mockResolvedValue({ data: [] })
    render(withProviders(<CodingFindingsPage />))
    expect(await screen.findByText('No coded findings yet.')).toBeInTheDocument()
  })
})
