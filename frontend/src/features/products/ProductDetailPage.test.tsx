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
import type { Page, ProductCategoryResponse, ProductResponse } from '@/types/api'
import { ProductDetailPage } from './ProductDetailPage'
import * as productsApi from './api'

// See ProductsListPage.test.tsx for why the real key builders must survive mocking.
vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, getProduct: vi.fn(), listProductCategories: vi.fn(), deactivateProduct: vi.fn(), updateProduct: vi.fn() }
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
  description: 'A useful widget',
  unit: 'pcs',
  price: 100,
  taxPercentage: 18,
  status: 'ACTIVE',
  categoryId: null,
  organizationId: 'org-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function categoryPage(content: ProductCategoryResponse[]): Page<ProductCategoryResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 100, first: true, last: true, empty: content.length === 0 }
}

function renderPage() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/products/p1']}>
        <AuthProvider>
          <Routes>
            <Route path="/products/:id" element={<ProductDetailPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ProductDetailPage', () => {
  beforeEach(() => {
    clearTokens()
    vi.mocked(productsApi.getProduct).mockResolvedValue(WIDGET)
    vi.mocked(productsApi.listProductCategories).mockResolvedValue(categoryPage([]))
  })
  afterEach(() => clearTokens())

  it('renders the product overview', async () => {
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Widget' })).toBeInTheDocument()
    expect(screen.getByText('SKU-1')).toBeInTheDocument()
    expect(screen.getByText('A useful widget')).toBeInTheDocument()
  })

  it('EMPLOYEE sees no Edit or Deactivate action', async () => {
    mockUser(['EMPLOYEE'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await screen.findByRole('heading', { name: 'Widget' })
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })

  it('OWNER can deactivate the product after confirming', async () => {
    vi.mocked(productsApi.deactivateProduct).mockResolvedValue(undefined)
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Deactivate' }))
    await userEvent.click(screen.getByRole('button', { name: 'Deactivate' }))

    await waitFor(() => expect(productsApi.deactivateProduct).toHaveBeenCalledWith('p1'))
  })

  it('shows an informational alert and a Reactivate action for an inactive product', async () => {
    vi.mocked(productsApi.getProduct).mockResolvedValue({ ...WIDGET, status: 'INACTIVE' })
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    expect(await screen.findByText(/This product is inactive/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reactivate' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deactivate' })).not.toBeInTheDocument()
  })
})
