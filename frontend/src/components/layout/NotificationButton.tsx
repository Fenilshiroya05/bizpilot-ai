import { Bell } from 'lucide-react'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

/**
 * Visibly disabled — the backend's `notifications` module has no
 * implemented REST endpoint yet (CLAUDE.md §24 names the module; no
 * controller exists). This is a placeholder affordance only, never wired to
 * fabricated data.
 */
export function NotificationButton() {
  return (
    <Tooltip delayDuration={200}>
      <TooltipTrigger asChild>
        <button
          type="button"
          disabled
          aria-label="Notifications (coming soon)"
          className="relative flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Bell className="h-4 w-4" aria-hidden="true" />
        </button>
      </TooltipTrigger>
      <TooltipContent>Notifications — coming soon</TooltipContent>
    </Tooltip>
  )
}
