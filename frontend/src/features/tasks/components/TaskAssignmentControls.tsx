import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { useAuth } from '@/app/providers/AuthProvider'
import { usePermission } from '@/hooks/usePermission'
import { assignTask, taskKeys } from '@/features/tasks/api'
import { Button } from '@/components/ui/button'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import type { TaskResponse } from '@/types/api'

/**
 * Same locked scope decision as Lead's `AssignmentControls` (Phase 21):
 * there is no user-directory API, so this app can only ever act on the
 * CURRENT user's own id. "Assign to a teammate" is not buildable. A task
 * assigned to someone else is shown as a plain "Assigned" state, never a
 * fabricated name.
 */
export function TaskAssignmentControls({ task, readOnly }: { task: TaskResponse; readOnly?: boolean }) {
  const { user } = useAuth()
  const canUpdate = usePermission('TASK_UPDATE') && !readOnly
  const queryClient = useQueryClient()
  const [isSubmitting, setIsSubmitting] = useState(false)

  const isAssignedToMe = !!user && task.assignedToUserId === user.id
  const isUnassigned = task.assignedToUserId === null
  const isAssignedToOther = !isUnassigned && !isAssignedToMe

  async function handleAssign(assigneeUserId: string | null) {
    setIsSubmitting(true)
    try {
      await assignTask(task.id, { assigneeUserId })
      await queryClient.invalidateQueries({ queryKey: taskKeys.detail(task.id) })
      await queryClient.invalidateQueries({ queryKey: ['tasks', 'list'] })
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
