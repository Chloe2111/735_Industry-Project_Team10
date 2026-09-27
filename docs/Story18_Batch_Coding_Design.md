# Story 18: Batch Coding and Grounding Design

Status: Proposed interface for implementation and team review.

## 1. Purpose

Process a batch of eligible, de-identified transcript text, collect
structured coding results, and verify each supporting quote against
its referenced source.

Grounding verifies that a quote occurs in its source. It does not
prove that the assigned code or theme correctly interprets the quote.

## 2. Implementation location

Implement Story 18 in the Java backend:

backend/src/main/java/com/vcnity/backend/security/

Build on the existing PipelineService and Grounding classes.
Avoid introducing a second independent grounding implementation.

## 3. Batch input

Each source record will contain:

- sourceRef: a non-empty, unique reference to the source transcript.
- text: the eligible, de-identified text supplied to the coder.
- tier: the classification supplied by the trusted upstream pipeline.
- speakerCode: a pseudonymous speaker identifier, where available.

Tier and consent decisions belong to the upstream workflow.
The coding job must not treat missing permission information as approval.

Initial testing will use synthetic text and a controlled mock coder.
A mock coder tests the software flow, not real AI coding quality.

## 4. Structured coding output

Each finding will contain:

- itemId: a unique finding identifier.
- sourceRef: the reference to the transcript supporting the finding.
- speakerCode: the pseudonymous speaker identifier, where available.
- theme: the theme or code label returned by the coder.
- quote: the supporting quotation.
- confidence: a finite number between 0 and 1.
- tier: the source classification copied from trusted input.
- flags: the checks that failed.

The job will assign or validate identifiers and source links.
It will not let model output override the source tier.

A source may produce multiple findings.
The existing coverage check must continue to receive itemId and
speakerCode. A missing speakerCode must not be replaced with itemId.

Java field names use camelCase. Any external snake_case schema mapping
must be documented explicitly.

## 5. Grounding interface

Reuse the existing interface:

Grounding.ground(codedItem, sourceLookup)

sourceLookup maps sourceRef to the exact de-identified source text
provided to the coder.

Build and validate this lookup before checking results.
Reject duplicate source references rather than silently overwriting text.

Grounding returns the finding identifier, check status and flags.

A successful grounding result means that the implemented checks passed.
It does not grant final approval for publication or bypass required
verification and community review.

## 6. Proposed fixed comparison rules

The existing implementation collapses whitespace and lowercases text.

For compatibility, the proposed initial rule is:

1. Remove leading and trailing ASCII whitespace.
2. Replace each run of ASCII whitespace with one ordinary space.
3. Lowercase using Locale.ROOT so results do not depend on the
   computer's default locale.
4. Match the quote literally against its referenced source.
5. Preserve punctuation and word separation.
6. Reject empty or whitespace-only quotes.
7. Preserve the existing boundary protection against matching inside
   a larger word.

ASCII whitespace here means space, tab, line feed, vertical tab,
form feed and carriage return.

Other Unicode spacing characters are not silently converted under
this proposed rule.

Keep the original quote and source text unchanged in stored results.
Normalisation applies only to comparison.

This is case-insensitive matching after whitespace normalisation,
not character-for-character exact matching.

Confirm this rule with the team because Story 19 describes an
"exact" quote match. Add explicit boundary and Unicode test cases.

## 7. Validation and failure handling

Validate inputs and coding results before using them.

Check for:

- Null batch or null records.
- Missing or blank source references.
- Duplicate source references.
- Missing or blank source text.
- Missing or blank theme and quote.
- Missing or invalid confidence, including NaN and infinity.
- Missing or invalid tier.
- Malformed coding output.
- Coding failures.
- Exceptions-queue write failures.

Reject invalid batch structure before coding begins.

Keep an explicit outcome for each processed input so failures cannot
silently disappear from the batch.

Do not mark malformed output or a failed queue write as successful.
Operational failures need a separate failed outcome unless the team
adds suitable queue flag types.

## 8. Grounding flags and Story 19 mapping

Existing grounding flag -> Story 19 flag:

- quoteNotGrounded -> QUOTE_NOT_FOUND
- lowConfidence -> LOW_CONFIDENCE
- sourceMissing -> SOURCE_MISSING
- contradiction -> CONTESTED

Preserve the existing confidence threshold of 0.75 unless the team
agrees to change it.

The contradiction flag is an existing input hook. It is not an
implemented automatic contradiction-detection model.

Tier violations must remain blocked. Story 19 currently has no
corresponding tier-violation flag.

## 9. Exceptions-queue integration

Story 19 provides:

ExceptionsQueueService.addFromPipelineOutcome(outcome, sourceContext)

It also provides:

ExceptionsQueueService.add(ExceptionItem)

The existing helper maps outcome.itemId to the queue's sourceRef.
Before using it for batch findings, preserve the actual sourceRef
through an agreed adapter or a compatible helper update.

One finding may generate multiple queue entries when several flags fail.

Only eligible, de-identified source context should enter the queue.
Do not include re-identification mappings or raw identifiers.

Queue entries begin with PENDING status.
Clearing an exception permits the next verification step; it is not
final publication approval.

The current queue is in memory and is not durable across restarts.

## 10. Unit and integration tests

Test:

- A valid quote from the correct source.
- Repeated spaces, tabs and line breaks.
- Case differences under the documented rule.
- Punctuation differences.
- Empty and whitespace-only quotes.
- Fabricated quotes.
- Quotes appearing in a different source only.
- Partial-word matches and Unicode boundary cases.
- Missing sources and duplicate source references.
- Invalid confidence values and confidence threshold boundaries.
- Malformed coder output and coder failures.
- Multiple inputs and multiple findings.
- Preservation of sourceRef and speakerCode.
- Mapping of each supported flag to Story 19.
- Queue write failure without successful onward routing.

Run regression tests for existing security and coverage behaviour.

## 11. Completion evidence

18.1: Reviewed interface and comparison rules.
18.2: Working batch logic and grounding implementation.
18.3: Validation and explicit failure outcomes.
18.4: Passing new tests and existing regression tests.
18.5: Results connected to the actual Story 19 queue, with an
integration test demonstrating creation and review of flagged items.

Until the Story 19 connection is implemented and tested, report
Subtask 18.5 as incomplete.