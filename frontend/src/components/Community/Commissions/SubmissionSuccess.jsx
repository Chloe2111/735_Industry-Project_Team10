import { CommunitySidebar } from "./OpenCommissions";
import CommunityHeader, { ChatButton } from "../CommunityHeader";

export default function SubmissionSuccess({ onNavigate }) {
  return (
    <div className="commission-page">
      <CommunityHeader active="commissions" onNavigate={onNavigate} />

      <div className="breadcrumb">
        Community Portal <span>›</span> Commissions <span>›</span> Submission
        Confirmation
      </div>

      <main className="commission-layout">
        <CommunitySidebar onNavigate={onNavigate} />

        <section className="success-layout">
          <article className="success-card">
            <div className="success-check">✓</div>

            <h1>Idea submitted successfully!</h1>

            <p className="success-intro">
              Thank you for contributing to the{" "}
              <strong>Skate Park Design Consultation.</strong>
            </p>

            <p>
              Your idea has been received and will be reviewed by Riverside
              City Council.
            </p>

            <div className="submission-details">
              <h2>Submission details</h2>

              <div className="submission-detail-row">
                <span>Idea title</span>
                <strong>Inclusive Skate Zone</strong>
              </div>

              <div className="submission-detail-row">
                <span>Submitted on</span>
                <strong>15 May 2026, 11:24 AM</strong>
              </div>

              <div className="submission-detail-row">
                <span>Reference ID</span>
                <strong>VCN-2026-000123</strong>
              </div>

              <div className="submission-detail-row">
                <span>Status</span>
                <strong className="status-badge">Under Review</strong>
              </div>
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