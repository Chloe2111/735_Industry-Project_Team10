import "./CommunityWorkspace.css";
import CommunityHeader, { Avatar } from "./CommunityHeader";

export default function Profile({ onNavigate }) {
  return (
    <div className="community-page">
      <CommunityHeader active="" onNavigate={onNavigate} />

      <div className="breadcrumb">
        Community Portal › Profile
      </div>

      <main className="standard-page profile-page">
        <section className="community-card profile-header-card">
          <Avatar initials="AT" />

          <div>
            <h1>Alex Thompson</h1>
            <p>Community Member</p>
            <span>Brisbane, Queensland</span>
          </div>

          <button className="secondary-button">
            Edit Profile
          </button>
        </section>

        <div className="profile-layout">
          <section>
            <article className="community-card">
              <h2>About</h2>

              <p>
                Interested in community design, public spaces and
                collaborative local projects.
              </p>
            </article>

            <article className="community-card">
              <h2>Community Activity</h2>

              <div className="profile-stat-grid">
                <div>
                  <strong>3</strong>
                  <span>Groups</span>
                </div>

                <div>
                  <strong>8</strong>
                  <span>Posts</span>
                </div>

                <div>
                  <strong>2</strong>
                  <span>Commissions</span>
                </div>
              </div>
            </article>
          </section>

          <aside>
            <article className="community-card">
              <h2>My Groups</h2>

              <p>Local Community Group</p>
              <p>Youth Design Collective</p>
              <p>Green Spaces Initiative</p>

              <button
                className="text-link"
                onClick={() => onNavigate("groups")}
              >
                View all groups
              </button>
            </article>

            <article className="community-card">
              <h2>Interests</h2>

              <div className="tag-list">
                <span className="tag">Community Design</span>
                <span className="tag">Sustainability</span>
                <span className="tag">Public Spaces</span>
              </div>
            </article>
          </aside>
        </div>
      </main>
    </div>
  );
}