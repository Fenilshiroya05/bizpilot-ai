import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { RequireAuth, RequirePermission } from '@/app/guards'
import { AuthProvider } from '@/app/providers/AuthProvider'
import { getMe } from '@/features/auth/api'
import { clearTokens, setTokens } from '@/lib/api-client'
import { createTestQueryClient } from '@/test/render'

vi.mock('@/features/auth/api', () => ({
  getMe: vi.fn().mockResolvedValue({
    id: 'user-1',
    email: 'a@b.com',
    firstName: 'A',
    lastName: 'B',
    roles: ['OWNER'],
    status: 'ACTIVE',
    organizationId: 'org-1',
    createdAt: '2026-01-01T00:00:00Z',
  }),
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
}))
vi.mock('@/features/settings/api', () => ({
  getCurrentOrganization: vi
    .fn()
    .mockResolvedValue({ id: 'org-1', name: 'Acme', createdAt: '2026-01-01T00:00:00Z' }),
}))

function renderGuarded(initialPath: string) {
  const queryClient = createTestQueryClient()
  const router = createMemoryRouter(
    [
      { path: '/auth/login', element: <div>Login Page</div> },
      {
        element: <RequireAuth />,
        children: [{ path: '/dashboard', element: <div>Protected Content</div> }],
      },
    ],
    { initialEntries: [initialPath] },
  )
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('RequireAuth', () => {
  beforeEach(() => clearTokens())
  afterEach(() => clearTokens())

  it('redirects to /auth/login when there is no session', () => {
    renderGuarded('/dashboard')
    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('renders the protected route when a session exists', async () => {
    setTokens({ accessToken: 'token', refreshToken: 'refresh' })
    renderGuarded('/dashboard')
    await waitFor(() => expect(screen.getByText('Protected Content')).toBeInTheDocument())
  })
})

describe('RequirePermission', () => {
  beforeEach(() => clearTokens())
  afterEach(() => clearTokens())

  function renderWithPermission() {
    const queryClient = createTestQueryClient()
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <RequirePermission permission="CUSTOMER_DELETE">
            <div>Restricted Content</div>
          </RequirePermission>
        </AuthProvider>
      </QueryClientProvider>,
    )
  }

  it('shows a clear no-access message for a user without the required permission', async () => {
    vi.mocked(getMe).mockResolvedValueOnce({
      id: 'user-2',
      email: 'employee@b.com',
      firstName: 'Em',
      lastName: 'Ployee',
      roles: ['EMPLOYEE'],
      status: 'ACTIVE',
      organizationId: 'org-1',
      createdAt: '2026-01-01T00:00:00Z',
    })
    setTokens({ accessToken: 'token', refreshToken: 'refresh' })

    renderWithPermission()

    await waitFor(() =>
      expect(screen.getByText("You don't have access to this page")).toBeInTheDocument(),
    )
    expect(screen.queryByText('Restricted Content')).not.toBeInTheDocument()
  })

  it('renders the content for a user who does have the required permission', async () => {
    // The default mocked getMe (module-level factory above) returns OWNER.
    setTokens({ accessToken: 'token', refreshToken: 'refresh' })

    renderWithPermission()

    await waitFor(() => expect(screen.getByText('Restricted Content')).toBeInTheDocument())
  })
})
