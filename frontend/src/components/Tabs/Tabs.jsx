import './Tabs.css'

export function Tabs({ tabs, activeId, onChange }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={tab.id === activeId}
          className={tab.id === activeId ? 'tabs__item tabs__item--active' : 'tabs__item'}
          onClick={() => onChange(tab.id)}
        >
          {tab.label} · {tab.count}
        </button>
      ))}
    </div>
  )
}
