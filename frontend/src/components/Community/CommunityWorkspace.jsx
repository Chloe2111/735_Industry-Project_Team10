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
import CommunityHeader from "./CommunityHeader";
import CommunitySubmissionPage from "../../pages/CommunitySubmissionPage";

// Temporary identity adapter until the team's shared authentication provider is connected.
// Keeping it here makes replacing it with the authenticated user a single change.
const CURRENT_USER_ID = "community-demo-user";

function loadErrorMessage(error) {
  // 0 = no response; other 5xx = the backend (or the dev proxy in front of it) failed.
  // 503 is the backend's own "database unavailable" answer, which already explains itself.
  const serverDown = !error || error.status === 0 || (error.status >= 500 && error.status !== 503);
  if (serverDown) {
    return "We couldn't load the community workspace because the server isn't responding. Check that the backend is running, then try again.";
  }
  return error.message || "We couldn't load the community workspace. Please try again.";
}

export default function CommunityWorkspace() {
  const [currentPage, setCurrentPage] = useState("dashboard");
  const [groups, setGroups] = useState([]);
  const [posts, setPosts] = useState([]);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [memberships, setMemberships] = useState([]);
  const [membershipLoadingId, setMembershipLoadingId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [lastIdea, setLastIdea] = useState(null);
  const toast = useToast();

  // Loads groups, the activity feed and this member's groups together.
  // A load failure is shown once, in the page (with "Try again"), instead of as
  // toasts: the page would otherwise claim there are no groups or posts yet.
  const refreshCommunity = useCallback(async (signal) => {
    setLoading(true);
    setLoadError(null);
    try {
      const [groupData, postData, membershipData] = await Promise.all([
        communityApi.getGroups({ signal }),
        communityApi.getFeed({ signal }),
        communityApi.getMemberships(CURRENT_USER_ID, { signal }),
      ]);
      setGroups(Array.isArray(groupData) ? groupData : []);
      setPosts(Array.isArray(postData) ? postData : []);
      setMemberships(Array.isArray(membershipData) ? membershipData : []);
    } catch (error) {
      if (error?.name === "AbortError") return;
      setLoadError(loadErrorMessage(error));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Abort on unmount (and on React's development double-run), so one visit
    // makes one set of requests and reports at most one problem.
    const controller = new AbortController();
    refreshCommunity(controller.signal);
    return () => controller.abort();
  }, [refreshCommunity]);

  const retryLoad = () => refreshCommunity();

  function navigateTo(page, payload) {
    if (page === "groupView" && payload) setSelectedGroup(payload);
    if (page === "success") setLastIdea(payload ?? null);
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

  // Keeps "My Groups" right when someone joins or leaves from a group's own page.
  function syncMembership(groupId, membership) {
    setMemberships((current) => membership
      ? (current.some((item) => item.groupId === groupId) ? current : [...current, membership])
      : current.filter((item) => item.groupId !== groupId));
  }

  const common = { onNavigate: navigateTo };

  switch (currentPage) {
    case "dashboard": return <Dashboard {...common} groups={groups} posts={posts} memberships={memberships} loading={loading} loadError={loadError} onRetry={retryLoad} />;
    case "groups": return <Groups {...common} groups={groups} memberships={memberships} loading={loading} loadError={loadError} onRetry={retryLoad} membershipLoadingId={membershipLoadingId} onJoinGroup={handleJoinGroup} onLeaveGroup={handleLeaveGroup} />;
    case "groupView": return <GroupView {...common} group={selectedGroup} currentUserId={CURRENT_USER_ID} onMembershipChange={syncMembership} />;
    case "createGroup": return <CreateGroup {...common} onCreateGroup={handleCreateGroup} />;
    case "createPost": return <CreatePost {...common} groups={groups} selectedGroup={selectedGroup} onCreatePost={handleCreatePost} />;
    case "messages": return <Messages {...common} />;
    case "profile": return <Profile {...common} />;
    case "commissions": return <OpenCommissions {...common} />;
    case "details": return <CommissionDetails {...common} />;
    case "submit": return <SubmitIdea {...common} />;
    case "success": return <SubmissionSuccess {...common} submission={lastIdea} />;
    // Story 24 share-feedback form, shown under the community nav so members can get back.
    case "shareFeedback":
      return (
        <div className="community-page">
          <CommunityHeader active="shareFeedback" onNavigate={navigateTo} crumbs={["Share feedback"]} />
          <CommunitySubmissionPage />
        </div>
      );
    default: return <Dashboard {...common} groups={groups} posts={posts} memberships={memberships} loading={loading} loadError={loadError} onRetry={retryLoad} />;
  }
}
