import "./CommunityWorkspace.css";
import CommunityHeader, { ChatButton } from "./CommunityHeader";

export default function Groups({ onNavigate, groups = [] }) {

  return (
    <div className="community-page">
      <CommunityHeader active="groups" onNavigate={onNavigate} />

      <div className="breadcrumb">Community Portal › Groups</div>

      <main className="standard-page">
        <div className="page-title-row">
          <div>
            <h1>Groups</h1>
            <p>
              Join community groups, connect with members and share ideas.
            </p>
          </div>

          <button
            className="primary-button"
            onClick={() => onNavigate("createGroup")}
          >
            + Create Group
          </button>
        </div>

        <div className="search-row">
          <input
            type="search"
            placeholder="Search groups..."
          />

          <select defaultValue="all">
            <option value="all">All groups</option>
            <option value="joined">My groups</option>
            <option value="popular">Popular</option>
          </select>
        </div>

        <div className="groups-grid">
          {groups.map((group) => (
            <article className="community-card group-card" key={group.name}>
              <div className="group-placeholder">👥</div>

              <h2>{group.name}</h2>
              <p>{group.description}</p>
              <span>{group.members} members</span>

              <button
                onClick={() => onNavigate("groupView")}
                className="secondary-button"
              >
                View Group
              </button>
            </article>
          ))}
        </div>
      </main>

      <ChatButton onNavigate={onNavigate} />
    </div>
  );
}