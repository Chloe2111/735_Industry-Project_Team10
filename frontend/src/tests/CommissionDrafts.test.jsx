import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// Fresh module state (and empty storage) for each test.
async function loadPages() {
  vi.resetModules()
  const { PostCommissionPage } = await import('../pages/PostCommissionPage')
  const { MyCommissionsPage } = await import('../pages/MyCommissionsPage')
  const { ToastProvider } = await import('../components/Toast/ToastProvider')
  return { PostCommissionPage, MyCommissionsPage, ToastProvider }
}

function renderApp(pages, initialPath) {
  const { PostCommissionPage, MyCommissionsPage, ToastProvider } = pages
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <ToastProvider>
        <Routes>
          <Route path="/post-commission" element={<PostCommissionPage />} />
          <Route path="/my-commissions" element={<MyCommissionsPage />} />
        </Routes>
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('Commission drafts', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('saves a draft that appears under My commissions › Drafts and can be continued', async () => {
    const user = userEvent.setup()
    const pages = await loadPages()
    const first = renderApp(pages, '/post-commission')

    await user.type(screen.getByLabelText(/project title/i), 'Library opening hours')
    await user.click(screen.getByRole('button', { name: 'Save as draft' }))
    expect(await screen.findByText(/Draft saved/)).toBeInTheDocument()
    first.unmount()

    // The draft is listed in the Drafts tab.
    renderApp(pages, '/my-commissions')
    await user.click(await screen.findByRole('tab', { name: /Drafts · 1/ }))
    const row = (await screen.findByText('Library opening hours')).closest('tr')
    expect(within(row).getByText('Commission details')).toBeInTheDocument()

    // "Continue editing" reopens the form with the saved values.
    await user.click(within(row).getByRole('link', { name: 'Continue editing' }))
    expect(await screen.findByDisplayValue('Library opening hours')).toBeInTheDocument()
  })

  it('keeps drafts after a page reload (stored in the browser)', async () => {
    const user = userEvent.setup()
    let pages = await loadPages()
    const first = renderApp(pages, '/post-commission')
    await user.type(screen.getByLabelText(/project title/i), 'Bus stop shelters')
    await user.click(screen.getByRole('button', { name: 'Save as draft' }))
    await screen.findByText(/Draft saved/)
    first.unmount()

    pages = await loadPages() // simulates a reload: module state is rebuilt from storage
    renderApp(pages, '/my-commissions')
    await user.click(await screen.findByRole('tab', { name: /Drafts · 1/ }))
    expect(await screen.findByText('Bus stop shelters')).toBeInTheDocument()
  })

  it('deletes a draft from the Drafts tab', async () => {
    const user = userEvent.setup()
    window.localStorage.setItem(
      'vcnity.commissionDrafts',
      JSON.stringify([{ id: 'draft-1', form: { title: 'Old idea' }, step: 'details', savedAt: '2026-10-14T00:00:00Z' }]),
    )
    const pages = await loadPages()
    renderApp(pages, '/my-commissions')

    await user.click(await screen.findByRole('tab', { name: /Drafts · 1/ }))
    await user.click(await screen.findByRole('button', { name: 'Delete' }))

    expect(await screen.findByText(/No saved drafts/)).toBeInTheDocument()
    expect(JSON.parse(window.localStorage.getItem('vcnity.commissionDrafts'))).toEqual([])
  })

  it('tells the user when a draft link no longer exists', async () => {
    const pages = await loadPages()
    renderApp(pages, '/post-commission?draft=missing')
    expect(await screen.findByText(/could not be found/)).toBeInTheDocument()
  })
})
