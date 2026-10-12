import { useCallback, useEffect, useState } from 'react'
import SubmissionForm from '../components/CommunitySubmission/SubmissionForm'
import SubmissionReceipt from '../components/CommunitySubmission/SubmissionReceipt'
import GroupTierPanel from '../components/CommunitySubmission/GroupTierPanel'
import { tierName } from '../components/CommunitySubmission/tiers'
import {
  getSubmission,
  listGroupTiers,
  listPublished,
  setGroupTier,
  submitFeedback,
} from '../services/intakeApi'
import { useToast } from '../components/Toast/ToastProvider'
import '../components/CommunitySubmission/CommunitySubmission.css'

/**
 * CommunitySubmissionPage (Story 24)
 *
 * 24.2: wires the submission form to POST /api/intake/submissions.
 * 24.3: shows the assigned tier back to the submitter (SubmissionReceipt).
 *
 * Nothing is published by this page itself. The backend decides: clean feedback is
 * published, flagged feedback waits for a reviewer, Tier 3 feedback is held, and a
 * group with no tier is refused.
 */
export default function CommunitySubmissionPage() {
  const toast = useToast()
  const [groups, setGroups] = useState([])
  const [receipt, setReceipt] = useState(null)
  const [published, setPublished] = useState([])
  const [refusal, setRefusal] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [savingTier, setSavingTier] = useState(false)

  const loadGroups = useCallback(async () => {
    try {
      setGroups(await listGroupTiers())
    } catch (err) {
      toast.error(err.message || 'Unable to load groups.')
    }
  }, [toast])

  useEffect(() => {
    loadGroups()
  }, [loadGroups])

  async function loadPublished(groupId) {
    try {
      setPublished(await listPublished(groupId))
    } catch {
      setPublished([])
    }
  }

  // Returns true when the backend accepted and recorded the submission.
  async function handleSubmit(payload) {
    setSubmitting(true)
    setRefusal('')
    try {
      const result = await submitFeedback(payload)
      setReceipt(result)
      // The confirmation card below carries the full message; a toast is only added for the happy path.
      if (result.status === 'PUBLISHED') toast.success('Feedback published.')
      await loadPublished(result.groupIds[0])
      return true
    } catch (err) {
      setReceipt(null)
      setRefusal(err.message || 'Something went wrong. Your feedback was not submitted.')
      return false
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRefresh() {
    if (!receipt) return
    setRefreshing(true)
    try {
      const latest = await getSubmission(receipt.id)
      setReceipt(latest)
      await loadPublished(latest.groupIds[0])
    } catch (err) {
      toast.error(err.message || 'Unable to check the status.')
    } finally {
      setRefreshing(false)
    }
  }

  // Returns true when the tier was saved.
  async function handleSetTier(body) {
    setSavingTier(true)
    try {
      const result = await setGroupTier(body)
      const halted = result.haltedSubmissionIds.length
      toast.success(
        `${result.group.groupName || result.group.groupId} is now ${tierName(result.group.tier)}.` +
          (halted > 0 ? ` ${halted} queued submission${halted === 1 ? ' was' : 's were'} halted for re-check.` : ''),
      )
      await loadGroups()
      return true
    } catch (err) {
      toast.error(err.message || 'Unable to set the tier.')
      return false
    } finally {
      setSavingTier(false)
    }
  }

  // Show the name a coordinator typed (e.g. "Youth Group A") rather than the stored id.
  const groupLabel = (groupId) => groups.find((g) => g.groupId === groupId)?.groupName || groupId

  return (
    <main className="submission-page">
      <header className="submission-page__header">
        <p className="submission-page__eyebrow">Community</p>
        <h1>Share feedback</h1>
        <p>Your feedback is checked before anything is published to the group.</p>
      </header>

      <div className="page-card submission-page__card">
        <SubmissionForm groups={groups} onSubmit={handleSubmit} submitting={submitting} />
        {refusal && (
          <p className="submission-page__refusal" role="alert">
            {refusal}
          </p>
        )}
      </div>

      <SubmissionReceipt
        receipt={receipt}
        groupLabel={groupLabel}
        onRefresh={handleRefresh}
        refreshing={refreshing}
      />

      {receipt && (
        <section className="published-list" aria-label="Published feedback">
          <h2>Published in {groupLabel(receipt.groupIds[0])}</h2>
          {published.length === 0 ? (
            <p className="published-list__empty">Nothing has been published for this group yet.</p>
          ) : (
            <ul>
              {published.map((item) => (
                <li key={item.id}>{item.text}</li>
              ))}
            </ul>
          )}
        </section>
      )}

      <GroupTierPanel groups={groups} onSetTier={handleSetTier} saving={savingTier} />
    </main>
  )
}
