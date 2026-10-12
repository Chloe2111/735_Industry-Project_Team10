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

})
