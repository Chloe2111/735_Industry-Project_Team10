import { useState } from 'react'
import './SourceEvidence.css'

export function SourceEvidencePanel({ sources, onClose }) {
  const [selectedSourceId, setSelectedSourceId] = useState(null)
  const selectedSource = sources.find((source) => source.id === selectedSourceId) ?? null

  return (
    <div className="source-evidence">
      <div className="source-evidence__backdrop" onClick={onClose} />
      <div className="source-evidence__panel" role="dialog" aria-label="Source evidence">
        {selectedSource ? (
          <SourceDetail source={selectedSource} onBack={() => setSelectedSourceId(null)} />
        ) : (
          <SourceList sources={sources} onSelect={setSelectedSourceId} onClose={onClose} />
        )}
      </div>
    </div>
  )
}

function SourceList({ sources, onSelect, onClose }) {
  return (
    <>
      <div className="source-evidence__header">
        <div>
          <h2>Source evidence</h2>
          <p>{sources.length} de-identified responses &middot; click any to see full detail</p>
        </div>
        <button type="button" className="source-evidence__close" aria-label="Close" onClick={onClose}>
          &times;
        </button>
      </div>

      <div className="source-evidence__list">
        {sources.map((source) => (
          <div
            key={source.id}
            className={
              source.flagged ? 'source-evidence__card source-evidence__card--flagged' : 'source-evidence__card'
            }
          >
            <div className="source-evidence__card-header">
              <span className="source-evidence__type-badge">{source.type}</span>
              <span className="source-evidence__id">{source.id}</span>
              {source.flagged && <span className="source-evidence__flagged-badge">AI flagged</span>}
              <span className="source-evidence__agree-percent">{source.agreementPercent}% agree</span>
            </div>
            <p className="source-evidence__quote">&ldquo;{source.quote}&rdquo;</p>
            <div className="source-evidence__bar">
              <div className="source-evidence__bar-fill" style={{ width: `${source.agreementPercent}%` }} />
            </div>
            <div className="source-evidence__card-footer">
              <span>
                {source.participantsAgreed} of {source.participantsTotal} participants &middot; {source.method}
              </span>
              <button type="button" className="source-evidence__view-detail" onClick={() => onSelect(source.id)}>
                View detail &rarr;
              </button>
            </div>
          </div>
        ))}
      </div>

      <p className="source-evidence__footnote">
        Raw recordings and personal details are never included. All content is de-identified or paraphrased by the
        group facilitator before submission.
      </p>
    </>
  )
}

function SourceDetail({ source, onBack }) {
  return (
    <>
      <button type="button" className="source-evidence__back" onClick={onBack}>
        &lsaquo; All sources &middot; {source.id}
      </button>

      <div className="source-evidence__detail-quote-card">
        <span className="source-evidence__type-badge">
          {source.type} <span className="source-evidence__via">via {source.method}</span>
        </span>
        <p className="source-evidence__quote source-evidence__quote--large">&ldquo;{source.quote}&rdquo;</p>
      </div>

      <section className="source-evidence__section">
        <h3>Agreement across group</h3>
        <div className="source-evidence__big-percent">{source.agreementPercent}%</div>
        <div className="source-evidence__bar">
          <div className="source-evidence__bar-fill" style={{ width: `${source.agreementPercent}%` }} />
        </div>
        <p className="source-evidence__meter-caption">
          {source.participantsAgreed} of {source.participantsTotal} participants expressed the same or a closely
          related view across the engagement.
        </p>
      </section>

      <section className="source-evidence__section">
        <h3>Who shared this view</h3>
        <span className="source-evidence__sub-label">Age range</span>
        <p className="source-evidence__age-range">{source.ageRange}</p>

        <span className="source-evidence__sub-label">Gender breakdown</span>
        <div className="source-evidence__gender-breakdown">
          {source.genderBreakdown.map((entry) => (
            <div key={entry.label} className="source-evidence__gender-row">
              <span className="source-evidence__gender-label">{entry.label}</span>
              <div className="source-evidence__gender-bar">
                <div className="source-evidence__gender-bar-fill" style={{ width: `${entry.percent}%` }} />
              </div>
              <span className="source-evidence__gender-percent">{entry.percent}%</span>
            </div>
          ))}
        </div>

        <span className="source-evidence__sub-label">Cultural backgrounds represented</span>
        <div className="source-evidence__chips">
          {source.culturalBackgrounds.map((background) => (
            <span key={background} className="source-evidence__chip">
              {background}
            </span>
          ))}
        </div>
      </section>

      <section className="source-evidence__section">
        <h3>Why this matters for the report</h3>
        <p>{source.whyItMatters}</p>
      </section>

      <section className="source-evidence__section">
        <h3>Themes tagged</h3>
        <div className="source-evidence__chips">
          {source.themes.map((theme) => (
            <span key={theme} className="source-evidence__theme-chip">
              {theme}
            </span>
          ))}
        </div>
      </section>
    </>
  )
}
