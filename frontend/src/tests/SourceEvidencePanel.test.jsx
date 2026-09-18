import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SourceEvidencePanel } from '../components/SourceEvidence/SourceEvidencePanel'

const sources = [
  {
    id: 'SRV-001',
    type: 'Survey response',
    method: 'Online survey',
    quote: 'The gate is hard to open.',
    agreementPercent: 70,
    participantsAgreed: 21,
    participantsTotal: 30,
    flagged: false,
    ageRange: '15–20 years old',
    genderBreakdown: [
      { label: 'male', percent: 50 },
      { label: 'female', percent: 50 },
    ],
    culturalBackgrounds: ['Anglo-Australian'],
    whyItMatters: 'This is why it matters.',
    themes: ['Access'],
  },
  {
    id: 'INT-001',
    type: 'Interview transcript',
    method: 'Individual interview',
    quote: 'Paraphrased quote about access.',
    agreementPercent: 40,
    participantsAgreed: 12,
    participantsTotal: 30,
    flagged: true,
    ageRange: '16–19 years old',
    genderBreakdown: [{ label: 'female', percent: 100 }],
    culturalBackgrounds: ['East Asian'],
    whyItMatters: 'Second reason.',
    themes: ['Inclusivity'],
  },
]

describe('SourceEvidencePanel', () => {
  it('lists every source with its agreement stats and method', () => {
    render(<SourceEvidencePanel sources={sources} onClose={() => {}} />)

    expect(screen.getByText('Source evidence')).toBeInTheDocument()
    expect(screen.getByText('2 de-identified responses', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('21 of 30 participants', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('AI flagged')).toBeInTheDocument()
  })

  it('drills into a source detail view and back again', async () => {
    const user = userEvent.setup()
    render(<SourceEvidencePanel sources={sources} onClose={() => {}} />)

    const viewDetailButtons = screen.getAllByRole('button', { name: /view detail/i })
    await user.click(viewDetailButtons[0])

    expect(screen.getByText(/all sources/i)).toBeInTheDocument()
    expect(screen.getByText('Who shared this view')).toBeInTheDocument()
    expect(screen.getByText('This is why it matters.')).toBeInTheDocument()

    await user.click(screen.getByText(/all sources/i))

    expect(screen.getByText('Source evidence')).toBeInTheDocument()
  })

  it('calls onClose when the close button is clicked', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<SourceEvidencePanel sources={sources} onClose={onClose} />)

    await user.click(screen.getByRole('button', { name: /close/i }))

    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
