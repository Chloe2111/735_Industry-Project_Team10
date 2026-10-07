/**
 * The three data tiers: the one place their names and descriptions are written down.
 *
 * Used by the consent form (Story 22) and the community submission screens (Story 24).
 * The backend deals in tier numbers only, so changing the wording here changes it everywhere.
 */
export const TIER_OPTIONS = [
  {
    value: 1,
    label: 'General feedback',
    description: 'Nothing personal or identifying in what I shared.',
  },
  {
    value: 2,
    label: 'Personal or sensitive',
    description: 'Includes my name, a personal story, or identifying details.',
  },
  {
    value: 3,
    label: 'Culturally restricted',
    description:
      'Includes cultural knowledge, sacred content, or anything that should never leave the community.',
  },
]

/** "Personal or sensitive" for 2; "Unknown" for a number that is not a tier. */
export function tierLabel(tier) {
  return TIER_OPTIONS.find((option) => option.value === tier)?.label ?? 'Unknown'
}

/** "Tier 2 – Personal or sensitive", or "Not yet classified" when there is no tier. */
export function tierName(tier) {
  return tier == null ? 'Not yet classified' : `Tier ${tier} – ${tierLabel(tier)}`
}
