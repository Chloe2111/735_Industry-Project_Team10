# Story 13 De-identification MVP

Python module for de-identifying synthetic English transcripts.

## Current status

Implemented:
- Local entity detection using spaCy and pattern matching.
- Consistent pseudonyms for repeated exact identifiers within a transcript.
- Separate mapping storage with owner-only filesystem permissions.
- Input validation and controlled errors.
- Local adapter that blocks ineligible inputs and holds successful
  results for human review.
- 29 passing automated tests in the developer's environment.

Integration with the team's Java backend and persistent exceptions
queue remains outstanding.

## Requirements

Tested with:
- macOS
- Python 3.12.7
- spaCy 3.8.16
- en_core_web_sm 3.8.0

Restricted file storage requires a POSIX system such as macOS or Linux.

## Setup

Run these commands from the repository root:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r ai_pipeline/requirements.txt
```

If the environment already exists, activate it rather than recreating it.

## Run the synthetic demonstration

```bash
python -m ai_pipeline.demo_deidentification
```

The demo directly exercises detection, replacement, and restricted storage.
It does not exercise the intake gate.

For the supplied synthetic example, the expected replacement count is 5.
The result requires human review.

Each demo run creates a new mapping file.

## Run all tests

```bash
python -m unittest discover -s ai_pipeline/tests -v
```

Expected: 29 tests ending in OK.

Tests cover replacement logic, validation, controlled failures, storage
permissions, adapter guardrails, and one real-model integration example.

Passing these tests does not establish accuracy on unfamiliar transcripts.

## Files

- deidentification.py: entity detection and replacement.
- key_store.py: separate restricted mapping storage.
- pipeline_adapter.py: eligibility checks and review/error outcomes.
- demo_deidentification.py: synthetic demonstration.
- tests/: automated tests.

## Mapping storage

Default location:

~/vcnity_story13_private_keys

Directory permissions: 700.
Mapping file permissions: 600.

The mapping files contain original identifiers and must not be committed
to GitHub or included in ordinary outputs or logs.

Storage is not encrypted by this module. It does not provide production
access auditing or a retention/deletion workflow. Processes running under
the same user account can access these files.

## Adapter contract

Call process_transcript with:
- transcript_id
- text
- tier
- consent_confirmed
- synthetic
- entity_detector
- key_store

Tier and consent must come from a trusted upstream workflow.
The adapter does not authenticate users or establish consent itself.

Successful processing returns:
- status: pending_human_review
- ready_for_ai: false
- result: de-identified output and mapping reference
- exception: null

Blocked processing returns:
- status: blocked
- ready_for_ai: false
- result: null
- exception: safe code, processing ID, and stage

Exception records are returned to the caller, not persisted in a queue.
The caller must retain the processing-ID association with the source
record in protected storage.

## Limitations

- Synthetic data only.
- English-language MVP.
- Initial categories: person, place, email, and selected Australian
  mobile/geographic phone formats.
- Detection may miss identifiers or replace non-identifying text.
- Aliases, indirect identifiers, and cultural context are not reliably
  resolved.
- Repeated replacement uses exact entity text and category, not
  person-level identity matching.
- Human review is required even when no entities are detected.
- No automatic release to AI analysis is implemented.

## Remaining integration work

Agree the Python-to-Java interface with the technical lead.
Connect the adapter to the team's trusted intake workflow and exceptions
queue. Verify review handling and onward processing in the actual system.

The local integration test covers the path through to pending human
review; it does not demonstrate the complete team application.