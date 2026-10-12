import { useState } from 'react'
import { ToastProvider } from './components/Toast/ToastProvider'
import ExceptionsQueue from './components/Exceptions/ExceptionsQueue'
import CommunitySubmissionPage from './pages/CommunitySubmissionPage'

// Two screens and no router yet: a simple switch keeps Story 24 and Story 19 both reachable.
// Replace this with the team's shared navigation when it exists.
const VIEWS = [
  { id: 'submit', label: 'Share feedback' },
  { id: 'exceptions', label: 'Exceptions queue' },
]

export default function App() {
  const [view, setView] = useState('submit')

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
      {/* The submission page stays mounted so a receipt is still there after a visit to the queue. */}
      <div hidden={view !== 'submit'}>
        <CommunitySubmissionPage />
      </div>
      {/* The queue is mounted on each visit so it always loads the latest items. */}
      {view === 'exceptions' && <ExceptionsQueue />}
    </ToastProvider>
  )
}
