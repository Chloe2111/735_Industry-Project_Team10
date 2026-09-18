import { useState } from 'react'
import { Link } from 'react-router-dom'

const TABS = [
  { id: 'notifications', label: 'Notifications' },
  { id: 'messages', label: 'Messages' },
]

export function NotificationsPanel({ notifications, onMarkAllRead }) {
  const [activeTab, setActiveTab] = useState('notifications')

  return (
    <div className="notifications-panel" role="dialog" aria-label="Notifications">
      <div className="notifications-panel__header">
        <div className="notifications-panel__tabs">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={
                tab.id === activeTab
                  ? 'notifications-panel__tab notifications-panel__tab--active'
                  : 'notifications-panel__tab'
              }
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        {activeTab === 'notifications' && (
          <button type="button" className="notifications-panel__mark-read" onClick={onMarkAllRead}>
            Mark all read
          </button>
        )}
      </div>

      {activeTab === 'notifications' ? (
        <ul className="notifications-panel__list">
          {notifications.map((item) => (
            <li
              key={item.id}
              className={
                item.unread
                  ? 'notifications-panel__item notifications-panel__item--unread'
                  : 'notifications-panel__item'
              }
            >
              <span className="notifications-panel__dot" aria-hidden="true" />
              <Link to={item.to} className="notifications-panel__body">
                <span className="notifications-panel__title">{item.title}</span>
                <span className="notifications-panel__subtitle">{item.subtitle}</span>
              </Link>
              <span className="notifications-panel__timestamp">{item.timestamp}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="notifications-panel__empty">No messages yet.</p>
      )}
    </div>
  )
}
