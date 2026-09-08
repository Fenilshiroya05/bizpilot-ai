import { ChevronLeft, ChevronRight } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { formatCount } from '@/lib/money'
import type { Page } from '@/types/api'

/**
 * Respects Spring Data's real `Page<T>` shape exactly (`number` is the
 * current 0-indexed page, `first`/`last` are booleans the backend already
 * computed) — no custom pagination format assumed.
 */
export function Pagination<T>({ page, onPageChange }: { page: Page<T>; onPageChange: (page: number) => void }) {
  if (page.totalElements === 0) return null

  const from = page.number * page.size + 1
  const to = Math.min(page.number * page.size + page.content.length, page.totalElements)

  return (
    <div className="flex items-center justify-between border-t border-border px-4 py-3 text-sm text-muted-foreground">
      <span>
        {formatCount(from)}–{formatCount(to)} of {formatCount(page.totalElements)}
      </span>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page.number - 1)}
          disabled={page.first}
          aria-label="Previous page"
        >
          <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          Previous
        </Button>
        <span className="tabular-nums">
          Page {page.number + 1} of {Math.max(page.totalPages, 1)}
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page.number + 1)}
          disabled={page.last}
          aria-label="Next page"
        >
          Next
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </div>
  )
}
