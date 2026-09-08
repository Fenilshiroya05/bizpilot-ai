import { z } from 'zod'

// Mirrors backend/src/main/java/com/bizpilot/sales/dto/{LeadCreateRequest,LeadUpdateRequest}.java
// exactly. `source` is required on create (no default the backend would
// apply); `priority` is optional there but the UI always sends an explicit
// value, defaulting the select itself to MEDIUM to match the backend's own
// default rather than relying on omission.
const LEAD_SOURCES = ['WEBSITE', 'REFERRAL', 'SOCIAL_MEDIA', 'EMAIL', 'PHONE', 'OTHER'] as const
const LEAD_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH'] as const
const LEAD_STATUSES = ['NEW', 'CONTACTED', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'] as const

export const leadFormSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(255, 'Name must be at most 255 characters'),
  company: z.string().max(255, 'Company must be at most 255 characters'),
  email: z.union([z.literal(''), z.string().max(255).email('Enter a valid email address')]),
  phone: z.string().max(30, 'Phone must be at most 30 characters'),
  source: z.enum(LEAD_SOURCES, { error: 'Select a source' }),
  priority: z.enum(LEAD_PRIORITIES),
  // '' means "no follow-up date" — see LeadFormDialog's clearFollowUpDate handling.
  followUpDate: z.string(),
})

export type LeadFormValues = z.infer<typeof leadFormSchema>

export const leadEditFormSchema = leadFormSchema.extend({
  status: z.enum(LEAD_STATUSES),
})

export type LeadEditFormValues = z.infer<typeof leadEditFormSchema>

export const leadNoteSchema = z.object({
  content: z.string().trim().min(1, 'Note cannot be empty').max(2000, 'Note must be at most 2000 characters'),
})

export type LeadNoteFormValues = z.infer<typeof leadNoteSchema>

export const LEAD_SOURCE_OPTIONS = LEAD_SOURCES
export const LEAD_PRIORITY_OPTIONS = LEAD_PRIORITIES
export const LEAD_STATUS_OPTIONS = LEAD_STATUSES
