import { useEffect, useState } from 'react'
import { DataTable } from '../components/DataTable/DataTable'
import { listFindings } from '../services/findingsService'
import { useToast } from '../components/Toast/ToastProvider'
import './CodingFindingsPage.css'

const FLAG_LABELS = {
  quoteNotGrounded: 'Quote not found',
  lowConfidence: 'Low confidence',
  sourceMissing: 'Source missing',
  contradiction: 'Contested',
  tierViolation: 'Tier violation',
}

/**
 * Story 23.4 guard: tier 3 (culturally restricted) must never reach this view --
 * CodedFinding.schema.json already restricts tier to 1|2, but this is a second,
 * local check so a malformed or future-schema response can't silently render
 * restricted content. Anything that fails this is dropped, not shown blank.
 */
export function isRenderableFinding(finding) {
  if (!finding || typeof finding !== 'object') return false
  const { itemId, sourceRef, theme, quote, confidence, tier, flags } = finding
  if (!itemId || !sourceRef || !theme || !quote) return false
  if (typeof confidence !== 'number' || !Number.isFinite(confidence) || confidence < 0 || confidence > 1) return false
  if (tier !== 1 && tier !== 2) return false
  if (!Array.isArray(flags)) return false
  return true
}

const COLUMNS = [
  { key: 'theme', header: 'Theme' },
  { key: 'quote', header: 'Quote', render: (row) => <span className="coding-findings-page__quote">&ldquo;{row.quote}&rdquo;</span> },
  { key: 'speakerCode', header: 'Speaker', render: (row) => row.speakerCode ?? 'Unknown speaker' },
  { key: 'confidence', header: 'Confidence', render: (row) => `${Math.round(row.confidence * 100)}%` },
  { key: 'tier', header: 'Tier', render: (row) => `Tier ${row.tier}` },
  {
    key: 'flags',
    header: 'Flags',
    render: (row) =>
      row.flags.length === 0 ? (
        <span className="coding-findings-page__no-flags">None</span>
      ) : (
        <div className="coding-findings-page__flags">
          {row.flags.map((flag) => (
            <span key={flag} className={`flag flag--${flag}`}>
              {FLAG_LABELS[flag] ?? flag}
            </span>
          ))}
        </div>
      ),
  },
]

export function CodingFindingsPage() {
  const toast = useToast()
  const [status, setStatus] = useState('loading')
  const [findings, setFindings] = useState([])

  useEffect(() => {
    let cancelled = false
    setStatus('loading')
    listFindings()
      .then(({ data }) => {
        if (cancelled) return
        setFindings((data ?? []).filter(isRenderableFinding))
        setStatus('ready')
      })
      .catch((error) => {
        if (cancelled) return
        toast.error(error.message || 'Could not load coded findings. Please try again.')
        setStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [toast])

  return (
    <div className="coding-findings-page">
      <h1>Coding findings</h1>
      <p className="coding-findings-page__subtitle">
        Themes and quotes coded by the AI pipeline, with confidence and any flags raised for human review.
      </p>

      <div className="page-card page-card--wide">
        {status === 'loading' && <p className="coding-findings-page__state">Loading coded findings&hellip;</p>}

        {status === 'error' && (
          <p className="coding-findings-page__state coding-findings-page__state--error">
            Could not load coded findings. Please try again.
          </p>
        )}

        {status === 'ready' && (
          <DataTable columns={COLUMNS} rows={findings} rowKey="itemId" emptyMessage="No coded findings yet." />
        )}
      </div>
    </div>
  )
}
