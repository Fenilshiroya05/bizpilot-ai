import { zodResolver } from '@hookform/resolvers/zod'
import { useQueries, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { createTask, taskKeys, updateTask } from '@/features/tasks/api'
import { LeadCombobox } from '@/features/tasks/components/LeadCombobox'
import {
  taskEditFormSchema,
  TASK_PRIORITY_OPTIONS,
  TASK_STATUS_OPTIONS,
  type TaskEditFormValues,
  type TaskFormValues,
} from '@/features/tasks/schemas'
import { customerKeys, getCustomer } from '@/features/customers/api'
import { getLead, leadKeys } from '@/features/leads/api'
import { CustomerCombobox } from '@/features/quotations/components/CustomerCombobox'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { FormField } from '@/components/forms/FormField'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import type { TaskResponse } from '@/types/api'

const CREATE_DEFAULTS: TaskFormValues = {
  title: '',
  description: '',
  priority: 'MEDIUM',
  dueDate: '',
  customerId: '',
  customerLabel: '',
  leadId: '',
  leadLabel: '',
}

function toEditDefaults(
  task: TaskResponse,
  customerLabel: string,
  leadLabel: string,
): TaskEditFormValues {
  return {
    title: task.title,
    description: task.description ?? '',
    priority: task.priority,
    dueDate: task.dueDate ?? '',
    customerId: task.customerId ?? '',
    customerLabel,
    leadId: task.leadId ?? '',
    leadLabel,
    status: task.status === 'CANCELLED' ? 'TODO' : task.status,
    notes: task.notes ?? '',
  }
}

/**
 * Mounted only while the dialog is open — see LeadForm's identical
 * rationale (Phase 21): a fresh component instance every time avoids
 * needing a reset-on-reopen effect entirely.
 */
function TaskForm({
  task,
  customerLabel,
  leadLabel,
  onOpenChange,
}: {
  task?: TaskResponse
  /** Present only in edit mode, when the task has a related Customer/Lead — resolved by the caller since TaskResponse only carries the id. */
  customerLabel?: string
  leadLabel?: string
  onOpenChange: (open: boolean) => void
}) {
  const isEdit = !!task
  const queryClient = useQueryClient()
  const [apiError, setApiError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<TaskEditFormValues>({
    resolver: zodResolver(taskEditFormSchema),
    defaultValues: isEdit
      ? toEditDefaults(task, customerLabel ?? '', leadLabel ?? '')
      : { ...CREATE_DEFAULTS, status: 'TODO', notes: '' },
  })

  async function onSubmit(values: TaskEditFormValues) {
    setApiError(null)
    try {
      if (isEdit && task) {
        const clearDueDate = values.dueDate === '' && task.dueDate !== null
        const clearCustomerId = values.customerId === '' && task.customerId !== null
        const clearLeadId = values.leadId === '' && task.leadId !== null
        await updateTask(task.id, {
          title: values.title,
          description: values.description,
          priority: values.priority,
          dueDate: values.dueDate || undefined,
          clearDueDate,
          customerId: values.customerId || undefined,
          clearCustomerId,
          leadId: values.leadId || undefined,
          clearLeadId,
          status: values.status,
          notes: values.notes,
        })
        await queryClient.invalidateQueries({ queryKey: taskKeys.detail(task.id) })
        toast({ title: 'Task updated', variant: 'success' })
      } else {
        await createTask({
          title: values.title,
          description: values.description,
          priority: values.priority,
          dueDate: values.dueDate || undefined,
          customerId: values.customerId || undefined,
          leadId: values.leadId || undefined,
        })
        toast({ title: 'Task created', variant: 'success' })
      }
      await queryClient.invalidateQueries({ queryKey: ['tasks', 'list'] })
      onOpenChange(false)
    } catch (error) {
      setApiError(error instanceof ApiError ? error.message : 'Unable to save this task. Please try again.')
    }
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      {apiError && <Alert variant="destructive">{apiError}</Alert>}

      <FormField label="Title" htmlFor="task-title" error={errors.title?.message}>
        <Input
          id="task-title"
          aria-invalid={!!errors.title}
          aria-describedby={errors.title ? 'task-title-error' : undefined}
          {...register('title')}
        />
      </FormField>

      <FormField label="Description" htmlFor="task-description" error={errors.description?.message}>
        <Input id="task-description" {...register('description')} />
      </FormField>

      <div className="grid grid-cols-2 gap-3">
        <FormField label="Priority" htmlFor="task-priority" error={errors.priority?.message}>
          <Select id="task-priority" {...register('priority')}>
            {TASK_PRIORITY_OPTIONS.map((priority) => (
              <option key={priority} value={priority}>
                {priority}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField label="Due date" htmlFor="task-due-date" hint="Leave empty to clear">
          <Input id="task-due-date" type="date" {...register('dueDate')} />
        </FormField>
      </div>

      <FormField label="Customer" htmlFor="task-customer">
        <CustomerCombobox
          initialLabel={customerLabel ?? ''}
          onSelect={(customer) => {
            setValue('customerId', customer.id, { shouldValidate: true })
            setValue('customerLabel', customer.name)
          }}
          onClear={() => {
            setValue('customerId', '')
            setValue('customerLabel', '')
          }}
        />
      </FormField>

      <FormField label="Lead" htmlFor="task-lead">
        <LeadCombobox
          initialLabel={leadLabel ?? ''}
          onSelect={(lead) => {
            setValue('leadId', lead.id, { shouldValidate: true })
            setValue('leadLabel', lead.name)
          }}
          onClear={() => {
            setValue('leadId', '')
            setValue('leadLabel', '')
          }}
        />
      </FormField>

      {isEdit && (
        <>
          <div className="grid grid-cols-2 gap-3">
            <FormField label="Status" htmlFor="task-status" error={errors.status?.message}>
              <Select id="task-status" {...register('status')}>
                {TASK_STATUS_OPTIONS.map((status) => (
                  <option key={status} value={status}>
                    {status.replace('_', ' ')}
                  </option>
                ))}
              </Select>
            </FormField>
          </div>
          <FormField label="Notes" htmlFor="task-notes" error={errors.notes?.message}>
            <textarea
              id="task-notes"
              rows={3}
              aria-invalid={!!errors.notes}
              aria-describedby={errors.notes ? 'task-notes-error' : undefined}
              className="flex w-full rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive"
              {...register('notes')}
            />
          </FormField>
        </>
      )}

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" isLoading={isSubmitting}>
          {isEdit ? 'Save changes' : 'Create task'}
        </Button>
      </div>
    </form>
  )
}

export function TaskFormDialog({
  open,
  onOpenChange,
  task,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Present = edit mode; absent = create mode. */
  task?: TaskResponse
}) {
  const isEdit = !!task

  // TaskResponse only carries customerId/leadId, never a display name — so
  // edit mode resolves each referenced Customer/Lead's name here (once,
  // centrally) rather than pushing that lookup onto every caller (list page
  // row actions, detail page). Both queries are `enabled` only when the
  // relevant id actually exists, and only while the dialog is open. Mirrors
  // QuotationDetailPage's identical customer-label resolution (Phase 22).
  const relatedQueries = useQueries({
    queries: [
      {
        queryKey: customerKeys.detail(task?.customerId ?? ''),
        queryFn: () => getCustomer(task?.customerId ?? ''),
        enabled: open && isEdit && !!task?.customerId,
      },
      {
        queryKey: leadKeys.detail(task?.leadId ?? ''),
        queryFn: () => getLead(task?.leadId ?? ''),
        enabled: open && isEdit && !!task?.leadId,
      },
    ],
  })
  const [customerQuery, leadQuery] = relatedQueries
  const isResolvingLabels =
    isEdit && ((!!task?.customerId && customerQuery.isLoading) || (!!task?.leadId && leadQuery.isLoading))

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Edit task' : 'Create task'}</DialogTitle>
          <DialogDescription>
            {isEdit ? 'Update this task’s details.' : 'Add a new task.'}
          </DialogDescription>
        </DialogHeader>
        {open && isResolvingLabels && (
          <div className="space-y-4">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        )}
        {open && !isResolvingLabels && (
          <TaskForm
            key={task?.id ?? 'create'}
            task={task}
            customerLabel={customerQuery.data?.name ?? ''}
            leadLabel={leadQuery.data?.name ?? ''}
            onOpenChange={onOpenChange}
          />
        )}
      </DialogContent>
    </Dialog>
  )
}
