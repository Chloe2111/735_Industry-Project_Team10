import { useCallback, useEffect, useState } from 'react'
import { exceptionsApi } from '../../services/exceptionsApi'
import { useToast } from '../Toast/ToastProvider'
import './ExceptionsQueue.css'

const FLAG_HELP = {
  REPRESENTATION_GAP: 'An expected speaker has no coded findings. Review source coverage before clearing.',
  QUOTE_NOT_FOUND: 'Quoted evidence could not be located in the source.',
  LOW_CONFIDENCE: 'AI confidence fell below the review threshold.',
  SOURCE_MISSING: 'No traceable source was available for this finding.',
  CONTESTED: 'The result is contested or needs a human tie-breaker.',
  TIER_VIOLATION: 'A data-tier restriction needs investigation. Clearing a flag does not override tier restrictions.',
}

const SUBMISSION_STATUS = {
  PENDING_REVIEW: 'Waiting for review',
  CLEARED: 'Cleared – not published yet',
  PUBLISHED: 'Published',
  REJECTED: 'Not published (a flag was rejected)',
  HELD: 'Held',
}

/**
 * Clearing a flag and publishing are two separate actions: "Clear" only resolves the flag.
 *
 * `communitySubmissions` is optional. When the app passes it ({ getStatus(id), publish(id) }),
 * items raised by a community submission also get "Clear & publish" and "Publish submission".
 * Without it this screen works exactly as before and never publishes anything.
 */
export default function ExceptionsQueue({ communitySubmissions = null }) {
  const toast = useToast()
  const [items, setItems] = useState([])
  const [selected, setSelected] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [note, setNote] = useState('')
  const [pendingOnly, setPendingOnly] = useState(true)
  const [submissionStatus, setSubmissionStatus] = useState(null)

  const isCommunityItem = selected?.sourceType === 'COMMUNITY_SUBMISSION' && Boolean(selected?.sourceId)
  const canPublish = Boolean(communitySubmissions) && isCommunityItem

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await exceptionsApi.list(pendingOnly)
      setItems(data)
      setSelected((current) => data.find((i) => i.id === current?.id) ?? data[0] ?? null)
    } catch (err) {
      setError(err.message || 'Unable to load exceptions queue.')
    } finally {
      setLoading(false)
    }
  }, [pendingOnly])

  useEffect(() => { load() }, [load])

  // For a community submission, show where the submission itself stands (cleared is not published).
  const selectedId = selected?.id
  const selectedStatus = selected?.status
  const selectedSourceId = selected?.sourceId
  useEffect(() => {
    let current = true
    setSubmissionStatus(null)
    if (canPublish) {
      communitySubmissions.getStatus(selectedSourceId)
        .then((receipt) => { if (current) setSubmissionStatus(receipt?.status ?? null) })
        .catch(() => { if (current) setSubmissionStatus(null) })
    }
    return () => { current = false }
  }, [canPublish, communitySubmissions, selectedId, selectedStatus, selectedSourceId])

  async function showUpdated(updated) {
    setNote('')
    if (pendingOnly) {
      setItems((current) => current.filter((i) => i.id !== updated.id))
      setSelected(null)
      await load()
    } else {
      setItems((current) => current.map((i) => i.id === updated.id ? updated : i))
      setSelected(updated)
    }
  }

  // "Clear" resolves the flag and nothing else. It never publishes.
  async function review(action) {
    if (!selected) return
    setBusy(true)
    try {
      const updated = action === 'clear'
        ? await exceptionsApi.clear(selected.id, note)
        : await exceptionsApi.reject(selected.id, note)
      toast.success(action === 'clear' ? 'Exception cleared.' : 'Exception rejected.')
      await showUpdated(updated)
    } catch (err) {
      toast.error(err.message || 'Unable to update exception.')
    } finally {
      setBusy(false)
    }
  }

  // "Clear & publish" is the explicit second action: clear this flag, then ask to publish the submission.
  // If it cannot be published yet (another flag is pending, or the group is Tier 3) the flag stays cleared
  // and the reviewer is told why it was not published.
  async function clearAndPublish() {
    if (!selected || !canPublish) return
    setBusy(true)
    try {
      const updated = await exceptionsApi.clear(selected.id, note)
      try {
        await communitySubmissions.publish(selected.sourceId)
        toast.success('Flag cleared and submission published.')
      } catch (err) {
        toast.error(`Flag cleared. Not published: ${err.message || 'the submission could not be published.'}`)
      }
      await showUpdated(updated)
    } catch (err) {
      toast.error(err.message || 'Unable to update exception.')
    } finally {
      setBusy(false)
    }
  }

  async function publishSubmission() {
    if (!selected || !canPublish) return
    setBusy(true)
    try {
      const receipt = await communitySubmissions.publish(selected.sourceId)
      setSubmissionStatus(receipt?.status ?? 'PUBLISHED')
      toast.success('Submission published.')
    } catch (err) {
      toast.error(err.message || 'Unable to publish the submission.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="exceptions-page">
      <header className="exceptions-header">
        <div>
          <p className="eyebrow">Human review</p>
          <h1>Exceptions Queue</h1>
          <p>Review AI/pipeline flags before verification sign-off.</p>
        </div>
        <label className="pending-toggle">
          <input type="checkbox" checked={pendingOnly} onChange={(e) => setPendingOnly(e.target.checked)} />
          Pending only
        </label>
      </header>

      {error && <div className="queue-error" role="alert">{error} <button onClick={load}>Retry</button></div>}

      <div className="exceptions-layout">
        <section className="queue-list" aria-label="Flagged items">
          {loading && <p className="queue-state">Loading exceptions…</p>}
          {!loading && !error && items.length === 0 && <p className="queue-state">No exceptions waiting for review.</p>}
          {items.map((item) => (
            <button key={item.id} className={`queue-item ${selected?.id === item.id ? 'is-selected' : ''}`} onClick={() => { setSelected(item); setNote('') }}>
              <span className={`flag flag--${item.flagType.toLowerCase()}`}>{item.flagType}</span>
              <strong>{item.sourceRef}</strong>
              <span className="queue-item__status">{item.status}</span>
            </button>
          ))}
        </section>

        <section className="review-card" aria-label="Exception details">
          {!selected ? <p className="queue-state">Select a flagged item to review its evidence.</p> : <>
            <div className="review-card__title">
              <div><span className={`flag flag--${selected.flagType.toLowerCase()}`}>{selected.flagType}</span><h2>{selected.sourceRef}</h2></div>
              <span className="status-pill">{selected.status}</span>
            </div>
            <p className="flag-help">{FLAG_HELP[selected.flagType]}</p>
            {selected.sourceType === 'COMMUNITY_SUBMISSION' && (
              <p className="flag-help">
                Community submission: clearing a flag does not publish it. It stays hidden until every flag is
                cleared and a reviewer publishes it. Rejecting keeps it hidden.
                {submissionStatus && <><br /><strong>Submission status:</strong> {SUBMISSION_STATUS[submissionStatus] ?? submissionStatus}</>}
              </p>
            )}
            {selected.blockedReason && (
              <p className="queue-error" role="alert">
                This item cannot be cleared: {selected.blockedReason}
              </p>
            )}
            <dl className="evidence-grid">
              <div><dt>Flagged quote</dt><dd>{selected.sourceQuote || 'Not available'}</dd></div>
              <div><dt>Confidence</dt><dd>{selected.confidence == null ? 'Not available' : selected.confidence.toFixed(2)}</dd></div>
              <div className="evidence-wide"><dt>Source context</dt><dd>{selected.sourceContext || 'No source context available.'}</dd></div>
            </dl>
            {selected.status === 'PENDING' && <>
              <label className="review-note">Reviewer note<textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="Optional review note" maxLength={500} /><small className="note-hint">Optional to clear. Required to reject.</small></label>
              <div className="review-actions">
                <button className="btn btn--secondary" disabled={busy || !note.trim()} title={note.trim() ? '' : 'Add a note to reject'} onClick={() => review('reject')}>Reject</button>
                <button className="btn btn--primary" disabled={busy || Boolean(selected.blockedReason)} title={selected.blockedReason ? 'This item cannot be cleared' : ''} onClick={() => review('clear')}>Clear for verification</button>
                {canPublish && (
                  <button className="btn btn--primary" disabled={busy || Boolean(selected.blockedReason)} title={selected.blockedReason ? 'This item cannot be cleared' : 'Clear this flag and publish the submission'} onClick={clearAndPublish}>Clear &amp; publish</button>
                )}
              </div>
            </>}
            {selected.status !== 'PENDING' && selected.reviewerNote && <p><strong>Reviewer note:</strong> {selected.reviewerNote}</p>}
            {canPublish && selected.status === 'CLEARED' && submissionStatus === 'CLEARED' && !selected.blockedReason && (
              <div className="review-actions">
                <button className="btn btn--primary" disabled={busy} onClick={publishSubmission}>Publish submission</button>
              </div>
            )}
          </>}
        </section>
      </div>
    </main>
  )
}
