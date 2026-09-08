import { apiFetch } from '@/lib/api-client'
import { buildQueryString } from '@/lib/utils'
import type {
  LeadActivityResponse,
  LeadAssignRequest,
  LeadCreateRequest,
  LeadListParams,
  LeadResponse,
  LeadScoreResponse,
  LeadUpdateRequest,
  Page,
} from '@/types/api'

// Same 'list'/'detail' key-segment convention as features/customers/api.ts —
// lets mutations invalidate precisely without ever touching an unrelated
// resource (e.g. ['analytics', ...]).
export const leadKeys = {
  list: (params: LeadListParams) => ['leads', 'list', params] as const,
  detail: (id: string) => ['leads', 'detail', id] as const,
  history: (id: string, page: number) => ['leads', 'detail', id, 'history', page] as const,
}

export function searchLeads(params: LeadListParams): Promise<Page<LeadResponse>> {
  return apiFetch<Page<LeadResponse>>(`/api/v1/leads${buildQueryString(params)}`)
}

export function getLead(id: string): Promise<LeadResponse> {
  return apiFetch<LeadResponse>(`/api/v1/leads/${id}`)
}

export function createLead(payload: LeadCreateRequest): Promise<LeadResponse> {
  return apiFetch<LeadResponse>('/api/v1/leads', { method: 'POST', body: payload })
}

export function updateLead(id: string, payload: LeadUpdateRequest): Promise<LeadResponse> {
  return apiFetch<LeadResponse>(`/api/v1/leads/${id}`, { method: 'PATCH', body: payload })
}

export function archiveLead(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/leads/${id}`, { method: 'DELETE' })
}

export function assignLead(id: string, payload: LeadAssignRequest): Promise<LeadResponse> {
  return apiFetch<LeadResponse>(`/api/v1/leads/${id}/assign`, { method: 'POST', body: payload })
}

export function addLeadNote(id: string, content: string): Promise<LeadActivityResponse> {
  return apiFetch<LeadActivityResponse>(`/api/v1/leads/${id}/notes`, { method: 'POST', body: { content } })
}

export function getLeadHistory(id: string, page: number, size = 20): Promise<Page<LeadActivityResponse>> {
  return apiFetch<Page<LeadActivityResponse>>(`/api/v1/leads/${id}/history${buildQueryString({ page, size })}`)
}

export function scoreLead(id: string): Promise<LeadScoreResponse> {
  return apiFetch<LeadScoreResponse>(`/api/v1/leads/${id}/score`, { method: 'POST' })
}
