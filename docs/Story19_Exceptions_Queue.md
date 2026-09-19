# Story 19 — Exceptions Queue UI/CLI

## 19.1 Breakdown
1. Represent the four required human-review flags: `QUOTE_NOT_FOUND`, `LOW_CONFIDENCE`, `SOURCE_MISSING`, `CONTESTED`.
2. Store the flagged item ID/source reference, quote, source context, confidence, status and reviewer note.
3. Surface pending exceptions through an API and lightweight web queue.
4. Let a reviewer inspect evidence and mark an item `CLEARED` or `REJECTED`.
5. Map the existing Grounding names (`quoteNotGrounded`, `lowConfidence`, `sourceMissing`, `contradiction`) at the Story 19 boundary without changing the tested security module.
6. Validate invalid input/IDs and expose clear HTTP errors.
7. Test the model/service mapping, all four flag types, review actions, API responses and UI actions.

## 19.2 Implementation
Backend package: `com.vcnity.backend.exceptions`.

Endpoints:
- `GET /api/exceptions?pendingOnly=true` — queue
- `GET /api/exceptions/{id}` — detail
- `POST /api/exceptions` — ingest an exception (MVP/integration hook)
- `PATCH /api/exceptions/{id}/clear` — clear for verification
- `PATCH /api/exceptions/{id}/reject` — reject

The queue is deliberately in-memory for the Sprint 9 MVP; no production persistence is claimed. `ExceptionsQueueService.addFromPipelineOutcome(...)` is the integration adapter for `PipelineService.PipelineOutcome`. It maps the existing Grounding flag names to the Story 19 user-facing names and leaves `security/Grounding.java` unchanged.

Frontend: `frontend/src/components/Exceptions/ExceptionsQueue.jsx`. It lists pending flags, displays quote/context/confidence, and provides Clear/Reject human-review actions with loading/error/toast feedback.

## 19.3 Tests
Backend:
```bash
cd backend
mvn test
```
Covers all required flag types, clear/reject status transitions, invalid IDs/confidence, Grounding-to-Story-19 mapping, pipeline-outcome import, queue API, detail API and 404 behavior.

Frontend:
```bash
cd frontend
npm install
npm test -- --run
npm run build
```
Covers rendering flag/evidence details and the Clear human-review action.

## 19.4 Run / demonstrate
Terminal 1:
```bash
cd backend
mvn spring-boot:run
```
Terminal 2:
```bash
cd frontend
npm run dev
```
Open `http://localhost:5173`. The Vite proxy forwards `/api` to Spring Boot on port 8080.

For a demo item, POST JSON to `/api/exceptions`:
```json
{
  "flagType": "QUOTE_NOT_FOUND",
  "sourceQuote": "this exact phrase is not in the source text",
  "sourceContext": "I thought the second session ran a bit long, but overall it was useful.",
  "confidence": 0.88,
  "sourceRef": "WS-006"
}
```
Then refresh the queue, inspect the evidence and choose **Clear for verification** or **Reject**.

## Scope note
This is intentionally a lightweight human-review queue. Authentication/role enforcement, durable database persistence, audit identity and automatic orchestration from every pipeline run are follow-on integration work rather than being falsely presented as complete in this MVP.
