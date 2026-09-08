import { createContext, useContext, useMemo, useSyncExternalStore, type ReactNode } from 'react'

import { useQuery, useQueryClient } from '@tanstack/react-query'

import { getMe, login as loginRequest, logout as logoutRequest } from '@/features/auth/api'
import { getCurrentOrganization } from '@/features/settings/api'
import { clearTokens, getTokens, setTokens, subscribeTokens } from '@/lib/api-client'
import { resolvePermissions, type Permission } from '@/lib/permissions'
import type { LoginRequest, OrganizationResponse, UserResponse } from '@/types/api'

interface AuthContextValue {
  isAuthenticated: boolean
  /** True only while an authenticated session's user/org data hasn't loaded yet. */
  isBootstrapping: boolean
  user: UserResponse | undefined
  organization: OrganizationResponse | undefined
  permissions: Set<Permission>
  login: (payload: LoginRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const isAuthenticated = useSyncExternalStore(subscribeTokens, () => getTokens() !== null)
  const queryClient = useQueryClient()

  const meQuery = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getMe,
    enabled: isAuthenticated,
  })

  const organizationQuery = useQuery({
    queryKey: ['organization', 'current'],
    queryFn: getCurrentOrganization,
    enabled: isAuthenticated,
  })

  async function login(payload: LoginRequest): Promise<void> {
    const tokens = await loginRequest(payload)
    setTokens(tokens)
  }

  async function logout(): Promise<void> {
    const tokens = getTokens()
    try {
      if (tokens) {
        await logoutRequest({ refreshToken: tokens.refreshToken })
      }
    } finally {
      clearTokens()
      queryClient.removeQueries({ queryKey: ['auth', 'me'] })
      queryClient.removeQueries({ queryKey: ['organization', 'current'] })
    }
  }

  const permissions = useMemo(
    () => resolvePermissions(meQuery.data?.roles ?? []),
    [meQuery.data?.roles],
  )

  const value: AuthContextValue = {
    isAuthenticated,
    isBootstrapping: isAuthenticated && (meQuery.isLoading || organizationQuery.isLoading),
    user: meQuery.data,
    organization: organizationQuery.data,
    permissions,
    login,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}
