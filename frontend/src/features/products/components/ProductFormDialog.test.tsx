import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach } from 'vitest'

import { renderWithClient } from '@/test/render'
import { ApiError } from '@/lib/api-client'
import type { Page, ProductCategoryResponse, ProductResponse } from '@/types/api'
import { ProductFormDialog } from './ProductFormDialog'
import * as productsApi from '@/features/products/api'

// See ProductsListPage.test.tsx for why the real key builders must survive mocking.
vi.mock('@/features/products/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/products/api')>()
  return {
    ...actual,
    listProductCategories: vi.fn(),
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
    createProductCategory: vi.fn(),
    updateProductCategory: vi.fn(),
  }
})

function categoryPage(content: ProductCategoryResponse[]): Page<ProductCategoryResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 100, first: true, last: true, empty: content.length === 0 }
}

const PRODUCT: ProductResponse = {
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

describe('ProductFormDialog', () => {
  beforeEach(() => {
    vi.mocked(productsApi.listProductCategories).mockResolvedValue(categoryPage([]))
  })

  it('shows validation errors when required fields are missing', async () => {
    renderWithClient(<ProductFormDialog open onOpenChange={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Create product' }))
    expect(await screen.findByText('SKU is required')).toBeInTheDocument()
    expect(screen.getByText('Name is required')).toBeInTheDocument()
    expect(screen.getByText('Unit is required')).toBeInTheDocument()
    expect(screen.getByText('Price is required')).toBeInTheDocument()
    expect(productsApi.createProduct).not.toHaveBeenCalled()
  })

  it('creates a product with valid input', async () => {
    vi.mocked(productsApi.createProduct).mockResolvedValue(PRODUCT)
    const onOpenChange = vi.fn()
    renderWithClient(<ProductFormDialog open onOpenChange={onOpenChange} />)

    await userEvent.type(screen.getByLabelText('SKU'), 'SKU-1')
    await userEvent.type(screen.getByLabelText('Name'), 'Widget')
    await userEvent.type(screen.getByLabelText('Unit'), 'pcs')
    await userEvent.type(screen.getByLabelText('Price'), '100')

    await userEvent.click(screen.getByRole('button', { name: 'Create product' }))

    await waitFor(() =>
      expect(productsApi.createProduct).toHaveBeenCalledWith(
        expect.objectContaining({ sku: 'SKU-1', name: 'Widget', unit: 'pcs', price: '100' }),
      ),
    )
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })

  it('surfaces a DUPLICATE_SKU error on the SKU field instead of a generic alert', async () => {
    vi.mocked(productsApi.createProduct).mockRejectedValue(
      new ApiError({
        timestamp: '2026-01-01T00:00:00Z',
        status: 409,
        code: 'DUPLICATE_SKU',
        message: 'A product with this SKU already exists.',
        path: '/api/v1/products',
      }),
    )
    renderWithClient(<ProductFormDialog open onOpenChange={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('SKU'), 'SKU-1')
    await userEvent.type(screen.getByLabelText('Name'), 'Widget')
    await userEvent.type(screen.getByLabelText('Unit'), 'pcs')
    await userEvent.type(screen.getByLabelText('Price'), '100')
    await userEvent.click(screen.getByRole('button', { name: 'Create product' }))

    expect(await screen.findByText('A product with this SKU already exists.')).toBeInTheDocument()
  })

  it('pre-fills the form for editing an existing product and shows the Status field', async () => {
    renderWithClient(<ProductFormDialog open onOpenChange={vi.fn()} product={PRODUCT} />)

    expect(screen.getByLabelText('SKU')).toHaveValue('SKU-1')
    expect(screen.getByLabelText('Name')).toHaveValue('Widget')
    expect(screen.getByLabelText('Status')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
  })

  it('updates an existing product', async () => {
    vi.mocked(productsApi.updateProduct).mockResolvedValue(PRODUCT)
    const onOpenChange = vi.fn()
    renderWithClient(<ProductFormDialog open onOpenChange={onOpenChange} product={PRODUCT} />)

    await userEvent.clear(screen.getByLabelText('Name'))
    await userEvent.type(screen.getByLabelText('Name'), 'Widget Pro')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() =>
      expect(productsApi.updateProduct).toHaveBeenCalledWith('p1', expect.objectContaining({ name: 'Widget Pro' })),
    )
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })
})
