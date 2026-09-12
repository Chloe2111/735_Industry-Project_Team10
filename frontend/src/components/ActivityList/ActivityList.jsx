import { Link } from 'react-router-dom'
import './ActivityList.css'

export function ActivityList({ items }) {
  return (
    <ul className="activity-list">
      {items.map((item) => (
        <li key={item.id} className="activity-list__item">
          <input type="checkbox" className="activity-list__checkbox" aria-label={`Mark ${item.title} reviewed`} />
          <div className="activity-list__body">
            <Link to={item.to} className="activity-list__title">
              {item.title}
            </Link>
            <span className="activity-list__subtitle">{item.subtitle}</span>
          </div>
          <span className="activity-list__timestamp">{item.timestamp}</span>
        </li>
      ))}
    </ul>
  )
}
