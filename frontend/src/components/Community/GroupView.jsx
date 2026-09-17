import "./CommunityWorkspace.css";
import CommunityHeader, { Avatar, ChatButton } from "./CommunityHeader";

export default function GroupView({ onNavigate }) {
  return (
    <div className="community-page">
      <CommunityHeader active="groups" onNavigate={onNavigate} />

      <div className="breadcrumb">
        Community Portal › Groups › Local Community Group
      </div>

      <main className="standard-page">
        <button
          className="back-button"
          onClick={() => onNavigate("groups")}
        >
          ← Back to Groups
        </button>

        <section className="community-card group-hero">
          <div>
            <span className="tag">Community</span>
            <h1>Local Community Group</h1>
            <p>
              A space for local residents to connect, share ideas and
              collaborate on community projects.
            </p>

            <span>12 members</span>
          </div>

          <button className="primary-button">Joined ✓</button>
        </section>

        <div className="group-view-layout">
          <section>
            <div className="group-tabs">
              <button className="active">Activity</button>
              <button>About</button>
              <button>Members</button>
            </div>

            <section
              className="community-card composer"
              onClick={() => onNavigate("createPost")}
            >
              <div className="composer-row">
                <Avatar initials="AT" />
                <div className="composer-input">
                  Share something with this group...
                </div>
              </div>

              <button className="primary-button">Create Post</button>
            </section>

            <article className="community-card post-card">
              <div className="post-author">
                <Avatar initials="JM" />

                <div>
                  <strong>Jordan Mitchell</strong>
                  <p>2 hours ago</p>
                </div>
              </div>

              <p>
                Thanks everyone who attended today's community discussion.
                There were some great ideas about improving our shared spaces.
              </p>

              <div className="post-actions">
                <button>♡ Like</button>
                <button>Comment</button>
                <button>Share</button>
              </div>
            </article>
          </section>

          <aside>
            <section className="community-card">
              <h2>About this group</h2>
              <p>
                Community discussions, local initiatives and collaborative
                projects.
              </p>

              <hr />

              <p><strong>12</strong> Members</p>
              <p><strong>Public</strong> Group</p>
            </section>

            <section className="community-card">
              <h2>Members</h2>

              <div className="member-row">
                <Avatar initials="AT" />
                <span>Alex Thompson</span>
              </div>

              <div className="member-row">
                <Avatar initials="JM" />
                <span>Jordan Mitchell</span>
              </div>

              <div className="member-row">
                <Avatar initials="SC" />
                <span>Sophie Chen</span>
              </div>
            </section>
          </aside>
        </div>
      </main>

      <ChatButton onNavigate={onNavigate} />
    </div>
  );
}