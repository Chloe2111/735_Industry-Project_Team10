import { useEffect, useRef, useState } from 'react'
import { listNotifications, markAllNotificationsRead } from '../../services/portalService'
import { NotificationsPanel } from './NotificationsPanel'
import './Notifications.css'

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [notifications, setNotifications] = useState([])
  const containerRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    listNotifications().then(({ data }) => {
      if (!cancelled) setNotifications(data)
    })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const unreadCount = notifications.filter((item) => item.unread).length

  async function handleMarkAllRead() {
    const { data } = await markAllNotificationsRead()
    setNotifications(data)
  }

  return (
    <div className="notification-bell" ref={containerRef}>
      {open && <NotificationsPanel notifications={notifications} onMarkAllRead={handleMarkAllRead} />}
      <button
        type="button"
        className="notification-bell__button"
        aria-label="Notifications"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M4 19h16l-1.6-2.4A5 5 0 0 1 17.6 14V10a5.6 5.6 0 0 0-11.2 0v4a5 5 0 0 1-.8 2.6L4 19Z"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinejoin="round"
          />
          <path d="M9.5 19a2.5 2.5 0 0 0 5 0" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
        {unreadCount > 0 && <span className="notification-bell__badge">{unreadCount}</span>}
      </button>
    </div>
  )
}
