import { CommunitySidebar } from "./OpenCommissions";
import CommunityHeader, { ChatButton } from "../CommunityHeader";

export default function CommissionDetails({ onNavigate }) {
  return (
    <div className="commission-page">
      <CommunityHeader active="commissions" onNavigate={onNavigate} />

      <div className="breadcrumb">
        Community Portal <span>›</span> Commissions <span>›</span> Skate Park
        Design Consultation
      </div>

      <main className="commission-layout">
        <CommunitySidebar onNavigate={onNavigate} />

        <section className="details-layout">
          <article className="details-card">
            <button
              className="back-link"
              type="button"
              onClick={() => onNavigate("commissions")}
            >
              ← Back to Commissions
            </button>

            <span className="category-badge">Community Design</span>

            <h1>Skate Park Design Consultation</h1>

            <p className="organisation-large">
              Riverside City Council
            </p>

            <p className="intro-text">
              Help shape a new community skate park by sharing your ideas,
              experience and suggestions.
            </p>

            <div className="detail-information">
              <div>
                <span>Closing date</span>
                <strong>31 May 2026</strong>
              </div>

              <div>
                <span>Incentive</span>
                <strong>$200 Community credit</strong>
              </div>

              <div>
                <span>Organisation</span>
                <strong>Riverside City Council</strong>
              </div>
            </div>

            <div className="details-actions">
              <button
                className="black-button large-button"
                type="button"
                onClick={() => onNavigate("submit")}
              >
                Submit an Idea →
              </button>

              <button className="secondary-button" type="button">
                ♡ Save for later
              </button>
            </div>

            <div className="about-section">
              <h2>About this commission</h2>

              <p>
                We want to create a skate park that encourages active
                lifestyles, creativity and community connection. Your ideas
                will help shape the design, features and overall experience.
              </p>

              <p>
                We're looking for input from people of all ages and
                backgrounds, including young people, families, local residents
                and community groups.
              </p>
            </div>
          </article>

          <aside className="guidance-column">
            <div className="guidance-card">
              <h2>What we're looking for</h2>

              <ul className="check-list">
                <li>Creative and inclusive design ideas</li>
                <li>Safety and accessibility considerations</li>
                <li>Ideas for different age groups and skill levels</li>
                <li>Sustainable and durable materials</li>
                <li>Integration with surrounding community spaces</li>
              </ul>
            </div>

            <div className="guidance-card">
              <h2>Guidelines for your submission</h2>

              <div className="guideline">
                <span>1</span>
                <div>
                  <strong>What are you proposing?</strong>
                  <p>Describe your idea clearly.</p>
                </div>
              </div>

              <div className="guideline">
                <span>2</span>
                <div>
                  <strong>What problem does it solve?</strong>
                  <p>Explain the need or challenge.</p>
                </div>
              </div>

              <div className="guideline">
                <span>3</span>
                <div>
                  <strong>Who will benefit?</strong>
                  <p>Consider different community groups.</p>
                </div>
              </div>

              <div className="guideline">
                <span>4</span>
                <div>
                  <strong>How could it be implemented?</strong>
                  <p>Think about feasibility and potential costs.</p>
                </div>
              </div>

              <div className="guideline">
                <span>5</span>
                <div>
                  <strong>Supporting material</strong>
                  <p>Include sketches, images or references if useful.</p>
                </div>
              </div>
            </div>
          </aside>
        </section>
      </main>

      <ChatButton onNavigate={onNavigate} />
    </div>
  );
}