import { useState } from 'react'
import { ToastProvider } from './components/Toast/ToastProvider'
import ExceptionsQueue from './components/Exceptions/ExceptionsQueue'
import CommunitySubmissionPage from './pages/CommunitySubmissionPage'
import { getSubmission, publishSubmission } from './services/intakeApi'

// Two screens and no router yet: a simple switch keeps Story 24 and Story 19 both reachable.
// Replace this with the team's shared navigation when it exists.
const VIEWS = [
  { id: 'submit', label: 'Share feedback' },
  { id: 'exceptions', label: 'Exceptions queue' },
]

// Gives the review screen the two things it needs to publish a community submission.
// Clearing a flag never publishes; publishing is this separate, explicit action.
const COMMUNITY_SUBMISSIONS = { getStatus: getSubmission, publish: publishSubmission }

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
      {view === 'exceptions' && <ExceptionsQueue communitySubmissions={COMMUNITY_SUBMISSIONS} />}
    </ToastProvider>
  )
}
