# Story 23 — Client Portal / AI Pipeline Integration Contract (DRAFT — 23.1)

Status: Draft for review. Not confirmed with Sharisha or Miah yet.
This file documents what subtask 23.1 asked for: the actual interface/contract
between the AI pipeline's output and the client portal's reporting view, and
the mismatches that block 23.2 until someone signs off on a direction.

## 1. What actually exists today (verified from code, 30 Sep 2026)

### 1.1 The pipeline is not a separate Python process

`Backend-Integration-Plan.md`'s framing (pipeline = separate Python process
reading Sessions/Artefacts from Mongo, writing Codes/Themes/Reports/Report
Themes back) does not match `feature/story18-batch-coding`. The batch coding
work there lives in the same Java/Spring Boot backend:

    backend/src/main/java/com/vcnity/backend/security/
      BatchCodingJob.java
      BatchCodingWorkflow.java
      BatchExceptionsAdapter.java
      CodedFinding.java
      CodingSource.java
      TranscriptCoder.java

There is also a standalone `security-module-java/` Maven module with a
parallel, simpler pipeline prototype (`vcnity.pipeline.PipelineCore`, etc.) —
unclear which one is meant to be "the" pipeline of record. Needs Sharisha to
confirm which module is authoritative, since they currently define
overlapping but non-identical shapes.

### 1.2 Nothing is persisted to MongoDB yet

Per Story 18's own design doc ("Remaining work and limitations"):

> MongoDB was unavailable during the test run. Database integration was
> not established by the passing tests.
> Queue entries currently disappear when the application restarts.

Confirmed independently: there is no `@Document(collection = "codes")`,
`"themes"`, `"sessions"`, or `"artefacts"` anywhere in the codebase. The only
Mongo-backed collections that exist are `reports` (Story 16) and `consents`
(Story 22). **There is currently nothing in Mongo for a read-only portal
integration to read.** `CodedFinding` is an in-memory Java `record`, produced
and consumed entirely within one process run; it never reaches a database.

This is the real blocker for 23.1, ahead of the schema question: before
field names matter, the team needs to confirm *whether/when* Story 18's
output will actually be written to a `codes`/`themes` collection, and who
implements that write path (presumably part of finishing Story 18, not 23).

### 1.3 The reporting view is React + REST, not Thymeleaf

The story brief says 23.2 needs "a controller endpoint + new Thymeleaf
template for the reporting view." That doesn't match this codebase — there
is no Thymeleaf anywhere in `backend/pom.xml` or `src`. The existing
reporting view (Story 16) is:

- `backend/.../report/Report.java` — `@Document(collection = "reports")`
- `backend/.../report/ReportController.java` — `@RestController`,
  `GET /api/reports`, `GET /api/reports/{id}`, returns
  `{ success, data }` JSON
- `frontend/src/pages/ReportDetailPage.jsx` — React page, fetches via
  `getReport()` in `portalService.js`

Any 23.2 work should follow this existing REST + React pattern, not
introduce Thymeleaf. This looks like the brief reused generic wording from
an older template rather than this project's actual Story 16 output —
worth flagging to whoever wrote the story text.

## 2. Schema comparison

Three different shapes are in play, and none of them match:

| Field | ERD (`Codes`/`Themes`) | Story brief's assumed schema | Actual `CodedFinding` (story18) |
|---|---|---|---|
| id | — | `item_id` | `itemId: String` |
| type | — | `type` | *(none)* |
| label/theme | `Label` | `label` | `theme: String` |
| description | `Description`/`Name` | `description` | *(none)* |
| source ref | `Quote_ref` | `source_ref` (object: file, line, modality, speaker_code) | `sourceRef: String` (flat), `speakerCode: String` (separate field) |
| quote | — | `quote` | `quote: String` |
| confidence | `Confidence` | `confidence` | `confidence: Double` (0.0–1.0) |
| tier | — | `tier` | `tier: Integer` (must be 1 or 2) |
| supports | — | `supports` | *(none)* |
| flags | `Flagged` (bool) | `flags` | `flags: List<String>` — one of `quoteNotGrounded`, `lowConfidence`, `sourceMissing`, `contradiction`, `tierViolation` |

`CodedFinding` is the only one of the three backed by real, tested code —
treat it as the current source of truth, but it is **not yet confirmed as
final/frozen by Sharisha**, and it does not persist to Mongo (see 1.2), so
"the exact field names the pipeline writes to Mongo" is still an open
question, not just a naming exercise.

## 3. A deeper mismatch: "codes/themes" vs. the existing Report shape

Even once `codes`/`themes` exist in Mongo, they don't fit the existing
reporting view's data model without extra work — this is a bigger gap than
a field-renaming exercise.

`Report.findings[]` (what `ReportDetailPage.jsx` / `FindingAccordion.jsx`
actually render today) is a **narrative, human-authored structure**:
`title`, `commissioningBody`, `deliveredBy`, `researchQuestion`, `method`,
`analysis`, `deliverableStatus`, plus per-source `agreementPercent`,
`genderBreakdown`, `culturalBackgrounds`, `whyItMatters`. None of this
exists in `CodedFinding` — it's flat: `theme`, `quote`, `confidence`,
`tier`, `flags`. `FindingAccordion.jsx` directly renders the narrative
fields (`finding.commissioningBody`, `finding.method`, `finding.analysis`,
...) — feeding raw coded findings into it as-is would render blank/undefined
for most of the view.

This means "wire the reporting view to `clean_codes`/`themes`" is
ambiguous between two real options:

- **Option A — new, separate view.** Add a "Coded findings" / "Coding
  review" page that lists raw `CodedFinding`-shaped rows (theme, quote,
  confidence, tier, flags) per project, read-only. Minimal schema
  invention, matches the pipeline output honestly, but it is a new UI
  surface — not literally "the reporting view."
- **Option B — extend the existing Report Detail page.** Treat
  codes/themes as optional/supplementary content on the existing page
  (e.g. a new "AI coding" section alongside the narrative findings), degrading
  gracefully where narrative fields are absent. Reuses existing UI and
  URL structure, but needs a UX decision on what that section looks like
  when there's no `method`/`analysis`/demographic data to show.

This can't be resolved from code alone — it's a product/UX call.

## 4. Recommendation for next steps

1. Confirm with Sharisha: which module is authoritative
   (`backend/.../security` vs `security-module-java/`), whether
   `CodedFinding`'s shape is considered final, and — most importantly —
   whether/when pipeline output actually gets written to Mongo, and by whom.
2. Flag to Miah: the ERD's `Codes`/`Themes` entities are out of date
   against `CodedFinding` (same drift pattern as Story 16), and the story
   brief's Thymeleaf assumption doesn't match the actual React/REST
   architecture already built.
3. Get a product decision on Option A vs Option B above (or confirm 23 is
   scoped to "wire once Story 18 lands in Mongo" and treat this story as
   blocked until then).

Until 1–3 are resolved, 23.2 code changes would be guessing at a schema and
a UX direction that aren't confirmed — happy to draft either option once
you've got an answer, or draft both behind a flag if you'd rather show
options to the team.
