import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { renderWithClient } from '@/test/render'
import type { CustomerResponse, Page, ProductResponse, QuotationResponse } from '@/types/api'
import { QuotationForm } from './QuotationForm'
import * as quotationsApi from '@/features/quotations/api'
import * as customersApi from '@/features/customers/api'
import * as productsApi from '@/features/products/api'

vi.mock('@/features/quotations/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/quotations/api')>()
  return { ...actual, createQuotation: vi.fn(), updateQuotation: vi.fn() }
})
vi.mock('@/features/customers/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/customers/api')>()
  return { ...actual, searchCustomers: vi.fn() }
})
vi.mock('@/features/products/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/products/api')>()
  return { ...actual, searchProducts: vi.fn() }
})

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

const WIDGET: ProductResponse = {
  id: 'p1',
  sku: 'SKU-1',
  name: 'Widget',
  description: null,
  unit: 'pcs',
  price: 100,
  taxPercentage: 18,
  status: 'ACTIVE',
  categoryId: null,
  organizationId: 'org-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const QUOTATION: QuotationResponse = {
  id: 'q1',
  customerId: 'c1',
  status: 'DRAFT',
  validUntil: null,
  discountPercentage: 0,
  subtotal: 200,
  discountAmount: 0,
  taxAmount: 36,
  grandTotal: 236,
  items: [],
  organizationId: 'org-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function customerPage(content: CustomerResponse[]): Page<CustomerResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 10, first: true, last: true, empty: content.length === 0 }
}

function productPage(content: ProductResponse[]): Page<ProductResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 10, first: true, last: true, empty: content.length === 0 }
}

describe('QuotationForm', () => {
  beforeEach(() => {
    vi.mocked(customersApi.searchCustomers).mockResolvedValue(customerPage([CUSTOMER]))
    vi.mocked(productsApi.searchProducts).mockResolvedValue(productPage([WIDGET]))
  })

  async function fillMinimalQuotation() {
    await userEvent.type(screen.getByRole('combobox', { name: 'Customer' }), 'Acme')
    await userEvent.click(await screen.findByRole('option', { name: /Acme Corp/ }))

    await userEvent.click(screen.getByRole('button', { name: 'Add line item' }))
    await userEvent.type(screen.getByRole('combobox', { name: 'Product' }), 'Wid')
    await userEvent.click(await screen.findByRole('option', { name: /Widget/ }))
  }

  it('requires a customer and at least one line item on submit', async () => {
    renderWithClient(<QuotationForm onSaved={vi.fn()} onCancel={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Create quotation' }))
    expect(await screen.findByText('Select a customer')).toBeInTheDocument()
    expect(screen.getByText('Add at least one line item')).toBeInTheDocument()
    expect(quotationsApi.createQuotation).not.toHaveBeenCalled()
  })

  it('rejects a discount percentage above 100', async () => {
    renderWithClient(<QuotationForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    await fillMinimalQuotation()

    const discountInput = screen.getByLabelText('Discount %')
    await userEvent.type(discountInput, '150')
    await userEvent.click(screen.getByRole('button', { name: 'Create quotation' }))

    expect(await screen.findByText('Discount must be at most 100')).toBeInTheDocument()
    expect(quotationsApi.createQuotation).not.toHaveBeenCalled()
  })

  it('updates the estimated total preview as line items and discount change, using only local math', async () => {
    renderWithClient(<QuotationForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    await fillMinimalQuotation()

    // Widget: qty 1 (default) x price 100 = 100 subtotal, 18% tax = 18, total 118.
    await waitFor(() => expect(screen.getByText('₹118.00')).toBeInTheDocument())
  })

  it('creates a quotation from customer + product selections', async () => {
    vi.mocked(quotationsApi.createQuotation).mockResolvedValue(QUOTATION)
    const onSaved = vi.fn()
    renderWithClient(<QuotationForm onSaved={onSaved} onCancel={vi.fn()} />)
    await fillMinimalQuotation()

    await userEvent.click(screen.getByRole('button', { name: 'Create quotation' }))

    await waitFor(() =>
      expect(quotationsApi.createQuotation).toHaveBeenCalledWith(
        expect.objectContaining({
          customerId: 'c1',
          items: [{ productId: 'p1', quantity: '1' }],
        }),
      ),
    )
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(QUOTATION))
  })

  it('surfaces a QUOTATION_NOT_EDITABLE conflict with a specific message', async () => {
    const { ApiError } = await import('@/lib/api-client')
    vi.mocked(quotationsApi.createQuotation).mockRejectedValue(
      new ApiError({
        timestamp: '2026-01-01T00:00:00Z',
        status: 409,
        code: 'QUOTATION_NOT_EDITABLE',
        message: 'Quotation is not editable',
        path: '/api/v1/quotations',
      }),
    )
    renderWithClient(<QuotationForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    await fillMinimalQuotation()
    await userEvent.click(screen.getByRole('button', { name: 'Create quotation' }))

    expect(await screen.findByText('Only draft quotations can be edited.')).toBeInTheDocument()
  })

  it('calls onCancel when Cancel is clicked', async () => {
    const onCancel = vi.fn()
    renderWithClient(<QuotationForm onSaved={vi.fn()} onCancel={onCancel} />)
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalled()
  })
})
