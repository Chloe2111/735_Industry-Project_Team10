import { useState } from 'react'
import { ToastProvider } from './components/Toast/ToastProvider'
import CommunityWorkspace from './components/Community/CommunityWorkspace'
import ExceptionsQueue from './components/Exceptions/ExceptionsQueue'
import CommunitySubmissionPage from './pages/CommunitySubmissionPage'

// No router yet: a simple switch keeps Story 17, Story 24 and Story 19 all reachable.
// Replace this with the team's shared navigation when it exists.
const VIEWS = [
  { id: 'workspace', label: 'Community workspace' },
  { id: 'submit', label: 'Share feedback' },
  { id: 'exceptions', label: 'Exceptions queue' },
]

export default function App() {
  const [view, setView] = useState('workspace')

  return (
    <ToastProvider>
      <nav className="app-nav" aria-label="Main">
        {VIEWS.map((v) => (
          <button
            key={v.id}
            type="button"
            className="app-nav__tab"
            aria-current={view === v.id ? 'page' : undefined}
            onClick={() => setView(v.id)}
          >
            {v.label}
          </button>
        ))}
      </nav>
      {/* Story 17: the community workspace stays mounted so its state survives switching tabs. */}
      <div hidden={view !== 'workspace'}>
        <CommunityWorkspace />
      </div>
      {/* Story 24: the submission page stays mounted so a receipt is still there after a visit to the queue. */}
      <div hidden={view !== 'submit'}>
        <CommunitySubmissionPage />
      </div>
      {/* Story 19: the queue is mounted on each visit so it always loads the latest items. */}
      {view === 'exceptions' && <ExceptionsQueue />}
    </ToastProvider>
  )
}
