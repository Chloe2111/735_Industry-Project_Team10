import { isRenderableFinding } from '../pages/CodingFindingsPage'

// The live "Community ideas" report shown in Reports received.
//
// It is built from published findings only: ideas that passed the tier gate,
// de-identification and AI coding, and -- when anything was flagged -- were
// approved by a facilitator. Rejected or still-pending ideas are never published,
// so they never appear here. Not yet split per commission (Semester 2).
export const COMMUNITY_REPORT_ID = 'community-ideas'

const percent = (value) => `${Math.round(value * 100)}%`

function today() {
  return new Date().toLocaleDateString('en-AU', { day: 'numeric', month: 'long', year: 'numeric' })
}

/** Groups findings by theme: largest theme first, strongest quote first. */
export function groupByTheme(findings) {
  const groups = new Map()
  for (const finding of findings) {
    const key = finding.theme.trim()
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(finding)
  }
  return [...groups.entries()]
    .map(([theme, items]) => ({
      theme,
      items: [...items].sort((a, b) => b.confidence - a.confidence),
      averageConfidence: items.reduce((sum, item) => sum + item.confidence, 0) / items.length,
    }))
    .sort((a, b) => b.items.length - a.items.length || a.theme.localeCompare(b.theme))
}

/** The row shown in the Reports received table. */
export function buildCommunityReportSummary(rawFindings) {
  const findings = rawFindings.filter(isRenderableFinding)
  return {
    id: COMMUNITY_REPORT_ID,
    project: 'Community ideas',
    group: 'Community members',
    reportingPeriod: findings.length > 0 ? `Live · updated ${today()}` : 'Waiting for approved ideas',
    status: findings.length > 0 ? 'FINAL' : 'DRAFT',
  }
}

/** The full report, in the same shape ReportDetailPage already renders. Null when nothing is published. */
export function buildCommunityReportDetail(rawFindings) {
  const findings = rawFindings.filter(isRenderableFinding)
  if (findings.length === 0) return null

  const themes = groupByTheme(findings)
  const ideaCount = new Set(findings.map((finding) => finding.sourceRef)).size
  const reviewedCount = findings.filter((finding) => finding.flags.length > 0).length
  let quoteNumber = 0

  return {
    id: COMMUNITY_REPORT_ID,
    title: 'Community ideas report',
    subtitle:
      'Themes from ideas community members submitted through the VCNITY platform, after the sensitivity tier gate, de-identification, AI coding and facilitator review.',
    badges: ['AI-assisted', 'Facilitator-verified', 'De-identified'],
    preparedFor: '[Council 1]',
    date: today(),
    file: 'vcnity-community-ideas-live',
    purpose:
      'This report brings together the ideas community members shared through "Submit an Idea". Every idea was checked against its group’s sensitivity tier, de-identified before any AI processing, and coded into themes by AI. Anything the AI was unsure about was reviewed and approved by a facilitator before it could appear here. Quotes are the de-identified text; no names, contact details or raw submissions are shown.',
    stats: [
      { label: 'Ideas published', value: String(ideaCount) },
      { label: 'Themes', value: String(themes.length) },
      { label: 'Supporting quotes', value: String(findings.length) },
      { label: 'Facilitator-reviewed', value: String(reviewedCount) },
    ],
    findingsIntro:
      'Each theme below lists the de-identified quotes that support it. AI confidence is shown for every quote; quotes the AI flagged were approved by a facilitator before release.',
    withheldFindingsCount: 0,
    findings: themes.map((group, index) => {
      const flagged = group.items.filter((item) => item.flags.length > 0).length
      return {
        id: `community-theme-${index + 1}`,
        title: group.theme,
        commissioningBody: '[Council 1]',
        deliveredBy: 'Community members, via the VCNITY platform',
        researchQuestion: 'Ideas shared by community members through “Submit an Idea”.',
        method:
          'Written ideas submitted in the community portal. Each passed the sensitivity tier gate and de-identification before AI coding.',
        analysis: `AI thematic coding, average confidence ${percent(group.averageConfidence)}. ${
          flagged === 0 ? 'No quotes needed facilitator review.' : `${flagged} of ${group.items.length} quote${group.items.length === 1 ? '' : 's'} flagged by the AI and approved by a facilitator.`
        }`,
        deliverableStatus: 'Complete — all flags resolved by a facilitator',
        sourceCount: group.items.length,
        sources: group.items.map((item) => {
          quoteNumber += 1
          return {
            id: `Q-${String(quoteNumber).padStart(3, '0')}`,
            type: 'Community idea',
            method: 'Submit an Idea',
            quote: item.quote,
            flagged: item.flags.length > 0,
            aiConfidence: Math.round(item.confidence * 100),
            tier: item.tier,
            themes: [group.theme],
            whyItMatters: item.flags.length > 0
              ? `Supports “${group.theme}”. The AI flagged it for review and a facilitator approved it.`
              : `Supports “${group.theme}”.`,
          }
        }),
      }
    }),
  }
}
