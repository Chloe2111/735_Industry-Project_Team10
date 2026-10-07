# Story 24 — Community submission form integrated with intake and tiering

Jira: IFN735-89, subtasks IFN735-91 to 94.

Merge order: Story 17, then Story 19, then this story. This branch carries the Story 19 files it needs;
once Story 19 is on `main`, merge `main` into this branch and the pull request shrinks to the Story 24 files.

## What it does

One minimal form (group + feedback). Every submission goes through intake before anything is published:

| Group's tier | What happens | Submitter sees |
|---|---|---|
| No tier set | Refused. Nothing is stored and the pipeline is not called. | A message that the group must be classified first (HTTP 422) |
| Tier 3 | Held. The pipeline is not called. The text is kept, as submitted, for a person to review. No endpoint returns it. | "Handled as Tier 3", status Held |
| Tier 1 or 2, checks pass | Published, with detected names and contact details removed | "Handled as Tier N", status Published |
| Tier 1 or 2, flagged | Hidden and sent to the exceptions queue (Story 19) | "Handled as Tier N", status Waiting for a person to review |

Only the de-identified text of a Tier 1 or 2 submission is stored and published. The original is not stored.
De-identification is rule-based and does not catch everything (see "Known limitations").

How a flagged submission is decided:

- While any of its flags is still pending, it stays "waiting for review", even if another flag was already rejected.
- When every flag has been reviewed: all cleared means published; any rejected means not published.
- A rejected submission is hidden, not deleted. Clearing the rejected item later publishes it.

## When a group becomes Tier 3

Nothing of a Tier 3 group is shown or can be published, whatever tier it was submitted under:

- Submissions waiting for review, and submissions a reviewer rejected, are halted (`HELD`). Their queue
  items are blocked, so none of them can be cleared and published afterwards, and the quote is removed
  from those items so the queue stops showing the group's text.
- Posts that were already published are hidden for as long as the group is Tier 3. `GET /submissions`
  returns none of them, the page says the group is Tier 3 instead of listing posts, and the submitter's
  receipt says the post is held. They are hidden, not deleted: if a person moves the group back, they show again.
- A post shared with several groups is hidden in all of them while any of those groups is Tier 3.
- Independently of the halt, a review can never publish into a group that is Tier 3 at that moment: the
  group's current tier is checked when the item is cleared.
- If the group becomes Tier 3 while a submission is still being checked by the pipeline, the result is thrown
  away and the submission is held as Tier 3.
- The halt runs every time a group is saved as Tier 3, so saving it again repairs a halt that failed part-way.

The person who makes the change is told how many submissions were halted and how many posts were hidden.

## Review feedback and where it was handled

| From | Point | Handled in |
|---|---|---|
| Chloee | Clear reports success on a halted item | "A halted item cannot be cleared" below |
| Chloee | Two coordinators overwrite each other | "Two coordinators changing the same group" below |
| Chloee | `POST /api/exceptions` accepts anything; auth gap | "Sharing the exceptions queue" and "Known gap" below |
| Chloee | Merge Story 17 and 19 first | Merge order at the top; Story 19 is its own branch |
| Chloee | Six smaller fixes | "Design notes" below and `docs/Story19_Exceptions_Queue.md` |
| Miah | A rejected post can be published after its group moves to Tier 3 | "When a group becomes Tier 3" above. `IntakeServiceTest`: `rejectedSubmissionIsHaltedWhenItsGroupBecomesTier3AndCanNoLongerBeCleared` and the three tests after it |
| Miah | Published posts stay visible after the group becomes Tier 3 | Same section. Tests: `publishedPostsAreHiddenWhileTheirGroupIsTier3` and the two after it |
| Miah | Published posts keep names and contact details | De-identified text only. Tests: `namesAndContactDetailsAreRemovedBeforeAnythingIsStoredOrPublished` and the two after it |
| Mohika | Tier 3: keep the text for a person to review | `Tier3SubmissionTest`: `tier3TextIsKeptForAPersonToReviewAndNothingAutomatedIsRecorded`, `tier3TextIsNotReturnedByAnyEndpoint` |

## Subtasks

| Subtask | Where |
|---|---|
| 24.1 Payload shape matches the endpoint | `SubmissionRequest.java` and `intakeApi.js` (same field names). Pinned by `IntakePayloadContractTest` and `CommunitySubmissionPage.test.jsx` |
| 24.2 Wire the form to the endpoint | `CommunitySubmissionPage.jsx` -> `intakeApi.submitFeedback` -> `POST /api/intake/submissions` |
| 24.3 Show the assigned tier to the submitter | `SubmissionReceipt.jsx`, using `tier` and `message` from the response |
| 24.4 Tier 3 is flagged and never auto-passed into coding | `IntakeService.submit` stops Tier 3 before the pipeline. Proved by `Tier3SubmissionTest` |

## Endpoints

All under `/api/intake`.

| Method and path | Body | Returns |
|---|---|---|
| `POST /submissions` | `{ "groupIds": ["Youth Group A"], "text": "..." }` | 201 receipt: `{ id, status, tier, groupIds, message, submittedAt }` |
| `GET /submissions/{id}` | – | The latest receipt, or 404. A receipt never contains the submitted text |
| `GET /submissions?groupId=...` | – | Published submissions of that group: `[{ id, groupIds, text, tier, submittedAt }]`. `text` is de-identified. Empty while the group is Tier 3 |
| `GET /group-tiers` | – | Every classified group: `{ groupId, groupName, tier, setBy, reason, setAt, history, version }` |
| `PUT /group-tiers` | `{ "groupId": "Youth Group A", "tier": 2, "setBy": "Mohika", "reason": "...", "expectedVersion": 0 }` | `{ group, haltedSubmissionIds, publishedToRecheck }` |

`status` is one of `PUBLISHED`, `PENDING_REVIEW`, `REJECTED`, `HELD`.
The submission body has no tier field on purpose. A `tier` sent by a client is ignored.

### Errors

Both controllers (intake and exceptions queue) answer errors through one handler, `common/ApiErrorHandler`,
always as `{ "message": "..." }`:

| Status | When |
|---|---|
| 400 | The request is invalid (missing group, empty text, reject without a note, bad source tag) |
| 409 | A tier change was made from a stale view, or a reviewer tried to clear a halted item |
| 422 | The group has no tier (body also lists `unclassifiedGroupIds`) |
| 503 | The pipeline or the database is unavailable. Nothing was saved |

## Tiering rules (Mohika's spec)

1. The tier lives on the group (`GroupTier`), is nullable and has no default. No record means "not yet classified" and intake refuses the post.
2. Only a person sets it. `setBy` and `reason` are required, and nothing in the pipeline calls the setter.
3. The group's tier is copied onto each submission (`tierAtIntake`) and does not change if the group is re-tiered later.
4. Every change is kept in `history`. If a group moves to Tier 3, its waiting and rejected submissions are halted (`HELD`), their queue items are blocked, and its published posts are hidden (see "When a group becomes Tier 3").
5. A submission to several groups takes the most restrictive tier. One unclassified group makes the whole submission unclassified.

Group names match regardless of case and extra spaces ("Youth Group A" = " youth  group a ").

### Two coordinators changing the same group

A tier change cannot silently overwrite another one:

- `GET /group-tiers` returns a `version` for each group. The tier panel sends it back as `expectedVersion`
  (`null` for a group that is not classified yet).
- If the stored version is different, the change is refused with 409 and a message saying what the group is now
  and who set it. Nothing is saved, nothing is halted, and the panel reloads the list.
- `GroupTier.version` is a Spring Data `@Version` field, so MongoDB also refuses a write that raced past that
  check in the same instant.

Tests: `GroupTierServiceTest` (stale view, two first-time classifications, 12 simultaneous saves where exactly
one wins) and `MongoIntakeStoresIntegrationTest` for the same behaviour on a real MongoDB.

### A halted item cannot be cleared

When a group becomes Tier 3, each waiting submission's queue items get a `blockedReason`. Trying to clear one
returns 409 with that reason. The item keeps the status it had, the submission stays held, and the reviewer sees the
reason instead of "Exception cleared." The queue screen shows the reason and disables Clear. The item can still
be rejected. The same applies to an item that was rejected before the group became Tier 3: it cannot be
cleared afterwards. A review also cannot publish a submission that was halted in the same instant: the status change
is a conditional update that only applies while the submission is still in the status the reviewer saw.

## Sharing the exceptions queue

Queue items raised here are tagged `sourceType = "COMMUNITY_SUBMISSION"` and `sourceId = <submission id>`.
`IntakeService` reacts only to items with that tag. Clearing or rejecting a Story 18 coding finding does
exactly what it did before and never touches a submission.

`POST /api/exceptions` is open to any caller, so an item posted there can only carry this tag if the
submission exists and is waiting for review. See `docs/Story19_Exceptions_Queue.md`.

## Known gap: no authentication or roles

The app has no login or roles yet. Anyone who can reach the API can submit, set a group's tier, and post,
clear or reject queue items. `setBy` is a typed name, not a verified identity. The checks in this story stop
invented items, overwritten tier changes and false successes, but they are not access control. This must be
closed before any real data is used.

## Running it

Backend (port 8080):

    cd backend
    mvn test
    mvn spring-boot:run

Submissions and group tiers are saved to MongoDB (collections `intake_submissions` and `group_tiers`),
using the same connection as the rest of the app. To run without MongoDB, keep them in memory instead
(lost on restart):

    mvn spring-boot:run "-Dspring-boot.run.arguments=--intake.store=memory"

The exceptions queue starts empty. The Story 19 demo items are seeded only in the `dev` profile:

    mvn spring-boot:run -Dspring-boot.run.profiles=dev "-Dspring-boot.run.arguments=--intake.store=memory"

Frontend (port 5173, proxies `/api` to 8080):

    cd frontend
    npm install
    npm test
    npm run dev

Demo: open "Share feedback", set a tier for a group in the "Group tiers" panel, then submit.
With no `ANTHROPIC_API_KEY` the pipeline uses its mock coder. The mock flags any text that contains
no full stop (for example `Loved the session`), which is a quick way to see the review path.

To run the MongoDB stores against a real local MongoDB (skipped otherwise):

    mvn -f backend/pom.xml -Dmongodb.integration=true -Dtest=MongoIntakeStoresIntegrationTest test

## What was checked, and what was not

Checked while building this (without Maven or npm, which were not available in that environment):

- 134 backend logic tests pass through a stand-in test runner: 78 for intake, 19 for the queue,
  4 for the error handler, 33 existing pipeline tests.
- Each new rule was broken on purpose once to confirm a test fails when it is broken.
- The whole flow was run in a real browser against the real service and controller code (64 checks),
  including a stale tier change, a clear on a halted item, invented queue items, a rejected post in a
  group that becomes Tier 3, hidden posts, and removed names and phone numbers.

Not yet run, and must be run before merging:

- `mvn test` (Spring context and controller tests, real JUnit).
- `npm test` (the Vitest files).
- `MongoIntakeStoresIntegrationTest`. The two MongoDB stores have only been compiled against stand-in
  Spring classes, never against the real library and never executed,
  so this test is the first real check of the version lock and the bulk update on MongoDB.

## Design notes

- Tier names are written in one place, `frontend/src/constants/tiers.js`, used by the consent form and
  these screens. The backend deals in tier numbers only.
- Database calls do not grow with the number of records: tiers for several groups are one query, halting a
  group's submissions is one bulk update plus one read, listing a group's published posts is two queries,
  and restoring the queue is one query.
- De-identification uses the pipeline's own `Deidentify` class and term lists, so the stored text is exactly
  what the coder saw. Removed parts are stored as `[REDACTED:...]` tags and shown on the page as "[removed]".
- Tier 3 text is not de-identified: that would be an automated step, and Tier 3 stops before all of them.
- The exceptions queue is in memory (Story 19), so `IntakeService` puts the review items of waiting
  submissions back once at startup (`@PostConstruct`). If the database cannot be reached at that moment the app
  still starts and logs a warning; restart it once the database is back.
- `BackendApplicationTests` starts the app with `intake.store=memory`, so it still needs no database.

## Decisions to confirm

- Tier 3 text: kept as submitted for a person to review (Mohika, 7 Oct). It sits in the same database as
  everything else and there is no screen for reviewing it yet, because that screen needs roles first.
  Confirm who reviews it and where, and whether storing it centrally fits "community-controlled or local only"
  in the project report.
- Published posts in a group that becomes Tier 3 are hidden, not deleted, and show again if the group is moved
  back. Confirm that is preferred over hiding them permanently.
- Published text is de-identified and the original is not stored. Confirm, since this also changes what group
  members see of their own posts.
- Rejected submissions are kept and can be recovered. Confirm whether they should be hidden permanently.
- The "Group tiers" panel is a small demo and admin aid, separate from the form. Remove `GroupTierPanel` from
  `CommunitySubmissionPage.jsx` if tier-setting UI is out of scope.

## Known limitations

- Not connected to CodedFinding persistence. Published and flagged submissions keep their coding result
  (`theme`, `quote`, `confidence`, `flags`, the same names as the contract) on the submission record only.
- Submissions go through `PipelineService`, not the Story 18 batch workflow. `PipelineService` has only run
  with its mock coder; it cannot yet read a real API reply.
- The reviewer sees the flagged quote and coded theme, not the full de-identified text, because
  `PipelineService` does not return it.
- Consent is not checked at submission.
- Not connected to the Story 17 community workspace; groups are identified by the name typed in the form.
- A group moved to Tier 3 and later moved back: its held submissions stay held (its hidden published posts
  show again).
- De-identification is rule-based (the pipeline's existing `Deidentify` class, not changed here). It removes
  emails, Australian mobile and area-code landline numbers, listed terms, and two or three capitalised words in
  a row. It can remove a non-name such as "Youth Group". It misses a single first name ("Maria"), names with
  accents or apostrophes ("José García", "Mary O'Brien"), names in capitals, eight-digit numbers without an
  area code, and overseas numbers. So published text can still contain personal details. Improving the rules
  belongs with the pipeline, not this story.
- Submissions stored before this change keep the original text they were stored with.

## Files

Story 24:

- `backend/src/main/java/com/vcnity/backend/intake/` (model, stores, `GroupTierService`, `IntakeService`, `IntakeController`)
- `backend/src/test/java/com/vcnity/backend/intake/` (6 test classes and their helpers)
- `frontend/src/services/intakeApi.js`, `frontend/src/constants/tiers.js`
- `frontend/src/components/CommunitySubmission/`
- `frontend/src/pages/CommunitySubmissionPage.jsx` and its test
- `docs/Story24_Intake_Tiering.md`

Changed for Story 24: `frontend/src/App.jsx` (switch between the two screens), `ExceptionsQueue.jsx` (one
reviewer hint), `ConsentCaptureForm.jsx` (reads tier names from the shared file; no change in behaviour),
`BackendApplicationTests.java` (in-memory intake store).

Story 19 (needed by Story 24, to be merged first): the `exceptions` package and its tests, `common/ApiException`
and `common/ApiErrorHandler`, the Exceptions screen, `exceptionsApi.js`, `docs/Story19_Exceptions_Queue.md`,
and the Vite proxy port fix (5000 -> 8080).
