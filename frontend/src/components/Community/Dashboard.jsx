import "./CommunityWorkspace.css";
import CommunityHeader, { Avatar, ChatButton } from "./CommunityHeader";

export default function Dashboard({ onNavigate }) {
  return (
    <div className="community-page">
      <CommunityHeader active="dashboard" onNavigate={onNavigate} />

      <div className="breadcrumb">Community Portal › Dashboard</div>

      <main className="dashboard-layout">
        <aside className="side-column">
          <section className="community-card">
            <div className="section-heading">
              <h2>My Groups</h2>
              <button onClick={() => onNavigate("groups")}>View all</button>
            </div>

            <GroupLink
              name="Local Community Group"
              members="12 members"
              onClick={() => onNavigate("groupView")}
            />
            <GroupLink
              name="Youth Design Collective"
              members="8 members"
              onClick={() => onNavigate("groupView")}
            />
            <GroupLink
              name="Green Spaces Initiative"
              members="23 members"
              onClick={() => onNavigate("groupView")}
            />

            <button
              className="text-link"
              onClick={() => onNavigate("createGroup")}
            >
              + Create new group
            </button>
          </section>

          <section className="community-card">
            <h2>Quick Links</h2>
            <p>Community Guidelines</p>
            <p>Events Calendar</p>
            <p>Resource Library</p>
            <p>Contact Support</p>
          </section>
        </aside>

        <section className="feed-column">
          <section
            className="community-card composer"
            onClick={() => onNavigate("createPost")}
          >
            <div className="composer-row">
              <Avatar initials="AT" />
              <div className="composer-input">
                Share something with your community...
              </div>
            </div>

            <div className="composer-actions">
              <span>📷 Image</span>
              <span>🎥 Video</span>
              <span>🎵 Audio</span>
              <button>Create Post</button>
            </div>
          </section>

          <h1>Community Activity</h1>

          <Post
            initials="JM"
            name="Jordan Mitchell"
            group="Local Community Group"
            time="2 hours ago"
            text="Great discussion at our community meeting today. We collected some useful ideas for improving the local park and shared spaces."
          />

          <Post
            initials="SC"
            name="Sophie Chen"
            group="Green Spaces Initiative"
            time="Yesterday"
            text="We're looking for community feedback on ideas for greener and more accessible public spaces."
          />
        </section>

        <aside className="side-column">
          <section className="community-card">
            <div className="section-heading">
              <h2>Open Commissions</h2>
              <button onClick={() => onNavigate("commissions")}>
                View all
              </button>
            </div>

            <div className="commission-preview">
              <span className="tag">Community Design</span>
              <h3>Skate Park Design Consultation</h3>
              <p>Riverside City Council</p>
              <p><strong>$200</strong> Community credit</p>
              <p>Deadline: 31 May 2026</p>

              <button onClick={() => onNavigate("details")}>
                View & participate
              </button>
            </div>
          </section>
        </aside>
      </main>

      <ChatButton onNavigate={onNavigate} />
    </div>
  );
}

function GroupLink({ name, members, onClick }) {
  return (
    <button className="group-link" onClick={onClick}>
      <strong>{name}</strong>
      <span>{members}</span>
    </button>
  );
}

function Post({ initials, name, group, time, text }) {
  return (
    <article className="community-card post-card">
      <div className="post-author">
        <Avatar initials={initials} />
        <div>
          <strong>{name}</strong>
          <p>{group} · {time}</p>
        </div>
      </div>

      <p>{text}</p>

      <div className="post-actions">
        <button>♡ Like</button>
        <button>Comment</button>
        <button>Share</button>
      </div>
    </article>
  );
}