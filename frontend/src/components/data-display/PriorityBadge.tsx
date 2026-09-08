import type { LeadPriority } from '@/types/api'
import { cn } from '@/lib/utils'

/**
 * Deliberately visually distinct from StatusBadge (a dot + label, not a
 * plain tinted pill) so a lead's priority is never confused with its status
 * at a glance — both still communicate meaning through text, never color
 * alone.
 */
const PRIORITY_STYLES: Record<LeadPriority, { dot: string; text: string }> = {
  LOW: { dot: 'bg-muted-foreground', text: 'text-muted-foreground' },
  MEDIUM: { dot: 'bg-info', text: 'text-info' },
  HIGH: { dot: 'bg-warning', text: 'text-warning' },
}

export function PriorityBadge({ priority }: { priority: LeadPriority }) {
  const style = PRIORITY_STYLES[priority]
  return (
    <span className={cn('inline-flex items-center gap-1.5 text-xs font-medium', style.text)}>
      <span className={cn('h-1.5 w-1.5 rounded-full', style.dot)} aria-hidden="true" />
      {priority}
    </span>
  )
}
