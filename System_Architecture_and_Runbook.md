# VCNITY Portal – System Architecture and Runbook

**Jira:** Story 30 (IFN735-185, 191, 192, 193, 194)
**Applies to:** `main` at commit `448566c` (after PRs #25 and #26)
**Audience:** the team, the tutor, and the client (Ehud). Anyone setting up the system for the first time should be able to follow Part B.

> **Correction (Sub-task 30.1 / 30.2).** Earlier planning notes (Story 16 skeleton, backend integration plan) described the portal as "Spring Boot + Thymeleaf with session-based auth". That is out of date. The system that was built is a **React single-page app talking to a Spring Boot REST API**. There are no Thymeleaf templates and no Thymeleaf dependency in `backend/pom.xml`. Server-side pages were never built; every screen is a React route, and every piece of data comes from a `/api/...` JSON endpoint. There is no login yet: a temporary "Viewing as" switch chooses the portal.

---

## Part A – Architecture

### A1. The system at a glance

```
 Browser (React 18 + Vite, port 5173)
 ├── Community portal   /community          (Submit an Idea, Share feedback, groups, posts)
 ├── Facilitator portal /facilitator/...     (Exceptions queue, Coding findings)
 └── Client portal      /dashboard, /post-commission, /my-commissions, /reports-received  (Dashboard, Post a commission, My commissions, Reports received)
          │  all calls go to /api/...  (Vite proxies /api → http://localhost:8080)
          ▼
 Spring Boot 3.3.4 REST API (Java 17, port 8080)
 ├── intake      submissions, group tiers, facilitator coding
 ├── security    THE pipeline: TierGate → Deidentify → AI coding → Grounding → Coverage
 ├── exceptions  human review queue
 ├── findings    read API for coded findings
 ├── community · commission · consent · report · common
          │
          ├──► MongoDB (local, or Atlas via MONGO_URI)
          └──► Claude API (only if ANTHROPIC_API_KEY is set; otherwise a mock coder)
```

| Layer | Technology | Where |
|---|---|---|
| Frontend | React 18.3, react-router-dom 6.26, Vite 5.4, Vitest 2.1 | `frontend/` |
| Backend | Spring Boot 3.3.4 (web, data-mongodb, validation), Java 17, Maven | `backend/` |
| Database | MongoDB | `MONGO_URI`, default `mongodb://127.0.0.1:27017/client_portal` |
| AI | Anthropic Claude Messages API | `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` |

**Not part of the running system.** These folders are earlier prototypes kept for reference. Their logic was ported into `backend/.../security` and they are not started by the steps below:
`security-module-java/` (stand-alone Java port of the security module), `ai_pipeline/` (Python de-identification MVP, Story 13), and the root `pom.xml` + `src/` (Maven template).

### A2. One consolidated pipeline (Sprint 10 decision)

In Sprint 10 the team had two overlapping ways of coding feedback: the security module's `PipelineService` and Story 18's `BatchCodingJob`. They were merged into **one pipeline** (see `docs/Pipeline_Consolidation.md`). There is now only one route from a community submission to a finding:

```
Community member submits text (Submit an Idea)
  │  POST /api/intake/submissions {groupIds, text}
  ▼
1. Tier gate (TierGate)      Group tier looked up in group_tiers.
                             Tier 3 or untiered → REJECTED. The text never reaches the AI.
2. Transcribe                Text passes through (audio/photo is out of scope for the MVP).
3. De-identify (Deidentify)  Gazetteer names, vernacular terms and capitalised 2–3-word
                             names are replaced before anything leaves the server.
4. AI coding                 PipelineService → BatchCodingJob → Claude (or mock coder).
                             Produces CodedFinding records (theme, quote, confidence, tier).
5. Grounding (Grounding)     Each finding is checked: quote must appear in the source,
                             confidence ≥ 0.75, source present, tier 1 or 2.
6. Coverage (CoverageCheck)  Reports NOT_ASSESSED / PARTIAL / GAPS / COMPLETE.
  │
  ├── all checks pass  → submission PUBLISHED, findings saved to `findings`
  ├── any flag raised  → PENDING_REVIEW, item added to the exceptions queue
  └── AI fails / finds nothing → AI_COULD_NOT_CODE flag in the exceptions queue
  ▼
7. Human check (Facilitator portal)
     Clear (approve) · Reject · Code it myself (for AI_COULD_NOT_CODE items)
  ▼
8. Client portal → Reports received → "Community ideas" report
     Built only from PUBLISHED findings. Facilitator-coded findings are labelled
     "Coded by facilitator".
```

Rules that hold across the whole pipeline:

- Tier 3 and untiered content is refused at step 1 and nothing later can override it.
- "Clean" means the automated checks passed. It does not replace the human check in step 7.
- A finding's `codedBy` is `ai` or `facilitator`. Only facilitator findings may have no confidence score.
- The contract for a finding is `docs/CodedFinding.schema.json` (example: `docs/CodedFinding.example.json`).

### A3. Backend packages

| Package | Responsibility |
|---|---|
| `intake` | Submissions, group tiers, publishing, facilitator coding (`FacilitatorCodingController`). Store is `mongo` (default) or `memory` via `intake.store`. |
| `security` | The pipeline: `TierGate`, `Deidentify`, `PipelineService`, `BatchCodingJob`, `TranscriptCoder`, `Grounding`, `CoverageCheck`, `CodedFinding`. |
| `exceptions` | Human review queue: list, clear, reject, code. |
| `findings` | Read API over coded findings (switched on by `findings.api.enabled`). |
| `community` | Groups, posts, memberships. |
| `commission` | Client commissions. |
| `consent` | Consent records. |
| `report` | Client reports, seeded from `reports-seed.json` by `ReportSeeder`. |
| `common` | `ApiErrorHandler`: every error comes back as `{ "message": "..." }`. |

### A4. REST endpoints

| Area | Endpoints |
|---|---|
| Health | `GET /api/health` |
| Intake | `POST /api/intake/submissions` · `GET /api/intake/submissions/{id}` · `POST /api/intake/submissions/{id}/publish` · `GET /api/intake/submissions?groupId=` (published only; `groupId` required) · `GET`/`PUT /api/intake/group-tiers` |
| Exceptions | `GET /api/exceptions[?pendingOnly=true]` · `GET /api/exceptions/{id}` · `POST /api/exceptions` · `PATCH /api/exceptions/{id}/clear` · `PATCH /api/exceptions/{id}/reject` · `PATCH /api/exceptions/{id}/code` |
| Findings | `GET /api/findings/{itemId}` · `GET /api/findings?sourceRef=&page=&size=` → `{findings, page, size, hasNext}` |
| Community | `/api/community/groups` (POST, GET, GET/PUT/DELETE `/{id}`) · `/api/community/posts` (POST, GET, GET `/group/{groupId}`, GET/DELETE `/{id}`) · `/api/community/groups/{groupId}/members` (POST, GET, DELETE `/{userId}`) · `GET /api/community/users/{userId}/memberships` |
| Client | `POST /api/commissions` · `GET /api/reports` · `GET /api/reports/{id}` |
| Consent | `POST /api/consent` |

### A5. MongoDB collections

`intake_submissions`, `group_tiers`, `findings`, `commissions`, `reports`, `community_groups`, `community_posts`, `group_memberships`, `consents`.
The exceptions queue is held in memory and is emptied when the backend restarts.

### A6. Configuration

| Setting | Where | Default | Purpose |
|---|---|---|---|
| `MONGO_URI` | env var, or `application-local.properties` (git-ignored, `local` profile) | `mongodb://127.0.0.1:27017/client_portal` | Database |
| `PORT` | env var | `8080` | Backend port |
| `ANTHROPIC_API_KEY` | env var only, never in code or commits | not set → mock coder | Turns on real AI coding |
| `ANTHROPIC_MODEL` | env var | set in `PipelineService` | Which Claude model |
| `FINDINGS_API_ENABLED` | env var (`findings.api.enabled`) | `false` | Must be `true` for Reports received to show community ideas |
| `intake.store` | property | `mongo` | `memory` for quick tests without a database |

Confidence threshold: `0.75`, in `Grounding.CONFIDENCE_THRESHOLD`. Change it only after calibration (Story 25).

### A7. Known limits (state of `main` today)

- No login or role-based access. The "Viewing as" switch is for demonstration only.
- De-identification misses single first names, dates and ages, and the gazetteer is a placeholder. It must be extended before real data goes through.
- The AI call is a direct call to the Anthropic API, not Bedrock in the Sydney region with zero data retention as the client's runbook asks.
- The exceptions queue is in memory.
- `PipelineService` still reads Claude's reply with a regular-expression text match instead of a JSON parser. If a theme or quote contains quotation marks, it comes back empty. Before the real run, the reply needs to be parsed as JSON (for example with Jackson) and the HTTP status checked. This fix has not been merged yet.
- Consent records are not yet linked to submissions.
- Text only: photo and audio intake are out of MVP scope.

---

## Part B – Setup and testing runbook

This follows the same stages as the client's *Setup and Testing Runbook*: governance first, then an offline test, then the live model on made-up data, then trying to break the guardrails, then calibration, and only then a small real pilot. Do not skip a stage.

**How this maps to the MVP test weekend.** Step 2 is optional for the team. It is for a new machine or for the client after handover. The **dry run uses the real AI API on synthetic text: Steps 3 and 4**. The **real text run is Step 6**, after Ehud has signed the checklist (Step 1) and the offline de-identification check on the real texts has passed. Calibration (Step 5) can finish after the real run. Until then the threshold stays at 0.75.

Each step has **RUN** (what to do), **EXPECT** (what should happen) and **CHECK** (how you know it worked). Commands are for Windows PowerShell. On macOS/Linux use `npm` instead of `npm.cmd`.

### Prerequisites

- Java 17 (`java -version`), Maven 3.9+ (`mvn -v`), Node 18+ (`node -v`)
- MongoDB running locally on port 27017, or an Atlas connection string
- Git, and a clone of the repository
- For Step 3 onwards only: an Anthropic API key, shared privately by the key owner

### Step 1 – Governance first

**RUN** Confirm the privacy and data-residency checklist (Story 27) has been signed by the client. Agree on where real data will be stored: on team members' own machines and local MongoDB only.
**EXPECT** A signed checklist, and a named person responsible for the API key.
**CHECK** Until this step is done, use made-up (synthetic) text only. Real community text never goes on GitHub, into group chat, or into an unredacted screenshot.

### Step 2 – Offline smoke test (no API key)

**RUN**
```powershell
# Terminal 1 – backend. Make sure no API key is set, so the mock coder is used.
cd backend
Remove-Item Env:ANTHROPIC_API_KEY -ErrorAction SilentlyContinue
$env:FINDINGS_API_ENABLED = "true"
mvn test
mvn spring-boot:run

# Terminal 2 – frontend
cd frontend
npm.cmd install
npm.cmd test
npm.cmd run dev
```
Open http://localhost:5173, then check http://localhost:8080/api/health.

Set a test group to Tier 1:
```powershell
Invoke-RestMethod -Method Put -Uri http://localhost:8080/api/intake/group-tiers `
  -ContentType "application/json" `
  -Body '{"groupId":"<group id>","groupName":"Test group","tier":1,"setBy":"<your name>","reason":"Smoke test"}'
```
Then, as Community: Commissions → **Submit an Idea**, choose the group, and enter a short synthetic idea.

**EXPECT** `mvn test` and `npm.cmd test` both pass with no failures. The submission is processed by the mock coder, which gives every finding the theme "General feedback".
**CHECK** As Facilitator: the item appears in the **Exceptions queue** or goes straight through. As Client: **Reports received** → "Community ideas" shows the finding once it is published.

### Step 3 – Live model on synthetic text (the dry run)

**RUN** Pull the latest `main` (it must include the JSON reply fix in A7). Start MongoDB. Then set the key and model in this terminal only, and start the backend:
```powershell
git pull
cd backend
$env:ANTHROPIC_API_KEY = "<key from the key owner>"   # never commit or paste this anywhere
$env:ANTHROPIC_MODEL   = "<model agreed with the key owner>"
$env:FINDINGS_API_ENABLED = "true"
mvn spring-boot:run
```
Start the frontend as in Step 2. As Community, go to **Share feedback → Group tiers**. Classify one test group as **Tier 2** and one as **Tier 3**. (Submit an Idea only lists groups that have a tier. The `PUT /api/intake/group-tiers` command in Step 2 does the same thing.)
Submit 3–5 synthetic ideas to the Tier 2 group through Submit an Idea. Note each receipt: reference ID, tier and status.
**EXPECT** Each idea comes back with a real theme and a quote taken from the idea's own text.
**CHECK** In Coding findings, themes are not empty and quotes match the submitted text. For flagged items, the facilitator uses **Clear & publish** or **Reject** in the Exceptions queue. Client → Reports received → **Community ideas** shows the published ones. If the backend log shows an HTTP error status, stop and fix that before going on. Close the terminal afterwards so the key is cleared.

### Step 4 – Break the guardrails on purpose

**RUN**
- Submit an idea to the Tier 3 group from Step 3. It is still the live AI setup; the point is that the AI is never called. Untiered groups do not appear in Submit an Idea, so test them with `mvn test -Dtest=GuardrailBreakTest` (below).
- Submit text with nothing to code (for example "ok"). Submit text that contains a made-up full name.
- Run the automated break tests: `mvn test -Dtest=GuardrailBreakTest` (see `docs/Story26_Guardrail_Break_Tests.md`).

**EXPECT**
- Tier 3 and untiered: refused. Nothing reaches the AI or the reports.
- Nothing to code: appears in the queue as **AI could not code**. The facilitator can code it, and the report shows "Coded by facilitator".
- Full name: replaced before coding. It does not appear in any finding.

**CHECK** All break tests pass. No refused text appears in Coding findings or Reports received. With the live AI, the "nothing to code" item may still get a weak theme instead of AI could not code. If so, it should be flagged for low confidence. Either result is acceptable, so record which one happened.

### Step 5 – Calibrate

**RUN** The calibration owner runs the labelled calibration set through the live path and compares AI themes and confidence with the human coding (Story 25).
**EXPECT** An agreed confidence threshold (the current value is 0.75) and any prompt changes, written up. This can happen after the first real run. Until it is agreed, keep 0.75 and say so in the results.
**CHECK** Only after this is agreed should `Grounding.CONFIDENCE_THRESHOLD` or the prompt change, through a reviewed PR.

### Step 6 – Small real pilot (only after sign-off)

**RUN** Only when Step 1 is signed off and an offline de-identification check on the real text has passed (de-identification run over the chosen texts, every change logged, and a second person reads every line). Use the same live setup as Step 3, on one machine with local MongoDB:
1. Community: Submit an Idea → Tier 2 group → real idea text. Note the receipt.
2. MongoDB: check that the stored text and the finding have names, dates and ages redacted.
3. Facilitator: for flagged items, Clear & publish, Reject, or code it yourself.
4. Client: in Reports received → Community ideas, check the theme and de-identified quote appear. Rejected items must not appear.
5. Submit one idea to the Tier 3 group and check it never reaches the AI or the report.
**EXPECT** Each real item ends up in exactly one place: refused, queued for a person, or published.
**CHECK** Reports received shows only published, de-identified findings. Record the counts (refused / queued / cleared / rejected / facilitator-coded / published) for the Test & Evaluation report. Screenshots must show de-identified text only.

### Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Reports received has no community ideas | `FINDINGS_API_ENABLED` not set to `true` | Set it and restart the backend |
| "Something went wrong" when submitting | Backend not running, or the group has no tier | Check `/api/health`; set a tier with `PUT /api/intake/group-tiers` |
| `npm` is blocked in PowerShell | Execution policy blocks `npm.ps1` | Use `npm.cmd` |
| Every finding has the theme "General feedback" | No API key, so the mock coder is running | Expected in Step 2; set the key for Step 3 |
| Exceptions queue is empty after a restart | The queue is in memory | Expected; submit again |
