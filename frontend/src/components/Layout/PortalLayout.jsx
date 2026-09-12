import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { NotificationBell } from '../Notifications/NotificationBell'
import './PortalLayout.css'

const NAV_LINKS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/post-commission', label: 'Post a commission' },
  { to: '/my-commissions', label: 'My commissions' },
  { to: '/reports-received', label: 'Reports received' },
]

const BREADCRUMB_LABELS = [
  { test: (path) => path === '/dashboard', label: 'Dashboard' },
  { test: (path) => path === '/post-commission', label: 'Post a commission' },
  { test: (path) => path === '/my-commissions', label: 'My commissions' },
  { test: (path) => path === '/reports-received', label: 'Reports Received' },
  { test: (path) => path === '/account', label: 'My account' },
  { test: (path) => path.startsWith('/reports-received/'), label: null },
]

function useBreadcrumbLabel() {
  const { pathname } = useLocation()
  const match = BREADCRUMB_LABELS.find((entry) => entry.test(pathname))
  return match ? match.label : null
}

export function PortalLayout() {
  const breadcrumbLabel = useBreadcrumbLabel()

  return (
    <div className="portal">
      <header className="portal-topnav">
        <div className="portal-topnav__brand">
          <span className="portal-topnav__logo">vcnity</span>
          <span className="portal-topnav__pill">Client Portal</span>
        </div>
        <nav className="portal-topnav__links">
          {NAV_LINKS.map((link) => (
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
        <NavLink to="/account" className="portal-topnav__account">
          <span className="portal-topnav__avatar">JM</span>
          <span className="portal-topnav__org">[Council 1]</span>
        </NavLink>
      </header>

      {breadcrumbLabel && (
        <div className="portal-breadcrumbs">
          <span>Council Portal</span>
          <span className="portal-breadcrumbs__sep">&rsaquo;</span>
          <span className="portal-breadcrumbs__current">{breadcrumbLabel}</span>
        </div>
      )}

      <main className="portal-content">
        <Outlet />
      </main>

      <NotificationBell />
    </div>
  )
}
