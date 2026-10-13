import { useState } from 'react'
import './FindingAccordion.css'

export function FindingAccordion({ findings, onTraceBack }) {
  const [openId, setOpenId] = useState(findings[0]?.id ?? null)

  return (
    <div className="finding-accordion">
      {findings.map((finding, index) => {
        const isOpen = finding.id === openId
        return (
          <div key={finding.id} className="finding-accordion__item">
            <button
              type="button"
              className="finding-accordion__header"
              aria-expanded={isOpen}
              onClick={() => setOpenId(isOpen ? null : finding.id)}
            >
              <span>
                {index + 1}. {finding.title}
              </span>
              <span aria-hidden="true">{isOpen ? '−' : '+'}</span>
            </button>

            {isOpen && (
              <div className="finding-accordion__body">
                <dl className="finding-accordion__meta">
                  <div>
                    <dt>Commissioning body</dt>
                    <dd>{finding.commissioningBody}</dd>
                  </div>
                  <div>
                    <dt>Delivered by</dt>
                    <dd>{finding.deliveredBy}</dd>
                  </div>
                  <div>
                    <dt>Research question or focus</dt>
                    <dd>{finding.researchQuestion}</dd>
                  </div>
                  <div>
                    <dt>Method</dt>
                    <dd>{finding.method}</dd>
                  </div>
                  <div>
                    <dt>Analysis</dt>
                    <dd>{finding.analysis}</dd>
                  </div>
                  <div>
                    <dt>Deliverable status</dt>
                    <dd>{finding.deliverableStatus}</dd>
                  </div>
                </dl>
                <div className="finding-accordion__footer">
                  <span>{finding.sourceCount} source responses linked to this finding</span>
                  <button type="button" className="btn btn--secondary" onClick={() => onTraceBack(finding)}>
                    &#8617; Trace back to source
                  </button>
                </div>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
