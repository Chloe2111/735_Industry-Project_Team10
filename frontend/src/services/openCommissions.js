import { apiClient } from './apiClient'

// Sample commissions from the original design. They have no record in the database,
// so ideas sent from them are not linked to a commission (source: 'sample').
export const SAMPLE_COMMISSIONS = [
  {
    id: 'sample-skate-park',
    source: 'sample',
    title: 'Skate Park Design Consultation',
    organisation: 'Riverside City Council',
    category: 'Community Design',
    deadline: '31 May 2026',
    incentive: '$200 Community credit',
    description:
      'We want to create a skate park that encourages active lifestyles, creativity and community connection. ' +
      'Your ideas will help shape the design, features and overall experience.',
  },
  {
    id: 'sample-urban-greening',
    source: 'sample',
    title: 'Urban Greening Ideas',
    organisation: 'Queensland Government',
    category: 'Sustainability',
    deadline: '15 Nov 2026',
    incentive: '$500 Community credit',
    description: 'Share ideas for more trees, gardens and green spaces in your neighbourhood.',
  },
  {
    id: 'sample-riverside-spaces',
    source: 'sample',
    title: 'Riverside Public Spaces',
    organisation: 'Brisbane City Council',
    category: 'Infrastructure',
    deadline: '30 Nov 2026',
    incentive: '$450 Community credit',
    description: 'Help us improve the public spaces along the river for everyone who uses them.',
  },
  {
    id: 'sample-youth-programs',
    source: 'sample',
    title: 'Youth Engagement Programs',
    organisation: 'Queensland Government',
    category: 'Social Impact',
    deadline: '12 Dec 2026',
    incentive: '$300 Community credit',
    description: 'Tell us which programs would help young people in your area get involved.',
  },
]

function formatIncentive(value) {
  const amount = Number(value)
  return Number.isFinite(amount) ? `$${amount.toLocaleString('en-AU')}` : '—'
}

/** A commission posted by a client (GET /api/commissions), shaped for the community cards. */
export function toOpenCommission(commission) {
  return {
    id: commission.id,
    source: 'client',
    title: commission.title || 'Untitled commission',
    organisation: 'Posted by a client',
    category: 'New commission',
    deadline: commission.deadline || '—',
    incentive: formatIncentive(commission.incentive),
    description: commission.description || '',
  }
}

/**
 * Open commissions posted by clients, newest first, followed by the sample commissions.
 * If the backend can't be reached, only the samples are returned, with a message to show.
 */
export async function listOpenCommissions(options) {
  try {
    const data = await apiClient.get('/commissions', options)
    const open = (Array.isArray(data) ? data : [])
      .filter((item) => item?.id && (item.status == null || item.status === 'OPEN'))
      .map(toOpenCommission)
    return { commissions: [...open, ...SAMPLE_COMMISSIONS], loadError: '' }
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    return {
      commissions: SAMPLE_COMMISSIONS,
      loadError: "New commissions couldn't be loaded, so only sample commissions are shown.",
    }
  }
}
