import { useState } from "react";
import "./CommunityWorkspace.css";

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
export default function CommunityWorkspace() {
  const [currentPage, setCurrentPage] = useState("dashboard");

  const [groups, setGroups] = useState([
    {
      name: "Local Community Group",
      description: "Connect with local residents and community projects.",
      category: "Community",
      privacy: "public",
      members: 12,
    },
    {
      name: "Youth Design Collective",
      description: "A collaborative space for young community designers.",
      category: "Design",
      privacy: "public",
      members: 8,
    },
    {
      name: "Green Spaces Initiative",
      description: "Working together for greener public spaces.",
      category: "Sustainability",
      privacy: "public",
      members: 23,
    },
  ]);

  function navigateTo(page) {
    setCurrentPage(page);
    window.scrollTo(0, 0);
  }

  function handleCreateGroup(group) {
    const newGroup = {
      ...group,
      members: 1,
    };

    setGroups((previousGroups) => [
      ...previousGroups,
      newGroup,
    ]);
  }

  switch (currentPage) {
    case "dashboard":
      return (
        <Dashboard
          onNavigate={navigateTo}
          groups={groups}
        />
      );

    case "groups":
      return (
        <Groups
          onNavigate={navigateTo}
          groups={groups}
        />
      );

    case "groupView":
      return (
        <GroupView
          onNavigate={navigateTo}
        />
      );

    case "createGroup":
      return (
        <CreateGroup
          onNavigate={navigateTo}
          onCreateGroup={handleCreateGroup}
        />
      );

    case "createPost":
      return (
        <CreatePost
          onNavigate={navigateTo}
        />
      );

    case "messages":
      return (
        <Messages
          onNavigate={navigateTo}
        />
      );

    case "profile":
      return (
        <Profile
          onNavigate={navigateTo}
        />
      );

    case "commissions":
      return (
        <OpenCommissions
          onNavigate={navigateTo}
        />
      );

    case "details":
      return (
        <CommissionDetails
          onNavigate={navigateTo}
        />
      );

    case "submit":
      return (
        <SubmitIdea
          onNavigate={navigateTo}
        />
      );

    case "success":
      return (
        <SubmissionSuccess
          onNavigate={navigateTo}
        />
      );

    default:
      return (
        <Dashboard
          onNavigate={navigateTo}
          groups={groups}
        />
      );
  }
}