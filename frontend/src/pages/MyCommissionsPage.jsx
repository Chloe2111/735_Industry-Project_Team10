import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Tabs } from '../components/Tabs/Tabs'
import { DataTable } from '../components/DataTable/DataTable'
import { StatusBadge } from '../components/StatusBadge/StatusBadge'
import { listCommissions } from '../services/portalService'
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

export function MyCommissionsPage() {
  const [commissions, setCommissions] = useState([])
  const [activeTab, setActiveTab] = useState('all')

  useEffect(() => {
    let cancelled = false
    listCommissions().then(({ data }) => {
      if (!cancelled) setCommissions(data)
    })
    return () => {
      cancelled = true
    }
  }, [])

  const tabs = useMemo(
    () => [
      { id: 'all', label: 'All', count: commissions.length },
      { id: 'open', label: 'Open', count: commissions.filter(FILTERS.open).length },
      { id: 'in-progress', label: 'In progress', count: commissions.filter(FILTERS['in-progress']).length },
      { id: 'submitted', label: 'Submitted', count: commissions.filter(FILTERS.submitted).length },
    ],
    [commissions],
  )

  const rows = commissions.filter(FILTERS[activeTab])

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
        <DataTable columns={COLUMNS} rows={rows} rowKey="id" emptyMessage="No commissions in this view yet." />
        <p className="my-commissions-page__footnote">
          Status reflects the group&rsquo;s workflow: Open (not yet applied) &rarr; In progress (evidence being
          gathered) &rarr; Submitted (report drafted, pending review).
        </p>
      </div>
    </div>
  )
}
