import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { DataTable } from '../components/DataTable/DataTable'
import { StatusBadge } from '../components/StatusBadge/StatusBadge'
import { listReports } from '../services/portalService'
import './ReportsReceivedPage.css'

const COLUMNS = [
  { key: 'project', header: 'Project' },
  { key: 'group', header: 'Group' },
  { key: 'reportingPeriod', header: 'Reporting period' },
  { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
  {
    key: 'action',
    header: 'Action',
    render: (row) =>
      row.status === 'FINAL' ? (
        <Link to={`/reports-received/${row.id}`} className="reports-received-page__view-link">
          View report &rsaquo;
        </Link>
      ) : (
        <span className="reports-received-page__unavailable">Not yet available</span>
      ),
  },
]

export function ReportsReceivedPage() {
  const [reports, setReports] = useState([])

  useEffect(() => {
    let cancelled = false
    listReports().then(({ data }) => {
      if (!cancelled) setReports(data)
    })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="reports-received-page">
      <h1>Reports received</h1>
      <p className="reports-received-page__subtitle">De-identified, human-reviewed reports from groups who completed a commission</p>

      <div className="page-card page-card--wide">
        <DataTable columns={COLUMNS} rows={reports} rowKey="id" emptyMessage="No reports received yet." />
        <p className="reports-received-page__footnote">
          A report appears here once the group&rsquo;s facilitator resolves every AI review flag — raw community data
          is never shown in this portal.
        </p>
      </div>
    </div>
  )
}
