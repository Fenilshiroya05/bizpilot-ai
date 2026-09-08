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
import type { CustomerResponse, Page, QuotationSummaryResponse } from '@/types/api'
import { QuotationsListPage } from './QuotationsListPage'
import * as quotationsApi from './api'
import * as customersApi from '@/features/customers/api'

// Preserves the real `quotationKeys`/`customerKeys` builders — see
// ProductsListPage.test.tsx for why auto-mocking them is unsafe when a page
// runs more than one simultaneous query.
vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, searchQuotations: vi.fn() }
})
vi.mock('@/features/customers/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/customers/api')>()
  return { ...actual, searchCustomers: vi.fn(), getCustomer: vi.fn() }
})
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
  vi.mocked(getCurrentOrganization).mockResolvedValue({ id: 'org-1', name: 'Acme', createdAt: '2026-01-01T00:00:00Z' })
}

const CUSTOMER: CustomerResponse = {
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
}

const QUOTATION: QuotationSummaryResponse = {
  id: 'q1234567-aaaa-bbbb-cccc-000000000000',
  customerId: 'c1',
  status: 'DRAFT',
  validUntil: null,
  discountPercentage: 0,
  subtotal: 200,
  discountAmount: 0,
  taxAmount: 36,
  grandTotal: 236,
  organizationId: 'org-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function quotationPage(content: QuotationSummaryResponse[]): Page<QuotationSummaryResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20, first: true, last: true, empty: content.length === 0 }
}

function renderPage() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthProvider>
          <QuotationsListPage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('QuotationsListPage', () => {
  beforeEach(() => {
    clearTokens()
    vi.mocked(quotationsApi.searchQuotations).mockResolvedValue(quotationPage([QUOTATION]))
    vi.mocked(customersApi.getCustomer).mockResolvedValue(CUSTOMER)
  })
  afterEach(() => clearTokens())

  it('has no free-text search input (quotations are filtered only, never searched by text)', async () => {
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()
    await waitFor(() => expect(screen.getAllByText('Acme Corp').length).toBeGreaterThan(0))
    expect(screen.queryByRole('textbox', { name: /search/i })).not.toBeInTheDocument()
  })

  it('resolves and displays the customer name via a per-id lookup', async () => {
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()
    await waitFor(() => expect(customersApi.getCustomer).toHaveBeenCalledWith('c1'))
    expect(await screen.findAllByText('Acme Corp')).not.toHaveLength(0)
  })

  it('applies the status filter', async () => {
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()
    await waitFor(() => expect(screen.getAllByText('Acme Corp').length).toBeGreaterThan(0))

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status filter' }), 'SENT')
    await waitFor(() =>
      expect(quotationsApi.searchQuotations).toHaveBeenCalledWith(expect.objectContaining({ status: 'SENT' })),
    )
  })

  it('applies the valid-until-before date filter', async () => {
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()
    await waitFor(() => expect(screen.getAllByText('Acme Corp').length).toBeGreaterThan(0))

    const dateInput = screen.getByLabelText('Valid until before')
    await userEvent.type(dateInput, '2026-12-31')
    await waitFor(() =>
      expect(quotationsApi.searchQuotations).toHaveBeenCalledWith(
        expect.objectContaining({ validUntilBefore: '2026-12-31' }),
      ),
    )
  })

  describe('RBAC visibility', () => {
    it('EMPLOYEE does not see the Create quotation button', async () => {
      mockUser(['EMPLOYEE'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Acme Corp').length).toBeGreaterThan(0))
      expect(screen.queryByRole('button', { name: /Create quotation/i })).not.toBeInTheDocument()
    })

    it('SALES sees the Create quotation button', async () => {
      mockUser(['SALES'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Acme Corp').length).toBeGreaterThan(0))
      expect(screen.getByRole('button', { name: /Create quotation/i })).toBeInTheDocument()
    })
  })
})
