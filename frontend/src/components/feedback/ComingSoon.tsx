import { Construction } from 'lucide-react'

import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/feedback/EmptyState'

/**
 * An honest placeholder for a nav destination that exists in the target
 * information architecture but whose feature isn't implemented yet
 * (CLAUDE.md's frontend phase roadmap defers Customers/Leads/Products/
 * Quotations/Invoices/Tasks/Documents to Phases 21-24). This keeps every
 * sidebar link resolving to a real, rendering route instead of a 404 or a
 * fabricated feature.
 */
export function ComingSoon({ title, phase }: { title: string; phase: string }) {
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={title} />
      <EmptyState
        icon={Construction}
        title={`${title} is coming in ${phase}`}
        description="This part of BizPilot AI hasn't been built yet — the backend API it will use already exists and is fully tested."
      />
    </div>
  )
}
