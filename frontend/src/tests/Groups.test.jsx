import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import Groups from '../components/Community/Groups'

describe('Groups', () => {
  const groups = [
    { id: 'g1', name: 'Joined Group', description: 'Already joined', category: 'Community' },
    { id: 'g2', name: 'Suggested Group', description: 'Available to join', category: 'Design' },
  ]

  it('separates joined and suggested groups and exposes membership actions', () => {
    render(<Groups onNavigate={vi.fn()} groups={groups} memberships={[{ id: 'm1', groupId: 'g1', userId: 'u1' }]} />)
    expect(screen.getByText('My Groups')).toBeInTheDocument()
    expect(screen.getByText('Suggested Groups')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Leave Group' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Join Group' })).toBeInTheDocument()
  })

  it('calls join for a suggested group', () => {
    const onJoinGroup = vi.fn()
    render(<Groups onNavigate={vi.fn()} groups={[groups[1]]} memberships={[]} onJoinGroup={onJoinGroup} />)
    fireEvent.click(screen.getByRole('button', { name: 'Join Group' }))
    expect(onJoinGroup).toHaveBeenCalledWith('g2')
  })
})
