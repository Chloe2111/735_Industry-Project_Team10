# Story 19 — Exceptions Queue (integration-ready module)

## Purpose
Story 19 surfaces AI pipeline exceptions for human review before a finding can continue to Stage 7 verification.

Supported flags:
- `QUOTE_NOT_FOUND` — cited quote cannot be found exactly in the source transcript.
- `LOW_CONFIDENCE` — model confidence is below the review threshold.
- `SOURCE_MISSING` — finding has no traceable source.
- `CONTESTED` — evidence is conflicting or ambiguous and needs a human tie-breaker.

## Human review workflow
The reviewer can inspect the flag, source reference, flagged quote, source context/evidence and confidence (when available), add a reviewer note, then choose **Clear for verification** or **Reject**. Reviewed items retain their status and note. The UI can filter to pending items only.

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

Clear/reject accepts a JSON body such as:

```json
{ "note": "Evidence manually verified against source transcript." }
```

## Demo data
`ExceptionsDemoData` seeds one pending example for each of the four Story 19 flags for development/demo purposes. This is isolated inside the Story 19 backend package and can later be replaced by real AI-pipeline exception creation without changing the review UI contract.

## Verification
Backend API and the four demo flags were manually verified in the corrected working copy before this integration-ready cleanup. Frontend unit tests and backend tests are included. Before merging into the team branch, run the project's normal backend and frontend test commands in the target branch and let the shared navigation owner connect the component.
