import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/app/providers/AuthProvider'
import { getMe } from '@/features/auth/api'
import { getCurrentOrganization } from '@/features/settings/api'
import { clearTokens, setTokens } from '@/lib/api-client'
import { createTestQueryClient } from '@/test/render'
import { CustomersListPage } from './CustomersListPage'
import * as customersApi from './api'

vi.mock('./api')
vi.mock('@/features/auth/api')
vi.mock('@/features/settings/api')

function mockUser(roles: string[]) {
  vi.mocked(getMe).mockResolvedValue({
    id: 'u1',
    email: 'a@b.com',
    firstName: 'A',
    lastName: 'B',
    roles,
    status: 'ACTIVE',
    organizationId: 'org-1',
    createdAt: '2026-01-01T00:00:00Z',
  })
  vi.mocked(getCurrentOrganization).mockResolvedValue({
    id: 'org-1',
    name: 'Acme',
    createdAt: '2026-01-01T00:00:00Z',
  })
}

function renderPage() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthProvider>
          <CustomersListPage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('CustomersListPage RBAC visibility', () => {
  beforeEach(() => {
    clearTokens()
    vi.mocked(customersApi.searchCustomers).mockResolvedValue({
      content: [
        {
          id: 'c1',
          name: 'Acme Corp',
          company: null,
          email: null,
          phone: null,
          address: null,
          gstin: null,
          status: 'ACTIVE',
          notes: null,
          organizationId: 'org-1',
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
      first: true,
      last: true,
      empty: false,
    })
  })
  afterEach(() => clearTokens())

  it('EMPLOYEE sees records but no Create customer button and no Edit/Archive row actions', async () => {
    mockUser(['EMPLOYEE'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await waitFor(() => expect(screen.getAllByText('Acme Corp').length).toBeGreaterThan(0))
    expect(screen.queryByRole('button', { name: /Create customer/i })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Acme Corp' }))
    expect(screen.getByRole('menuitem', { name: 'View details' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Archive' })).not.toBeInTheDocument()
  })

  it('SALES sees Create customer and Edit, but not Archive', async () => {
    mockUser(['SALES'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await waitFor(() => expect(screen.getAllByText('Acme Corp').length).toBeGreaterThan(0))
    expect(screen.getByRole('button', { name: /Create customer/i })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Acme Corp' }))
    expect(screen.getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Archive' })).not.toBeInTheDocument()
  })

  it('OWNER sees Create customer, Edit, and Archive', async () => {
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await waitFor(() => expect(screen.getAllByText('Acme Corp').length).toBeGreaterThan(0))
    expect(screen.getByRole('button', { name: /Create customer/i })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Acme Corp' }))
    expect(screen.getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Archive' })).toBeInTheDocument()
  })
})
