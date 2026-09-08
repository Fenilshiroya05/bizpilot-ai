import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/app/providers/AuthProvider'
import { getMe } from '@/features/auth/api'
import { getCurrentOrganization } from '@/features/settings/api'
import { usePermission } from '@/hooks/usePermission'
import { clearTokens, setTokens } from '@/lib/api-client'
import { createTestQueryClient } from '@/test/render'

vi.mock('@/features/auth/api')
vi.mock('@/features/settings/api')

function Probe() {
  const canCreateCustomer = usePermission('CUSTOMER_CREATE')
  const canDeleteCustomer = usePermission('CUSTOMER_DELETE')
  return (
    <div>
      <span data-testid="create">{String(canCreateCustomer)}</span>
      <span data-testid="delete">{String(canDeleteCustomer)}</span>
    </div>
  )
}

describe('usePermission', () => {
  beforeEach(() => {
    clearTokens()
    vi.mocked(getCurrentOrganization).mockResolvedValue({
      id: 'org-1',
      name: 'Acme',
      createdAt: '2026-01-01T00:00:00Z',
    })
  })
  afterEach(() => clearTokens())

  it('reflects the SALES role from the backend: create allowed, delete denied', async () => {
    vi.mocked(getMe).mockResolvedValue({
      id: 'u1',
      email: 'a@b.com',
      firstName: 'A',
      lastName: 'B',
      roles: ['SALES'],
      status: 'ACTIVE',
      organizationId: 'org-1',
      createdAt: '2026-01-01T00:00:00Z',
    })
    setTokens({ accessToken: 't', refreshToken: 'r' })

    const queryClient = createTestQueryClient()
    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </QueryClientProvider>,
    )

    await waitFor(() => expect(screen.getByTestId('create')).toHaveTextContent('true'))
    expect(screen.getByTestId('delete')).toHaveTextContent('false')
  })

  it('denies everything when there is no session at all', () => {
    const queryClient = createTestQueryClient()
    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </QueryClientProvider>,
    )

    expect(screen.getByTestId('create')).toHaveTextContent('false')
    expect(screen.getByTestId('delete')).toHaveTextContent('false')
  })
})
