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
import type { Page, ProductCategoryResponse, ProductResponse } from '@/types/api'
import { ProductsListPage } from './ProductsListPage'
import * as productsApi from './api'

// Preserves the real `productKeys`/`productCategoryKeys` builders (needed as
// valid TanStack Query keys — auto-mocking them collapses every query onto
// the same undefined key, causing the products and categories queries to
// share cached state) while only mocking the network calls themselves.
vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, searchProducts: vi.fn(), listProductCategories: vi.fn(), deactivateProduct: vi.fn(), updateProduct: vi.fn() }
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

const WIDGET: ProductResponse = {
  id: 'p1',
  sku: 'SKU-1',
  name: 'Widget',
  description: null,
  unit: 'pcs',
  price: 100,
  taxPercentage: 18,
  status: 'ACTIVE',
  categoryId: 'cat-1',
  organizationId: 'org-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const CATEGORY: ProductCategoryResponse = {
  id: 'cat-1',
  name: 'Hardware',
  organizationId: 'org-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function productPage(content: ProductResponse[]): Page<ProductResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20, first: true, last: true, empty: content.length === 0 }
}

function categoryPage(content: ProductCategoryResponse[]): Page<ProductCategoryResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 100, first: true, last: true, empty: content.length === 0 }
}

function renderPage() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthProvider>
          <ProductsListPage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ProductsListPage', () => {
  beforeEach(() => {
    clearTokens()
    vi.mocked(productsApi.searchProducts).mockResolvedValue(productPage([WIDGET]))
    vi.mocked(productsApi.listProductCategories).mockResolvedValue(categoryPage([CATEGORY]))
  })
  afterEach(() => clearTokens())

  describe('RBAC visibility', () => {
    it('EMPLOYEE sees products but no Create button and no Edit/Deactivate row actions', async () => {
      mockUser(['EMPLOYEE'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))
      expect(screen.queryByRole('button', { name: /Create product/i })).not.toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Actions for Widget' }))
      expect(screen.getByRole('menuitem', { name: 'View details' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Edit' })).not.toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Deactivate' })).not.toBeInTheDocument()
    })

    it('SALES sees Create and Edit, but not Deactivate', async () => {
      mockUser(['SALES'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))
      expect(screen.getByRole('button', { name: /Create product/i })).toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Actions for Widget' }))
      expect(screen.getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Deactivate' })).not.toBeInTheDocument()
    })

    it('OWNER sees Create, Edit, and Deactivate', async () => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))
      await userEvent.click(screen.getByRole('button', { name: 'Actions for Widget' }))
      expect(screen.getByRole('menuitem', { name: 'Edit' })).toBeInTheDocument()
      expect(screen.getByRole('menuitem', { name: 'Deactivate' })).toBeInTheDocument()
    })
  })

  describe('search and filters', () => {
    beforeEach(() => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
    })

    it('debounces search input into the q query param', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))

      await userEvent.type(screen.getByRole('textbox', { name: 'Search products' }), 'Wid')
      await waitFor(() =>
        expect(productsApi.searchProducts).toHaveBeenCalledWith(expect.objectContaining({ q: 'Wid' })),
      )
    })

    it('applies the status filter', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))

      await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status filter' }), 'INACTIVE')
      await waitFor(() =>
        expect(productsApi.searchProducts).toHaveBeenCalledWith(expect.objectContaining({ status: 'INACTIVE' })),
      )
    })

    it('applies the category filter', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))

      await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Category filter' }), 'cat-1')
      await waitFor(() =>
        expect(productsApi.searchProducts).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 'cat-1' })),
      )
    })
  })

  describe('deactivate / reactivate', () => {
    beforeEach(() => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
    })

    it('deactivates a product after confirmation', async () => {
      vi.mocked(productsApi.deactivateProduct).mockResolvedValue(undefined)
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))

      await userEvent.click(screen.getByRole('button', { name: 'Actions for Widget' }))
      await userEvent.click(screen.getByRole('menuitem', { name: 'Deactivate' }))
      await userEvent.click(screen.getByRole('button', { name: 'Deactivate' }))

      await waitFor(() => expect(productsApi.deactivateProduct).toHaveBeenCalledWith('p1'))
    })

    it('shows Reactivate instead of Deactivate for an inactive product', async () => {
      vi.mocked(productsApi.searchProducts).mockResolvedValue(productPage([{ ...WIDGET, status: 'INACTIVE' }]))
      renderPage()
      await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))

      await userEvent.click(screen.getByRole('button', { name: 'Actions for Widget' }))
      expect(screen.getByRole('menuitem', { name: 'Reactivate' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Deactivate' })).not.toBeInTheDocument()
    })
  })
})
