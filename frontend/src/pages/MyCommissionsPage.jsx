import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Tabs } from '../components/Tabs/Tabs'
import { DataTable } from '../components/DataTable/DataTable'
import { StatusBadge } from '../components/StatusBadge/StatusBadge'
import { deleteDraft, listCommissions, listDrafts } from '../services/portalService'
import { reports } from '../data/portalSeed'
import './MyCommissionsPage.css'

const REPORTED_IDS = new Set(reports.map((report) => report.id))

const FILTERS = {
  all: () => true,
  open: (commission) => commission.status === 'OPEN',
  'in-progress': (commission) => commission.status === 'IN_PROGRESS',
  submitted: (commission) => commission.status === 'SUBMITTED',
}

const COLUMNS = [
  {
    key: 'project',
    header: 'Project',
    render: (row) =>
      REPORTED_IDS.has(row.id) ? (
        <Link to={`/reports-received/${row.id}`} className="my-commissions-page__project-link">
          {row.title}
        </Link>
      ) : (
        <span className="my-commissions-page__project-link">{row.title}</span>
      ),
  },
  { key: 'assignedGroup', header: 'Assigned group', render: (row) => row.assignedGroup ?? '—' },
  { key: 'incentive', header: 'Incentive', render: (row) => `$${row.incentive.toLocaleString()}` },
  {
    key: 'tier',
    header: 'Tier',
    render: (row) => row.tier ?? <span className="my-commissions-page__tier-pending">Pending</span>,
  },
  { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
]

const STEP_LABELS = {
  details: 'Commission details',
  questionnaire: 'Finding groups',
  groups: 'Choosing groups',
}

function formatSavedAt(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

export function MyCommissionsPage() {
  const [commissions, setCommissions] = useState([])
  const [drafts, setDrafts] = useState([])
  const [activeTab, setActiveTab] = useState('all')

  useEffect(() => {
    let cancelled = false
    listCommissions().then(({ data }) => {
      if (!cancelled) setCommissions(data)
    })
    listDrafts().then(({ data }) => {
      if (!cancelled) setDrafts(data)
    })
    return () => {
      cancelled = true
    }
  }, [])

  async function handleDeleteDraft(id) {
    await deleteDraft(id)
    setDrafts((current) => current.filter((draft) => draft.id !== id))
  }

  const draftColumns = [
    {
      key: 'title',
      header: 'Project',
      render: (row) => row.form?.title?.trim() || <span className="my-commissions-page__tier-pending">Untitled draft</span>,
    },
    { key: 'step', header: 'Saved at step', render: (row) => STEP_LABELS[row.step] ?? STEP_LABELS.details },
    { key: 'savedAt', header: 'Last saved', render: (row) => formatSavedAt(row.savedAt) },
    {
      key: 'actions',
      header: '',
      render: (row) => (
        <div className="my-commissions-page__draft-actions">
          <Link to={`/post-commission?draft=${encodeURIComponent(row.id)}`} className="btn btn--secondary">
            Continue editing
          </Link>
          <button type="button" className="btn btn--secondary my-commissions-page__delete-draft" onClick={() => handleDeleteDraft(row.id)}>
            Delete
          </button>
        </div>
      ),
    },
  ]

  const tabs = useMemo(
    () => [
      { id: 'all', label: 'All', count: commissions.length },
      { id: 'open', label: 'Open', count: commissions.filter(FILTERS.open).length },
      { id: 'in-progress', label: 'In progress', count: commissions.filter(FILTERS['in-progress']).length },
      { id: 'submitted', label: 'Submitted', count: commissions.filter(FILTERS.submitted).length },
      { id: 'drafts', label: 'Drafts', count: drafts.length },
    ],
    [commissions, drafts],
  )

  const showingDrafts = activeTab === 'drafts'
  const rows = showingDrafts ? [] : commissions.filter(FILTERS[activeTab])

  return (
    <div className="my-commissions-page">
      <div className="my-commissions-page__header">
        <div>
          <h1>My commissions</h1>
          <p className="my-commissions-page__subtitle">All commissions posted by [Council 1]</p>
        </div>
        <Link to="/post-commission" className="btn btn--primary">
          + Post a new commission
        </Link>
      </div>

      <div className="page-card page-card--wide">
        <Tabs tabs={tabs} activeId={activeTab} onChange={setActiveTab} />
        {showingDrafts ? (
          <DataTable
            columns={draftColumns}
            rows={drafts}
            rowKey="id"
            emptyMessage="No saved drafts. Use “Save as draft” while posting a commission to keep it here."
          />
        ) : (
          <DataTable columns={COLUMNS} rows={rows} rowKey="id" emptyMessage="No commissions in this view yet." />
        )}
        {!showingDrafts && (
          <p className="my-commissions-page__footnote">
            Status reflects the group&rsquo;s workflow: Open (not yet applied) &rarr; In progress (evidence being
            gathered) &rarr; Submitted (report drafted, pending review).
          </p>
        )}
      </div>
    </div>
  )
}
