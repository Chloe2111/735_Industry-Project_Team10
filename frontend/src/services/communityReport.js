import { isFacilitatorCoded, isRenderableFinding } from '../pages/CodingFindingsPage'

// The live "Community ideas" report shown in Reports received.
//
// It is built from published findings only: ideas that passed the tier gate,
// de-identification and AI coding, and -- when anything was flagged -- were
// approved by a facilitator. Rejected or still-pending ideas are never published,
// so they never appear here.
//
// Ideas sent from a client's commission get their own report ("<commission title>");
// ideas not linked to a commission (sample commissions, older ideas) stay in "Community ideas".
export const COMMUNITY_REPORT_ID = 'community-ideas'
export const COMMISSION_REPORT_PREFIX = 'commission-ideas-'

export function commissionReportId(commissionId) {
  return `${COMMISSION_REPORT_PREFIX}${commissionId}`
}

/** The commission id in a per-commission report id, or null for any other report. */
export function commissionIdFromReportId(reportId) {
  return typeof reportId === 'string' && reportId.startsWith(COMMISSION_REPORT_PREFIX)
    ? reportId.slice(COMMISSION_REPORT_PREFIX.length) || null
    : null
}

/**
 * Splits findings into the ideas for each known commission and the rest.
 * An idea whose commission no longer exists falls back to the general report.
 */
export function splitByCommission(findings, commissions) {
  const known = new Set((commissions ?? []).map((commission) => commission.id))
  const byCommission = new Map()
  const general = []
  for (const finding of findings) {
    if (finding.commissionId && known.has(finding.commissionId)) {
      if (!byCommission.has(finding.commissionId)) byCommission.set(finding.commissionId, [])
      byCommission.get(finding.commissionId).push(finding)
    } else {
      general.push(finding)
    }
  }
  return { byCommission, general }
}

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
    .map(([theme, items]) => {
      // Facilitator-coded quotes have no AI confidence, so they are left out of the average.
      const aiItems = items.filter((item) => !isFacilitatorCoded(item))
      return {
        theme,
        items: [...aiItems].sort((a, b) => b.confidence - a.confidence)
          .concat(items.filter((item) => isFacilitatorCoded(item))),
        averageConfidence: aiItems.length > 0
          ? aiItems.reduce((sum, item) => sum + item.confidence, 0) / aiItems.length
          : null,
        facilitatorCoded: items.length - aiItems.length,
      }
    })
    .sort((a, b) => b.items.length - a.items.length || a.theme.localeCompare(b.theme))
}

/** The row shown in the Reports received table. */
export function buildCommunityReportSummary(rawFindings, commission = null) {
  const findings = rawFindings.filter(isRenderableFinding)
  return {
    id: commission ? commissionReportId(commission.id) : COMMUNITY_REPORT_ID,
    project: commission ? commission.title : 'Community ideas',
    group: 'Community members',
    reportingPeriod: findings.length > 0 ? `Live · updated ${today()}` : 'Waiting for approved ideas',
    status: findings.length > 0 ? 'FINAL' : 'DRAFT',
  }
}

/** The full report, in the same shape ReportDetailPage already renders. Null when nothing is published. */
export function buildCommunityReportDetail(rawFindings, commission = null) {
  const findings = rawFindings.filter(isRenderableFinding)
  if (findings.length === 0) return null

  const researchQuestion = commission
    ? `Ideas shared by community members in response to \u201c${commission.title}\u201d.`
    : 'Ideas shared by community members through \u201cSubmit an Idea\u201d.'

  const themes = groupByTheme(findings)
  const ideaCount = new Set(findings.map((finding) => finding.sourceRef)).size
  const reviewedCount = findings.filter(
    (finding) => finding.flags.length > 0 || isFacilitatorCoded(finding),
  ).length
  let quoteNumber = 0

  return {
    id: commission ? commissionReportId(commission.id) : COMMUNITY_REPORT_ID,
    title: commission ? `${commission.title} \u2014 community ideas` : 'Community ideas report',
    subtitle: commission
      ? `Themes from ideas community members submitted for this commission, after the sensitivity tier gate, de-identification, AI coding and facilitator review.`
      : 'Themes from ideas community members submitted through the VCNITY platform, after the sensitivity tier gate, de-identification, AI coding and facilitator review.',
    badges: ['AI-assisted', 'Facilitator-verified', 'De-identified'],
    preparedFor: '[Council 1]',
    date: today(),
    file: commission ? `vcnity-commission-${commission.id}-ideas` : 'vcnity-community-ideas-live',
    purpose:
      (commission
        ? `This report brings together the ideas community members shared for \u201c${commission.title}\u201d. `
        : 'This report brings together the ideas community members shared through "Submit an Idea". ') +
      'Every idea was checked against its group’s sensitivity tier, de-identified before any AI processing, and coded into themes by AI. Anything the AI was unsure about was reviewed and approved by a facilitator before it could appear here. Quotes are the de-identified text; no names, contact details or raw submissions are shown.',
    stats: [
      { label: 'Ideas published', value: String(ideaCount) },
      { label: 'Themes', value: String(themes.length) },
      { label: 'Supporting quotes', value: String(findings.length) },
      { label: 'Facilitator-reviewed', value: String(reviewedCount) },
    ],
    findingsIntro:
      'Each theme below lists the de-identified quotes that support it. AI confidence is shown for AI-coded quotes; quotes the AI flagged were approved by a facilitator, and ideas the AI could not code were coded by a facilitator.',
    withheldFindingsCount: 0,
    findings: themes.map((group, index) => {
      const flagged = group.items.filter((item) => item.flags.length > 0 && !isFacilitatorCoded(item)).length
      const aiCount = group.items.length - group.facilitatorCoded
      const parts = []
      if (aiCount > 0) {
        parts.push(`AI thematic coding, average confidence ${percent(group.averageConfidence)}.`)
        parts.push(flagged === 0
          ? 'No AI-coded quotes needed facilitator review.'
          : `${flagged} AI-coded quote${flagged === 1 ? ' was' : 's were'} flagged and approved by a facilitator.`)
      }
      if (group.facilitatorCoded > 0) {
        parts.push(`${group.facilitatorCoded} quote${group.facilitatorCoded === 1 ? ' was' : 's were'} coded by a facilitator because the AI could not code ${group.facilitatorCoded === 1 ? 'that idea' : 'those ideas'}.`)
      }
      return {
        id: `community-theme-${index + 1}`,
        title: group.theme,
        commissioningBody: '[Council 1]',
        deliveredBy: 'Community members, via the VCNITY platform',
        researchQuestion,
        method:
          'Written ideas submitted in the community portal. Each passed the sensitivity tier gate and de-identification before AI coding.',
        analysis: parts.join(' '),
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
            codedByFacilitator: isFacilitatorCoded(item),
            aiConfidence: isFacilitatorCoded(item) ? null : Math.round(item.confidence * 100),
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
