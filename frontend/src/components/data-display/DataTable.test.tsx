import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { DataTable, type DataTableColumn } from './DataTable'

interface Row {
  id: string
  name: string
}

const columns: DataTableColumn<Row>[] = [
  { key: 'name', header: 'Name', cell: (row) => row.name },
]

describe('DataTable', () => {
  it('renders accessible table semantics', () => {
    render(<DataTable caption="Widgets" columns={columns} rows={[{ id: '1', name: 'Acme' }]} rowKey={(r) => r.id} />)
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Name' })).toBeInTheDocument()
    expect(screen.getByText('Acme')).toBeInTheDocument()
  })

  it('calls onRowClick when a row is clicked', async () => {
    const onRowClick = vi.fn()
    render(
      <DataTable
        caption="Widgets"
        columns={columns}
        rows={[{ id: '1', name: 'Acme' }]}
        rowKey={(r) => r.id}
        onRowClick={onRowClick}
      />,
    )
    await userEvent.click(screen.getByText('Acme'))
    expect(onRowClick).toHaveBeenCalledWith({ id: '1', name: 'Acme' })
  })

  it('renders skeleton rows while loading, not the real rows', () => {
    render(
      <DataTable
        caption="Widgets"
        columns={columns}
        rows={[{ id: '1', name: 'Acme' }]}
        rowKey={(r) => r.id}
        isLoading
        skeletonRowCount={2}
      />,
    )
    expect(screen.queryByText('Acme')).not.toBeInTheDocument()
  })
})
