import { CommunitySidebar } from "./OpenCommissions";
import CommunityHeader, { ChatButton } from "../CommunityHeader";
import { tierName } from "../../../constants/tiers";
import { SAMPLE_COMMISSIONS } from "../../../services/openCommissions";

const STATUS_LABELS = {
  PUBLISHED: "Published",
  PENDING_REVIEW: "Waiting for a facilitator to review",
  CLEARED: "Checked – waiting to be published",
  REJECTED: "Not published",
  HELD: "Held – not published",
};

function formatWhen(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export default function SubmissionSuccess({ onNavigate, submission = null, commission = SAMPLE_COMMISSIONS[0] }) {
  const receipt = submission?.receipt ?? null;
  return (
    <div className="commission-page">
      <CommunityHeader active="commissions" onNavigate={onNavigate} crumbs={["Commissions", "Submission Confirmation"]} />

      <main className="commission-layout">
        <CommunitySidebar onNavigate={onNavigate} />

        <section className="success-layout">
          <article className="success-card">
            <div className="success-check">✓</div>

            <h1>Idea submitted successfully!</h1>

            <p className="success-intro">
              Thank you for contributing to the{" "}
              <strong>{commission.title}.</strong>
            </p>

            <p>
              Your idea has been received and will be reviewed by{" "}
              {commission.source === "client" ? "the organisation that posted this commission" : commission.organisation}.
            </p>

            <div className="submission-details">
              <h2>Submission details</h2>

              <div className="submission-detail-row">
                <span>Idea title</span>
                <strong>{submission?.title || "—"}</strong>
              </div>

              <div className="submission-detail-row">
                <span>Submitted on</span>
                <strong>{receipt ? formatWhen(receipt.submittedAt) : "—"}</strong>
              </div>

              <div className="submission-detail-row">
                <span>Handled as</span>
                <strong>{receipt ? tierName(receipt.tier) : "—"}</strong>
              </div>

              <div className="submission-detail-row">
                <span>Reference ID</span>
                <strong>{receipt?.id ?? "—"}</strong>
              </div>

              <div className="submission-detail-row">
                <span>Status</span>
                <strong className="status-badge">{receipt ? (STATUS_LABELS[receipt.status] ?? receipt.status) : "—"}</strong>
              </div>

              {receipt?.message && <p className="submission-receipt-message">{receipt.message}</p>}
            </div>

            <div className="success-buttons">
              <button className="black-button" type="button">
                View My Submissions
              </button>

              <button
                className="secondary-button"
                type="button"
                onClick={() => onNavigate("commissions")}
              >
                Back to Commissions
              </button>
            </div>
          </article>

          <aside className="success-side">
            <div className="guidance-card">
              <h2>What happens next?</h2>

              <div className="next-step">
                <span>1</span>
                <p>
                  Your submission will be reviewed by the organisation.
                </p>
              </div>

              <div className="next-step">
                <span>2</span>
                <p>
                  You may be contacted if further information is required.
                </p>
              </div>

              <div className="next-step">
                <span>3</span>
                <p>
                  You'll receive an update when the status of your submission
                  changes.
                </p>
              </div>

              <div className="next-step">
                <span>4</span>
                <p>
                  Keep an eye on the community page for more opportunities.
                </p>
              </div>
            </div>

            <div className="help-card">
              <h3>Need help?</h3>
              <button type="button">Community Guidelines</button>
              <button type="button">Contact Support</button>
            </div>
          </aside>
        </section>
      </main>

      <ChatButton onNavigate={onNavigate} />
    </div>
  );
}