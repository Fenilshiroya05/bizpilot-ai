import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/app/providers/AuthProvider'
import { getMe } from '@/features/auth/api'
import { getCurrentOrganization } from '@/features/settings/api'
import { clearTokens, setTokens } from '@/lib/api-client'
import { createTestQueryClient } from '@/test/render'
import type { CustomerResponse, QuotationResponse } from '@/types/api'
import { QuotationDetailPage } from './QuotationDetailPage'
import * as quotationsApi from './api'
import * as customersApi from '@/features/customers/api'

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return {
    ...actual,
    getQuotation: vi.fn(),
    cancelQuotation: vi.fn(),
    updateQuotation: vi.fn(),
    downloadQuotationPdf: vi.fn(),
  }
})
vi.mock('@/features/customers/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/customers/api')>()
  return { ...actual, getCustomer: vi.fn(), searchCustomers: vi.fn() }
})
vi.mock('@/features/products/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/products/api')>()
  return { ...actual, searchProducts: vi.fn() }
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

function draftQuotation(overrides: Partial<QuotationResponse> = {}): QuotationResponse {
  return {
    id: 'q1',
    customerId: 'c1',
    status: 'DRAFT',
    validUntil: null,
    discountPercentage: 0,
    subtotal: 200,
    discountAmount: 0,
    taxAmount: 36,
    grandTotal: 236,
    items: [
      {
        id: 'i1',
        productId: 'p1',
        productNameSnapshot: 'Widget',
        quantity: 2,
        unitPrice: 100,
        taxPercentage: 18,
        lineSubtotal: 200,
        lineTaxAmount: 36,
      },
    ],
    organizationId: 'org-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function renderPage() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/quotations/q1']}>
        <AuthProvider>
          <Routes>
            <Route path="/quotations/:id" element={<QuotationDetailPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('QuotationDetailPage', () => {
  beforeEach(() => {
    clearTokens()
    vi.mocked(customersApi.getCustomer).mockResolvedValue(CUSTOMER)
  })
  afterEach(() => clearTokens())

  it('renders a read-only line-item breakdown and totals', async () => {
    vi.mocked(quotationsApi.getQuotation).mockResolvedValue(draftQuotation())
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    expect(await screen.findByText('Widget')).toBeInTheDocument()
    expect(screen.getAllByText('₹236.00').length).toBeGreaterThan(0)
    expect(screen.getByText('Grand total').nextSibling).toHaveTextContent('₹236.00')
  })

  it('shows Edit and the status-change control for a DRAFT quotation when the user can update', async () => {
    vi.mocked(quotationsApi.getQuotation).mockResolvedValue(draftQuotation())
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    expect(await screen.findByRole('button', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'New status' })).toBeInTheDocument()
  })

  it('hides Edit and the status-change control once the quotation has left DRAFT', async () => {
    vi.mocked(quotationsApi.getQuotation).mockResolvedValue(draftQuotation({ status: 'SENT' }))
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await screen.findByText('Widget')
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'New status' })).not.toBeInTheDocument()
    expect(screen.getByText(/left draft status and can no longer be edited/)).toBeInTheDocument()
  })

  it('EMPLOYEE (read-only) sees no Edit, no status control, and no Cancel action', async () => {
    vi.mocked(quotationsApi.getQuotation).mockResolvedValue(draftQuotation())
    mockUser(['EMPLOYEE'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await screen.findByText('Widget')
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'New status' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel quotation' })).not.toBeInTheDocument()
  })

  it('always shows the Download PDF action, regardless of status', async () => {
    vi.mocked(quotationsApi.getQuotation).mockResolvedValue(draftQuotation({ status: 'CANCELLED' }))
    mockUser(['EMPLOYEE'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    expect(await screen.findByRole('button', { name: /Download PDF/ })).toBeInTheDocument()
  })

  it('changes status after confirming, an irreversible action', async () => {
    vi.mocked(quotationsApi.getQuotation).mockResolvedValue(draftQuotation())
    vi.mocked(quotationsApi.updateQuotation).mockResolvedValue(draftQuotation({ status: 'SENT' }))
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await screen.findByText('Widget')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'New status' }), 'SENT')
    await userEvent.click(screen.getByRole('button', { name: 'Update status' }))

    const confirmButtons = await screen.findAllByRole('button', { name: 'Update status' })
    await userEvent.click(confirmButtons[confirmButtons.length - 1]!)

    await waitFor(() => expect(quotationsApi.updateQuotation).toHaveBeenCalledWith('q1', { status: 'SENT' }))
  })

  it('cancels the quotation after confirming', async () => {
    vi.mocked(quotationsApi.getQuotation).mockResolvedValue(draftQuotation())
    vi.mocked(quotationsApi.cancelQuotation).mockResolvedValue(undefined)
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel quotation' }))
    const confirmButtons = await screen.findAllByRole('button', { name: 'Cancel quotation' })
    await userEvent.click(confirmButtons[confirmButtons.length - 1]!)

    await waitFor(() => expect(quotationsApi.cancelQuotation).toHaveBeenCalledWith('q1'))
  })

  it('SALES (no QUOTATION_DELETE) cannot cancel a quotation', async () => {
    vi.mocked(quotationsApi.getQuotation).mockResolvedValue(draftQuotation())
    mockUser(['SALES'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await screen.findByText('Widget')
    expect(screen.queryByRole('button', { name: 'Cancel quotation' })).not.toBeInTheDocument()
  })

  it('toggles into the shared edit form for a DRAFT quotation and back on cancel', async () => {
    vi.mocked(quotationsApi.getQuotation).mockResolvedValue(draftQuotation())
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    expect(await screen.findByRole('heading', { name: 'Edit quotation' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Customer' })).toHaveValue('Acme Corp')

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(await screen.findByRole('button', { name: 'Edit' })).toBeInTheDocument()
  })
})
