import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import ExceptionsQueue from '../components/Exceptions/ExceptionsQueue'
import { ToastProvider } from '../components/Toast/ToastProvider'
import { exceptionsApi } from '../services/exceptionsApi'

vi.mock('../services/exceptionsApi', () => ({ exceptionsApi: { list: vi.fn(), clear: vi.fn(), reject: vi.fn() } }))

const item = { id:'EX-1', flagType:'QUOTE_NOT_FOUND', sourceQuote:'made up quote', sourceContext:'real transcript context', confidence:.88, sourceRef:'WS-006', status:'PENDING' }

function renderQueue(){ return render(<ToastProvider><ExceptionsQueue /></ToastProvider>) }

describe('ExceptionsQueue', () => {
  beforeEach(() => vi.clearAllMocks())

  it('surfaces flag reason and evidence', async () => {
    exceptionsApi.list.mockResolvedValue([item])
    renderQueue()
    expect((await screen.findAllByText('QUOTE_NOT_FOUND')).length).toBeGreaterThan(0)
    expect(screen.getByText('made up quote')).toBeInTheDocument()
    expect(screen.getByText('real transcript context')).toBeInTheDocument()
  })

  it('clears an exception for human review', async () => {
    const user = userEvent.setup()
    exceptionsApi.list.mockResolvedValue([item]).mockResolvedValueOnce([item]).mockResolvedValueOnce([])
    exceptionsApi.clear.mockResolvedValue({ ...item, status:'CLEARED' })
    renderQueue()
    await screen.findByText('made up quote')
    await user.click(screen.getByRole('button', { name:'Clear for verification' }))
    await waitFor(() => expect(exceptionsApi.clear).toHaveBeenCalledWith('EX-1', ''))
  })

  it('rejects an exception for human review', async () => {
    const user = userEvent.setup()
    exceptionsApi.list.mockResolvedValueOnce([item]).mockResolvedValueOnce([])
    exceptionsApi.reject.mockResolvedValue({ ...item, status:'REJECTED' })
    renderQueue()
    await screen.findByText('made up quote')
    await user.type(screen.getByPlaceholderText('Optional review note'), 'Rejected after source review')
    await user.click(screen.getByRole('button', { name:'Reject' }))
    await waitFor(() => expect(exceptionsApi.reject).toHaveBeenCalledWith('EX-1', 'Rejected after source review'))
  })

  it('keeps Reject disabled until a note is entered', async () => {
    const user = userEvent.setup()
    exceptionsApi.list.mockResolvedValue([item])
    renderQueue()
    await screen.findByText('made up quote')
    const reject = screen.getByRole('button', { name: 'Reject' })
    expect(reject).toBeDisabled()
    await user.type(screen.getByPlaceholderText('Optional review note'), 'Not in source')
    expect(reject).toBeEnabled()
  })

  it('shows why a blocked item cannot be cleared and disables Clear', async () => {
    exceptionsApi.list.mockResolvedValue([{ ...item, blockedReason: 'Halted: the group is now Tier 3.' }])
    renderQueue()
    await screen.findByText('made up quote')
    expect(screen.getByRole('alert')).toHaveTextContent('Halted: the group is now Tier 3.')
    expect(screen.getByRole('button', { name: 'Clear for verification' })).toBeDisabled()
  })

  it('shows the server message, not a success, when a clear is refused', async () => {
    const user = userEvent.setup()
    exceptionsApi.list.mockResolvedValue([item])
    exceptionsApi.clear.mockRejectedValue(new Error('Halted: the group is now Tier 3.'))
    renderQueue()
    await screen.findByText('made up quote')
    await user.click(screen.getByRole('button', { name: 'Clear for verification' }))
    expect(await screen.findByText('Halted: the group is now Tier 3.')).toBeInTheDocument()
    expect(screen.queryByText('Exception cleared.')).not.toBeInTheDocument()
  })

  // --- Clear and publish are two separate actions ---

  const communityItem = { ...item, sourceType: 'COMMUNITY_SUBMISSION', sourceId: 'SUB-1' }

  function renderWithPublishing(actions) {
    return render(<ToastProvider><ExceptionsQueue communitySubmissions={actions} /></ToastProvider>)
  }

  it('has no publish buttons unless the app provides the publish action', async () => {
    exceptionsApi.list.mockResolvedValue([communityItem])
    renderQueue()
    await screen.findByText('made up quote')
    expect(screen.queryByRole('button', { name: 'Clear & publish' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Publish submission' })).not.toBeInTheDocument()
  })

  it('Clear only resolves the flag and never publishes', async () => {
    const user = userEvent.setup()
    const actions = { getStatus: vi.fn().mockResolvedValue({ status: 'PENDING_REVIEW' }), publish: vi.fn() }
    exceptionsApi.list.mockResolvedValue([communityItem])
    exceptionsApi.clear.mockResolvedValue({ ...communityItem, status: 'CLEARED' })
    renderWithPublishing(actions)
    await screen.findByText('made up quote')
    await user.click(screen.getByRole('button', { name: 'Clear for verification' }))
    await waitFor(() => expect(exceptionsApi.clear).toHaveBeenCalledWith('EX-1', ''))
    expect(actions.publish).not.toHaveBeenCalled()
  })

  it('Clear & publish clears the flag and then publishes the submission', async () => {
    const user = userEvent.setup()
    const actions = { getStatus: vi.fn().mockResolvedValue({ status: 'PENDING_REVIEW' }), publish: vi.fn().mockResolvedValue({ status: 'PUBLISHED' }) }
    exceptionsApi.list.mockResolvedValue([communityItem])
    exceptionsApi.clear.mockResolvedValue({ ...communityItem, status: 'CLEARED' })
    renderWithPublishing(actions)
    await screen.findByText('made up quote')
    await user.click(screen.getByRole('button', { name: 'Clear & publish' }))
    await waitFor(() => expect(actions.publish).toHaveBeenCalledWith('SUB-1'))
    expect(exceptionsApi.clear).toHaveBeenCalledWith('EX-1', '')
    expect(await screen.findByText('Flag cleared and submission published.')).toBeInTheDocument()
  })

  it('says why it was not published when Clear & publish cannot publish yet', async () => {
    const user = userEvent.setup()
    const actions = { getStatus: vi.fn().mockResolvedValue({ status: 'PENDING_REVIEW' }), publish: vi.fn().mockRejectedValue(new Error('1 flag on this submission still needs review.')) }
    exceptionsApi.list.mockResolvedValue([communityItem])
    exceptionsApi.clear.mockResolvedValue({ ...communityItem, status: 'CLEARED' })
    renderWithPublishing(actions)
    await screen.findByText('made up quote')
    await user.click(screen.getByRole('button', { name: 'Clear & publish' }))
    expect(await screen.findByText('Flag cleared. Not published: 1 flag on this submission still needs review.')).toBeInTheDocument()
    expect(screen.queryByText('Flag cleared and submission published.')).not.toBeInTheDocument()
  })

  it('offers Publish submission for a cleared item whose submission is not published yet', async () => {
    const user = userEvent.setup()
    const actions = { getStatus: vi.fn().mockResolvedValue({ status: 'CLEARED' }), publish: vi.fn().mockResolvedValue({ status: 'PUBLISHED' }) }
    exceptionsApi.list.mockResolvedValue([{ ...communityItem, status: 'CLEARED' }])
    renderWithPublishing(actions)
    await screen.findByText('made up quote')
    await user.click(await screen.findByRole('button', { name: 'Publish submission' }))
    await waitFor(() => expect(actions.publish).toHaveBeenCalledWith('SUB-1'))
    expect(await screen.findByText('Submission published.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Publish submission' })).not.toBeInTheDocument())
  })

})
