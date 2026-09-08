import { apiFetch, apiFetchBlob } from '@/lib/api-client'
import { buildQueryString } from '@/lib/utils'
import type {
  Page,
  QuotationCreateRequest,
  QuotationListParams,
  QuotationResponse,
  QuotationSummaryResponse,
  QuotationUpdateRequest,
} from '@/types/api'

// Same 'list'/'detail' key-segment convention as every other Phase 21/22 feature.
export const quotationKeys = {
  list: (params: QuotationListParams) => ['quotations', 'list', params] as const,
  detail: (id: string) => ['quotations', 'detail', id] as const,
}

export function searchQuotations(params: QuotationListParams): Promise<Page<QuotationSummaryResponse>> {
  return apiFetch<Page<QuotationSummaryResponse>>(`/api/v1/quotations${buildQueryString(params)}`)
}

export function getQuotation(id: string): Promise<QuotationResponse> {
  return apiFetch<QuotationResponse>(`/api/v1/quotations/${id}`)
}

export function createQuotation(payload: QuotationCreateRequest): Promise<QuotationResponse> {
  return apiFetch<QuotationResponse>('/api/v1/quotations', { method: 'POST', body: payload })
}

export function updateQuotation(id: string, payload: QuotationUpdateRequest): Promise<QuotationResponse> {
  return apiFetch<QuotationResponse>(`/api/v1/quotations/${id}`, { method: 'PATCH', body: payload })
}

/** Cancels the quotation (backend transitions status to CANCELLED) — the only lifecycle action on a non-DRAFT quotation. */
export function cancelQuotation(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/quotations/${id}`, { method: 'DELETE' })
}

/** Generated on demand by the backend, never persisted — returns the raw PDF bytes. */
export function downloadQuotationPdf(id: string): Promise<Blob> {
  return apiFetchBlob(`/api/v1/quotations/${id}/pdf`)
}
