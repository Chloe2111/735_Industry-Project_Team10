import { StrictMode } from 'react'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import CommunityWorkspace from '../components/Community/CommunityWorkspace'
import CommunityHeader from '../components/Community/CommunityHeader'
import Dashboard from '../components/Community/Dashboard'
import { ToastProvider } from '../components/Toast/ToastProvider'

const GROUPS = [
  { id: 'g1', name: 'Riverside Youth', description: 'Skate park ideas', category: 'Youth' },
  { id: 'g2', name: 'Green Spaces', description: 'Parks and gardens', category: 'Environment' },
]

function json(body, status = 200) {
  return { ok: status >= 200 && status < 300, status, headers: { get: () => 'application/json' }, json: async () => body }
}

// What Vite's dev proxy answers when the backend is not running: an empty 500.
function proxyDown() {
  return { ok: false, status: 500, headers: { get: () => 'text/plain' }, text: async () => '' }
}

function mockCommunityApi({ down = false } = {}) {
  global.fetch = vi.fn(async (url) => {
    if (down) return proxyDown()
    if (url === '/api/community/groups') return json(GROUPS)
    if (url === '/api/community/posts') return json([])
    if (url.endsWith('/memberships')) return json([{ id: 'm1', groupId: 'g2', userId: 'community-demo-user' }])
    throw new Error(`Unexpected request: ${url}`)
  })
}

function renderWorkspace() {
  // StrictMode runs effects twice in development, which is what used to show two error toasts.
  return render(<StrictMode><ToastProvider><CommunityWorkspace /></ToastProvider></StrictMode>)
}

describe('CommunityWorkspace loading', () => {
  it('shows one in-page message, and no toasts, when the backend is not reachable', async () => {
    mockCommunityApi({ down: true })
    renderWorkspace()

    expect(await screen.findByText(/the server isn't responding/)).toBeInTheDocument()
    expect(screen.getAllByRole('alert')).toHaveLength(1)
    expect(screen.queryAllByRole('status')).toHaveLength(0)
    expect(screen.queryByText('Something went wrong. Please try again.')).not.toBeInTheDocument()
    // The empty-state text would wrongly suggest there is simply no data yet.
    expect(screen.queryByText(/No posts yet/)).not.toBeInTheDocument()
  })

  it('loads the workspace after "Try again" once the backend is back', async () => {
    mockCommunityApi({ down: true })
    renderWorkspace()
    await screen.findByText(/the server isn't responding/)

    mockCommunityApi()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
    expect(await screen.findByText('No posts yet. Create the first community update.')).toBeInTheDocument()
  })
})

describe('Community dashboard', () => {
  it('lists only the groups the member has joined under My Groups', () => {
    render(
      <ToastProvider>
        <Dashboard onNavigate={vi.fn()} groups={GROUPS} posts={[]} memberships={[{ id: 'm1', groupId: 'g2', userId: 'u1' }]} />
      </ToastProvider>,
    )
    const myGroups = screen.getByRole('heading', { name: 'My Groups' }).closest('section')
    expect(within(myGroups).getByText('Green Spaces')).toBeInTheDocument()
    expect(within(myGroups).queryByText('Riverside Youth')).not.toBeInTheDocument()
  })
})

describe('CommunityHeader', () => {
  it('uses the shared portal top bar and breadcrumbs, with no separate black bar', () => {
    const onNavigate = vi.fn()
    const { container } = render(<CommunityHeader active="groups" onNavigate={onNavigate} crumbs={['Groups', 'Create Group']} />)

    expect(container.querySelector('.portal-topnav')).not.toBeNull()
    expect(container.querySelector('.portal-topbar')).toBeNull()
    expect(screen.getByRole('button', { name: 'Groups' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('Create Group')).toHaveClass('portal-breadcrumbs__current')
    expect(screen.getByRole('combobox', { name: 'Switch role' })).toHaveValue('community')
  })

  it('switches page from the nav and opens the profile from the account button', async () => {
    const onNavigate = vi.fn()
    render(<CommunityHeader active="dashboard" onNavigate={onNavigate} />)

    await userEvent.click(screen.getByRole('button', { name: 'Share feedback' }))
    await userEvent.click(screen.getByRole('button', { name: /Alex Thompson/ }))
    expect(onNavigate).toHaveBeenNthCalledWith(1, 'shareFeedback')
    expect(onNavigate).toHaveBeenNthCalledWith(2, 'profile')
  })
})
