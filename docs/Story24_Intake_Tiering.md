# Story 24 — Community submission form integrated with intake and tiering

Jira: IFN735-89, subtasks IFN735-91 to 94.

## What it does

One minimal form (group + feedback). Every submission goes through intake before anything is published:

| Group's tier | What happens | Submitter sees |
|---|---|---|
| No tier set | Refused. Nothing is stored and the pipeline is not called. | A message that the group must be classified first (HTTP 422) |
| Tier 3 | Held. The pipeline is not called and the text is not stored. | "Handled as Tier 3", status Held |
| Tier 1 or 2, checks pass | Published | "Handled as Tier N", status Published |
| Tier 1 or 2, flagged | Hidden and sent to the exceptions queue (Story 19) | "Handled as Tier N", status Waiting for a person to review |

A flagged submission is published when a reviewer clears every flag on it in the exceptions queue.
A reject keeps it hidden. It is not deleted, and clearing the same item later brings it back.

## Subtasks

| Subtask | Where |
|---|---|
| 24.1 Payload shape matches the endpoint | `SubmissionRequest.java` and `intakeApi.js` (same field names). Pinned by `IntakePayloadContractTest` and `CommunitySubmissionPage.test.jsx` |
| 24.2 Wire the form to the endpoint | `CommunitySubmissionPage.jsx` -> `intakeApi.submitFeedback` -> `POST /api/intake/submissions` |
| 24.3 Show the assigned tier to the submitter | `SubmissionReceipt.jsx`, using `tier` and `message` from the response |
| 24.4 Tier 3 is flagged and never auto-passed into coding | `IntakeService.submit` stops Tier 3 before the pipeline. Proved by `Tier3SubmissionTest` |

## Endpoints

All under `/api/intake`. Errors come back as `{ "message": "..." }`.

| Method and path | Body | Returns |
|---|---|---|
| `POST /submissions` | `{ "groupIds": ["Youth Group A"], "text": "..." }` | 201 receipt: `{ id, status, tier, tierLabel, groupIds, message, submittedAt }` |
| `GET /submissions/{id}` | – | The latest receipt, or 404 |
| `GET /submissions?groupId=...` | – | Published submissions of that group: `[{ id, groupIds, text, tier, submittedAt }]` |
| `GET /group-tiers` | – | Every classified group with its tier and history |
| `PUT /group-tiers` | `{ "groupId": "Youth Group A", "tier": 2, "setBy": "Mohika", "reason": "..." }` | `{ group, haltedSubmissionIds, publishedToRecheck }` |

`status` is one of `PUBLISHED`, `PENDING_REVIEW`, `REJECTED`, `HELD`.
Other status codes: 400 invalid request, 422 group has no tier, 503 pipeline or database unavailable (nothing saved).

The submission body has no tier field on purpose. A `tier` sent by a client is ignored.

## Tiering rules (Mohika's spec)

1. The tier lives on the group (`GroupTier`), is nullable and has no default. No record means "not yet classified" and intake refuses the post.
2. Only a person sets it. `setBy` and `reason` are required, and nothing in the pipeline calls the setter.
3. The group's tier is copied onto each submission (`tierAtIntake`) and does not change if the group is re-tiered later.
4. Every change is kept in `history`. If a group moves to Tier 3, its submissions that are still waiting for review are halted (`HELD`), their queue items are marked "HALTED", and clearing them no longer publishes anything.
5. A submission to several groups takes the most restrictive tier. One unclassified group makes the whole submission unclassified.

Group names match regardless of case and extra spaces ("Youth Group A" = " youth  group a ").

## Sharing the exceptions queue with Story 18

Queue items raised here are tagged `sourceType = "COMMUNITY_SUBMISSION"` and `sourceId = <submission id>`.
`IntakeService` reacts only to items with that tag. Clearing or rejecting a Story 18 coding finding does
exactly what it did before and never touches a submission. See `docs/Story19_Exceptions_Queue.md`.

## Running it

Backend (port 8080):

    cd backend
    mvn test
    mvn spring-boot:run

Submissions and group tiers are saved to MongoDB (collections `intake_submissions` and `group_tiers`),
using the same connection as the rest of the app. To run without MongoDB, keep them in memory instead
(lost on restart):

    mvn spring-boot:run -Dspring-boot.run.arguments=--intake.store=memory

Frontend (port 5173, proxies `/api` to 8080):

    cd frontend
    npm install
    npm test
    npm run dev

Demo: open "Share feedback", set a tier for a group in the "Group tiers" panel, then submit.
With no `ANTHROPIC_API_KEY` the pipeline uses its mock coder. The mock flags any text that contains
no full stop (for example `Loved the session`), which is a quick way to see the review path.

## What was checked, and what was not

Checked while building this (without Maven or npm, which were not available in that environment):

- 91 backend logic tests pass, including the 47 new intake tests, run through a stand-in test runner.
- The whole flow was run in a real browser against the real service and controller code: refusal,
  publish, flag -> clear -> publish, reject, Tier 3 hold, re-tier halt.

Not yet run, and must be run before merging:

- `mvn test` (Spring context and controller tests, real JUnit).
- `npm test` (the Vitest files, including `CommunitySubmissionPage.test.jsx`).
- Anything against a real MongoDB. `MongoSubmissionStore` and `MongoGroupTierStore` have only been compiled, never executed.

## Decisions to confirm

- Tier 3: the submission is recorded as held (id, group, time) but its text is not stored. Confirm with Chloee whether it should instead be refused outright, or kept for a person to handle.
- Rejected submissions are kept and can be recovered. Confirm whether they should be hidden permanently.
- The "Group tiers" panel is a small demo and admin aid, separate from the form. Remove `GroupTierPanel` from `CommunitySubmissionPage.jsx` if tier-setting UI is out of scope.
- Anyone can call `PUT /group-tiers`. There are no roles in the app yet.

## Known limitations

- Not connected to CodedFinding persistence. Published and flagged submissions keep their coding result
  (`theme`, `quote`, `confidence`, `flags`, the same names as the contract) on the submission record only.
- Submissions go through `PipelineService`, not the Story 18 batch workflow.
- The exceptions queue is in memory (Story 19). After a restart, review items for submissions still
  waiting are re-created the first time an intake endpoint is called.
- The reviewer sees the flagged quote and coded theme, not the full de-identified text, because
  `PipelineService` does not return it.
- Consent is not checked at submission.
- Not connected to the Story 17 community workspace; groups are identified by the name typed in the form.

## Files

Story 24 (new):

- `backend/src/main/java/com/vcnity/backend/intake/` (model, stores, `GroupTierService`, `IntakeService`, `IntakeController`)
- `backend/src/test/java/com/vcnity/backend/intake/` (5 test classes and a test stand-in for the pipeline)
- `frontend/src/services/intakeApi.js`
- `frontend/src/components/CommunitySubmission/`
- `frontend/src/pages/CommunitySubmissionPage.jsx` and its test
- `docs/Story24_Intake_Tiering.md`

Changed for Story 24: `frontend/src/App.jsx` (switch between the two screens), `ExceptionsQueue.jsx` (one reviewer hint).

Story 19 (needed by Story 24, carried on this branch): the `exceptions` package and its tests, the
Exceptions screen, `exceptionsApi.js`, `docs/Story19_Exceptions_Queue.md`, and the Vite proxy port fix (5000 -> 8080).
Added to Story 19 for this work: `ExceptionItem.sourceType` / `sourceId`, `ExceptionReviewListener`, and
`ExceptionsQueueService.addReviewListener` / `listBySource`.
