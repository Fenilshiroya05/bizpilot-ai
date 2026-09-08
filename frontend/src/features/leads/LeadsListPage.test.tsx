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
import { LeadsListPage } from './LeadsListPage'
import * as leadsApi from './api'

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
          <LeadsListPage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('LeadsListPage RBAC visibility', () => {
  beforeEach(() => {
    clearTokens()
    vi.mocked(leadsApi.searchLeads).mockResolvedValue({
      content: [
        {
          id: 'l1',
          name: 'Jane Prospect',
          company: 'Acme Prospects',
          email: null,
          phone: null,
          status: 'NEW',
          source: 'WEBSITE',
          priority: 'MEDIUM',
          followUpDate: null,
          assignedToUserId: null,
          archivedAt: null,
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

  it('EMPLOYEE sees leads but no Create lead button and no Edit/Archive row actions', async () => {
    mockUser(['EMPLOYEE'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await waitFor(() => expect(screen.getAllByText('Jane Prospect').length).toBeGreaterThan(0))
    expect(screen.queryByRole('button', { name: /Create lead/i })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Jane Prospect' }))
    expect(screen.getByRole('menuitem', { name: 'View details' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Archive' })).not.toBeInTheDocument()
  })

  it('SALES sees Create lead and Edit, but not Archive', async () => {
    mockUser(['SALES'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await waitFor(() => expect(screen.getAllByText('Jane Prospect').length).toBeGreaterThan(0))
    expect(screen.getByRole('button', { name: /Create lead/i })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Jane Prospect' }))
    expect(screen.getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Archive' })).not.toBeInTheDocument()
  })

  it('shows the My Leads / Unassigned / Follow-up Due quick filters', async () => {
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await waitFor(() => expect(screen.getAllByText('Jane Prospect').length).toBeGreaterThan(0))
    expect(screen.getByRole('button', { name: /My Leads/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Unassigned/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Follow-up Due/i })).toBeInTheDocument()
  })
})
