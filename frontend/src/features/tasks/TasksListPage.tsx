import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckSquare, Clock, MoreHorizontal, Plus, Search, UserCheck, UserX } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { useAuth } from '@/app/providers/AuthProvider'
import { usePermission } from '@/hooks/usePermission'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { cancelTask, searchTasks, taskKeys } from '@/features/tasks/api'
import { TaskFormDialog } from '@/features/tasks/components/TaskFormDialog'
import { TASK_PRIORITY_OPTIONS, TASK_STATUS_OPTIONS } from '@/features/tasks/schemas'
import { DataTable, type DataTableColumn } from '@/components/data-display/DataTable'
import { Pagination } from '@/components/data-display/Pagination'
import { PriorityBadge } from '@/components/data-display/PriorityBadge'
import { StatusBadge } from '@/components/data-display/StatusBadge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { FilterBar } from '@/components/forms/FilterBar'
import { ConfirmDialog } from '@/components/feedback/ConfirmDialog'
import { EmptyState } from '@/components/feedback/EmptyState'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import { formatDate, isOnOrBeforeToday, todayIsoDate } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { TaskPriority, TaskResponse, TaskStatus } from '@/types/api'

function useTaskListParams(currentUserId: string | undefined) {
  const [searchParams, setSearchParams] = useSearchParams()

  const q = searchParams.get('q') ?? ''
  const status = (searchParams.get('status') as TaskStatus | null) ?? undefined
  const priority = (searchParams.get('priority') as TaskPriority | null) ?? undefined
  const assignedToUserId = searchParams.get('assignedToUserId') ?? undefined
  const unassigned = searchParams.get('unassigned') === 'true'
  const dueDateOnOrBefore = searchParams.get('dueDateOnOrBefore') ?? undefined
  const page = Number(searchParams.get('page') ?? '0')

  function setParams(updates: Record<string, string | undefined>, resetPage = true) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      for (const [key, value] of Object.entries(updates)) {
        if (value) next.set(key, value)
        else next.delete(key)
      }
      if (resetPage) next.set('page', '0')
      return next
    })
  }

  const isMyTasks = !!currentUserId && assignedToUserId === currentUserId
  const isDueTodayOrOverdue = !!dueDateOnOrBefore

  return {
    q,
    status,
    priority,
    assignedToUserId,
    unassigned,
    dueDateOnOrBefore,
    page,
    setSearch: (value: string) => setParams({ q: value || undefined }),
    setStatus: (value: string) => setParams({ status: value || undefined }),
    setPriority: (value: string) => setParams({ priority: value || undefined }),
    toggleMyTasks: () =>
      setParams({ assignedToUserId: isMyTasks ? undefined : currentUserId, unassigned: undefined }),
    toggleUnassigned: () =>
      setParams({ unassigned: unassigned ? undefined : 'true', assignedToUserId: undefined }),
    toggleDueTodayOrOverdue: () =>
      setParams({ dueDateOnOrBefore: isDueTodayOrOverdue ? undefined : todayIsoDate() }),
    setPage: (value: number) => setParams({ page: String(value) }, false),
    clear: () => setSearchParams({}),
    clearStatusAndPriority: () => setParams({ status: undefined, priority: undefined }),
    isMyTasks,
    isDueTodayOrOverdue,
  }
}

function assignmentLabel(task: TaskResponse, currentUserId: string | undefined): string {
  if (task.assignedToUserId === null) return 'Unassigned'
  if (task.assignedToUserId === currentUserId) return 'You'
  return 'Assigned'
}

export function TasksListPage() {
  const { user } = useAuth()
  const params = useTaskListParams(user?.id)
  const [searchInput, setSearchInput] = useState(params.q)
  const debouncedSearch = useDebouncedValue(searchInput, 300)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const canCreate = usePermission('TASK_CREATE')
  const canUpdate = usePermission('TASK_UPDATE')
  const canCancel = usePermission('TASK_DELETE')

  useEffect(() => {
    if (debouncedSearch !== params.q) params.setSearch(debouncedSearch)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const queryParams = {
    q: params.q || undefined,
    status: params.status,
    priority: params.priority,
    assignedToUserId: params.assignedToUserId,
    unassigned: params.unassigned || undefined,
    dueDateOnOrBefore: params.dueDateOnOrBefore,
    page: params.page,
    size: 20,
  }
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: taskKeys.list(queryParams),
    queryFn: () => searchTasks(queryParams),
  })

  const [formOpen, setFormOpen] = useState(false)
  const [editingTask, setEditingTask] = useState<TaskResponse | undefined>(undefined)
  const [cancelling, setCancelling] = useState<TaskResponse | null>(null)
  const [isCancelling, setIsCancelling] = useState(false)

  function openCreate() {
    setEditingTask(undefined)
    setFormOpen(true)
  }

  function openEdit(task: TaskResponse) {
    setEditingTask(task)
    setFormOpen(true)
  }

  async function confirmCancel() {
    if (!cancelling) return
    setIsCancelling(true)
    try {
      await cancelTask(cancelling.id)
      await queryClient.invalidateQueries({ queryKey: ['tasks', 'list'] })
      await queryClient.invalidateQueries({ queryKey: taskKeys.detail(cancelling.id) })
      toast({ title: 'Task cancelled', variant: 'success' })
      setCancelling(null)
    } catch (err) {
      toast({
        title: 'Could not cancel task',
        description: err instanceof ApiError ? err.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsCancelling(false)
    }
  }

  const columns: DataTableColumn<TaskResponse>[] = [
    {
      key: 'title',
      header: 'Title',
      cell: (task) => (
        <Link to={`/tasks/${task.id}`} className="font-medium text-foreground hover:underline">
          {task.title}
        </Link>
      ),
    },
    { key: 'priority', header: 'Priority', cell: (task) => <PriorityBadge priority={task.priority} /> },
    { key: 'status', header: 'Status', cell: (task) => <StatusBadge status={task.status} /> },
    {
      key: 'dueDate',
      header: 'Due Date',
      cell: (task) => {
        const overdue =
          task.dueDate && isOnOrBeforeToday(task.dueDate) && task.status !== 'COMPLETED' && task.status !== 'CANCELLED'
        return <span className={cn(overdue && 'font-medium text-warning')}>{formatDate(task.dueDate)}</span>
      },
    },
    { key: 'assignment', header: 'Assignment', cell: (task) => assignmentLabel(task, user?.id) },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      cell: (task) => (
        <DropdownMenu>
          <DropdownMenuTrigger
            className="rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={`Actions for ${task.title}`}
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
            <DropdownMenuItem onSelect={() => navigate(`/tasks/${task.id}`)}>View details</DropdownMenuItem>
            {canUpdate && task.status !== 'CANCELLED' && (
              <DropdownMenuItem onSelect={() => openEdit(task)}>Edit</DropdownMenuItem>
            )}
            {canCancel && task.status !== 'CANCELLED' && (
              <DropdownMenuItem
                onSelect={() => setCancelling(task)}
                className="text-destructive focus:bg-destructive/10"
              >
                Cancel
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ]

  const activeFilterCount = [params.status, params.priority].filter(Boolean).length
  const hasActiveFilters = Boolean(
    params.q || activeFilterCount || params.unassigned || params.assignedToUserId || params.dueDateOnOrBefore,
  )

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Tasks"
        description="Track follow-up work across your organization."
        breadcrumb={[{ label: 'Tasks' }]}
        actions={
          canCreate ? (
            <Button onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create task
            </Button>
          ) : undefined
        }
      />

      <div className="flex flex-col gap-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full sm:max-w-xs">
            <Search
              className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden="true"
            />
            <Input
              className="pl-9"
              placeholder="Search tasks..."
              aria-label="Search tasks"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
          </div>

          <FilterBar activeCount={activeFilterCount} onClear={params.clearStatusAndPriority}>
            <Select aria-label="Status filter" value={params.status ?? ''} onChange={(e) => params.setStatus(e.target.value)}>
              <option value="">All statuses</option>
              {TASK_STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>
                  {status.replace('_', ' ')}
                </option>
              ))}
              <option value="CANCELLED">CANCELLED</option>
            </Select>
            <Select aria-label="Priority filter" value={params.priority ?? ''} onChange={(e) => params.setPriority(e.target.value)}>
              <option value="">All priorities</option>
              {TASK_PRIORITY_OPTIONS.map((priority) => (
                <option key={priority} value={priority}>
                  {priority}
                </option>
              ))}
            </Select>
          </FilterBar>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button
            variant={params.isMyTasks ? 'primary' : 'outline'}
            size="sm"
            onClick={params.toggleMyTasks}
          >
            <UserCheck className="h-3.5 w-3.5" aria-hidden="true" />
            My Tasks
          </Button>
          <Button
            variant={params.unassigned ? 'primary' : 'outline'}
            size="sm"
            onClick={params.toggleUnassigned}
          >
            <UserX className="h-3.5 w-3.5" aria-hidden="true" />
            Unassigned
          </Button>
          <Button
            variant={params.isDueTodayOrOverdue ? 'primary' : 'outline'}
            size="sm"
            onClick={params.toggleDueTodayOrOverdue}
          >
            <Clock className="h-3.5 w-3.5" aria-hidden="true" />
            Due Today / Overdue
          </Button>
        </div>
      </div>

      {isError ? (
        <ErrorState title="We couldn't load your tasks" error={error} onRetry={() => refetch()} />
      ) : !isLoading && data?.content.length === 0 ? (
        <EmptyState
          icon={CheckSquare}
          title={hasActiveFilters ? 'No tasks found' : 'No tasks yet'}
          description={hasActiveFilters ? 'Try changing your search or filters.' : 'Add your first task to get started.'}
          action={
            hasActiveFilters ? (
              <Button variant="outline" size="sm" onClick={params.clear}>
                Clear filters
              </Button>
            ) : canCreate ? (
              <Button size="sm" onClick={openCreate}>
                Add your first task
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="hidden lg:block">
            <DataTable
              caption="Tasks"
              columns={columns}
              rows={data?.content ?? []}
              rowKey={(task) => task.id}
              onRowClick={(task) => navigate(`/tasks/${task.id}`)}
              isLoading={isLoading}
            />
          </div>

          <div className="flex flex-col gap-2 lg:hidden">
            {isLoading
              ? Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="h-28 animate-pulse rounded-lg border border-border bg-muted" />
                ))
              : data?.content.map((task) => (
                  <Link
                    key={task.id}
                    to={`/tasks/${task.id}`}
                    className="flex flex-col gap-1.5 rounded-lg border border-border bg-background p-4 hover:bg-muted/40"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-foreground">{task.title}</span>
                      <StatusBadge status={task.status} />
                    </div>
                    <div className="flex items-center justify-between text-xs text-muted-foreground">
                      <PriorityBadge priority={task.priority} />
                      <span>{formatDate(task.dueDate)}</span>
                      <span>{assignmentLabel(task, user?.id)}</span>
                    </div>
                  </Link>
                ))}
          </div>

          {data && <Pagination page={data} onPageChange={params.setPage} />}
        </>
      )}

      <TaskFormDialog open={formOpen} onOpenChange={setFormOpen} task={editingTask} />

      <ConfirmDialog
        open={!!cancelling}
        onOpenChange={(open) => !open && setCancelling(null)}
        title="Cancel this task?"
        description={`${cancelling?.title ?? 'This task'} will be marked as CANCELLED.`}
        confirmLabel="Cancel task"
        isLoading={isCancelling}
        onConfirm={confirmCancel}
      />
    </div>
  )
}
