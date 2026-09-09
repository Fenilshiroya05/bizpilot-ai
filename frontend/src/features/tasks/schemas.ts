import { z } from 'zod'

// Mirrors backend/src/main/java/com/bizpilot/tasks/dto/{TaskCreateRequest,TaskUpdateRequest}.java
// exactly. `notes` is deliberately absent from the create schema — the
// backend's own TaskCreateRequest has no notes field at all.
const TASK_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as const
const TASK_STATUSES = ['TODO', 'IN_PROGRESS', 'COMPLETED'] as const // CANCELLED excluded — reached only via the dedicated Cancel action.

export const taskFormSchema = z.object({
  title: z.string().trim().min(1, 'Title is required').max(255, 'Title must be at most 255 characters'),
  description: z.string().max(2000, 'Description must be at most 2000 characters'),
  priority: z.enum(TASK_PRIORITIES),
  // '' means "no due date" — see TaskFormDialog's clearDueDate handling.
  dueDate: z.string(),
  // '' means "no related customer/lead" — see clearCustomerId/clearLeadId handling.
  customerId: z.string(),
  customerLabel: z.string(),
  leadId: z.string(),
  leadLabel: z.string(),
})

export type TaskFormValues = z.infer<typeof taskFormSchema>

export const taskEditFormSchema = taskFormSchema.extend({
  status: z.enum(TASK_STATUSES),
  notes: z.string().max(2000, 'Notes must be at most 2000 characters'),
})

export type TaskEditFormValues = z.infer<typeof taskEditFormSchema>

export const TASK_PRIORITY_OPTIONS = TASK_PRIORITIES
export const TASK_STATUS_OPTIONS = TASK_STATUSES
