import './StatusBadge.css'

const LABELS = {
  OPEN: 'OPEN',
  IN_PROGRESS: 'IN PROGRESS',
  SUBMITTED: 'SUBMITTED',
  FINAL: 'FINAL',
  DRAFT: 'DRAFT',
}

const SOLID_STATUSES = new Set(['IN_PROGRESS', 'SUBMITTED', 'FINAL'])

export function StatusBadge({ status }) {
  const variant = SOLID_STATUSES.has(status) ? 'status-badge--solid' : 'status-badge--outline'
  return <span className={`status-badge ${variant}`}>{LABELS[status] ?? status}</span>
}
