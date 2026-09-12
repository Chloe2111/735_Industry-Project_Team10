import './DataTable.css'

export function DataTable({ columns, rows, rowKey, emptyMessage = 'Nothing to show yet.' }) {
  if (!rows.length) {
    return <p className="data-table__empty">{emptyMessage}</p>
  }

  return (
    <div className="data-table__scroll">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row[rowKey]}>
              {columns.map((column) => (
                <td key={column.key}>{column.render ? column.render(row) : row[column.key]}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
