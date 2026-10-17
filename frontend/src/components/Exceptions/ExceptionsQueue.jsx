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
  AI_COULD_NOT_CODE: 'The AI could not code this idea. Read it, then either code it yourself (choose a theme and the sentence that supports it) or reject it with a reason.',
}

const AI_COULD_NOT_CODE = 'AI_COULD_NOT_CODE'
const MAX_THEME_LENGTH = 80

/**
 * Splits the de-identified idea text into sentences the facilitator can pick as evidence.
 * Labels added by Submit Idea ("Idea: ", "Problem it solves: ") are left out, and every
 * sentence is an exact piece of the text, so the backend's quote check can find it.
 */
export function ideaSentences(text) {
  if (!text) return []
  const sentences = []
  for (const line of text.split('\n')) {
    const body = line.replace(/^[A-Za-z][A-Za-z ]{0,40}:\s+/, '')
    for (const part of body.split(/(?<=[.!?])\s+/)) {
      const sentence = part.trim()
      if (sentence && text.includes(sentence) && !sentences.includes(sentence)) sentences.push(sentence)
    }
  }
  return sentences
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
  const [codedTheme, setCodedTheme] = useState('')
  const [codedQuote, setCodedQuote] = useState('')

  const isCommunityItem = selected?.sourceType === 'COMMUNITY_SUBMISSION' && Boolean(selected?.sourceId)
  const canPublish = Boolean(communitySubmissions) && isCommunityItem
  const needsHumanCoding = selected?.flagType === AI_COULD_NOT_CODE
  const canApproveCoding = Boolean(codedTheme.trim()) && Boolean(codedQuote)

  function resetReviewInputs() {
    setNote('')
    setCodedTheme('')
    setCodedQuote('')
  }

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
    resetReviewInputs()
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

  // "AI could not code": the facilitator's own theme and chosen sentence become the finding,
  // marked as coded by the facilitator. Then, like Clear & publish, the submission is published.
  async function approveWithCoding() {
    if (!selected || !canApproveCoding) return
    setBusy(true)
    try {
      const updated = await exceptionsApi.codeByFacilitator(selected.id, {
        theme: codedTheme.trim(),
        quote: codedQuote,
        note: note.trim(),
      })
      if (canPublish) {
        try {
          await communitySubmissions.publish(selected.sourceId)
          toast.success('Coded by facilitator and published.')
        } catch (err) {
          toast.error(`Coded, but not published: ${err.message || 'the submission could not be published.'}`)
        }
      } else {
        toast.success('Coded by facilitator.')
      }
      await showUpdated(updated)
    } catch (err) {
      toast.error(err.message || 'Unable to save the facilitator coding.')
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
            <button key={item.id} className={`queue-item ${selected?.id === item.id ? 'is-selected' : ''}`} onClick={() => { setSelected(item); resetReviewInputs() }}>
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
            {needsHumanCoding ? (
              <IdeaCodingPanel
                text={selected.sourceQuote}
                pending={selected.status === 'PENDING'}
                theme={codedTheme}
                onThemeChange={setCodedTheme}
                quote={codedQuote}
                onQuoteChange={setCodedQuote}
              />
            ) : (
              <dl className="evidence-grid">
                <div><dt>Flagged quote</dt><dd>{selected.sourceQuote || 'Not available'}</dd></div>
                <div><dt>Confidence</dt><dd>{selected.confidence == null ? 'Not available' : selected.confidence.toFixed(2)}</dd></div>
                <div className="evidence-wide"><dt>Source context</dt><dd>{selected.sourceContext || 'No source context available.'}</dd></div>
              </dl>
            )}
            {selected.status === 'PENDING' && <>
              <label className="review-note">{needsHumanCoding ? 'Explanation' : 'Reviewer note'}<textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder={needsHumanCoding ? 'Why you chose this theme, or why you are rejecting the idea' : 'Optional review note'} maxLength={500} /><small className="note-hint">{needsHumanCoding ? 'Optional when approving. Required to reject.' : 'Optional to clear. Required to reject.'}</small></label>
              <div className="review-actions">
                <button className="btn btn--secondary" disabled={busy || !note.trim()} title={note.trim() ? '' : 'Add a note to reject'} onClick={() => review('reject')}>Reject</button>
                {needsHumanCoding ? (
                  <button className="btn btn--primary" disabled={busy || !canApproveCoding} title={canApproveCoding ? '' : 'Enter a theme and choose a supporting sentence'} onClick={approveWithCoding}>
                    {canPublish ? 'Approve & publish' : 'Approve with this theme'}
                  </button>
                ) : (
                  <>
                    <button className="btn btn--primary" disabled={busy || Boolean(selected.blockedReason)} title={selected.blockedReason ? 'This item cannot be cleared' : ''} onClick={() => review('clear')}>Clear for verification</button>
                    {canPublish && (
                      <button className="btn btn--primary" disabled={busy || Boolean(selected.blockedReason)} title={selected.blockedReason ? 'This item cannot be cleared' : 'Clear this flag and publish the submission'} onClick={clearAndPublish}>Clear &amp; publish</button>
                    )}
                  </>
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

/** Shows the idea and lets the facilitator choose a theme and the sentence that supports it. */
function IdeaCodingPanel({ text, pending, theme, onThemeChange, quote, onQuoteChange }) {
  const sentences = ideaSentences(text)
  return (
    <div className="idea-coding">
      <div className="idea-coding__text">
        <span className="idea-coding__label">Idea (de-identified)</span>
        <p>{text || 'No idea text available.'}</p>
      </div>
      {pending && <>
        <label className="idea-coding__theme">
          Theme
          <input
            value={theme}
            onChange={(e) => onThemeChange(e.target.value)}
            maxLength={MAX_THEME_LENGTH}
            placeholder="A short theme, e.g. Safety after dark"
          />
        </label>
        <fieldset className="idea-coding__sentences">
          <legend>Supporting sentence</legend>
          {sentences.length === 0 && <p className="queue-state">No sentences found in this idea. Reject it with a reason.</p>}
          {sentences.map((sentence) => (
            <label key={sentence} className={`idea-coding__sentence ${quote === sentence ? 'is-chosen' : ''}`}>
              <input type="radio" name="supporting-sentence" checked={quote === sentence} onChange={() => onQuoteChange(sentence)} />
              <span>{sentence}</span>
            </label>
          ))}
        </fieldset>
      </>}
    </div>
  )
}
