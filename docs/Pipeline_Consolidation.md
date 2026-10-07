# Pipeline consolidation

## Implemented
- PipelineService delegates coding and grounding to BatchCodingJob.
- PipelineRun retains every produced CodedFinding, including finding IDs,
  source references, speaker codes, confidence values and grounding flags.
- Tier 3 and untiered sources are rejected before coding.
- Coding failures and empty outputs have explicit outcomes.
- Coverage uses actual speaker codes and produced findings.
- Coverage reports NOT_ASSESSED, PARTIAL, GAPS or COMPLETE.
- Missing-speaker flags refer to source IDs, not speaker IDs.
- Exception routing supports all five shared finding flags.
- The queue additionally supports REPRESENTATION_GAP, which is separate
  from the CodedFinding schema.
- Existing four-argument PipelineItem and PipelineRun constructors remain
  available for compatibility.

## Verification
- Full backend suite: 94 tests passed, none failed or skipped.
- Frontend suite: 17 tests passed.
- Frontend production build: passed.
- New pipeline tests use synthetic text and controlled coders.
- These results do not verify the live AI-provider path.

## Integration boundaries
- PipelineService returns results; it does not write to the exceptions queue.
  The caller must coordinate routing to avoid duplicate review items.
- The legacy clean label means automated finding checks passed.
  It does not grant human approval or publication permission.
- Coverage measures speaker inclusion among eligible supplied sources.
  It does not prove every source produced findings or every quote is grounded.
- COMPLETE coverage does not establish consent, grounding or publication approval.
- Missing speaker codes remain null and prevent a complete coverage assessment.
- Existing intake callers still need to consume full findings and coverage status.
- Intake must retain the separate fix that checks all matching outcomes.
- Consent and source eligibility must be established by the upstream workflow.
- MongoDB findings persistence and intake-to-persistence wiring are not included.
- Report grouping and report publication approval are not implemented here.
- The exceptions queue remains in memory.
- The existing live-provider JSON parsing and mock-coder behaviour remain unchanged.
- npm reported 8 dependency vulnerabilities: 3 moderate, 2 high and 3 critical.
  Dependency remediation remains outstanding.

## Review order
Merge the shared CodedFinding contract first, then review this consolidation,
then update and review persistence against the consolidated pipeline.
This branch includes the existing Story 18/19 foundation as dependencies.
