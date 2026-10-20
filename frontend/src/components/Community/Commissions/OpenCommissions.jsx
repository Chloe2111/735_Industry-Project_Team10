import { useEffect, useState } from "react";
import "./Commissions.css";
import { SAMPLE_COMMISSIONS, listOpenCommissions } from "../../../services/openCommissions";
import CommunityHeader, { ChatButton } from "../CommunityHeader";

export default function OpenCommissions({ onNavigate }) {
  const [commissions, setCommissions] = useState(SAMPLE_COMMISSIONS);
  const [loadError, setLoadError] = useState("");
  const [category, setCategory] = useState("all");

  useEffect(() => {
    const controller = new AbortController();
    listOpenCommissions({ signal: controller.signal })
      .then((result) => {
        setCommissions(result.commissions);
        setLoadError(result.loadError);
      })
      .catch(() => {});
    return () => controller.abort();
  }, []);

  const categories = [...new Set(commissions.map((commission) => commission.category))];
  const shown = category === "all" ? commissions : commissions.filter((commission) => commission.category === category);

  return (
    <div className="commission-page">
      <CommunityHeader active="commissions" onNavigate={onNavigate} crumbs={["Commissions"]} />

      <main className="commission-layout">
        <CommunitySidebar onNavigate={onNavigate} />

        <section className="commission-main">
          <div className="page-heading">
            <div>
              <h1>Open Commissions</h1>
              <p>
                Real opportunities to make an impact. Share your ideas, skills
                and experience with organisations and councils.
              </p>
            </div>

            <select
              className="category-filter"
              value={category}
              onChange={(event) => setCategory(event.target.value)}
              aria-label="Category"
            >
              <option value="all">All categories</option>
              {categories.map((name) => (
                <option key={name}>{name}</option>
              ))}
            </select>
          </div>

          {loadError && <p className="form-error">{loadError}</p>}

          <div className="commission-list">
            {shown.map((commission) => (
              <article className="commission-card" key={commission.id}>
                <div className="commission-card-top">
                  <div>
                    <span className="category-badge">
                      {commission.category}
                    </span>

                    <h2>{commission.title}</h2>
                    <p className="organisation">
                      {commission.organisation}
                    </p>
                  </div>

                  <button
                    className="bookmark-button"
                    type="button"
                    aria-label="Save commission"
                  >
                    ♡
                  </button>
                </div>

                <div className="commission-meta">
                  <div>
                    <span>Closes</span>
                    <strong>{commission.deadline}</strong>
                  </div>

                  <div>
                    <span>Incentive</span>
                    <strong>{commission.incentive}</strong>
                  </div>
                </div>

                <button
                  className="black-button"
                  type="button"
                  onClick={() => onNavigate("details", commission)}
                >
                  View &amp; participate →
                </button>
              </article>
            ))}
          </div>
        </section>
      </main>

      <ChatButton onNavigate={onNavigate} />
    </div>
  );
}

export function CommunitySidebar({ onNavigate }) {
  return (
    <aside className="community-sidebar">
      <div className="sidebar-card">
        <h3>My Groups</h3>

        <div className="sidebar-group">
          <span>Local Community Group</span>
          <strong>12</strong>
        </div>

        <div className="sidebar-group">
          <span>Youth Design Collective</span>
          <strong>8</strong>
        </div>

        <div className="sidebar-group">
          <span>Green Spaces Initiative</span>
          <strong>23</strong>
        </div>

        <button className="orange-link" type="button" onClick={() => onNavigate?.("groups")}>
          View all
        </button>
      </div>

      <div className="sidebar-card">
        <h3>Quick Links</h3>
        <button type="button">Community Guidelines</button>
        <button type="button">Events Calendar</button>
        <button type="button">Resource Library</button>
        <button type="button">Contact Support</button>
      </div>
    </aside>
  );
}
