import { SlidersHorizontal } from 'lucide-react'
import { useState, type ReactNode } from 'react'

import { Button } from '@/components/ui/button'
import { Sheet, SheetContent } from '@/components/ui/sheet'

/**
 * Renders the same filter controls twice — once inline (desktop, ≥768px)
 * and once inside a Sheet (mobile) — chosen via plain CSS visibility
 * (`hidden md:flex` / `md:hidden`), not conditional mounting. Both copies
 * are controlled by the same parent state, so this is safe; it deliberately
 * avoids portalling one set of live inputs between two locations, which
 * would be far more complex for no real benefit at this filter-set size.
 * Filter controls inside must use `aria-label` rather than a paired
 * `<label for>` (see Select usages) to avoid duplicate DOM ids between the
 * two renders.
 */
export function FilterBar({
  children,
  activeCount = 0,
  onClear,
}: {
  children: ReactNode
  activeCount?: number
  onClear?: () => void
}) {
  const [open, setOpen] = useState(false)

  return (
    <>
      <div className="hidden flex-wrap items-center gap-2 md:flex">
        {children}
        {activeCount > 0 && onClear && (
          <Button variant="ghost" size="sm" onClick={onClear}>
            Clear filters
          </Button>
        )}
      </div>

      <div className="md:hidden">
        <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
          <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
          Filters{activeCount > 0 ? ` (${activeCount})` : ''}
        </Button>
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetContent title="Filters" side="right">
            <div className="flex h-14 items-center border-b border-border px-1">
              <span className="text-base font-semibold text-foreground">Filters</span>
            </div>
            <div className="flex flex-col gap-3 pt-4">
              {children}
              {activeCount > 0 && onClear && (
                <Button variant="ghost" size="sm" onClick={onClear}>
                  Clear filters
                </Button>
              )}
            </div>
          </SheetContent>
        </Sheet>
      </div>
    </>
  )
}
