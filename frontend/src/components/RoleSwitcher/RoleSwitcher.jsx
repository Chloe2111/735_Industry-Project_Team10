import './RoleSwitcher.css'

// Temporary role switch until login exists: lets the team move between the
// client, facilitator and community sides when demoing. Remove once a real
// login decides which portal a person lands on.
export const ROLES = [
  { id: 'client', label: 'Client', path: '/dashboard' },
  { id: 'facilitator', label: 'Facilitator', path: '/facilitator/exceptions-queue' },
  { id: 'community', label: 'Community member', path: '/community' },
]

/**
 * `onSwitch(path)` lets a caller inside the router navigate without a page
 * reload. Without it (e.g. the community header, which is also rendered
 * outside a router in tests) the switch falls back to a normal page load.
 */
export function RoleSwitcher({ current, onSwitch, className = '' }) {
  function handleChange(event) {
    const role = ROLES.find((item) => item.id === event.target.value)
    if (!role || role.id === current) return
    if (onSwitch) {
      onSwitch(role.path)
    } else {
      window.location.assign(role.path)
    }
  }

  return (
    <label className={`role-switcher ${className}`.trim()}>
      <span className="role-switcher__label">Viewing as</span>
      <select className="role-switcher__select" value={current} onChange={handleChange} aria-label="Switch role">
        {ROLES.map((role) => (
          <option key={role.id} value={role.id}>
            {role.label}
          </option>
        ))}
      </select>
    </label>
  )
}
