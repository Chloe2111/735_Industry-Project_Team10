import { useCallback, useEffect, useState } from 'react'
import SubmissionForm from '../components/CommunitySubmission/SubmissionForm'
import SubmissionReceipt from '../components/CommunitySubmission/SubmissionReceipt'
import GroupTierPanel from '../components/CommunitySubmission/GroupTierPanel'
import { tierName } from '../constants/tiers'
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
 *
 * What is published is the de-identified text: detected names and contact details arrive already
 * replaced by [REDACTED:...] tags, shown here as "[removed]".
 * A group that is Tier 3 shows no posts at all, including posts published before it became Tier 3.
 */
const REDACTION_TAG = /\[REDACTED:[^\]]+\]/g

export function displayText(text) {
  return (text ?? '').replace(REDACTION_TAG, '[removed]')
}

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

  // Loads the group's posts together with the latest tiers, so a group that someone has just
  // moved to Tier 3 is shown as restricted rather than as "nothing published yet".
  async function loadPublished(groupId) {
    const [posts] = await Promise.all([listPublished(groupId).catch(() => []), loadGroups()])
    setPublished(posts)
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
      const hidden = result.publishedToRecheck ?? 0
      toast.success(
        `${result.group.groupName || result.group.groupId} is now ${tierName(result.group.tier)}.` +
          (halted > 0
            ? ` ${halted} waiting or rejected submission${halted === 1 ? ' was' : 's were'} halted for re-check.`
            : '') +
          (hidden > 0 ? ` ${hidden} published post${hidden === 1 ? ' is' : 's are'} now hidden.` : ''),
      )
      // The change may have hidden (or brought back) the posts shown below, and may have changed
      // what happened to the submission on the confirmation card.
      if (receipt) {
        await loadPublished(receipt.groupIds[0])
        try {
          setReceipt(await getSubmission(receipt.id))
        } catch {
          // keep the card as it is; "Check status" or a new submission will refresh it
        }
      } else {
        await loadGroups()
      }
      return true
    } catch (err) {
      toast.error(err.message || 'Unable to set the tier.')
      // 409: someone else changed this group first. Show what it is now before they try again.
      if (err.status === 409) await loadGroups()
      return false
    } finally {
      setSavingTier(false)
    }
  }

  // Show the name a coordinator typed (e.g. "Youth Group A") rather than the stored id.
  const groupLabel = (groupId) => groups.find((g) => g.groupId === groupId)?.groupName || groupId
  const shownGroupIsRestricted =
    receipt != null && groups.some((g) => g.groupId === receipt.groupIds[0] && g.tier === 3)

  return (
    <main className="submission-page">
      <header className="submission-page__header">
        <p className="submission-page__eyebrow">Community</p>
        <h1>Share feedback</h1>
        <p>
          Your feedback is checked before anything is published to the group. Names and contact details that
          are detected are removed.
        </p>
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
          {shownGroupIsRestricted ? (
            <p className="published-list__empty" role="note">
              This group is Tier 3, so nothing is shown here. Posts published before it became Tier 3 are hidden
              until a person re-checks them.
            </p>
          ) : published.length === 0 ? (
            <p className="published-list__empty">Nothing has been published for this group yet.</p>
          ) : (
            <ul>
              {published.map((item) => (
                <li key={item.id}>{displayText(item.text)}</li>
              ))}
            </ul>
          )}
        </section>
      )}

      <GroupTierPanel groups={groups} onSetTier={handleSetTier} saving={savingTier} />
    </main>
  )
}
