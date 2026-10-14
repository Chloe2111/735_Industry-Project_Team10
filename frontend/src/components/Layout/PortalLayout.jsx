import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { RoleSwitcher } from '../RoleSwitcher/RoleSwitcher'
import { NotificationBell } from '../Notifications/NotificationBell'
import './PortalLayout.css'

// One shell, two portals. The facilitator portal is a first draft that reuses
// the client portal's design; it will be redesigned next semester.
const PORTALS = {
  client: {
    pill: 'Client Portal',
    breadcrumbRoot: 'Council Portal',
    links: [
      { to: '/dashboard', label: 'Dashboard' },
      { to: '/post-commission', label: 'Post a commission' },
      { to: '/my-commissions', label: 'My commissions' },
      { to: '/reports-received', label: 'Reports received' },
    ],
    breadcrumbs: [
      { test: (path) => path === '/dashboard', label: 'Dashboard' },
      { test: (path) => path === '/post-commission', label: 'Post a commission' },
      { test: (path) => path === '/my-commissions', label: 'My commissions' },
      { test: (path) => path === '/reports-received', label: 'Reports Received' },
      { test: (path) => path === '/account', label: 'My account' },
      { test: (path) => path.startsWith('/reports-received/'), label: null },
    ],
    account: { to: '/account', initials: 'JM', name: '[Council 1]' },
    notifications: true,
  },
  // The human gate in the AI analysis pipeline: review flagged items, then
  // check the coded findings before anything reaches a client.
  facilitator: {
    pill: 'Facilitator Portal',
    breadcrumbRoot: 'Facilitator Portal',
    links: [
      { to: '/facilitator/exceptions-queue', label: 'Exceptions queue' },
      { to: '/facilitator/coding-findings', label: 'Coding findings' },
    ],
    breadcrumbs: [
      { test: (path) => path === '/facilitator/exceptions-queue', label: 'Exceptions Queue' },
      { test: (path) => path === '/facilitator/coding-findings', label: 'Coding Findings' },
    ],
    account: { initials: 'FA', name: '[Facilitator]' },
    notifications: false,
  },
}

function useBreadcrumbLabel(breadcrumbs) {
  const { pathname } = useLocation()
  const match = breadcrumbs.find((entry) => entry.test(pathname))
  return match ? match.label : null
}

export function PortalLayout({ role = 'client' }) {
  const portal = PORTALS[role] ?? PORTALS.client
  const breadcrumbLabel = useBreadcrumbLabel(portal.breadcrumbs)
  const navigate = useNavigate()

  return (
    <div className="portal">
      <header className="portal-topnav">
        <div className="portal-topnav__brand">
          <span className="portal-topnav__logo">vcnity</span>
          <span className="portal-topnav__pill">{portal.pill}</span>
        </div>
        <nav className="portal-topnav__links">
          {portal.links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) =>
                isActive ? 'portal-topnav__link portal-topnav__link--active' : 'portal-topnav__link'
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
        <RoleSwitcher current={role} onSwitch={(path) => navigate(path)} className="portal-topnav__roles" />
        {portal.account.to ? (
          <NavLink to={portal.account.to} className="portal-topnav__account">
            <span className="portal-topnav__avatar">{portal.account.initials}</span>
            <span className="portal-topnav__org">{portal.account.name}</span>
          </NavLink>
        ) : (
          <span className="portal-topnav__account">
            <span className="portal-topnav__avatar">{portal.account.initials}</span>
            <span className="portal-topnav__org">{portal.account.name}</span>
          </span>
        )}
      </header>

      {breadcrumbLabel && (
        <div className="portal-breadcrumbs">
          <span>{portal.breadcrumbRoot}</span>
          <span className="portal-breadcrumbs__sep">&rsaquo;</span>
          <span className="portal-breadcrumbs__current">{breadcrumbLabel}</span>
        </div>
      )}

      <main className="portal-content">
        <Outlet />
      </main>

      {portal.notifications && <NotificationBell />}
    </div>
  )
}
