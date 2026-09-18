import './PillOptions.css'

function toggleValue(list, value) {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
}

/**
 * Single- or multi-select pill button group.
 *
 * `value` is always an array (even for single-select) so callers have one
 * shape to work with; `multi=false` replaces the array with `[value]` on
 * each click instead of toggling membership.
 */
export function PillOptions({ options, value, onChange, multi = false }) {
  function handleClick(option) {
    if (multi) {
      onChange(toggleValue(value, option))
    } else {
      onChange([option])
    }
  }

  return (
    <div className="pill-options" role="group">
      {options.map((option) => {
        const selected = value.includes(option)
        return (
          <button
            key={option}
            type="button"
            className={selected ? 'pill-options__pill pill-options__pill--selected' : 'pill-options__pill'}
            aria-pressed={selected}
            onClick={() => handleClick(option)}
          >
            {option}
          </button>
        )
      })}
    </div>
  )
}
