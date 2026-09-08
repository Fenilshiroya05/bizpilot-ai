import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { useAuth } from '@/app/providers/AuthProvider'
import { usePermission } from '@/hooks/usePermission'
import { assignLead, leadKeys } from '@/features/leads/api'
import { Button } from '@/components/ui/button'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import type { LeadResponse } from '@/types/api'

/**
 * Locked Phase 21 scope decision: there is no user-directory API, so this
 * app can only ever act on the CURRENT user's own id. "Assign to a
 * teammate" is not buildable — see docs/roadmap.md's Phase 21 entry. A lead
 * assigned to someone else is shown as a plain "Assigned" state, never a
 * fabricated name.
 */
export function AssignmentControls({ lead }: { lead: LeadResponse }) {
  const { user } = useAuth()
  const canUpdate = usePermission('LEAD_UPDATE')
  const queryClient = useQueryClient()
  const [isSubmitting, setIsSubmitting] = useState(false)

  const isAssignedToMe = !!user && lead.assignedToUserId === user.id
  const isUnassigned = lead.assignedToUserId === null
  const isAssignedToOther = !isUnassigned && !isAssignedToMe

  async function handleAssign(assigneeUserId: string | null) {
    setIsSubmitting(true)
    try {
      await assignLead(lead.id, { assigneeUserId })
      await queryClient.invalidateQueries({ queryKey: leadKeys.detail(lead.id) })
      await queryClient.invalidateQueries({ queryKey: ['leads', 'list'] })
      toast({ title: assigneeUserId ? 'Assigned to you' : 'Unassigned', variant: 'success' })
    } catch (error) {
      toast({
        title: 'Could not update assignment',
        description: error instanceof ApiError ? error.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsSubmitting(false)
    }
  }

  if (!canUpdate) {
    return (
      <span className="text-sm text-foreground">
        {isAssignedToMe ? 'Assigned to you' : isUnassigned ? 'Unassigned' : 'Assigned'}
      </span>
    )
  }

  if (isAssignedToMe) {
    return (
      <div className="flex items-center gap-2">
        <span className="text-sm text-foreground">Assigned to you</span>
        <Button variant="outline" size="sm" isLoading={isSubmitting} onClick={() => handleAssign(null)}>
          Unassign
        </Button>
      </div>
    )
  }

  if (isAssignedToOther) {
    return <span className="text-sm text-foreground">Assigned</span>
  }

  return (
    <Button
      variant="outline"
      size="sm"
      isLoading={isSubmitting}
      onClick={() => user && handleAssign(user.id)}
    >
      Assign to me
    </Button>
  )
}
