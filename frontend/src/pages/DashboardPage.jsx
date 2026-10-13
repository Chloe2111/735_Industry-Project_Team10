import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { StatCard } from '../components/StatCard/StatCard'
import { ActivityList } from '../components/ActivityList/ActivityList'
import { getDashboardSummary } from '../services/portalService'
import './DashboardPage.css'

export function DashboardPage() {
  const [summary, setSummary] = useState(null)

  useEffect(() => {
    let cancelled = false
    getDashboardSummary().then(({ data }) => {
      if (!cancelled) setSummary(data)
    })
    return () => {
      cancelled = true
    }
  }, [])

  if (!summary) return null

  return (
    <div className="dashboard-page">
      <div className="dashboard-page__header">
        <div>
          <h1>Dashboard</h1>
          <p className="dashboard-page__subtitle">Overview for [Council 1] — commissions and reports at a glance</p>
        </div>
        <Link to="/post-commission" className="btn btn--primary">
          + Post a new commission
        </Link>
      </div>

      <div className="dashboard-page__stats">
        <StatCard label="Open commissions" value={summary.stats.openCommissions} hint="not yet assigned to a group" />
        <StatCard label="In progress" value={summary.stats.inProgress} hint="evidence being gathered" />
        <StatCard
          label="Reports awaiting review"
          value={summary.stats.reportsAwaitingReview}
          hint="facilitator still finalising"
        />
        <StatCard
          label="Reports received (total)"
          value={summary.stats.reportsReceivedTotal}
          hint="since this council joined"
        />
      </div>

      <div className="page-card page-card--wide">
        <div className="dashboard-page__activity-header">
          <h2>Recent activity</h2>
          <Link to="/my-commissions">View all</Link>
        </div>
        <ActivityList items={summary.activity} />
      </div>
    </div>
  )
}
