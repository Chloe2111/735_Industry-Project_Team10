import { tierName } from './tiers'

const STATUS_LABELS = {
  PUBLISHED: 'Published',
  PENDING_REVIEW: 'Waiting for a person to review',
  REJECTED: 'Not published',
  HELD: 'Held – not processed',
}

/**
 * Sub Task 24.3: shows the submitter the tier their feedback was handled under,
 * and what happened to it.
 */
export default function SubmissionReceipt({ receipt, groupLabel = (id) => id, onRefresh, refreshing = false }) {
  if (!receipt) return null

  return (
    <section
      className={`submission-receipt submission-receipt--${receipt.status.toLowerCase()}`}
      aria-label="Submission confirmation"
    >
      <p className="submission-receipt__eyebrow">Received</p>
      <h2>Handled as {tierName(receipt.tier)}</h2>
      <p className="submission-receipt__message">{receipt.message}</p>
      <dl className="submission-receipt__details">
        <div>
          <dt>Status</dt>
          <dd>{STATUS_LABELS[receipt.status] ?? receipt.status}</dd>
        </div>
        <div>
          <dt>Group</dt>
          <dd>{receipt.groupIds.map(groupLabel).join(', ')}</dd>
        </div>
        <div>
          <dt>Reference</dt>
          <dd>{receipt.id}</dd>
        </div>
      </dl>
      {receipt.status === 'PENDING_REVIEW' && (
        <button type="button" className="btn btn--secondary" onClick={onRefresh} disabled={refreshing}>
          {refreshing ? 'Checking…' : 'Check status'}
        </button>
      )}
    </section>
  )
}
