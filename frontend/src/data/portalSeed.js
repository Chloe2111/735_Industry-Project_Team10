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
        sources: [
          {
            id: 'SRV-014',
            type: 'Survey response',
            method: 'Online survey',
            quote: "The ramp near the entrance has a crack that I've tripped on twice. It needs fixing before anything else.",
            agreementPercent: 78,
            participantsAgreed: 37,
            participantsTotal: 47,
            flagged: false,
            ageRange: '14–22 years old',
            genderBreakdown: [
              { label: 'male', percent: 58 },
              { label: 'female', percent: 34 },
              { label: 'non-binary', percent: 8 },
            ],
            culturalBackgrounds: ['Anglo-Australian', 'East Asian', 'South Asian', 'Pacific Islander'],
            whyItMatters:
              'Physical safety hazards were the single most cited barrier to park use. This quote captures a direct, recurring structural risk mentioned across 37 of 47 participants. It directly supports the finding’s recommendation for infrastructure audit as the first priority.',
            themes: ['Safety hazard', 'Infrastructure', 'Urgency'],
          },
          {
            id: 'SRV-031',
            type: 'Survey response',
            method: 'Online survey',
            quote: 'Lighting is really bad at night — feels unsafe after 6pm, especially in winter.',
            agreementPercent: 64,
            participantsAgreed: 30,
            participantsTotal: 47,
            flagged: false,
            ageRange: '15–24 years old',
            genderBreakdown: [
              { label: 'male', percent: 41 },
              { label: 'female', percent: 52 },
              { label: 'non-binary', percent: 7 },
            ],
            culturalBackgrounds: ['Anglo-Australian', 'South Asian', 'Middle Eastern'],
            whyItMatters:
              'Lighting concerns were consistently linked to evening and winter use, pointing to a distinct safety gap from the entrance-ramp issue above. Supports a secondary recommendation on lighting upgrades.',
            themes: ['Safety hazard', 'Lighting', 'Evening access'],
          },
          {
            id: 'INT-002',
            type: 'Interview transcript',
            method: 'Individual interview',
            quote:
              '[Paraphrased by facilitator] Participant described feeling excluded from the main bowl area due to skill level and perceived social dynamics.',
            agreementPercent: 53,
            participantsAgreed: 25,
            participantsTotal: 47,
            flagged: true,
            ageRange: '16–19 years old',
            genderBreakdown: [
              { label: 'male', percent: 30 },
              { label: 'female', percent: 62 },
              { label: 'non-binary', percent: 8 },
            ],
            culturalBackgrounds: ['Anglo-Australian', 'East Asian'],
            whyItMatters:
              'Social-inclusion barriers were harder to surface than physical ones and only emerged through interview, not survey, responses. AI flagged the original quote as potentially identifying; the facilitator paraphrased it before inclusion.',
            themes: ['Inclusivity', 'Skill level', 'Social dynamics'],
          },
          {
            id: 'OBS-007',
            type: 'Sticky-note wall',
            method: 'In-person drop-in',
            quote: '"No helmet rack" — written by participant during drop-in session.',
            agreementPercent: 47,
            participantsAgreed: 22,
            participantsTotal: 47,
            flagged: false,
            ageRange: '12–20 years old',
            genderBreakdown: [
              { label: 'male', percent: 55 },
              { label: 'female', percent: 40 },
              { label: 'non-binary', percent: 5 },
            ],
            culturalBackgrounds: ['Anglo-Australian', 'Pacific Islander'],
            whyItMatters:
              'A small but recurring amenity gap raised independently across multiple sticky notes. Included as a low-cost, high-visibility fix alongside the larger infrastructure recommendations.',
            themes: ['Amenity', 'Infrastructure'],
          },
          {
            id: 'SRV-058',
            type: 'Survey response',
            method: 'Online survey',
            quote: 'Wheelchair access at the side gate is blocked by a permanent barrier. Nobody seems to know who put it there.',
            agreementPercent: 38,
            participantsAgreed: 18,
            participantsTotal: 47,
            flagged: false,
            ageRange: '17–25 years old',
            genderBreakdown: [
              { label: 'male', percent: 44 },
              { label: 'female', percent: 50 },
              { label: 'non-binary', percent: 6 },
            ],
            culturalBackgrounds: ['Anglo-Australian', 'South Asian'],
            whyItMatters:
              'A smaller group raised this, but it describes an outright access barrier rather than a preference, which is why it is retained as a distinct accessibility finding rather than folded into the general safety theme.',
            themes: ['Accessibility', 'Infrastructure'],
          },
        ],
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
        sources: [],
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
        sources: [],
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

export const ageGroupOptions = ['Under 18', '18–25', '26–40', '41–60', 'Over 60', 'All ages']

export const topicAreaOptions = [
  'Urban planning & infrastructure',
  'Environment & green spaces',
  'Arts, culture & heritage',
  'Community services & wellbeing',
  'Youth, education & development',
  'Health & safety',
]

export const communityVoiceOptions = [
  'General public opinion',
  'Lived experience from specific groups',
  "Young people's perspectives",
  'Culturally diverse voices',
  'Environmental advocates',
  'Any / open to all',
]

export const geographicScopeOptions = [
  'A specific local neighbourhood',
  'City-wide',
  'Regional or state-wide',
  'No geographic restriction',
]

// The 4 shown as "AI-suggested" in the design are first; the rest exist so
// the "Add more groups" search in step 3 has somewhere to look.
export const groupCatalog = [
  {
    id: 'youth-design-collective',
    name: 'Youth Design Collective',
    matchPercent: 97,
    reason: 'Specialises in youth-led urban design input. Age range 16-25 matches your target cohort.',
  },
  {
    id: 'green-spaces-initiative',
    name: 'Green Spaces Initiative',
    matchPercent: 84,
    reason: 'Covers outdoor recreation and infrastructure — relevant to skate park context.',
  },
  {
    id: 'multicultural-community-alliance',
    name: 'Multicultural Community Alliance',
    matchPercent: 71,
    reason: 'Provides culturally diverse voices, particularly for public space inclusion.',
  },
  {
    id: 'northside-neighbourhood-network',
    name: 'Northside Neighbourhood Network',
    matchPercent: 63,
    reason: 'Local residents group near the proposed site. Geographically relevant.',
  },
  {
    id: 'river-care-volunteers',
    name: 'River Care Volunteers',
    matchPercent: 58,
    reason: 'Environmental landcare group active in nearby waterway corridors.',
  },
  {
    id: 'elders-advisory-circle',
    name: 'Elders Advisory Circle',
    matchPercent: 55,
    reason: 'Provides intergenerational perspective on shared community spaces.',
  },
  {
    id: 'accessible-city-network',
    name: 'Accessible City Network',
    matchPercent: 52,
    reason: 'Advocates for accessibility in public infrastructure projects.',
  },
  {
    id: 'local-business-forum',
    name: 'Local Business Forum',
    matchPercent: 44,
    reason: 'Represents nearby traders with an interest in foot traffic and amenity.',
  },
]
