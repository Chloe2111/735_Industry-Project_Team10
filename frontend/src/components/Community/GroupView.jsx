import { useEffect, useState } from "react";
import "./CommunityWorkspace.css";
import CommunityHeader, { Avatar, ChatButton } from "./CommunityHeader";
import { communityApi } from "../../services/communityApi";
import { useToast } from "../Toast/ToastProvider";

export default function GroupView({ onNavigate, group, currentUserId }) {
  const [posts, setPosts] = useState([]);
  const [members, setMembers] = useState([]);
  const [joined, setJoined] = useState(false);
  const [loading, setLoading] = useState(true);
  const toast = useToast();

  useEffect(() => {
    if (!group?.id) { setLoading(false); return; }
    const controller = new AbortController();
    Promise.all([
      communityApi.getGroupPosts(group.id, { signal: controller.signal }),
      communityApi.getMembers(group.id, { signal: controller.signal }),
    ]).then(([postData, memberData]) => {
      setPosts(Array.isArray(postData) ? postData : []);
      const safeMembers = Array.isArray(memberData) ? memberData : [];
      setMembers(safeMembers);
      setJoined(safeMembers.some((member) => member.userId === currentUserId));
    }).catch((error) => { if (error.name !== "AbortError") toast.error(error.message || "Unable to load this group."); }).finally(() => setLoading(false));
    return () => controller.abort();
  }, [group?.id, currentUserId, toast]);

  async function toggleMembership() {
    if (!group?.id) return;
    try {
      if (joined) { await communityApi.leaveGroup(group.id, currentUserId); setMembers((current) => current.filter((m) => m.userId !== currentUserId)); setJoined(false); toast.success("You left the group."); }
      else { const membership = await communityApi.joinGroup(group.id, currentUserId); setMembers((current) => current.some((m) => m.userId === currentUserId) ? current : [...current, membership]); setJoined(true); toast.success("You joined the group."); }
    } catch (error) { toast.error(error.message || "Unable to update membership."); }
  }

  if (!group) return <div className="community-page"><CommunityHeader active="groups" onNavigate={onNavigate} /><main className="standard-page"><section className="community-card"><h1>Group not selected</h1><button className="primary-button" onClick={() => onNavigate("groups")}>Back to Groups</button></section></main></div>;

  return <div className="community-page"><CommunityHeader active="groups" onNavigate={onNavigate} /><div className="breadcrumb">Community Portal › Groups › {group.name}</div><main className="standard-page"><button className="back-button" onClick={() => onNavigate("groups")}>← Back to Groups</button><section className="community-card group-hero"><div><span className="tag">{group.category}</span><h1>{group.name}</h1><p>{group.description}</p><span>{members.length} members</span></div><button className="primary-button" onClick={toggleMembership}>{joined ? "Joined ✓" : "Join Group"}</button></section><div className="group-view-layout"><section><div className="group-tabs"><button className="active">Activity</button><button>About</button><button>Members</button></div><section className="community-card composer" onClick={() => onNavigate("createPost")}><div className="composer-row"><Avatar initials="AT" /><div className="composer-input">Share something with this group...</div></div><button className="primary-button">Create Post</button></section>{loading && <p>Loading group activity...</p>}{!loading && posts.length === 0 && <section className="community-card"><p>No posts in this group yet.</p></section>}{posts.map((post) => <article className="community-card post-card" key={post.id}><div className="post-author"><Avatar initials={(post.userId || "CM").slice(0,2).toUpperCase()} /><div><strong>{post.userId || "Community member"}</strong></div></div><h3>{post.title}</h3><p>{post.content}</p></article>)}</section><aside><section className="community-card"><h2>About this group</h2><p>{group.description}</p><hr /><p><strong>{members.length}</strong> Members</p><p><strong>{group.privacy === "private" ? "Private" : "Public"}</strong> Group</p></section><section className="community-card"><h2>Members</h2>{members.length === 0 ? <p>No members yet.</p> : members.map((member) => <div className="member-row" key={member.id || member.userId}><Avatar initials={(member.userId || "CM").slice(0,2).toUpperCase()} /><span>{member.userId}</span></div>)}</section></aside></div></main><ChatButton onNavigate={onNavigate} /></div>;
}
