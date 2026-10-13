import "./CommunityWorkspace.css";
import CommunityHeader, { Avatar, ChatButton } from "./CommunityHeader";

export default function Dashboard({ onNavigate, groups = [], posts = [], loading = false }) {
  const groupNames = new Map(groups.map((group) => [group.id, group.name]));
  return <div className="community-page">
    <CommunityHeader active="dashboard" onNavigate={onNavigate} />
    <div className="breadcrumb">Community Portal › Dashboard</div>
    <main className="dashboard-layout">
      <aside className="side-column"><section className="community-card"><div className="section-heading"><h2>My Groups</h2><button onClick={() => onNavigate("groups")}>View all</button></div>{groups.slice(0, 3).map((group) => <GroupLink key={group.id || group.name} name={group.name} onClick={() => onNavigate("groupView", group)} />)}{!loading && groups.length === 0 && <p>No groups yet.</p>}<button className="text-link" onClick={() => onNavigate("createGroup")}>+ Create new group</button></section><section className="community-card"><h2>Quick Links</h2><p>Community Guidelines</p><p>Events Calendar</p><p>Resource Library</p><p>Contact Support</p></section></aside>
      <section className="feed-column"><section className="community-card composer" onClick={() => onNavigate("createPost")}><div className="composer-row"><Avatar initials="AT" /><div className="composer-input">Share something with your community...</div></div><div className="composer-actions"><span>📷 Image</span><span>🎥 Video</span><span>🎵 Audio</span><button>Create Post</button></div></section><h1>Community Activity</h1>{loading && <p>Loading activity...</p>}{!loading && posts.length === 0 && <section className="community-card"><p>No posts yet. Create the first community update.</p></section>}{posts.map((post) => <Post key={post.id} name={post.userId || "Community member"} group={groupNames.get(post.groupId) || "Community group"} text={post.content} title={post.title} />)}</section>
      <aside className="side-column"><section className="community-card"><div className="section-heading"><h2>Open Commissions</h2><button onClick={() => onNavigate("commissions")}>View all</button></div><div className="commission-preview"><span className="tag">Community Design</span><h3>Skate Park Design Consultation</h3><p>Riverside City Council</p><p><strong>$200</strong> Community credit</p><p>Deadline: 31 May 2026</p><button onClick={() => onNavigate("details")}>View & participate</button></div></section></aside>
    </main><ChatButton onNavigate={onNavigate} />
  </div>;
}
function GroupLink({ name, onClick }) { return <button className="group-link" onClick={onClick}><strong>{name}</strong><span>View group</span></button>; }
function Post({ name, group, title, text }) { return <article className="community-card post-card"><div className="post-author"><Avatar initials={(name || "CM").slice(0, 2).toUpperCase()} /><div><strong>{name}</strong><p>{group}</p></div></div>{title && <h3>{title}</h3>}<p>{text}</p><div className="post-actions"><button>♡ Like</button><button>Comment</button><button>Share</button></div></article>; }
