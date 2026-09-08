import { z } from 'zod'

// Mirrors backend/src/main/java/com/bizpilot/crm/dto/{CustomerCreateRequest,CustomerUpdateRequest}.java
// exactly, including the GSTIN pattern (which itself permits an empty string,
// matching the backend's own "^$|..." convention for clearing an optional field).
const GSTIN_REGEX = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/

export const customerFormSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(255, 'Name must be at most 255 characters'),
  company: z.string().max(255, 'Company must be at most 255 characters'),
  email: z.union([z.literal(''), z.string().max(255).email('Enter a valid email address')]),
  // No format validation — the backend deliberately accepts any phone format.
  phone: z.string().max(30, 'Phone must be at most 30 characters'),
  address: z.string().max(500, 'Address must be at most 500 characters'),
  gstin: z
    .string()
    .max(15, 'GSTIN must be at most 15 characters')
    .refine((value) => value === '' || GSTIN_REGEX.test(value), 'GSTIN must be a valid 15-character GSTIN'),
  notes: z.string().max(2000, 'Notes must be at most 2000 characters'),
})

export type CustomerFormValues = z.infer<typeof customerFormSchema>

export const customerEditFormSchema = customerFormSchema.extend({
  status: z.enum(['ACTIVE', 'INACTIVE']),
})

export type CustomerEditFormValues = z.infer<typeof customerEditFormSchema>

export const customerNoteSchema = z.object({
  content: z.string().trim().min(1, 'Note cannot be empty').max(2000, 'Note must be at most 2000 characters'),
})

export type CustomerNoteFormValues = z.infer<typeof customerNoteSchema>
