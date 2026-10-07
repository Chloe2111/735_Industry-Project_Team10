# Story 19 — Exceptions Queue (integration-ready module)

## Purpose
Story 19 surfaces AI pipeline exceptions for human review before a finding can continue to Stage 7 verification.

Supported flags:
- `QUOTE_NOT_FOUND` — cited quote cannot be found exactly in the source transcript.
- `LOW_CONFIDENCE` — model confidence is below the review threshold.
- `SOURCE_MISSING` — finding has no traceable source.
- `CONTESTED` — evidence is conflicting or ambiguous and needs a human tie-breaker.
- `TIER_VIOLATION` — the pipeline's tier check failed; a human must decide.

## Human review workflow
The reviewer can inspect the flag, source reference, flagged quote, source context/evidence and confidence (when available), add a reviewer note, then choose **Clear for verification** (note optional) or **Reject** (note required, enforced in both UI and API; max 500 characters). Reviewed items retain their status and note. The UI can filter to pending items only.

## Running it
- Backend: `cd backend && mvn spring-boot:run` (listens on port 8080 by default, override with `PORT`).
- Frontend: `cd frontend && npm install && npm run dev`, then open http://localhost:5173 (Vite proxies `/api` to port 8080).
- `App.jsx` now renders `ExceptionsQueue` directly so the page is visible. If the team's shared navigation replaces `App.jsx`, keep the `ExceptionsQueue` import and mount it on your chosen route.

## Module ownership / integration
Story 19 is intentionally kept as a separate module. It does **not** replace the Client Portal root render and does **not** own the application's shared navigation/router.

Frontend entry component:
`frontend/src/components/Exceptions/ExceptionsQueue.jsx`

The shared Client Portal navigation owner can import that component and attach it to the team's chosen tab/route, for example:

```jsx
import ExceptionsQueue from './components/Exceptions/ExceptionsQueue'
```

No Story 19 route is hard-coded in `App.jsx` in this integration-ready package.

## Frontend files
- `frontend/src/components/Exceptions/ExceptionsQueue.jsx`
- `frontend/src/components/Exceptions/ExceptionsQueue.css`
- `frontend/src/services/exceptionsApi.js`
- `frontend/src/tests/ExceptionsQueue.test.jsx`

The module uses the project's existing `apiClient` and calls `/api/exceptions...`. Shared development proxy/routing configuration remains owned by the team application rather than Story 19.

## Backend files
- `backend/src/main/java/com/vcnity/backend/exceptions/controller/ExceptionsQueueController.java`
- `backend/src/main/java/com/vcnity/backend/exceptions/model/ExceptionItem.java`
- `backend/src/main/java/com/vcnity/backend/exceptions/model/FlagType.java`
- `backend/src/main/java/com/vcnity/backend/exceptions/model/ReviewStatus.java`
- `backend/src/main/java/com/vcnity/backend/exceptions/service/ExceptionsQueueService.java`
- `backend/src/main/java/com/vcnity/backend/exceptions/config/ExceptionsDemoData.java`

Backend tests:
- `backend/src/test/java/com/vcnity/backend/exceptions/ExceptionsQueueControllerTest.java`
- `backend/src/test/java/com/vcnity/backend/exceptions/ExceptionsQueueServiceTest.java`

## API
- `GET /api/exceptions?pendingOnly=true|false`
- `GET /api/exceptions/{id}`
- `PATCH /api/exceptions/{id}/clear`
- `PATCH /api/exceptions/{id}/reject`

Clear/reject accepts a JSON body such as (reject returns HTTP 400 if the note is blank):

```json
{ "note": "Evidence manually verified against source transcript." }
```

## Pipeline integration
`ExceptionsQueueService.addFromPipelineRun(run)` imports every `exceptions_queue` outcome from `PipelineService` (all five grounding flags are mapped; duplicate pending flags for the same item are skipped). The context text is built from de-identified pipeline output only. In the `dev` profile, in mock mode only, `ExceptionsDemoData` runs the synthetic demo pipeline through this method. Your real ingestion path should call the same method after `runPipeline(...)`.

## Sharing the queue with other features
The queue holds items from more than one feature (community submissions and Story 18 coding findings).
Two small additions keep them apart:

- `ExceptionItem.sourceType` and `sourceId` are optional tags naming the feature and record that raised an item.
  Items without a tag (all Story 18 findings) are reviewed exactly as before: clear or reject, nothing else happens.
- `ExceptionsQueueService.addReviewListener(...)` lets a feature react to a clear or reject of its own items.
  A listener is called for every reviewed item, so it must check `sourceType` and ignore the rest.
  It runs before the new status is stored; if it throws, the item stays `PENDING` and the review can be retried.

Two more rules protect the shared queue:

- **A blocked item cannot be cleared.** If `ExceptionItem.blockedReason` is set, `clear` returns HTTP 409 with
  that reason and the item stays `PENDING`. It can still be rejected. The UI shows the reason and disables Clear.
- **`POST /api/exceptions` is open to any caller, so it is checked.** The caller cannot choose the id or pre-fill
  the review fields. A `sourceType`/`sourceId` pair is accepted only when the feature it names has registered an
  `ExceptionSourceValidator` and that validator recognises the id. An id that already exists is never overwritten.

## Errors
All errors from this controller go through `common/ApiErrorHandler` and come back as `{ "message": "..." }`:
400 for an invalid request (for example a reject without a note), 409 for a blocked clear, 404 for an unknown id.

## Known gap: no authentication or roles
The app has no login or roles yet. Anyone who can reach the API can list, post, clear and reject queue items.
The checks above stop invented or overwritten items, but they do not replace access control. This must be
closed before any real data is used.

`TIER_VIOLATION` note: `PipelineService` rejects Tier 3 and untiered items at the tier gate, before coding,
and those items are never sent to this queue. The flag only applies if another caller adds it directly.

## Known limitation
The queue is held in memory, so reviews and notes are lost on restart. Persisting to MongoDB is the next step.

## Demo data
`ExceptionsDemoData` seeds one pending example for each of the four Story 19 flags, **only in the `dev` profile**:

    mvn spring-boot:run -Dspring-boot.run.profiles=dev

A normal start leaves the queue empty. The frontend's local demo fallback (used when the API cannot be reached)
is likewise limited to `npm run dev`; a production build shows the real error instead. This is isolated inside the Story 19 backend package and can later be replaced by real AI-pipeline exception creation without changing the review UI contract.

## Verification
Backend API and the four demo flags were manually verified in the corrected working copy before this integration-ready cleanup. Frontend unit tests and backend tests are included. Before merging into the team branch, run the project's normal backend and frontend test commands in the target branch and let the shared navigation owner connect the component.
