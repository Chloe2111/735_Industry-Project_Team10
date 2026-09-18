import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/Toast/ToastProvider'
import { PostCommissionPage } from '../pages/PostCommissionPage'

function mockFetchOnce({ ok, status, body }) {
  global.fetch = vi.fn().mockResolvedValue({
    ok,
    status,
    headers: { get: () => 'application/json' },
    json: async () => body,
    text: async () => JSON.stringify(body),
  })
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <PostCommissionPage />
      </ToastProvider>
    </MemoryRouter>,
  )
}

async function fillStep1(user) {
  await user.type(screen.getByLabelText(/project title/i), 'Community Garden Feedback')
  await user.type(screen.getByLabelText(/incentive/i), '1500')
  await user.click(screen.getByRole('button', { name: /next: find groups/i }))
}

async function answerQuestionnaire(user) {
  await user.click(screen.getByRole('button', { name: 'Under 18' }))
  await user.click(screen.getByRole('button', { name: 'Urban planning & infrastructure' }))
  await user.click(screen.getByRole('button', { name: 'General public opinion' }))
  await user.click(screen.getByRole('button', { name: 'A specific local neighbourhood' }))
}

async function advanceToGroupsStep(user) {
  await fillStep1(user)
  await screen.findByText('Find the right groups')
  await answerQuestionnaire(user)
  await user.click(screen.getByRole('button', { name: /analyse & suggest groups/i }))
  await screen.findByText('AI-suggested groups')
}

describe('PostCommissionPage — step 1 (commission details)', () => {
  it('blocks advancing and shows inline errors when required fields are empty', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: /next: find groups/i }))

    expect(await screen.findByText('Project title is required.')).toBeInTheDocument()
    expect(screen.getByText(/enter an incentive amount greater than zero/i)).toBeInTheDocument()
    expect(screen.queryByText('Find the right groups')).not.toBeInTheDocument()
  })

  it('shows the automatic-tier notice instead of a tier picker', () => {
    renderPage()
    expect(screen.getByText(/tier is assigned automatically/i)).toBeInTheDocument()
    expect(screen.queryByText(/requested data sensitivity tier/i)).not.toBeInTheDocument()
  })
})

describe('PostCommissionPage — step 2 (find groups questionnaire)', () => {
  it('keeps "Analyse & suggest groups" disabled until all four questions are answered', async () => {
    const user = userEvent.setup()
    renderPage()
    await fillStep1(user)
    await screen.findByText('Find the right groups')

    const analyseButton = screen.getByRole('button', { name: /analyse & suggest groups/i })
    expect(analyseButton).toBeDisabled()

    await answerQuestionnaire(user)

    expect(analyseButton).toBeEnabled()
  })

  it('returns to step 1 with the entered values kept via the back link', async () => {
    const user = userEvent.setup()
    renderPage()
    await fillStep1(user)
    await screen.findByText('Find the right groups')

    await user.click(screen.getByRole('button', { name: /back to commission details/i }))

    expect(screen.getByLabelText(/project title/i)).toHaveValue('Community Garden Feedback')
  })
})

describe('PostCommissionPage — step 3 (AI-suggested groups)', () => {
  it('shows suggested groups with match badges and a live selected count', async () => {
    const user = userEvent.setup()
    renderPage()
    await advanceToGroupsStep(user)

    expect(screen.getByText('Youth Design Collective')).toBeInTheDocument()
    expect(screen.getByText('97% match')).toBeInTheDocument()
    expect(screen.getByText('4 groups selected')).toBeInTheDocument()

    const checkboxes = screen.getAllByRole('checkbox')
    await user.click(checkboxes[0])

    expect(screen.getByText('3 groups selected')).toBeInTheDocument()
  })

  it('posts the merged payload without a tier key and shows the confirmation screen', async () => {
    const user = userEvent.setup()
    mockFetchOnce({
      ok: true,
      status: 201,
      body: { success: true, data: { id: 'c1', title: 'Community Garden Feedback', status: 'OPEN', tier: null } },
    })
    renderPage()
    await advanceToGroupsStep(user)

    await user.click(screen.getByRole('button', { name: /^post commission$/i }))

    expect(await screen.findByText('Commission posted.')).toBeInTheDocument()

    const [url, options] = global.fetch.mock.calls[0]
    expect(url).toBe('/api/commissions')
    const body = JSON.parse(options.body)
    expect(body.title).toBe('Community Garden Feedback')
    expect(body.incentive).toBe(1500)
    expect(body.groups).toEqual([
      'Youth Design Collective',
      'Green Spaces Initiative',
      'Multicultural Community Alliance',
      'Northside Neighbourhood Network',
    ])
    expect(body).not.toHaveProperty('tier')
  })

  it('jumps back to step 1 and shows an inline error on a step-1 field error from the API', async () => {
    const user = userEvent.setup()
    mockFetchOnce({
      ok: false,
      status: 400,
      body: {
        success: false,
        message: 'Please fix the highlighted fields.',
        errors: { title: 'Project title must be 200 characters or fewer.' },
      },
    })
    renderPage()
    await advanceToGroupsStep(user)

    await user.click(screen.getByRole('button', { name: /^post commission$/i }))

    expect(await screen.findByText('Project title must be 200 characters or fewer.')).toBeInTheDocument()
    expect(screen.getByLabelText(/project title/i)).toBeInTheDocument()
    expect(screen.queryByText('Commission posted.')).not.toBeInTheDocument()
  })

  it('lets the user start a new commission after posting one', async () => {
    const user = userEvent.setup()
    mockFetchOnce({
      ok: true,
      status: 201,
      body: { success: true, data: { id: 'c1', title: 'Community Garden Feedback', status: 'OPEN', tier: null } },
    })
    renderPage()
    await advanceToGroupsStep(user)
    await user.click(screen.getByRole('button', { name: /^post commission$/i }))
    await screen.findByText('Commission posted.')

    await user.click(screen.getByRole('button', { name: /post another/i }))

    expect(screen.getByLabelText(/project title/i)).toHaveValue('')
  })
})
