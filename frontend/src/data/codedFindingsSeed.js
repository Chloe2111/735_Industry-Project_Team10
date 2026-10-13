// Mock data shaped exactly like docs/CodedFinding.schema.json (Sharisha's locked
// pipeline contract, feature/coded-finding-contract). Field names and types here
// must match that schema field-for-field -- this is what gets swapped for a real
// GET call in Story 23.2, once Sharisha's persistence endpoint is merged.
//
// Includes a null speakerCode (schema allows it) and an empty flags array, since
// both are valid per the contract and the UI must handle them without crashing.
export const codedFindingsSeed = [
  {
    itemId: 'finding_001',
    sourceRef: 'transcript_014',
    speakerCode: 'PERSON_003',
    theme: 'Access to community activities',
    quote: 'We need affordable transport to attend community workshops.',
    confidence: 0.92,
    tier: 2,
    flags: [],
  },
  {
    itemId: 'finding_002',
    sourceRef: 'transcript_014',
    speakerCode: null,
    theme: 'Safety hazard',
    quote: 'The ramp near the entrance has a crack that needs fixing.',
    confidence: 0.58,
    tier: 1,
    flags: ['lowConfidence'],
  },
  {
    itemId: 'finding_003',
    sourceRef: 'transcript_021',
    speakerCode: 'PERSON_007',
    theme: 'Inclusivity',
    quote: 'Some participants felt excluded from the main activity area.',
    confidence: 0.81,
    tier: 2,
    flags: ['quoteNotGrounded', 'contradiction'],
  },
]
