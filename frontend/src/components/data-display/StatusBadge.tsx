import { Badge, type BadgeProps } from '@/components/ui/badge'

/**
 * Reusable status → semantic-color mapping (CLAUDE.md §10) so no feature
 * page ever invents its own per-status color. Not yet used by any screen —
 * Phase 20 has no status-bearing entity on any implemented page — but the
 * mapping is established now, sourced from every status enum CLAUDE.md
 * actually defines, so Phase 21+ (Leads/Quotations/Invoices/Tasks/Documents)
 * reuses this one lookup instead of each inventing its own colors.
 */
const STATUS_VARIANTS: Record<string, BadgeProps['variant']> = {
  // Leads (CLAUDE.md §11)
  NEW: 'info',
  CONTACTED: 'info',
  QUALIFIED: 'primary',
  PROPOSAL: 'primary',
  NEGOTIATION: 'warning',
  WON: 'success',
  LOST: 'destructive',
  // Quotations (§14) / Invoices (§15)
  DRAFT: 'neutral',
  SENT: 'info',
  ISSUED: 'info',
  ACCEPTED: 'success',
  PAID: 'success',
  PARTIALLY_PAID: 'warning',
  REJECTED: 'destructive',
  EXPIRED: 'destructive',
  OVERDUE: 'warning',
  CANCELLED: 'destructive',
  // Tasks (§23)
  TODO: 'neutral',
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
  // Documents (§16)
  UPLOADED: 'neutral',
  PROCESSING: 'info',
  FAILED: 'destructive',
  // Products
  ACTIVE: 'success',
  INACTIVE: 'neutral',
  ARCHIVED: 'neutral',
}

export function StatusBadge({ status }: { status: string }) {
  return <Badge variant={STATUS_VARIANTS[status] ?? 'neutral'}>{status.replace(/_/g, ' ')}</Badge>
}
