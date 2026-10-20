import { useState } from 'react'
import { TIER_OPTIONS, tierName } from '../../constants/tiers'
import { normaliseGroupId } from '../../services/intakeApi'

/**
 * Lets a coordinator set or change a group's tier, and lists the groups classified so far.
 *
 * Only a person sets a tier, so a name and a short reason are required every time.
 * Each change carries the version of the group that was on screen, so two coordinators
 * editing the same group cannot overwrite each other without noticing.
 * This panel is separate from the submission form and can be removed from the page
 * without affecting it.
 */
export default function GroupTierPanel({ groups = [], onSetTier, saving = false }) {
  const [group, setGroup] = useState('')
  const [tier, setTier] = useState('')
  const [setBy, setSetBy] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    if (!group.trim() || !tier || !setBy.trim() || !reason.trim()) {
      setError('Enter the group, a tier, your name and a short reason.')
      return
    }
    setError('')
    // Send the version of the group as it is shown in the table (null if it is not classified yet).
    // The backend refuses the change if someone else has changed the group since.
    const shown = groups.find((g) => g.groupId === normaliseGroupId(group))
    const saved = await onSetTier({
      groupId: group.trim(),
      tier: Number(tier),
      setBy: setBy.trim(),
      reason: reason.trim(),
      expectedVersion: shown ? shown.version : null,
    })
    if (saved) {
      setGroup('')
      setTier('')
      setReason('')
    }
  }

  return (
    <section className="tier-panel" aria-label="Group tiers">
      <h2>Group tiers</h2>
      <p className="tier-panel__intro">
        A group needs a tier before it can receive feedback. Only a person sets it.
      </p>

      {groups.length === 0 ? (
        <p className="tier-panel__empty">No group has been classified yet.</p>
      ) : (
        <div className="tier-panel__table-wrap">
          <table className="tier-panel__table">
            <thead>
              <tr>
                <th scope="col">Group</th>
                <th scope="col">Tier</th>
                <th scope="col">Set by</th>
                <th scope="col">Changes</th>
              </tr>
            </thead>
            <tbody>
              {groups.map((g) => (
                <tr key={g.groupId}>
                  <td>{g.groupName || g.groupId}</td>
                  <td>{tierName(g.tier)}</td>
                  <td>{g.setBy}</td>
                  <td>{g.history?.length ?? 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <form className="tier-panel__form" onSubmit={handleSubmit} noValidate>
        <div className="form-row">
          <div className="form-field">
            <label htmlFor="tier-group">Group to classify</label>
            <input id="tier-group" type="text" value={group} onChange={(e) => setGroup(e.target.value)} />
          </div>
          <div className="form-field">
            <label htmlFor="tier-value">Tier</label>
            <select id="tier-value" value={tier} onChange={(e) => setTier(e.target.value)}>
              <option value="">Choose a tier</option>
              {TIER_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.value} &ndash; {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="form-row">
          <div className="form-field">
            <label htmlFor="tier-set-by">Set by (your name)</label>
            <input id="tier-set-by" type="text" value={setBy} onChange={(e) => setSetBy(e.target.value)} />
          </div>
          <div className="form-field">
            <label htmlFor="tier-reason">Reason</label>
            <input
              id="tier-reason"
              type="text"
              value={reason}
              maxLength={500}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
        </div>
        {error && (
          <p className="form-field__error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="btn btn--secondary" disabled={saving}>
          {saving ? 'Saving…' : 'Set tier'}
        </button>
      </form>
    </section>
  )
}
