import { useMemo, useState } from "react";
import "./CommunityWorkspace.css";
import CommunityHeader, { ChatButton } from "./CommunityHeader";

export default function Groups({
  onNavigate,
  groups = [],
  memberships = [],
  loading = false,
  membershipLoadingId = null,
  onJoinGroup,
  onLeaveGroup,
}) {
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");

  const joinedIds = useMemo(
    () => new Set(memberships.map((membership) => membership.groupId)),
    [memberships],
  );

  const filtered = useMemo(() => {
    const search = query.trim().toLowerCase();
    return groups.filter((group) => {
      const matchesSearch = `${group.name || ""} ${group.description || ""} ${group.category || ""}`
        .toLowerCase()
        .includes(search);
      const isJoined = joinedIds.has(group.id);
      if (filter === "joined") return matchesSearch && isJoined;
      if (filter === "suggested") return matchesSearch && !isJoined;
      return matchesSearch;
    });
  }, [groups, joinedIds, query, filter]);

  const joinedGroups = groups.filter((group) => joinedIds.has(group.id));
  const suggestedGroups = groups.filter((group) => !joinedIds.has(group.id));

  async function handleMembership(group, isJoined) {
    if (isJoined) await onLeaveGroup?.(group.id);
    else await onJoinGroup?.(group.id);
  }

  function GroupCard({ group }) {
    const isJoined = joinedIds.has(group.id);
    const busy = membershipLoadingId === group.id;
    return (
      <article className="community-card group-card" key={group.id || group.name}>
        <div className="group-placeholder">👥</div>
        <span className="tag">{group.category}</span>
        <h2>{group.name}</h2>
        <p>{group.description}</p>
        <div className="group-card-actions">
          <button onClick={() => onNavigate("groupView", group)} className="secondary-button">
            View Group
          </button>
          <button
            type="button"
            className={isJoined ? "secondary-button" : "primary-button"}
            disabled={busy}
            onClick={() => handleMembership(group, isJoined)}
          >
            {busy ? "Please wait..." : isJoined ? "Leave Group" : "Join Group"}
          </button>
        </div>
      </article>
    );
  }

  return (
    <div className="community-page">
      <CommunityHeader active="groups" onNavigate={onNavigate} />
      <div className="breadcrumb">Community Portal › Groups</div>
      <main className="standard-page">
        <div className="page-title-row">
          <div>
            <h1>Groups</h1>
            <p>Discover community groups, connect with members and share ideas.</p>
          </div>
          <button className="primary-button" onClick={() => onNavigate("createGroup")}>+ Create Group</button>
        </div>

        <div className="search-row">
          <input type="search" placeholder="Search groups..." value={query} onChange={(event) => setQuery(event.target.value)} />
          <select value={filter} onChange={(event) => setFilter(event.target.value)} aria-label="Group filter">
            <option value="all">All groups</option>
            <option value="joined">My groups</option>
            <option value="suggested">Suggested groups</option>
          </select>
        </div>

        {loading && <p>Loading groups...</p>}

        {!loading && !query && filter === "all" && (
          <>
            <section className="groups-section">
              <div className="section-heading-row">
                <div><h2>My Groups</h2><p>Groups you have already joined.</p></div>
                <span className="tag">{joinedGroups.length}</span>
              </div>
              {joinedGroups.length === 0 ? (
                <section className="community-card"><p>You have not joined a group yet. Explore the suggestions below.</p></section>
              ) : (
                <div className="groups-grid">{joinedGroups.map((group) => <GroupCard group={group} key={group.id || group.name} />)}</div>
              )}
            </section>

            <section className="groups-section">
              <div className="section-heading-row">
                <div><h2>Suggested Groups</h2><p>Discover other community groups you can join.</p></div>
                <span className="tag">{suggestedGroups.length}</span>
              </div>
              {suggestedGroups.length === 0 ? (
                <section className="community-card"><p>No additional groups are available right now.</p></section>
              ) : (
                <div className="groups-grid">{suggestedGroups.map((group) => <GroupCard group={group} key={group.id || group.name} />)}</div>
              )}
            </section>
          </>
        )}

        {!loading && (query || filter !== "all") && (
          filtered.length === 0
            ? <section className="community-card"><p>No community groups found.</p></section>
            : <div className="groups-grid">{filtered.map((group) => <GroupCard group={group} key={group.id || group.name} />)}</div>
        )}
      </main>
      <ChatButton onNavigate={onNavigate} />
    </div>
  );
}
