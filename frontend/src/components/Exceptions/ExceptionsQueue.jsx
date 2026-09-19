import { useCallback, useEffect, useState } from 'react'
import { exceptionsApi } from '../../services/exceptionsApi'
import { useToast } from '../Toast/ToastProvider'
import './ExceptionsQueue.css'

const FLAG_HELP = {
  QUOTE_NOT_FOUND: 'Quoted evidence could not be located in the source.',
  LOW_CONFIDENCE: 'AI confidence fell below the review threshold.',
  SOURCE_MISSING: 'No traceable source was available for this finding.',
  CONTESTED: 'The result is contested or needs a human tie-breaker.',
}

export default function ExceptionsQueue() {
  const toast = useToast()
  const [items, setItems] = useState([])
  const [selected, setSelected] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [note, setNote] = useState('')
  const [pendingOnly, setPendingOnly] = useState(true)

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

  async function review(action) {
    if (!selected) return
    setBusy(true)
    try {
      const updated = action === 'clear'
        ? await exceptionsApi.clear(selected.id, note)
        : await exceptionsApi.reject(selected.id, note)
      toast.success(action === 'clear' ? 'Exception cleared.' : 'Exception rejected.')
      setNote('')
      if (pendingOnly) {
        setItems((current) => current.filter((i) => i.id !== updated.id))
        setSelected(null)
        await load()
      } else {
        setItems((current) => current.map((i) => i.id === updated.id ? updated : i))
        setSelected(updated)
      }
    } catch (err) {
      toast.error(err.message || 'Unable to update exception.')
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
            <dl className="evidence-grid">
              <div><dt>Flagged quote</dt><dd>{selected.sourceQuote || 'Not available'}</dd></div>
              <div><dt>Confidence</dt><dd>{selected.confidence == null ? 'Not available' : selected.confidence.toFixed(2)}</dd></div>
              <div className="evidence-wide"><dt>Source context</dt><dd>{selected.sourceContext || 'No source context available.'}</dd></div>
            </dl>
            {selected.status === 'PENDING' && <>
              <label className="review-note">Reviewer note<textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="Optional review note" maxLength={500} /></label>
              <div className="review-actions">
                <button className="btn btn--secondary" disabled={busy} onClick={() => review('reject')}>Reject</button>
                <button className="btn btn--primary" disabled={busy} onClick={() => review('clear')}>Clear for verification</button>
              </div>
            </>}
            {selected.status !== 'PENDING' && selected.reviewerNote && <p><strong>Reviewer note:</strong> {selected.reviewerNote}</p>}
          </>}
        </section>
      </div>
    </main>
  )
}
