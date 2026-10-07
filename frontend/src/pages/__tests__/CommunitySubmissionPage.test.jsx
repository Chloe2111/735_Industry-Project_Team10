import { describe, test, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ToastProvider } from '../../components/Toast/ToastProvider'
import CommunitySubmissionPage from '../CommunitySubmissionPage'

/**
 * Story 24 integration test: the real page, form and apiClient, with only
 * `fetch` replaced. Covers:
 *   24.1 the payload the form sends is exactly what the endpoint expects
 *   24.2 the form calls POST /api/intake/submissions
 *   24.3 the assigned tier is shown back to the submitter
 *   24.4 a Tier 3 submission is shown as held, never as published
 * and the review points: a Tier 3 group shows no posts, and published text has details removed.
 *
 * The backend side of the same contract is in IntakePayloadContractTest.java.
 */

const YOUTH_GROUP = { groupId: 'youth group a', groupName: 'Youth Group A', tier: 2, setBy: 'Mohika', history: [{}], version: 4 }

function json(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'application/json' },
    json: async () => body,
  }
}

// Answers each request the page makes; `submitResponse` is what the POST returns,
// `tierResponse` what the PUT that sets a tier returns.
function mockApi({ groups = [YOUTH_GROUP], submitResponse, tierResponse, published = [] }) {
  global.fetch = vi.fn(async (url, options = {}) => {
    const method = options.method ?? 'GET'
    if (url === '/api/intake/group-tiers' && method === 'GET') return json(groups)
    if (url === '/api/intake/group-tiers' && method === 'PUT') return tierResponse
    if (url === '/api/intake/submissions' && method === 'POST') return submitResponse
    if (url.startsWith('/api/intake/submissions?groupId=') && method === 'GET') return json(published)
    throw new Error(`Unexpected request: ${method} ${url}`)
  })
}

function postCalls() {
  return global.fetch.mock.calls.filter(([, options]) => options?.method === 'POST')
}

function renderPage() {
  return render(
    <ToastProvider>
      <CommunitySubmissionPage />
    </ToastProvider>,
  )
}

function putCalls() {
  return global.fetch.mock.calls.filter(([, options]) => options?.method === 'PUT')
}

async function setTier(user, group, tier) {
  await user.type(screen.getByLabelText('Group to classify'), group)
  await user.selectOptions(screen.getByLabelText('Tier'), String(tier))
  await user.type(screen.getByLabelText('Set by (your name)'), 'Mohika')
  await user.type(screen.getByLabelText('Reason'), 'Agreed with the group')
  await user.click(screen.getByRole('button', { name: 'Set tier' }))
}

async function fillAndSubmit(user, group, text) {
  await user.type(screen.getByLabelText('Which group is this for?'), group)
  await user.type(screen.getByLabelText('Your feedback'), text)
  await user.click(screen.getByRole('button', { name: 'Submit feedback' }))
}

const receipt = (overrides) => ({
  id: 'sub-1',
  status: 'PUBLISHED',
  tier: 2,
  groupIds: ['youth group a'],
  message: 'Thank you. Your feedback has been published to the group. Names and contact details that were detected have been removed.',
  submittedAt: '2026-10-05T10:00:00Z',
  ...overrides,
})

describe('CommunitySubmissionPage (Story 24)', () => {
  test('sends exactly { groupIds, text } to the intake endpoint, with no tier', async () => {
    mockApi({ submitResponse: json(receipt(), 201) })
    const user = userEvent.setup()
    renderPage()

    await fillAndSubmit(user, 'Youth Group A', 'The workshop was well organised.')

    await waitFor(() => expect(postCalls()).toHaveLength(1))
    const [url, options] = postCalls()[0]
    expect(url).toBe('/api/intake/submissions')
    expect(JSON.parse(options.body)).toEqual({
      groupIds: ['Youth Group A'],
      text: 'The workshop was well organised.',
    })
  })

  test('shows the assigned tier back to the submitter as confirmation', async () => {
    mockApi({
      submitResponse: json(receipt(), 201),
      published: [{ id: 'sub-1', groupIds: ['youth group a'], text: 'The workshop was well organised.', tier: 2 }],
    })
    const user = userEvent.setup()
    renderPage()

    await fillAndSubmit(user, 'Youth Group A', 'The workshop was well organised.')

    expect(await screen.findByRole('heading', { name: /Handled as Tier 2/ })).toBeInTheDocument()
    expect(screen.getByText(/Your feedback has been published to the group\./)).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Published in Youth Group A' })).toBeInTheDocument()
  })

  test('shows a flagged submission as waiting for review, not as published', async () => {
    mockApi({
      submitResponse: json(
        receipt({
          status: 'PENDING_REVIEW',
          message: 'Thank you. Your feedback was received and will be checked by a person before it is published.',
        }),
        201,
      ),
    })
    const user = userEvent.setup()
    renderPage()

    await fillAndSubmit(user, 'Youth Group A', 'Loved the session')

    expect(await screen.findByText('Waiting for a person to review')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Check status' })).toBeInTheDocument()
    expect(await screen.findByText('Nothing has been published for this group yet.')).toBeInTheDocument()
  })

  test('shows a Tier 3 submission as held and not processed', async () => {
    const eldersCircle = { groupId: 'elders circle', groupName: 'Elders Circle', tier: 3, setBy: 'Mohika', history: [{}], version: 0 }
    mockApi({
      groups: [eldersCircle],
      submitResponse: json(
        receipt({
          status: 'HELD',
          tier: 3,
          groupIds: ['elders circle'],
          message:
            'This group is Tier 3, so your feedback was not processed automatically and is not published. It has been kept for a person to review.',
        }),
        201,
      ),
    })
    const user = userEvent.setup()
    renderPage()

    await fillAndSubmit(user, 'Elders Circle', 'This story should stay within the community.')

    expect(await screen.findByRole('heading', { name: /Handled as Tier 3/ })).toBeInTheDocument()
    expect(screen.getByText(/Held/)).toBeInTheDocument()
    expect(screen.getByText(/not processed automatically and is not published/)).toBeInTheDocument()
    expect(screen.getByText(/kept for a person to review/)).toBeInTheDocument()
    expect(screen.queryByText('Published', { exact: true })).not.toBeInTheDocument()
    // The published area says why it is empty instead of looking like "no posts yet".
    expect(await screen.findByText(/This group is Tier 3, so nothing is shown here/)).toBeInTheDocument()
    expect(screen.queryByText('Nothing has been published for this group yet.')).not.toBeInTheDocument()
  })

  test('shows published text with names and contact details marked as removed', async () => {
    mockApi({
      submitResponse: json(receipt(), 201),
      published: [
        {
          id: 'sub-1',
          groupIds: ['youth group a'],
          text: 'Thanks to [REDACTED:CAPITALISED_NAME] for running it. Call [REDACTED:PHONE].',
          tier: 2,
        },
      ],
    })
    const user = userEvent.setup()
    renderPage()

    await fillAndSubmit(user, 'Youth Group A', 'Thanks to Maria Lopez for running it. Call 0412 345 678.')

    expect(await screen.findByText('Thanks to [removed] for running it. Call [removed].')).toBeInTheDocument()
  })

  test('tells the coordinator how many submissions were halted and how many posts were hidden', async () => {
    mockApi({
      tierResponse: json({
        group: { ...YOUTH_GROUP, tier: 3, version: 5 },
        haltedSubmissionIds: ['sub-2', 'sub-3'],
        publishedToRecheck: 1,
      }),
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'Youth Group A' })

    await setTier(user, 'Youth Group A', 3)

    expect(
      await screen.findByText(/2 waiting or rejected submissions were halted for re-check\. 1 published post is now hidden\./),
    ).toBeInTheDocument()
  })

  test('shows the refusal for a group with no tier and no confirmation card', async () => {
    mockApi({
      groups: [],
      submitResponse: json(
        { message: 'No tier has been set for: new group. A person must classify the group before anything can be submitted for it.' },
        422,
      ),
    })
    const user = userEvent.setup()
    renderPage()

    await fillAndSubmit(user, 'New Group', 'The workshop was well organised.')

    expect(await screen.findByText(/No tier has been set for: new group/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Submission confirmation' })).not.toBeInTheDocument()
    // The person's text is kept so they do not have to type it again.
    expect(screen.getByLabelText('Your feedback')).toHaveValue('The workshop was well organised.')
  })

  test('does not call the endpoint when the form is empty', async () => {
    mockApi({ submitResponse: json(receipt(), 201) })
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: 'Submit feedback' }))

    expect(screen.getByText('Please enter which group this is for.')).toBeInTheDocument()
    expect(screen.getByText('Please write your feedback before submitting.')).toBeInTheDocument()
    expect(postCalls()).toHaveLength(0)
  })

  test('a tier change carries the version of the group that was on screen', async () => {
    mockApi({
      tierResponse: json({ group: { ...YOUTH_GROUP, tier: 3, version: 5 }, haltedSubmissionIds: [], publishedToRecheck: 0 }),
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'Youth Group A' })

    await setTier(user, 'youth group a', 3)

    await waitFor(() => expect(putCalls()).toHaveLength(1))
    expect(JSON.parse(putCalls()[0][1].body)).toEqual({
      groupId: 'youth group a',
      tier: 3,
      setBy: 'Mohika',
      reason: 'Agreed with the group',
      expectedVersion: 4,
    })
  })

  test('a group that is not classified yet is sent with no expected version', async () => {
    mockApi({
      groups: [],
      tierResponse: json({ group: { groupId: 'new group', groupName: 'New Group', tier: 1, version: 0 }, haltedSubmissionIds: [], publishedToRecheck: 0 }),
    })
    const user = userEvent.setup()
    renderPage()

    await setTier(user, 'New Group', 1)

    await waitFor(() => expect(putCalls()).toHaveLength(1))
    expect(JSON.parse(putCalls()[0][1].body).expectedVersion).toBeNull()
  })

  test('shows who changed the group when a tier change is refused, and does not report success', async () => {
    mockApi({
      tierResponse: json(
        { message: 'Youth Group A was set to Tier 3 by Chloee while you were editing. Your change was not saved. Reload the page and check before changing it again.' },
        409,
      ),
    })
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'Youth Group A' })

    await setTier(user, 'Youth Group A', 1)

    expect(await screen.findByText(/was set to Tier 3 by Chloee while you were editing/)).toBeInTheDocument()
    expect(screen.queryByText(/is now Tier 1/)).not.toBeInTheDocument()
    // The form keeps what was typed, and the group list is loaded again so the next try uses the new version.
    expect(screen.getByLabelText('Group to classify')).toHaveValue('Youth Group A')
    await waitFor(() =>
      expect(global.fetch.mock.calls.filter(([url, options]) => url === '/api/intake/group-tiers' && options?.method === 'GET')).toHaveLength(2),
    )
  })
})
