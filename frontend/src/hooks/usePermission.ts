import { useAuth } from '@/app/providers/AuthProvider'
import type { Permission } from '@/lib/permissions'

/**
 * UI visibility hint only — see lib/permissions.ts. Backend @PreAuthorize
 * remains the authoritative check on every request this drives the
 * visibility of.
 */
export function usePermission(permission: Permission): boolean {
  const { permissions } = useAuth()
  return permissions.has(permission)
}
