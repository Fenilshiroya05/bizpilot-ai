import { z } from 'zod'

import { isValidDecimalString } from '@/lib/decimal'

// Mirrors backend/src/main/java/com/bizpilot/sales/dto/{QuotationCreateRequest,QuotationUpdateRequest,QuotationItemRequest}.java
// exactly, including the audit-hardened quantity bound (999999.9999).
export const quotationLineFormSchema = z.object({
  productId: z.string().min(1, 'Select a product'),
  // Display-only — never sent to the backend.
  productLabel: z.string(),
  quantity: z
    .string()
    .min(1, 'Quantity is required')
    .refine((v) => isValidDecimalString(v, 4), 'Enter a valid quantity (up to 4 decimal places)')
    .refine((v) => Number(v) > 0, 'Quantity must be greater than 0')
    .refine((v) => Number(v) <= 999999.9999, 'Quantity must be at most 999999.9999'),
  // Snapshotted from the selected product purely for the local, non-authoritative
  // preview total — never sent to the backend (see previewTotals.ts).
  unitPrice: z.number(),
  taxPercentage: z.number(),
})

export type QuotationLineFormValues = z.infer<typeof quotationLineFormSchema>

export const quotationFormSchema = z.object({
  customerId: z.string().min(1, 'Select a customer'),
  // Display-only — never sent to the backend.
  customerLabel: z.string(),
  // '' = no date.
  validUntil: z.string(),
  // '' = omitted, backend defaults to 0.
  discountPercentage: z
    .string()
    .refine((v) => v === '' || isValidDecimalString(v, 2), 'Enter a valid discount (up to 2 decimal places)')
    .refine((v) => v === '' || Number(v) <= 100, 'Discount must be at most 100'),
  items: z.array(quotationLineFormSchema).min(1, 'Add at least one line item'),
})

export type QuotationFormValues = z.infer<typeof quotationFormSchema>
