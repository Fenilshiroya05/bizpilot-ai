import type { ReactNode } from 'react'

import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

export interface DataTableColumn<T> {
  key: string
  header: string
  cell: (row: T) => ReactNode
  className?: string
}

/**
 * A plain, column-driven desktop table — not a generic table framework.
 * Deliberately renders only the <table> itself; each feature's list page
 * decides how to present the same rows on narrow viewports (a stacked card
 * list), since Customer and Lead cards need genuinely different fields and
 * forcing one "renderMobileCard" abstraction here would be premature
 * generalization for two call sites.
 *
 * `onRowClick` is a mouse-only convenience (a <tr> can't be a native
 * keyboard/focus target without hacking its semantics) — the primary column
 * a caller renders (e.g. the name) MUST be a real <Link>/<button> so
 * keyboard and screen-reader users have an actual accessible way to reach
 * the same destination.
 */
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  onRowClick,
  caption,
  isLoading,
  skeletonRowCount = 5,
}: {
  columns: DataTableColumn<T>[]
  rows: T[]
  rowKey: (row: T) => string
  onRowClick?: (row: T) => void
  caption: string
  isLoading?: boolean
  skeletonRowCount?: number
}) {
  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <table className="w-full text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="border-b border-border bg-muted/50">
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={cn(
                  'px-4 py-2.5 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground',
                  column.className,
                )}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {isLoading
            ? Array.from({ length: skeletonRowCount }).map((_, rowIndex) => (
                <tr key={rowIndex}>
                  {columns.map((column) => (
                    <td key={column.key} className={cn('px-4 py-3', column.className)}>
                      <Skeleton className="h-4 w-24" />
                    </td>
                  ))}
                </tr>
              ))
            : rows.map((row) => (
                <tr
                  key={rowKey(row)}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  className={cn('bg-background', onRowClick && 'cursor-pointer hover:bg-muted/40')}
                >
                  {columns.map((column) => (
                    <td key={column.key} className={cn('px-4 py-3 align-middle', column.className)}>
                      {column.cell(row)}
                    </td>
                  ))}
                </tr>
              ))}
        </tbody>
      </table>
    </div>
  )
}
