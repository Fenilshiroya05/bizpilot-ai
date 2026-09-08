import type { ReactNode } from 'react'

import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from '@/app/providers/AuthProvider'
import { EmptyState } from '@/components/feedback/EmptyState'
import type { Permission } from '@/lib/permissions'

/**
 * Route-level auth gate. This is a UX convenience (avoids rendering a shell
 * full of components that would all 401) — it is not the security boundary.
 * Every API call is still independently authorized by the backend.
 */
export function RequireAuth() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/auth/login" replace state={{ from: location }} />
  }

  return <Outlet />
}

/**
 * UI visibility hint only — see lib/permissions.ts. Hides a route the user's
 * role isn't mirrored as having; the backend's own @PreAuthorize check is
 * what actually protects the underlying data if this ever drifts.
 */
export function RequirePermission({
  permission,
  children,
}: {
  permission: Permission
  children: ReactNode
}) {
  const { permissions } = useAuth()

  if (!permissions.has(permission)) {
    return (
      <EmptyState
        title="You don't have access to this page"
        description="Contact your organization owner or admin if you believe this is a mistake."
      />
    )
  }

  return <>{children}</>
}
