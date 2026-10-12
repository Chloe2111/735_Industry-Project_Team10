import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import CreateGroup from '../components/Community/CreateGroup'
import { ToastProvider } from '../components/Toast/ToastProvider'

function renderPage(props = {}) {
  return render(<ToastProvider><CreateGroup onNavigate={vi.fn()} onCreateGroup={vi.fn()} {...props} /></ToastProvider>)
}

describe('CreateGroup', () => {
  it('shows validation errors for an empty submission', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Create Group' }))
    expect(screen.getByText('Group name is required.')).toBeInTheDocument()
    expect(screen.getByText('Please provide a group description.')).toBeInTheDocument()
    expect(screen.getByText('Please select a category.')).toBeInTheDocument()
  })

  it('submits valid group data and navigates after success', async () => {
    const onCreateGroup = vi.fn().mockResolvedValue({ id: 'g1' })
    const onNavigate = vi.fn()
    renderPage({ onCreateGroup, onNavigate })
    await userEvent.type(screen.getByPlaceholderText('Enter a group name'), 'Local Group')
    await userEvent.type(screen.getByPlaceholderText('What is this group about?'), 'Local projects')
    await userEvent.selectOptions(screen.getByRole('combobox'), 'Community')
    await userEvent.click(screen.getByRole('button', { name: 'Create Group' }))
    expect(onCreateGroup).toHaveBeenCalledWith(expect.objectContaining({ name: 'Local Group', category: 'Community' }))
    expect(onNavigate).toHaveBeenCalledWith('groups')
  })
})
