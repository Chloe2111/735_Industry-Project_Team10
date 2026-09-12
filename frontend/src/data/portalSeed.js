export const account = {
  fullName: 'Jordan Mitchell',
  initials: 'JM',
  email: 'jordan.mitchell@council1.nsw.gov.au',
  phone: '+61 2 9000 0001',
  organisation: '[Council 1]',
  role: 'Commissioning officer',
}

export const commissions = [
  {
    id: 'skate-park-design-consultation',
    title: 'Skate Park Design Consultation',
    assignedGroup: '[Group 2]',
    incentive: 3200,
    tier: 'Tier 1',
    status: 'IN_PROGRESS',
  },
  {
    id: 'youth-space-ideas',
    title: 'Youth Space Ideas',
    assignedGroup: null,
    incentive: 2400,
    tier: 'Tier 1',
    status: 'OPEN',
  },
  {
    id: 'multicultural-family-services-review',
    title: 'Multicultural Family Services Review',
    assignedGroup: null,
    incentive: 4000,
    tier: 'Tier 2',
    status: 'OPEN',
  },
]

export const dashboardStats = {
  openCommissions: 2,
  inProgress: 1,
  reportsAwaitingReview: 1,
  reportsReceivedTotal: 5,
}

export const recentActivity = [
  {
    id: 'activity-1',
    title: 'Report received — Skate Park Design Consultation',
    subtitle: '[Group 2] submitted the final report',
    timestamp: '2 days ago',
    to: '/reports-received/skate-park-design-consultation',
  },
  {
    id: 'activity-2',
    title: 'Evidence submitted — Youth Space Ideas',
    subtitle: '[Group 4] uploaded a survey batch (61 responses)',
    timestamp: '4 days ago',
    to: '/my-commissions',
  },
  {
    id: 'activity-3',
    title: 'Flag resolved — Multicultural Family Services Review',
    subtitle: "Facilitator clarified 1 finding before reporting",
    timestamp: '6 days ago',
    to: '/my-commissions',
  },
  {
    id: 'activity-4',
    title: 'Commission posted — Youth Space Ideas',
    subtitle: 'Open to Youth groups · Tier 1',
    timestamp: '1 week ago',
    to: '/my-commissions',
  },
  {
    id: 'activity-5',
    title: 'Commission assigned — Skate Park Design Consultation',
    subtitle: '[Group 2] applied and was assigned',
    timestamp: '2 weeks ago',
    to: '/my-commissions',
  },
]

export const reports = [
  {
    id: 'skate-park-design-consultation',
    project: 'Skate Park Design Consultation',
    group: '[Group 2]',
    reportingPeriod: '12–26 Aug 2026',
    status: 'FINAL',
  },
  {
    id: 'youth-space-ideas',
    project: 'Youth Space Ideas',
    group: '[Group 4]',
    reportingPeriod: 'In progress',
    status: 'DRAFT',
  },
]

export const reportDetails = {
  'skate-park-design-consultation': {
    id: 'skate-park-design-consultation',
    title: 'Skate Park Design Consultation',
    subtitle: 'A community engagement report prepared by [Group 2] for [Council 1], delivered through the VCNITY platform.',
    badges: ['AI-assisted', 'Human-reviewed'],
    preparedFor: '[Council 1]',
    date: '26 August 2026',
    file: 'vcnity-rpt-com-047-final',
    purpose:
      "This report presents findings from a commissioned community engagement on the proposed redesign of a local skate park. The commissioning body required feedback from young people aged 12–25 on safety, accessibility, and design preferences. All findings are de-identified and were reviewed by the group's facilitator before submission. No raw recordings, photographs, or identifying information are included.",
    stats: [
      { label: 'Participants', value: '47' },
      { label: 'Support for redesign proposal', value: '67%' },
      { label: 'Engagement hours contributed', value: '112' },
    ],
    findingsIntro:
      'Each finding below follows the commissioned report structure: commissioning body, research question, method, analysis approach, and deliverable status. AI confidence is noted where a finding was flagged. The facilitator resolved all flags before this report was released.',
    findings: [
      {
        id: 'finding-1',
        title: 'Safety and accessibility of the skate area',
        commissioningBody: '[Council 1] — Parks and Recreation Division',
        deliveredBy: '[Group 2], on behalf of its members, facilitated by VCNITY',
        researchQuestion:
          'What do young people aged 12–25 need from the proposed skate park redesign, and what barriers currently affect their safe and accessible use of the space?',
        method:
          'Three-method engagement running 12–26 August 2026: an in-person drop-in session (Thursday afternoon, 14 sticky-note wall photos captured), facilitator-led individual interviews (audio transcribed, 3 participants), and an online survey (61 responses, multiple choice and free text). Total participants: 47.',
        analysis:
          'Thematic coding applied to qualitative responses; survey data aggregated with frequency counts. AI flagged one quote for potential identification — facilitator reviewed and paraphrased before submission.',
        deliverableStatus: 'Complete — all flags resolved',
        sourceCount: 5,
      },
      {
        id: 'finding-2',
        title: 'Design preferences and feature priorities',
        commissioningBody: '[Council 1] — Parks and Recreation Division',
        deliveredBy: '[Group 2], on behalf of its members, facilitated by VCNITY',
        researchQuestion:
          'Which design features (ramps, rails, bowls, lighting, seating) matter most to prospective users, and how should limited budget be prioritised?',
        method:
          'Survey ranking question (61 responses) plus drop-in session sticky-note votes (14 photographed boards).',
        analysis:
          'Frequency-ranked feature list cross-checked against drop-in votes; no identification flags raised.',
        deliverableStatus: 'Complete — no flags raised',
        sourceCount: 8,
      },
      {
        id: 'finding-3',
        title: 'Inclusivity and representation gaps',
        commissioningBody: '[Council 1] — Parks and Recreation Division',
        deliveredBy: '[Group 2], on behalf of its members, facilitated by VCNITY',
        researchQuestion:
          'Which groups of young people are under-represented in this engagement, and what would improve access for them?',
        method:
          'Cross-tabulation of survey respondent demographics against the local youth population profile; interview notes reviewed for access barriers raised.',
        analysis:
          'Gap identified for younger (12–14) and female respondents; one interview quote flagged for potential identification and paraphrased by the facilitator before inclusion.',
        deliverableStatus: 'Complete — all flags resolved',
        sourceCount: 3,
      },
    ],
  },
}

export const notifications = [
  {
    id: 'notif-1',
    title: 'Report received — Skate Park Design Consultation',
    subtitle: '[Group 2] submitted the final report',
    timestamp: '2 days ago',
    unread: true,
    to: '/reports-received/skate-park-design-consultation',
  },
  {
    id: 'notif-2',
    title: 'Flag resolved — Multicultural Family Services Review',
    subtitle: 'Facilitator clarified 1 finding before reporting',
    timestamp: '6 days ago',
    unread: true,
    to: '/my-commissions',
  },
  {
    id: 'notif-3',
    title: 'Commission assigned — Skate Park Design Consultation',
    subtitle: '[Group 2] applied and was assigned',
    timestamp: '2 weeks ago',
    unread: false,
    to: '/my-commissions',
  },
]

export const groupOptions = [
  'Youth groups',
  'Cultural & multicultural groups',
  'Environmental & landcare groups',
  'Neighbourhood associations',
  'Open to any group type',
]

export const reportFormatOptions = [
  'Executive summary',
  'Detailed thematic analysis',
  'Supporting quotes included',
  'Data tables & statistics',
]

export const deadlineOptions = [
  '4 weeks from posting',
  '8 weeks from posting',
  '12 weeks from posting',
]

export const tierOptions = [
  { value: 'Tier 1', label: 'Tier 1 — public feedback' },
  { value: 'Tier 2', label: 'Tier 2 — includes personal information' },
  { value: 'Tier 3', label: 'Tier 3 — sensitive or vulnerable groups' },
]
