import ExceptionsQueue from '../components/Exceptions/ExceptionsQueue'
import { getSubmission, publishSubmission } from '../services/intakeApi'

// Gives the review screen the two things it needs to publish a community submission.
// Clearing a flag never publishes; publishing is this separate, explicit action.
const COMMUNITY_SUBMISSIONS = { getStatus: getSubmission, publish: publishSubmission }

export function ExceptionsQueuePage() {
  return <ExceptionsQueue communitySubmissions={COMMUNITY_SUBMISSIONS} />
}
