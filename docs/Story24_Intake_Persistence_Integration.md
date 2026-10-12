# Story 24 intake findings persistence

## Implemented
- DefaultPipelineRunner receives FindingRepository through Spring injection.
- The runner saves the findings returned by PipelineService without rerunning
  coding or generating replacement finding IDs.
- Finding source references and duplicate IDs are checked before writing.
- Storage failures or missing save confirmations propagate before intake can
  publish the submission.
- Intake remains responsible for its tagged exception items.
  The runner does not create additional queue entries.
- Tier 3 is rejected before coding and finding storage.

## Verification
- Full backend suite with MongoDB integration enabled: 187 tests passed,
  with no failures, errors or skipped tests.
- Six persistence tests cover multiple findings, first-write failure,
  partial-write failure, missing confirmation, source mismatch and duplicate IDs.
- A real MongoDB test verifies intake service, controlled coding, finding
  persistence, retrieval and a single tagged review item.
- That test uses in-memory submission/group stores and synthetic data.
  It does not test browser submission or a live AI provider.
- The preceding merge passed 24 frontend tests and a production build.
  Subsequent integration changes were backend-only.

## Remaining limitations
- Finding writes and submission storage are not one atomic transaction.
  Earlier findings can remain stored when a later operation fails.
- Resubmitting intake generates a new submission ID and reruns coding.
  This is not an idempotent submission retry or an automatic recovery mechanism.
- The intake request does not provide speaker codes. They remain null.
  The runner does not yet use the coverage assessment status as a publication gate.
- The runner fails closed on coding_failed and no_findings outcomes.
  Their user-facing handling still needs refinement.
- All findings are persisted, but the legacy intake outcome carries only one
  theme/quote/confidence tuple. Per-finding review linkage remains incomplete.
- The community publication rules are unchanged. Saved findings and automated
  clean outcomes do not establish report approval or community sign-off.
- Consent enforcement, report grouping and report publication approval are not
  completed by this change.
- The exceptions queue remains in memory.
- Live AI-provider parsing and dependency vulnerabilities remain separate work.

## Branch scope
This integration branch combines intake, consolidation and persistence.
It must not be presented as a persistence-only diff against main.
Coordinate dependency merges and review the final PR base before merging.
