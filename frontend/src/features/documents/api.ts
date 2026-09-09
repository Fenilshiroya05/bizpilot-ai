import { apiFetch, apiFetchBlob } from '@/lib/api-client'
import { buildQueryString } from '@/lib/utils'
import type { DocumentListParams, DocumentResponse, Page } from '@/types/api'

// Same 'list'/'detail' key-segment convention as every other feature's
// api.ts (see tasks/api.ts, quotations/api.ts).
export const documentKeys = {
  list: (params: DocumentListParams) => ['documents', 'list', params] as const,
  detail: (id: string) => ['documents', 'detail', id] as const,
}

export function searchDocuments(params: DocumentListParams): Promise<Page<DocumentResponse>> {
  return apiFetch<Page<DocumentResponse>>(`/api/v1/documents${buildQueryString(params)}`)
}

export function getDocument(id: string): Promise<DocumentResponse> {
  return apiFetch<DocumentResponse>(`/api/v1/documents/${id}`)
}

/**
 * The backend accepts only a single `file` multipart part — no title,
 * description, or other metadata field exists on the upload endpoint
 * (verified against `DocumentController.upload`).
 */
export function uploadDocument(file: File): Promise<DocumentResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch<DocumentResponse>('/api/v1/documents', { method: 'POST', body: formData })
}

export function downloadDocument(id: string): Promise<Blob> {
  return apiFetchBlob(`/api/v1/documents/${id}/download`)
}

/** Hard delete — there is no soft-cancel state for Documents (unlike Task/Quotation/Invoice). */
export function deleteDocument(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/documents/${id}`, { method: 'DELETE' })
}
