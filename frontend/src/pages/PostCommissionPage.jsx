import { useState } from 'react'
import { Link } from 'react-router-dom'
import { deadlineOptions, groupOptions, reportFormatOptions, tierOptions } from '../data/portalSeed'
import { createCommission } from '../services/portalService'
import { useToast } from '../components/Toast/ToastProvider'
import './PostCommissionPage.css'

const INITIAL_FORM = {
  title: '',
  description: '',
  incentive: '',
  deadline: deadlineOptions[deadlineOptions.length - 1],
  tier: tierOptions[0].value,
  groups: ['Youth groups'],
  reportFormats: ['Executive summary', 'Detailed thematic analysis'],
}

function toggleValue(list, value) {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
}

export function PostCommissionPage() {
  const toast = useToast()
  const [form, setForm] = useState(INITIAL_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [posted, setPosted] = useState(false)

  function updateField(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  function handleSaveDraft() {
    toast.success('Draft saved.')
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    try {
      await createCommission({
        title: form.title,
        incentive: Number(form.incentive) || 0,
        tier: form.tier,
      })
      setPosted(true)
    } catch {
      toast.error('Could not post the commission. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  function handlePostAnother() {
    setForm(INITIAL_FORM)
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

  return (
    <div className="post-commission-page">
      <h1>Post a new commission</h1>
      <p className="post-commission-page__subtitle">Posting as [Council 1]</p>

      <form className="page-card post-commission-page__form" onSubmit={handleSubmit}>
        <div className="form-field">
          <label htmlFor="title">Project title</label>
          <input
            id="title"
            type="text"
            placeholder="e.g. Skate Park Design Consultation"
            value={form.title}
            onChange={(event) => updateField('title', event.target.value)}
            required
          />
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
            />
          </div>
          <div className="form-field">
            <label htmlFor="deadline">Response deadline</label>
            <select id="deadline" value={form.deadline} onChange={(event) => updateField('deadline', event.target.value)}>
              {deadlineOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
        </div>

        <fieldset className="form-field">
          <legend>Requested data sensitivity tier</legend>
          <p className="post-commission-page__tier-hint">
            The tier is what you&rsquo;re asking for. A group&rsquo;s own default tier can never be lowered by this
            request — only matched, or protected further.
          </p>
          {tierOptions.map((option) => (
            <label key={option.value} className="radio-option">
              <input
                type="radio"
                name="tier"
                value={option.value}
                checked={form.tier === option.value}
                onChange={(event) => updateField('tier', event.target.value)}
              />
              {option.label}
            </label>
          ))}
        </fieldset>

        <div className="form-row form-row--checkboxes">
          <fieldset className="form-field">
            <legend>Which groups should see this?</legend>
            {groupOptions.map((option) => (
              <label key={option} className="checkbox-option">
                <input
                  type="checkbox"
                  checked={form.groups.includes(option)}
                  onChange={() => updateField('groups', toggleValue(form.groups, option))}
                />
                {option}
              </label>
            ))}
          </fieldset>

          <fieldset className="form-field">
            <legend>Report format you&rsquo;d like back</legend>
            {reportFormatOptions.map((option) => (
              <label key={option} className="checkbox-option">
                <input
                  type="checkbox"
                  checked={form.reportFormats.includes(option)}
                  onChange={() => updateField('reportFormats', toggleValue(form.reportFormats, option))}
                />
                {option}
              </label>
            ))}
          </fieldset>
        </div>

        <div className="post-commission-page__actions">
          <button type="button" className="btn btn--secondary" onClick={handleSaveDraft}>
            Save as draft
          </button>
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {submitting ? 'Posting…' : 'Post commission'}
          </button>
        </div>
      </form>
    </div>
  )
}
