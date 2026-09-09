import { z } from 'zod'

import type { DocumentStatus } from '@/types/api'

/** Mirrors `DocumentValidator.EXTENSION_TO_CONTENT_TYPE` exactly — do not add formats the backend doesn't accept. */
export const ALLOWED_DOCUMENT_EXTENSIONS = ['.pdf', '.txt', '.docx']

/** Mirrors `DocumentValidator.MAX_FILE_SIZE_BYTES` — kept in sync with the backend constant, not independently chosen. */
export const MAX_DOCUMENT_SIZE_BYTES = 20 * 1024 * 1024

function hasAllowedExtension(filename: string): boolean {
  const lower = filename.toLowerCase()
  return ALLOWED_DOCUMENT_EXTENSIONS.some((ext) => lower.endsWith(ext))
}

/**
 * Client-side validation for UX only — mirrors `DocumentValidator`'s rules
 * (extension/size) so obviously-invalid uploads fail fast, but the backend
 * remains authoritative (it additionally checks declared Content-Type
 * agreement and a magic-byte signature, neither of which the browser lets
 * us reliably pre-check).
 */
export const documentUploadSchema = z.object({
  file: z
    .instanceof(File, { message: 'A file is required' })
    .refine((file) => file.size > 0, { message: 'File is empty' })
    .refine((file) => file.size <= MAX_DOCUMENT_SIZE_BYTES, {
      message: 'File exceeds the maximum allowed size of 20 MB',
    })
    .refine((file) => hasAllowedExtension(file.name), {
      message: 'Unsupported file type. Use PDF, TXT, or DOCX.',
    }),
})

export type DocumentUploadFormValues = z.infer<typeof documentUploadSchema>

export const DOCUMENT_STATUS_OPTIONS: DocumentStatus[] = ['UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED']

/** Mirrors `DocumentValidator.EXTENSION_TO_CONTENT_TYPE` — the only three values the backend's `contentType` filter can ever match. */
export const DOCUMENT_CONTENT_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'application/pdf', label: 'PDF' },
  { value: 'text/plain', label: 'TXT' },
  { value: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', label: 'DOCX' },
]

export function contentTypeLabel(contentType: string): string {
  return DOCUMENT_CONTENT_TYPE_OPTIONS.find((o) => o.value === contentType)?.label ?? contentType
}
