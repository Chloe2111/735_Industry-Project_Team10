import { useState } from 'react'
import { tierName } from '../../constants/tiers'
import { normaliseGroupId } from '../../services/intakeApi'

const MAX_TEXT_LENGTH = 5000

/**
 * The single minimal submission form for Story 24: which group, and the feedback.
 *
 * The form never asks for a tier. The tier belongs to the group and is looked up
 * by the backend; here it is only shown as a hint so the person knows what to expect.
 */
export default function SubmissionForm({ groups = [], onSubmit, submitting = false }) {
  const [group, setGroup] = useState('')
  const [text, setText] = useState('')
  const [errors, setErrors] = useState({})

  const typedGroupId = normaliseGroupId(group)
  const knownGroup = groups.find((g) => g.groupId === typedGroupId)

  function validate() {
    const nextErrors = {}
    if (!group.trim()) {
      nextErrors.group = 'Please enter which group this is for.'
    }
    if (!text.trim()) {
      nextErrors.text = 'Please write your feedback before submitting.'
    } else if (text.trim().length > MAX_TEXT_LENGTH) {
      nextErrors.text = `Feedback must be ${MAX_TEXT_LENGTH} characters or fewer.`
    }
    setErrors(nextErrors)
    return Object.keys(nextErrors).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!validate()) return
    const accepted = await onSubmit({ groupIds: [group.trim()], text: text.trim() })
    if (accepted) setText('')
  }

  return (
    <form className="submission-form" onSubmit={handleSubmit} noValidate>
      <div className="form-field">
        <label htmlFor="submission-group">Which group is this for?</label>
        <input
          id="submission-group"
          type="text"
          list="submission-group-options"
          value={group}
          onChange={(e) => setGroup(e.target.value)}
          aria-invalid={Boolean(errors.group)}
          aria-describedby="submission-group-hint"
          autoComplete="off"
        />
        <datalist id="submission-group-options">
          {groups.map((g) => (
            <option key={g.groupId} value={g.groupName || g.groupId} />
          ))}
        </datalist>
        <p className="form-field__hint" id="submission-group-hint">
          {!typedGroupId && 'The group decides how your feedback is handled.'}
          {typedGroupId && knownGroup && `This group is ${tierName(knownGroup.tier)}.`}
          {typedGroupId && !knownGroup &&
            'This group has no tier yet. A coordinator has to classify it before feedback can be accepted.'}
        </p>
        {errors.group && (
          <p className="form-field__error" role="alert">
            {errors.group}
          </p>
        )}
      </div>

      <div className="form-field">
        <label htmlFor="submission-text">Your feedback</label>
        <textarea
          id="submission-text"
          rows={5}
          value={text}
          onChange={(e) => setText(e.target.value)}
          aria-invalid={Boolean(errors.text)}
        />
        {errors.text && (
          <p className="form-field__error" role="alert">
            {errors.text}
          </p>
        )}
      </div>

      <button type="submit" className="btn btn--primary" disabled={submitting}>
        {submitting ? 'Submitting…' : 'Submit feedback'}
      </button>
    </form>
  )
}
