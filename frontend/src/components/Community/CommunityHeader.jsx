import "./CommunityWorkspace.css";
import "../Layout/PortalLayout.css";
import { RoleSwitcher } from "../RoleSwitcher/RoleSwitcher";

// Same shell as the client and facilitator portals (PortalLayout): one white top bar
// with the brand, the page links, the role switcher and the account, then a breadcrumb
// strip. The community workspace switches pages itself, so the links are buttons.
const NAV_ITEMS = [
  { id: "dashboard", label: "Dashboard" },
  { id: "groups", label: "Groups" },
  { id: "messages", label: "Messages" },
  { id: "commissions", label: "Commissions" },
  { id: "shareFeedback", label: "Share feedback" },
];

/**
 * `crumbs` lists the page's place after "Community Portal", e.g. ["Groups", "Create Group"].
 * The last entry is the current page.
 */
export default function CommunityHeader({ active = "", onNavigate, crumbs = [] }) {
  const go = (page) => onNavigate?.(page);
  return (
    <>
      <header className="portal-topnav community-topnav">
        <div className="portal-topnav__brand">
          <span className="portal-topnav__logo">vcnity</span>
          <span className="portal-topnav__pill">Community Portal</span>
        </div>
        <nav className="portal-topnav__links" aria-label="Community">
          {NAV_ITEMS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={active === item.id ? "portal-topnav__link portal-topnav__link--active" : "portal-topnav__link"}
              aria-current={active === item.id ? "page" : undefined}
              onClick={() => go(item.id)}
            >
              {item.label}
            </button>
          ))}
        </nav>
        <RoleSwitcher current="community" className="portal-topnav__roles" />
        <button type="button" className="portal-topnav__account" onClick={() => go("profile")}>
          <span className="portal-topnav__avatar">AT</span>
          <span className="portal-topnav__org">Alex Thompson</span>
        </button>
      </header>

      {crumbs.length > 0 && (
        <div className="portal-breadcrumbs">
          <span>Community Portal</span>
          {crumbs.map((crumb, index) => (
            <span key={`${index}-${crumb}`} className="portal-breadcrumbs__item">
              <span className="portal-breadcrumbs__sep">&rsaquo;</span>
              <span className={index === crumbs.length - 1 ? "portal-breadcrumbs__current" : undefined}>{crumb}</span>
            </span>
          ))}
        </div>
      )}
    </>
  );
}

export function Avatar({ initials }) { return <span className="avatar">{initials}</span>; }
export function ChatButton({ onNavigate }) { return <button className="chat-button" type="button" aria-label="Open messages" onClick={() => onNavigate?.("messages")}><span>2</span>💬</button>; }

/** Shown in place of the community data when it could not be loaded. */
export function LoadErrorNotice({ message, onRetry }) {
  return (
    <section className="community-card community-load-error" role="alert">
      <p>{message}</p>
      {onRetry && <button type="button" className="secondary-button" onClick={onRetry}>Try again</button>}
    </section>
  );
}
