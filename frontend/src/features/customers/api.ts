import { apiFetch } from '@/lib/api-client'
import { buildQueryString } from '@/lib/utils'
import type {
  CustomerActivityResponse,
  CustomerCreateRequest,
  CustomerListParams,
  CustomerResponse,
  CustomerUpdateRequest,
  Page,
} from '@/types/api'

// A 'list'/'detail' second segment lets mutations invalidate precisely —
// e.g. archiving a customer invalidates ['customers','list'] (every list
// query, any filter combo) and ['customers','detail',id] (that customer's
// detail + its history, which nests under the same id prefix) without ever
// touching the other's queries or anything outside the 'customers' resource
// (never ['analytics', ...]).
export const customerKeys = {
  list: (params: CustomerListParams) => ['customers', 'list', params] as const,
  detail: (id: string) => ['customers', 'detail', id] as const,
  history: (id: string, page: number) => ['customers', 'detail', id, 'history', page] as const,
}

export function searchCustomers(params: CustomerListParams): Promise<Page<CustomerResponse>> {
  return apiFetch<Page<CustomerResponse>>(`/api/v1/customers${buildQueryString(params)}`)
}

export function getCustomer(id: string): Promise<CustomerResponse> {
  return apiFetch<CustomerResponse>(`/api/v1/customers/${id}`)
}

export function createCustomer(payload: CustomerCreateRequest): Promise<CustomerResponse> {
  return apiFetch<CustomerResponse>('/api/v1/customers', { method: 'POST', body: payload })
}

export function updateCustomer(id: string, payload: CustomerUpdateRequest): Promise<CustomerResponse> {
  return apiFetch<CustomerResponse>(`/api/v1/customers/${id}`, { method: 'PATCH', body: payload })
}

export function archiveCustomer(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/customers/${id}`, { method: 'DELETE' })
}

export function addCustomerNote(id: string, content: string): Promise<CustomerActivityResponse> {
  return apiFetch<CustomerActivityResponse>(`/api/v1/customers/${id}/notes`, {
    method: 'POST',
    body: { content },
  })
}

export function getCustomerHistory(id: string, page: number, size = 20): Promise<Page<CustomerActivityResponse>> {
  return apiFetch<Page<CustomerActivityResponse>>(
    `/api/v1/customers/${id}/history${buildQueryString({ page, size })}`,
  )
}
