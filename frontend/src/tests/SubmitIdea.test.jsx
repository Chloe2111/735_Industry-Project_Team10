import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/Toast/ToastProvider'
import SubmitIdea, { buildIdeaText } from '../components/Community/Commissions/SubmitIdea'
import { listGroupTiers, submitFeedback } from '../services/intakeApi'

vi.mock('../services/intakeApi', () => ({
  listGroupTiers: vi.fn(),
  submitFeedback: vi.fn(),
}))

function renderForm(onNavigate = vi.fn(), commission) {
  render(
    <ToastProvider>
      <SubmitIdea onNavigate={onNavigate} commission={commission} />
    </ToastProvider>,
  )
  return onNavigate
}

async function fillRequired(user) {
  await user.selectOptions(await screen.findByRole('combobox', { name: 'Your group' }), 'riverside youth')
  await user.type(screen.getByPlaceholderText('e.g. Inclusive Skate Zone'), 'Lit evening paths')
  await user.type(screen.getByPlaceholderText('Describe your idea...'), 'Add lighting along the park paths.')
  await user.type(screen.getByPlaceholderText('Explain the need or challenge...'), 'Paths feel unsafe after dark.')
  await user.type(screen.getByPlaceholderText('e.g. young people, local families, schools...'), 'Young people and families.')
  await user.click(screen.getByRole('checkbox'))
}

describe('SubmitIdea (sent to intake)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listGroupTiers.mockResolvedValue([
      { groupId: 'riverside youth', groupName: 'Riverside Youth', tier: 2 },
      { groupId: 'unclassified', groupName: 'Unclassified', tier: null },
    ])
  })

  it('joins the answers into one labelled text, skipping empty ones', () => {
    expect(buildIdeaText({ title: ' A ', description: 'B', problem: 'C', beneficiaries: 'D', impact: '' }))
      .toBe('Idea title: A\nIdea: B\nProblem it solves: C\nWho would benefit: D')
  })

  it('only offers groups that have a tier', async () => {
    renderForm()
    const select = await screen.findByRole('combobox', { name: 'Your group' })
    expect(await screen.findByRole('option', { name: /Riverside Youth · Tier 2/ })).toBeInTheDocument()
    expect(select).not.toHaveTextContent('Unclassified')
  })

  it('requires a group before submitting', async () => {
    const user = userEvent.setup()
    renderForm()
    await screen.findByRole('option', { name: /Riverside Youth/ })
    await user.click(screen.getByRole('button', { name: /Submit Idea/ }))
    expect(screen.getByText('Choose the group you are submitting with.')).toBeInTheDocument()
    expect(submitFeedback).not.toHaveBeenCalled()
  })

  it('submits the idea to intake and shows the real receipt next', async () => {
    const user = userEvent.setup()
    const receipt = { id: 'sub-1', status: 'PUBLISHED', tier: 2, groupIds: ['riverside youth'], submittedAt: '2026-10-18T01:00:00Z' }
    submitFeedback.mockResolvedValue(receipt)
    const onNavigate = renderForm()
    await screen.findByRole('option', { name: /Riverside Youth/ })
    await fillRequired(user)

    await user.click(screen.getByRole('button', { name: /Submit Idea/ }))

    expect(submitFeedback).toHaveBeenCalledWith({
      groupIds: ['riverside youth'],
      text: 'Idea title: Lit evening paths\nIdea: Add lighting along the park paths.\nProblem it solves: Paths feel unsafe after dark.\nWho would benefit: Young people and families.',
    })
    expect(onNavigate).toHaveBeenCalledWith('success', { receipt, title: 'Lit evening paths' })
  })

  it('keeps the form and shows the error when intake refuses', async () => {
    const user = userEvent.setup()
    submitFeedback.mockRejectedValue(new Error('This group has not been classified yet.'))
    const onNavigate = renderForm()
    await screen.findByRole('option', { name: /Riverside Youth/ })
    await fillRequired(user)

    await user.click(screen.getByRole('button', { name: /Submit Idea/ }))

    expect(await screen.findByText('This group has not been classified yet.')).toBeInTheDocument()
    expect(onNavigate).not.toHaveBeenCalledWith('success', expect.anything())
  })

  it('links the idea to a commission a client posted', async () => {
    const user = userEvent.setup()
    submitFeedback.mockResolvedValue({ id: 'sub-2', status: 'PUBLISHED', tier: 2 })
    const commission = { id: 'c-123', source: 'client', title: 'TEST - Riverside shade and seating ideas' }
    renderForm(vi.fn(), commission)
    expect(screen.getByRole('heading', { level: 1, name: 'Submit an Idea' })).toBeInTheDocument()
    expect(screen.getAllByText('TEST - Riverside shade and seating ideas').length).toBeGreaterThan(0)
    await screen.findByRole('option', { name: /Riverside Youth/ })
    await fillRequired(user)

    await user.click(screen.getByRole('button', { name: /Submit Idea/ }))

    expect(submitFeedback).toHaveBeenCalledWith(expect.objectContaining({ commissionId: 'c-123', groupIds: ['riverside youth'] }))
  })

  it('does not send a commission for the sample commissions', async () => {
    const user = userEvent.setup()
    submitFeedback.mockResolvedValue({ id: 'sub-3', status: 'PUBLISHED', tier: 2 })
    renderForm(vi.fn(), { id: 'sample-riverside-spaces', source: 'sample', title: 'Riverside Public Spaces' })
    await screen.findByRole('option', { name: /Riverside Youth/ })
    await fillRequired(user)

    await user.click(screen.getByRole('button', { name: /Submit Idea/ }))

    expect(submitFeedback.mock.calls[0][0]).not.toHaveProperty('commissionId')
  })
})
