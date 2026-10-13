import { useCallback, useEffect, useState } from "react";
import "./CommunityWorkspace.css";
import { communityApi } from "../../services/communityApi";
import { useToast } from "../Toast/ToastProvider";

import Dashboard from "./Dashboard";
import Groups from "./Groups";
import GroupView from "./GroupView";
import CreateGroup from "./CreateGroup";
import CreatePost from "./CreatePost";
import Messages from "./Messages";
import Profile from "./Profile";
import OpenCommissions from "./Commissions/OpenCommissions";
import CommissionDetails from "./Commissions/CommissionDetails";
import SubmitIdea from "./Commissions/SubmitIdea";
import SubmissionSuccess from "./Commissions/SubmissionSuccess";

// Temporary identity adapter until the team's shared authentication provider is connected.
// Keeping it here makes replacing it with the authenticated user a single change.
const CURRENT_USER_ID = "community-demo-user";

export default function CommunityWorkspace() {
  const [currentPage, setCurrentPage] = useState("dashboard");
  const [groups, setGroups] = useState([]);
  const [posts, setPosts] = useState([]);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [memberships, setMemberships] = useState([]);
  const [membershipLoadingId, setMembershipLoadingId] = useState(null);
  const [loading, setLoading] = useState(true);
  const toast = useToast();

  const refreshCommunity = useCallback(async () => {
    setLoading(true);
    try {
      const [groupData, postData, membershipData] = await Promise.all([
        communityApi.getGroups(),
        communityApi.getFeed(),
        communityApi.getMemberships(CURRENT_USER_ID),
      ]);
      setGroups(Array.isArray(groupData) ? groupData : []);
      setPosts(Array.isArray(postData) ? postData : []);
      setMemberships(Array.isArray(membershipData) ? membershipData : []);
    } catch (error) {
      toast.error(error.message || "Unable to load the community workspace.");
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    refreshCommunity();
  }, [refreshCommunity]);

  function navigateTo(page, payload) {
    if (page === "groupView" && payload) setSelectedGroup(payload);
    setCurrentPage(page);
    window.scrollTo(0, 0);
  }

  async function handleCreateGroup(form) {
    const created = await communityApi.createGroup({
      name: form.name.trim(),
      description: form.description.trim(),
      category: form.category,
      privacy: form.privacy,
      createdBy: CURRENT_USER_ID,
    });
    setGroups((current) => [...current, created]);
    toast.success("Group created successfully.");
    return created;
  }

  async function handleJoinGroup(groupId) {
    setMembershipLoadingId(groupId);
    try {
      const membership = await communityApi.joinGroup(groupId, CURRENT_USER_ID);
      setMemberships((current) =>
        current.some((item) => item.groupId === groupId) ? current : [...current, membership],
      );
      toast.success("You joined the group.");
    } catch (error) {
      toast.error(error.message || "Unable to join the group.");
    } finally {
      setMembershipLoadingId(null);
    }
  }

  async function handleLeaveGroup(groupId) {
    setMembershipLoadingId(groupId);
    try {
      await communityApi.leaveGroup(groupId, CURRENT_USER_ID);
      setMemberships((current) => current.filter((item) => item.groupId !== groupId));
      toast.success("You left the group.");
    } catch (error) {
      toast.error(error.message || "Unable to leave the group.");
    } finally {
      setMembershipLoadingId(null);
    }
  }

  async function handleCreatePost({ groupId, title, content }) {
    const created = await communityApi.createPost({
      groupId,
      userId: CURRENT_USER_ID,
      title: title.trim(),
      content: content.trim(),
    });
    setPosts((current) => [created, ...current]);
    toast.success("Post created successfully.");
    return created;
  }

  const common = { onNavigate: navigateTo };

  switch (currentPage) {
    case "dashboard": return <Dashboard {...common} groups={groups} posts={posts} loading={loading} />;
    case "groups": return <Groups {...common} groups={groups} memberships={memberships} loading={loading} membershipLoadingId={membershipLoadingId} onJoinGroup={handleJoinGroup} onLeaveGroup={handleLeaveGroup} />;
    case "groupView": return <GroupView {...common} group={selectedGroup} currentUserId={CURRENT_USER_ID} />;
    case "createGroup": return <CreateGroup {...common} onCreateGroup={handleCreateGroup} />;
    case "createPost": return <CreatePost {...common} groups={groups} selectedGroup={selectedGroup} onCreatePost={handleCreatePost} />;
    case "messages": return <Messages {...common} />;
    case "profile": return <Profile {...common} />;
    case "commissions": return <OpenCommissions {...common} />;
    case "details": return <CommissionDetails {...common} />;
    case "submit": return <SubmitIdea {...common} />;
    case "success": return <SubmissionSuccess {...common} />;
    default: return <Dashboard {...common} groups={groups} posts={posts} loading={loading} />;
  }
}
