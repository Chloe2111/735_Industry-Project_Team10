import { codedFindingsSeed } from '../data/codedFindingsSeed'

// Story 23.1: mock stand-in, shaped exactly like docs/CodedFinding.schema.json.
// Story 23.2 swaps this for `apiClient.get('/findings')` once Sharisha's
// persistence endpoint (Merge Sequence Step 3) is on main -- same `{ data }`
// shape apiClient already returns, so CodingFindingsPage won't need to change.
const LATENCY_MS = 250

export function listFindings() {
  return new Promise((resolve) => {
    setTimeout(() => resolve({ data: codedFindingsSeed }), LATENCY_MS)
  })
}
