import { describe, expect, it } from 'vitest'

import { hasPermission, resolvePermissions } from './permissions'

describe('resolvePermissions / hasPermission', () => {
  it('grants EMPLOYEE read-only access plus Task create/update, matching V10/V4 seed data', () => {
    expect(hasPermission(['EMPLOYEE'], 'CUSTOMER_READ')).toBe(true)
    expect(hasPermission(['EMPLOYEE'], 'CUSTOMER_CREATE')).toBe(false)
    expect(hasPermission(['EMPLOYEE'], 'TASK_CREATE')).toBe(true)
    expect(hasPermission(['EMPLOYEE'], 'TASK_DELETE')).toBe(false)
  })

  it('grants SALES CRUD minus delete on Customers/Leads/Quotations/Products/Invoices', () => {
    expect(hasPermission(['SALES'], 'CUSTOMER_UPDATE')).toBe(true)
    expect(hasPermission(['SALES'], 'CUSTOMER_DELETE')).toBe(false)
    expect(hasPermission(['SALES'], 'INVOICE_CREATE')).toBe(true)
    expect(hasPermission(['SALES'], 'INVOICE_DELETE')).toBe(false)
  })

  it('grants MANAGER full CRUD on business records but never USER_MANAGE', () => {
    expect(hasPermission(['MANAGER'], 'CUSTOMER_DELETE')).toBe(true)
    expect(hasPermission(['MANAGER'], 'DOCUMENT_DELETE')).toBe(true)
    expect(hasPermission(['MANAGER'], 'USER_MANAGE')).toBe(false)
  })

  it('grants OWNER and ADMIN the full permission catalog, including USER_MANAGE', () => {
    expect(hasPermission(['OWNER'], 'USER_MANAGE')).toBe(true)
    expect(hasPermission(['ADMIN'], 'USER_MANAGE')).toBe(true)
    expect(hasPermission(['OWNER'], 'ANALYTICS_READ')).toBe(true)
  })

  it('grants ANALYTICS_READ to every role', () => {
    for (const role of ['OWNER', 'ADMIN', 'MANAGER', 'SALES', 'EMPLOYEE']) {
      expect(hasPermission([role], 'ANALYTICS_READ')).toBe(true)
    }
  })

  it('returns false for a user with no roles', () => {
    expect(hasPermission([], 'CUSTOMER_READ')).toBe(false)
    expect(hasPermission(undefined, 'CUSTOMER_READ')).toBe(false)
  })

  it('unions permissions across multiple roles', () => {
    const permissions = resolvePermissions(['EMPLOYEE', 'SALES'])
    expect(permissions.has('CUSTOMER_CREATE')).toBe(true) // from SALES
    expect(permissions.has('TASK_CREATE')).toBe(true) // from EMPLOYEE
  })
})
