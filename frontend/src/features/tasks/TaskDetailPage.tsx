import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'

import { usePermission } from '@/hooks/usePermission'
import { cancelTask, getTask, taskKeys } from '@/features/tasks/api'
import { TaskAssignmentControls } from '@/features/tasks/components/TaskAssignmentControls'
import { TaskFormDialog } from '@/features/tasks/components/TaskFormDialog'
import { getCustomer, customerKeys } from '@/features/customers/api'
import { getLead, leadKeys } from '@/features/leads/api'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { PriorityBadge } from '@/components/data-display/PriorityBadge'
import { StatusBadge } from '@/components/data-display/StatusBadge'
import { ConfirmDialog } from '@/components/feedback/ConfirmDialog'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import { formatDate, formatDateTime } from '@/lib/date'

function OverviewField({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="text-sm text-foreground">{value ?? '—'}</div>
    </div>
  )
}

export function TaskDetailPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const canUpdate = usePermission('TASK_UPDATE')
  const canCancel = usePermission('TASK_DELETE')

  const [formOpen, setFormOpen] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [isCancelling, setIsCancelling] = useState(false)

  const detailQuery = useQuery({
    queryKey: taskKeys.detail(id ?? ''),
    queryFn: () => getTask(id ?? ''),
    enabled: !!id,
  })
  const task = detailQuery.data

  const customerQuery = useQuery({
    queryKey: customerKeys.detail(task?.customerId ?? ''),
    queryFn: () => getCustomer(task?.customerId ?? ''),
    enabled: !!task?.customerId,
  })

  const leadQuery = useQuery({
    queryKey: leadKeys.detail(task?.leadId ?? ''),
    queryFn: () => getLead(task?.leadId ?? ''),
    enabled: !!task?.leadId,
  })

  async function confirmCancel() {
    if (!id) return
    setIsCancelling(true)
    try {
      await cancelTask(id)
      await queryClient.invalidateQueries({ queryKey: ['tasks', 'list'] })
      await queryClient.invalidateQueries({ queryKey: taskKeys.detail(id) })
      toast({ title: 'Task cancelled', variant: 'success' })
      setCancelOpen(false)
    } catch (error) {
      toast({
        title: 'Could not cancel task',
        description: error instanceof ApiError ? error.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsCancelling(false)
    }
  }

  if (detailQuery.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  if (detailQuery.isError || !task) {
    return (
      <ErrorState title="We couldn't load this task" error={detailQuery.error} onRetry={() => detailQuery.refetch()} />
    )
  }

  const isCancelled = task.status === 'CANCELLED'

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={task.title}
        breadcrumb={[{ label: 'Tasks', to: '/tasks' }, { label: task.title }]}
        actions={
          <div className="flex items-center gap-2">
            <StatusBadge status={task.status} />
            <PriorityBadge priority={task.priority} />
            {canUpdate && !isCancelled && (
              <Button variant="outline" size="sm" onClick={() => setFormOpen(true)}>
                Edit
              </Button>
            )}
            {canCancel && !isCancelled && (
              <Button variant="destructive" size="sm" onClick={() => setCancelOpen(true)}>
                Cancel task
              </Button>
            )}
          </div>
        }
      />

      {isCancelled && (
        <Alert variant="warning">This task has been cancelled and can no longer be edited.</Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Overview</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <OverviewField label="Description" value={task.description} />
          <OverviewField label="Due date" value={formatDate(task.dueDate)} />
          <OverviewField
            label="Assignment"
            value={<TaskAssignmentControls task={task} readOnly={isCancelled} />}
          />
          <OverviewField
            label="Customer"
            value={
              task.customerId ? (
                <Link to={`/customers/${task.customerId}`} className="text-primary hover:underline">
                  {customerQuery.data?.name ?? 'Loading...'}
                </Link>
              ) : null
            }
          />
          <OverviewField
            label="Lead"
            value={
              task.leadId ? (
                <Link to={`/leads/${task.leadId}`} className="text-primary hover:underline">
                  {leadQuery.data?.name ?? 'Loading...'}
                </Link>
              ) : null
            }
          />
          <OverviewField label="Created" value={formatDateTime(task.createdAt)} />
          <OverviewField label="Last updated" value={formatDateTime(task.updatedAt)} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Notes</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="whitespace-pre-wrap text-sm text-foreground">{task.notes ?? '—'}</p>
        </CardContent>
      </Card>

      <TaskFormDialog open={formOpen} onOpenChange={setFormOpen} task={task} />

      <ConfirmDialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        title="Cancel this task?"
        description={`${task.title} will be marked as CANCELLED.`}
        confirmLabel="Cancel task"
        isLoading={isCancelling}
        onConfirm={confirmCancel}
      />
    </div>
  )
}
