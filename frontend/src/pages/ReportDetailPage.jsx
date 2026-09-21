import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { FindingAccordion } from '../components/Accordion/FindingAccordion'
import { SourceEvidencePanel } from '../components/SourceEvidence/SourceEvidencePanel'
import { getReport } from '../services/portalService'
import { useToast } from '../components/Toast/ToastProvider'
import './ReportDetailPage.css'

export function ReportDetailPage() {
  const { reportId } = useParams()
  const toast = useToast()
  const [report, setReport] = useState(undefined)
  const [activeSources, setActiveSources] = useState(null)

  useEffect(() => {
    let cancelled = false
    setReport(undefined)
    getReport(reportId)
      .then(({ data }) => {
        if (!cancelled) setReport(data)
      })
      .catch((error) => {
        if (cancelled) return
        toast.error(error.message || 'Could not load this report. Please try again.')
        setReport(null)
      })
    return () => {
      cancelled = true
    }
  }, [reportId, toast])

  if (report === undefined) return null

  if (report === null) {
    return (
      <div className="report-detail-page">
        <Link to="/reports-received" className="report-detail-page__back">
          &lsaquo; Back to reports received
        </Link>
        <div className="page-card">
          <p>This report isn&rsquo;t available yet.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="report-detail-page">
      <div className="report-detail-page__toolbar">
        <Link to="/reports-received" className="report-detail-page__back">
          &lsaquo; Back to reports received
        </Link>
        <button type="button" className="btn btn--secondary" onClick={() => toast.info('Export is coming soon.')}>
          &darr; Export PDF
        </button>
      </div>

      <div className="page-card page-card--wide report-detail-page__card">
        <h1>{report.title}</h1>
        <p className="report-detail-page__subtitle">{report.subtitle}</p>

        <div className="report-detail-page__badges">
          {report.badges.map((badge) => (
            <span key={badge} className="report-detail-page__badge">
              {badge}
            </span>
          ))}
        </div>

        <div className="report-detail-page__meta">
          <div>
            <span className="report-detail-page__meta-label">Prepared for</span>
            <span className="report-detail-page__meta-value">{report.preparedFor}</span>
          </div>
          <div>
            <span className="report-detail-page__meta-label">Date</span>
            <span className="report-detail-page__meta-value">{report.date}</span>
          </div>
          <div>
            <span className="report-detail-page__meta-label">File</span>
            <span className="report-detail-page__meta-value">{report.file}</span>
          </div>
        </div>

        <h2 className="report-detail-page__section-title">Purpose</h2>
        <p>{report.purpose}</p>

        <div className="report-detail-page__stats">
          {report.stats.map((stat) => (
            <div key={stat.label} className="report-detail-page__stat">
              <span className="report-detail-page__stat-value">{stat.value}</span>
              <span className="report-detail-page__stat-label">{stat.label}</span>
            </div>
          ))}
        </div>

        <h2 className="report-detail-page__section-title">Key findings</h2>
        <p className="report-detail-page__findings-intro">{report.findingsIntro}</p>
        {report.withheldFindingsCount > 0 && (
          <p className="report-detail-page__withheld-note">
            {report.withheldFindingsCount} finding{report.withheldFindingsCount === 1 ? '' : 's'} withheld pending
            linked evidence.
          </p>
        )}
        <FindingAccordion
          findings={report.findings}
          onTraceBack={(finding) =>
            finding.sources?.length > 0
              ? setActiveSources(finding.sources)
              : toast.info('Trace back to source is coming soon.')
          }
        />
      </div>

      {activeSources && <SourceEvidencePanel sources={activeSources} onClose={() => setActiveSources(null)} />}
    </div>
  )
}
