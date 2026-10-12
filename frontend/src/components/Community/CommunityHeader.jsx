import "./CommunityWorkspace.css";

export default function CommunityHeader({ active = "", onNavigate }) {
  const go = (page) => onNavigate?.(page);
  return (
    <>
      <div className="portal-topbar"><span>Client Portal</span><span>•</span><strong>Community Portal</strong></div>
      <nav className="portal-nav">
        <div className="brand-area"><span className="vcnity-logo">vcnity</span><span className="community-pill">Community</span></div>
        <div className="nav-links">
          <button className={active === "dashboard" ? "active" : ""} onClick={() => go("dashboard")}>Dashboard</button>
          <button className={active === "groups" ? "active" : ""} onClick={() => go("groups")}>Groups</button>
          <button className={active === "messages" ? "active" : ""} onClick={() => go("messages")}>Messages</button>
          <button className={active === "commissions" ? "active" : ""} onClick={() => go("commissions")}>Commissions</button>
        </div>
        <button className="profile-area profile-button" onClick={() => go("profile")} type="button"><span className="profile-avatar">AT</span><span>Alex Thompson</span></button>
      </nav>
    </>
  );
}

export function Avatar({ initials }) { return <span className="avatar">{initials}</span>; }
export function ChatButton({ onNavigate }) { return <button className="chat-button" type="button" aria-label="Open messages" onClick={() => onNavigate?.("messages")}><span>2</span>💬</button>; }
