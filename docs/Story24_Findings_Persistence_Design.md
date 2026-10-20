# Story 24.5 — Findings Persistence Design

## 1. Purpose

Persist structured CodedFinding records after coding and grounding,
and provide a retrieval endpoint for Story 23.

Saving a finding does not approve it for publication.

Status: proposed design. The shared contract is awaiting team review.

## 2. Verified development connection

- Java: 17.
- Database: local MongoDB.
- Development database name: vcnity_story24_dev.
- Spring Data MongoDB connectivity was verified through an integration test.
- The test successfully inserted, retrieved, and removed a synthetic record.

Run the connection test:

mvn -f backend/pom.xml -Dmongodb.integration=true -Dtest=MongoPersistenceIntegrationTest test

This verifies database access only. It does not verify CodedFinding
persistence or the complete pipeline.

## 3. Findings collection

Collection name: findings.

Preserve the eight fields from the shared CodedFinding contract:

| Field | Storage type | Rule |
|---|---|---|
| itemId | String | Required, nonblank finding identifier |
| sourceRef | String | Required, nonblank source reference |
| speakerCode | String or null | Nonblank when supplied |
| theme | String | Required, nonblank |
| quote | String | Required, nonblank |
| confidence | Double | Finite value between 0 and 1 inclusive |
| tier | Integer | 1 or 2 |
| flags | Array of strings | Supported values only; no duplicates |

Supported flags:
- quoteNotGrounded
- lowConfidence
- sourceMissing
- contradiction
- tierViolation

Store speakerCode explicitly as null when unavailable.

MongoDB's internal _id will equal itemId. Retain itemId as an explicit
field so the eight contract fields remain available unchanged.

The API response will exclude MongoDB-specific metadata.

Create an index on sourceRef for source-based retrieval.
MongoDB's unique _id index prevents duplicate records with the same ID.

## 4. Repository responsibilities

The persistence layer will:

- Accept validated CodedFinding records.
- Preserve all eight fields without renaming them.
- Store both unflagged and flagged findings.
- Retrieve findings by itemId or sourceRef.
- Report database failures explicitly.

Use the existing CodedFinding class once the dependency is integrated.
Do not create a second competing finding class.

## 5. Duplicate writes and retries

For a repeated save with the same itemId:

- If all eight fields match the stored record, treat it as an
  idempotent retry.
- If any field differs, return a conflict instead of silently
  overwriting the record.

Retry persistence using the original finding and itemId.
Rerunning the coder currently generates new finding IDs and is not
an idempotent persistence retry.

## 6. Pipeline integration

Only eligible, de-identified inputs may reach the coding job.
Consent and processing eligibility must be established upstream.

Persist the validated findings returned by coding and grounding,
including findings requiring exception review.

If coding fails or returns no findings, do not create a fabricated
finding record.

Track persistence success separately from exception-queue routing.
A queue failure must not be presented as successful routing, and
a database failure must not be presented as successful persistence.

Saving a batch may partially succeed. Report which finding IDs were
saved and which failed so retries can reuse the original records.

The existing exception queue is in memory. Writing findings to MongoDB
does not make queue entries durable or create an atomic transaction
between the queue and database.

## 7. Proposed GET API

GET /api/findings/{itemId}

- 200: return one finding using the eight contract fields.
- 404: finding does not exist.
- 503: database is unavailable.

GET /api/findings?sourceRef={sourceRef}&page=0&size=20

- Require a nonblank sourceRef.
- Require page >= 0.
- Require size between 1 and 100.
- Sort consistently by itemId.
- Return an envelope containing findings, page, size, and hasNext.
- Return an empty findings array when no records match.
- Return 400 for invalid query parameters.
- Return 503 when the database is unavailable.

Endpoint names and the list-response envelope require agreement
with the Story 23 consumer.

## 8. Review and access boundaries

These endpoints retrieve pipeline findings for the agreed internal
workflow. They are not a public publication API.

Empty flags do not establish that human verification has occurred.
Human-review status is not currently part of CodedFinding.

Before use with real client material, connect the endpoints to the
team's authorization and review rules. A sourceRef filter alone is
not access control.

Until those controls are integrated, development tests use synthetic
data only.

## 9. Planned verification

Repository integration tests:
- Save and retrieve all eight fields.
- Preserve null speakerCode and supported flags.
- Retrieve findings for the requested source only.
- Accept an identical retry without creating a duplicate.
- Reject a conflicting record using an existing itemId.

Pipeline integration tests:
- Synthetic transcript produces a persisted finding.
- Flagged findings are persisted and routed for review.
- Coding failures create no finding records.
- Database and queue failures remain distinguishable.

API tests:
- Existing and missing finding IDs.
- Source filtering and pagination.
- Invalid query parameters.
- Database-unavailable responses.
- Responses match the agreed contract.

## 10. Dependencies and remaining work

- Shared CodedFinding contract review.
- Integration of the Story 18 implementation and Story 19 queue.
- Consolidation of the existing pipeline coding paths.
- Repository implementation.
- Pipeline persistence integration.
- GET endpoint implementation.
- Synthetic end-to-end verification.

This document is a design proposal, not evidence that the planned
functionality has already been implemented.
## 11. Implementation and verification update

This section updates the earlier proposed implementation status.

Implemented:
- MongoDB finding repository preserving all eight contract fields.
- Identical-write retries and rejection of conflicting finding IDs.
- Source-filtered retrieval with pagination.
- Batch-workflow persistence with separate storage and queue outcomes.
- Persistence retries using the original findings and IDs.
- GET endpoints for individual findings and source-filtered findings.
- Controlled validation, storage-failure, and conflict handling.

Verification:
- Full backend suite: 104 tests passed, zero failures, errors, or skips.
- Executed using Java 17 and local MongoDB.
- Synthetic batch findings were stored in MongoDB and retrieved through
  the GET controller using MockMvc.
- Database-failure scenarios were simulated using a mocked repository.

Run the full suite with MongoDB running:

mvn -f backend/pom.xml -Dmongodb.integration=true clean test

Integration limits:
- The tested workflow uses eligible, de-identified synthetic text and a
  controlled coder. It does not verify raw-data intake, consent,
  transcription, de-identification, or a live AI provider.
- The persistence wrapper is constructed explicitly in the tests.
  The older PipelineService and the application entry point have not
  yet been connected to it.
- ensureIndexes() is called explicitly in integration tests; application
  setup still needs to invoke it.
- The findings API is disabled unless findings.api.enabled=true.
  This switch is not authentication or authorization.
- Human approval and publication remain separate from persistence.
- Report grouping and method/analysis/source mapping require team agreement.
- The exceptions queue remains in memory.
- The tierViolation queue-mapping issue and Clear/Clear & Republish
  behaviour are separate unresolved integration work.