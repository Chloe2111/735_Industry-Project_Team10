// Same wording as the consent form, so people see one set of names for the three tiers.
export const TIER_LABELS = {
  1: 'General feedback',
  2: 'Personal or sensitive',
  3: 'Culturally restricted',
}

export function tierName(tier) {
  return tier == null ? 'Not yet classified' : `Tier ${tier} – ${TIER_LABELS[tier] ?? 'Unknown'}`
}

/** Mirrors the backend rule: group names match regardless of case and extra spaces. */
export function normaliseGroupId(value) {
  return (value ?? '').trim().replace(/\s+/g, ' ').toLowerCase()
}
