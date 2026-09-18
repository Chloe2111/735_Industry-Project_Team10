import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ageGroupOptions,
  communityVoiceOptions,
  deadlineOptions,
  geographicScopeOptions,
  groupCatalog,
  reportFormatOptions,
  topicAreaOptions,
} from '../data/portalSeed'
import { createCommission, suggestGroups } from '../services/portalService'
import { useToast } from '../components/Toast/ToastProvider'
import { PillOptions } from '../components/PillOptions/PillOptions'
import './PostCommissionPage.css'

const INITIAL_FORM = {
  title: '',
  description: '',
  incentive: '',
  deadline: deadlineOptions[deadlineOptions.length - 1],
  reportFormats: ['Executive summary', 'Detailed thematic analysis'],
}

const INITIAL_ANSWERS = {
  ageGroups: [],
  topicArea: [],
  voiceTypes: [],
  scope: [],
}

const STEP1_FIELDS = new Set(['title', 'description', 'incentive', 'deadline'])

function validateStep1(form) {
  const errors = {}
  if (!form.title.trim()) {
    errors.title = 'Project title is required.'
  }
  if (!form.incentive || Number(form.incentive) <= 0) {
    errors.incentive = 'Enter an incentive amount greater than zero.'
  }
  return errors
}

function questionnaireComplete(answers) {
  return (
    answers.ageGroups.length > 0 &&
    answers.topicArea.length > 0 &&
    answers.voiceTypes.length > 0 &&
    answers.scope.length > 0
  )
}

function matchBadgeClass(percent) {
  if (percent >= 90) return 'post-commission-page__match post-commission-page__match--high'
  if (percent >= 70) return 'post-commission-page__match post-commission-page__match--medium'
  return 'post-commission-page__match post-commission-page__match--low'
}

export function PostCommissionPage() {
  const toast = useToast()

  const [step, setStep] = useState('details')
  const [form, setForm] = useState(INITIAL_FORM)
  const [errors, setErrors] = useState({})
  const [answers, setAnswers] = useState(INITIAL_ANSWERS)
  const [analysing, setAnalysing] = useState(false)
  const [suggestedGroups, setSuggestedGroups] = useState([])
  const [selectedGroupIds, setSelectedGroupIds] = useState(new Set())
  const [groupsError, setGroupsError] = useState('')
  const [groupSearch, setGroupSearch] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [posted, setPosted] = useState(false)

  function updateField(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => {
      if (!current[field]) return current
      const next = { ...current }
      delete next[field]
      return next
    })
  }

  function handleSaveDraft() {
    toast.success('Draft saved.')
  }

  function handleNextFromDetails(event) {
    event.preventDefault()
    const validationErrors = validateStep1(form)
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }
    setErrors({})
    setStep('questionnaire')
  }

  async function handleAnalyse() {
    setAnalysing(true)
    try {
      const { data } = await suggestGroups(answers)
      setSuggestedGroups(data)
      setSelectedGroupIds(new Set(data.map((group) => group.id)))
      setGroupsError('')
      setStep('groups')
    } finally {
      setAnalysing(false)
    }
  }

  function toggleGroupSelected(id) {
    setSelectedGroupIds((current) => {
      const next = new Set(current)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
    setGroupsError('')
  }

  function addGroupFromSearch(group) {
    setSuggestedGroups((current) => (current.some((g) => g.id === group.id) ? current : [...current, group]))
    setSelectedGroupIds((current) => new Set(current).add(group.id))
    setGroupSearch('')
  }

  const searchResults =
    groupSearch.trim().length === 0
      ? []
      : groupCatalog.filter(
          (group) =>
            !suggestedGroups.some((existing) => existing.id === group.id) &&
            group.name.toLowerCase().includes(groupSearch.trim().toLowerCase()),
        )

  async function handleSubmit() {
    if (selectedGroupIds.size === 0) {
      setGroupsError('Select at least one group.')
      return
    }

    setSubmitting(true)
    try {
      await createCommission({
        title: form.title.trim(),
        description: form.description.trim(),
        incentive: Number(form.incentive),
        deadline: form.deadline,
        reportFormats: form.reportFormats,
        groups: suggestedGroups.filter((group) => selectedGroupIds.has(group.id)).map((group) => group.name),
      })
      setPosted(true)
    } catch (error) {
      const fieldErrors = error.fieldErrors || {}
      const step1Errors = {}
      let hasStep1Error = false
      for (const [field, message] of Object.entries(fieldErrors)) {
        if (STEP1_FIELDS.has(field)) {
          step1Errors[field] = message
          hasStep1Error = true
        }
      }
      if (hasStep1Error) {
        setErrors(step1Errors)
        setStep('details')
      }
      if (fieldErrors.groups) {
        setGroupsError(fieldErrors.groups)
      }
      toast.error(error.message || 'Could not post the commission. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  function handlePostAnother() {
    setForm(INITIAL_FORM)
    setErrors({})
    setAnswers(INITIAL_ANSWERS)
    setSuggestedGroups([])
    setSelectedGroupIds(new Set())
    setGroupsError('')
    setGroupSearch('')
    setStep('details')
    setPosted(false)
  }

  if (posted) {
    return (
      <div className="post-commission-page post-commission-page--centered">
        <div className="page-card post-commission-page__confirmation">
          <h2>Commission posted.</h2>
          <p>Groups matching your criteria can now see and apply. You&rsquo;ll be notified when a group picks it up.</p>
          <div className="post-commission-page__confirmation-actions">
            <Link to="/my-commissions" className="btn btn--secondary">
              View my commissions
            </Link>
            <button type="button" className="btn btn--primary" onClick={handlePostAnother}>
              Post another
            </button>
          </div>
        </div>
      </div>
    )
  }

  if (step === 'questionnaire') {
    const complete = questionnaireComplete(answers)
    return (
      <div className="post-commission-page">
        <div className="post-commission-page__step">
          <button type="button" className="post-commission-page__back" onClick={() => setStep('details')}>
            &lsaquo; Back to commission details
          </button>
          <h1>Find the right groups</h1>
          <p className="post-commission-page__subtitle">
            Answer a few questions so VCNITY can identify which community groups are most relevant to your
            commission.
          </p>

          <div className="post-commission-page__questionnaire">
            <div className="page-card post-commission-page__question-card">
              <span className="post-commission-page__question-number">1 / 4</span>
              <h2>Which age group is most relevant to this commission?</h2>
              <p className="post-commission-page__question-hint">Select all that apply.</p>
              <PillOptions
                options={ageGroupOptions}
                value={answers.ageGroups}
                multi
                onChange={(value) => setAnswers((current) => ({ ...current, ageGroups: value }))}
              />
            </div>

            <div className="page-card post-commission-page__question-card">
              <span className="post-commission-page__question-number">2 / 4</span>
              <h2>What is the primary topic area?</h2>
              <p className="post-commission-page__question-hint">Choose the closest match.</p>
              <PillOptions
                options={topicAreaOptions}
                value={answers.topicArea}
                onChange={(value) => setAnswers((current) => ({ ...current, topicArea: value }))}
              />
            </div>

            <div className="page-card post-commission-page__question-card">
              <span className="post-commission-page__question-number">3 / 4</span>
              <h2>What type of community voice are you seeking?</h2>
              <p className="post-commission-page__question-hint">Select all that apply.</p>
              <PillOptions
                options={communityVoiceOptions}
                value={answers.voiceTypes}
                multi
                onChange={(value) => setAnswers((current) => ({ ...current, voiceTypes: value }))}
              />
            </div>

            <div className="page-card post-commission-page__question-card">
              <span className="post-commission-page__question-number">4 / 4</span>
              <h2>What is the geographic or community scope?</h2>
              <p className="post-commission-page__question-hint">Choose the closest match.</p>
              <PillOptions
                options={geographicScopeOptions}
                value={answers.scope}
                onChange={(value) => setAnswers((current) => ({ ...current, scope: value }))}
              />
            </div>
          </div>

          <div className="post-commission-page__actions post-commission-page__actions--right">
            <button
              type="button"
              className="btn btn--primary"
              disabled={!complete || analysing}
              onClick={handleAnalyse}
            >
              {analysing ? 'Analysing…' : '\u{1F50D} Analyse & suggest groups'}
            </button>
          </div>
        </div>
      </div>
    )
  }

  if (step === 'groups') {
    return (
      <div className="post-commission-page">
        <div className="post-commission-page__step">
          <button type="button" className="post-commission-page__back" onClick={() => setStep('questionnaire')}>
            &lsaquo; Back to questionnaire
          </button>
          <h1>AI-suggested groups</h1>
          <p className="post-commission-page__subtitle">
            Based on your answers, VCNITY identified these groups as the best match. Confirm your selection or add
            others.
          </p>

          <div className="post-commission-page__groups">
            {suggestedGroups.map((group) => (
              <label key={group.id} className="page-card post-commission-page__group-card">
                <input
                  type="checkbox"
                  checked={selectedGroupIds.has(group.id)}
                  onChange={() => toggleGroupSelected(group.id)}
                />
                <div className="post-commission-page__group-body">
                  <div className="post-commission-page__group-header">
                    <span className="post-commission-page__group-name">{group.name}</span>
                    <span className={matchBadgeClass(group.matchPercent)}>{group.matchPercent}% match</span>
                  </div>
                  <p className="post-commission-page__group-reason">{group.reason}</p>
                </div>
              </label>
            ))}
          </div>

          <div className="page-card post-commission-page__add-groups">
            <label htmlFor="group-search">Add more groups</label>
            <input
              id="group-search"
              type="text"
              placeholder="Search groups by name…"
              value={groupSearch}
              onChange={(event) => setGroupSearch(event.target.value)}
            />
            {searchResults.length > 0 && (
              <ul className="post-commission-page__search-results">
                {searchResults.map((group) => (
                  <li key={group.id}>
                    <button type="button" onClick={() => addGroupFromSearch(group)}>
                      {group.name}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {groupsError && <p className="form-field__error">{groupsError}</p>}

          <div className="post-commission-page__actions post-commission-page__actions--split">
            <span className="post-commission-page__selected-count">{selectedGroupIds.size} groups selected</span>
            <div className="post-commission-page__actions-buttons">
              <button type="button" className="btn btn--secondary" onClick={handleSaveDraft}>
                Save as draft
              </button>
              <button
                type="button"
                className="btn btn--primary"
                disabled={selectedGroupIds.size === 0 || submitting}
                onClick={handleSubmit}
              >
                {submitting ? 'Posting…' : 'Post commission'}
              </button>
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="post-commission-page">
      <div className="post-commission-page__step">
        <h1>Post a new commission</h1>
        <p className="post-commission-page__subtitle">Posting as [Council 1]</p>

        <form className="page-card post-commission-page__form" onSubmit={handleNextFromDetails} noValidate>
          <div className="form-field">
            <label htmlFor="title">Project title</label>
            <input
              id="title"
              type="text"
              placeholder="e.g. Skate Park Design Consultation"
              value={form.title}
              onChange={(event) => updateField('title', event.target.value)}
              aria-invalid={Boolean(errors.title)}
              aria-describedby={errors.title ? 'title-error' : undefined}
            />
            {errors.title && (
              <p className="form-field__error" id="title-error">
                {errors.title}
              </p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="description">Description</label>
            <textarea
              id="description"
              rows={3}
              placeholder="What do you need feedback or evidence on?"
              value={form.description}
              onChange={(event) => updateField('description', event.target.value)}
            />
          </div>

          <div className="form-row">
            <div className="form-field">
              <label htmlFor="incentive">Incentive</label>
              <input
                id="incentive"
                type="number"
                min="0"
                placeholder="e.g. 3,200"
                value={form.incentive}
                onChange={(event) => updateField('incentive', event.target.value)}
                aria-invalid={Boolean(errors.incentive)}
                aria-describedby={errors.incentive ? 'incentive-error' : undefined}
              />
              {errors.incentive && (
                <p className="form-field__error" id="incentive-error">
                  {errors.incentive}
                </p>
              )}
            </div>
            <div className="form-field">
              <label htmlFor="deadline">Response deadline</label>
              <select
                id="deadline"
                value={form.deadline}
                onChange={(event) => updateField('deadline', event.target.value)}
              >
                {deadlineOptions.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="post-commission-page__tier-notice">
            <span aria-hidden="true">&#9432;</span>
            <p>
              Data sensitivity tier is assigned automatically. VCNITY&rsquo;s AI will classify the appropriate tier
              based on your project description and the type of community groups involved. You will be notified of
              the assigned tier before the commission goes live.
            </p>
          </div>

          <div className="form-field">
            <label>Report format you&rsquo;d like back</label>
            <PillOptions
              options={reportFormatOptions}
              value={form.reportFormats}
              multi
              onChange={(value) => updateField('reportFormats', value)}
            />
          </div>

          <div className="post-commission-page__actions">
            <button type="button" className="btn btn--secondary" onClick={handleSaveDraft}>
              Save as draft
            </button>
            <button type="submit" className="btn btn--primary">
              Next: find groups &rsaquo;
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
