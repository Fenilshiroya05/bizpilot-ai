import { z } from 'zod'

import { isValidDecimalString } from '@/lib/decimal'

// Mirrors backend/src/main/java/com/bizpilot/products/dto/{ProductCreateRequest,ProductUpdateRequest}.java
// exactly, including the audit-hardened price bound (99999999.9999) and the
// 4dp/2dp precision limits.
export const productFormSchema = z.object({
  sku: z.string().trim().min(1, 'SKU is required').max(50, 'SKU must be at most 50 characters'),
  name: z.string().trim().min(1, 'Name is required').max(255, 'Name must be at most 255 characters'),
  description: z.string().max(2000, 'Description must be at most 2000 characters'),
  unit: z.string().trim().min(1, 'Unit is required').max(20, 'Unit must be at most 20 characters'),
  price: z
    .string()
    .min(1, 'Price is required')
    .refine((v) => isValidDecimalString(v, 4), 'Enter a valid price (up to 4 decimal places)')
    .refine((v) => Number(v) <= 99999999.9999, 'Price must be at most 99999999.9999'),
  // '' = omitted, backend defaults to 0.
  taxPercentage: z
    .string()
    .refine((v) => v === '' || isValidDecimalString(v, 2), 'Enter a valid tax percentage (up to 2 decimal places)')
    .refine((v) => v === '' || Number(v) <= 100, 'Tax percentage must be at most 100'),
  categoryId: z.string(),
  status: z.enum(['ACTIVE', 'INACTIVE']),
})

export type ProductFormValues = z.infer<typeof productFormSchema>

export const productCategoryFormSchema = z.object({
  name: z.string().trim().min(1, 'Category name is required').max(255, 'Name must be at most 255 characters'),
})

export type ProductCategoryFormValues = z.infer<typeof productCategoryFormSchema>
