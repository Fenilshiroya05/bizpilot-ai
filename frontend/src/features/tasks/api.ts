import { apiFetch } from '@/lib/api-client'
import { buildQueryString } from '@/lib/utils'
import type {
  Page,
  TaskAssignRequest,
  TaskCreateRequest,
  TaskListParams,
  TaskResponse,
  TaskUpdateRequest,
} from '@/types/api'

// Same 'list'/'detail' key-segment convention as every other feature's
// api.ts — lets mutations invalidate precisely without ever touching an
// unrelated resource (e.g. ['customers', ...] or ['leads', ...]), even
// though a Task can reference a Customer/Lead.
export const taskKeys = {
  list: (params: TaskListParams) => ['tasks', 'list', params] as const,
  detail: (id: string) => ['tasks', 'detail', id] as const,
}

export function searchTasks(params: TaskListParams): Promise<Page<TaskResponse>> {
  return apiFetch<Page<TaskResponse>>(`/api/v1/tasks${buildQueryString(params)}`)
}

export function getTask(id: string): Promise<TaskResponse> {
  return apiFetch<TaskResponse>(`/api/v1/tasks/${id}`)
}

export function createTask(payload: TaskCreateRequest): Promise<TaskResponse> {
  return apiFetch<TaskResponse>('/api/v1/tasks', { method: 'POST', body: payload })
}

export function updateTask(id: string, payload: TaskUpdateRequest): Promise<TaskResponse> {
  return apiFetch<TaskResponse>(`/api/v1/tasks/${id}`, { method: 'PATCH', body: payload })
}

export function assignTask(id: string, payload: TaskAssignRequest): Promise<TaskResponse> {
  return apiFetch<TaskResponse>(`/api/v1/tasks/${id}/assign`, { method: 'POST', body: payload })
}

/** Cancel — CLAUDE.md's task "delete," modeled as a transition to the existing CANCELLED status. */
export function cancelTask(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/tasks/${id}`, { method: 'DELETE' })
}
