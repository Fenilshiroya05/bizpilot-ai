/**
 * This map is a UI visibility hint only.
 * Backend @PreAuthorize remains the authoritative security boundary.
 * Keep this synchronized with backend role/permission seed migrations.
 *
 * `/api/v1/auth/me` returns role NAMES only (`UserResponse.roles: Set<String>`)
 * — the backend never exposes a resolved permission list over the wire. This
 * map is therefore hand-mirrored from the actual Flyway seed data, read
 * directly from source (not assumed) on 2026-09-08:
 *   backend/src/main/resources/db/migration/V4__create_rbac_model.sql
 *   backend/src/main/resources/db/migration/V7__create_products.sql
 *   backend/src/main/resources/db/migration/V9__create_invoices.sql
 *   backend/src/main/resources/db/migration/V10__create_tasks.sql
 *   backend/src/main/resources/db/migration/V11__create_documents.sql
 *   backend/src/main/resources/db/migration/V13__create_analytics_permission.sql
 *
 * If a future backend migration changes a role's grants, this file must be
 * updated in the same PR — a stale entry here only ever over- or under-shows
 * a nav item / action button; it can never grant real access, because every
 * mutating and read endpoint re-checks the real permission server-side.
 */

export type Role = 'OWNER' | 'ADMIN' | 'MANAGER' | 'SALES' | 'EMPLOYEE'

export type Permission =
  | 'CUSTOMER_READ'
  | 'CUSTOMER_CREATE'
  | 'CUSTOMER_UPDATE'
  | 'CUSTOMER_DELETE'
  | 'LEAD_READ'
  | 'LEAD_CREATE'
  | 'LEAD_UPDATE'
  | 'LEAD_DELETE'
  | 'QUOTATION_READ'
  | 'QUOTATION_CREATE'
  | 'QUOTATION_UPDATE'
  | 'QUOTATION_DELETE'
  | 'PRODUCT_READ'
  | 'PRODUCT_CREATE'
  | 'PRODUCT_UPDATE'
  | 'PRODUCT_DELETE'
  | 'INVOICE_READ'
  | 'INVOICE_CREATE'
  | 'INVOICE_UPDATE'
  | 'INVOICE_DELETE'
  | 'TASK_READ'
  | 'TASK_CREATE'
  | 'TASK_UPDATE'
  | 'TASK_DELETE'
  | 'DOCUMENT_READ'
  | 'DOCUMENT_UPLOAD'
  | 'DOCUMENT_DELETE'
  | 'AI_USE'
  | 'USER_MANAGE'
  | 'ANALYTICS_READ'

const ALL_PERMISSIONS: Permission[] = [
  'CUSTOMER_READ',
  'CUSTOMER_CREATE',
  'CUSTOMER_UPDATE',
  'CUSTOMER_DELETE',
  'LEAD_READ',
  'LEAD_CREATE',
  'LEAD_UPDATE',
  'LEAD_DELETE',
  'QUOTATION_READ',
  'QUOTATION_CREATE',
  'QUOTATION_UPDATE',
  'QUOTATION_DELETE',
  'PRODUCT_READ',
  'PRODUCT_CREATE',
  'PRODUCT_UPDATE',
  'PRODUCT_DELETE',
  'INVOICE_READ',
  'INVOICE_CREATE',
  'INVOICE_UPDATE',
  'INVOICE_DELETE',
  'TASK_READ',
  'TASK_CREATE',
  'TASK_UPDATE',
  'TASK_DELETE',
  'DOCUMENT_READ',
  'DOCUMENT_UPLOAD',
  'DOCUMENT_DELETE',
  'AI_USE',
  'USER_MANAGE',
  'ANALYTICS_READ',
]

export const ROLE_PERMISSIONS: Record<Role, Permission[]> = {
  OWNER: ALL_PERMISSIONS,
  ADMIN: ALL_PERMISSIONS,
  MANAGER: [
    'CUSTOMER_READ',
    'CUSTOMER_CREATE',
    'CUSTOMER_UPDATE',
    'CUSTOMER_DELETE',
    'LEAD_READ',
    'LEAD_CREATE',
    'LEAD_UPDATE',
    'LEAD_DELETE',
    'QUOTATION_READ',
    'QUOTATION_CREATE',
    'QUOTATION_UPDATE',
    'QUOTATION_DELETE',
    'PRODUCT_READ',
    'PRODUCT_CREATE',
    'PRODUCT_UPDATE',
    'PRODUCT_DELETE',
    'INVOICE_READ',
    'INVOICE_CREATE',
    'INVOICE_UPDATE',
    'INVOICE_DELETE',
    'TASK_READ',
    'TASK_CREATE',
    'TASK_UPDATE',
    'TASK_DELETE',
    'DOCUMENT_READ',
    'DOCUMENT_UPLOAD',
    'DOCUMENT_DELETE',
    'AI_USE',
    'ANALYTICS_READ',
  ],
  SALES: [
    'CUSTOMER_READ',
    'CUSTOMER_CREATE',
    'CUSTOMER_UPDATE',
    'LEAD_READ',
    'LEAD_CREATE',
    'LEAD_UPDATE',
    'QUOTATION_READ',
    'QUOTATION_CREATE',
    'QUOTATION_UPDATE',
    'PRODUCT_READ',
    'PRODUCT_CREATE',
    'PRODUCT_UPDATE',
    'INVOICE_READ',
    'INVOICE_CREATE',
    'INVOICE_UPDATE',
    'TASK_READ',
    'TASK_CREATE',
    'TASK_UPDATE',
    'DOCUMENT_READ',
    'DOCUMENT_UPLOAD',
    'AI_USE',
    'ANALYTICS_READ',
  ],
  EMPLOYEE: [
    'CUSTOMER_READ',
    'LEAD_READ',
    'QUOTATION_READ',
    'PRODUCT_READ',
    'INVOICE_READ',
    'TASK_READ',
    'TASK_CREATE',
    'TASK_UPDATE',
    'DOCUMENT_READ',
    'AI_USE',
    'ANALYTICS_READ',
  ],
}

/** Union of every permission granted by any of the user's roles. */
export function resolvePermissions(roles: readonly string[]): Set<Permission> {
  const resolved = new Set<Permission>()
  for (const role of roles) {
    const grants = ROLE_PERMISSIONS[role as Role]
    if (!grants) continue
    for (const permission of grants) resolved.add(permission)
  }
  return resolved
}

export function hasPermission(
  roles: readonly string[] | undefined,
  permission: Permission,
): boolean {
  if (!roles || roles.length === 0) return false
  return resolvePermissions(roles).has(permission)
}
