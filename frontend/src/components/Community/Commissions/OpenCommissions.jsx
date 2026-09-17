import "./Commissions.css";
import CommunityHeader, { ChatButton } from "../CommunityHeader";

export default function OpenCommissions({ onNavigate }) {
  const commissions = [
    {
      id: 1,
      title: "Skate Park Design Consultation",
      organisation: "Riverside City Council",
      category: "Community Design",
      deadline: "31 May 2026",
      incentive: "$200 Community credit",
    },
    {
      id: 2,
      title: "Urban Greening Ideas",
      organisation: "Queensland Government",
      category: "Sustainability",
      deadline: "15 Nov 2026",
      incentive: "$500 Community credit",
    },
    {
      id: 3,
      title: "Riverside Public Spaces",
      organisation: "Brisbane City Council",
      category: "Infrastructure",
      deadline: "30 Nov 2026",
      incentive: "$450 Community credit",
    },
    {
      id: 4,
      title: "Youth Engagement Programs",
      organisation: "Queensland Government",
      category: "Social Impact",
      deadline: "12 Dec 2026",
      incentive: "$300 Community credit",
    },
  ];

  return (
    <div className="commission-page">
      <CommunityHeader active="commissions" onNavigate={onNavigate} />

      <div className="breadcrumb">
        Community Portal <span>›</span> Commissions
      </div>

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

            <select className="category-filter" defaultValue="all">
              <option value="all">All categories</option>
              <option>Community Design</option>
              <option>Sustainability</option>
              <option>Infrastructure</option>
              <option>Social Impact</option>
            </select>
          </div>

          <div className="commission-list">
            {commissions.map((commission) => (
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
                  onClick={() => onNavigate("details")}
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
