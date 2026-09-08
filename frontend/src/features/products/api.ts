import { apiFetch } from '@/lib/api-client'
import { buildQueryString } from '@/lib/utils'
import type {
  Page,
  ProductCategoryCreateRequest,
  ProductCategoryResponse,
  ProductCategoryUpdateRequest,
  ProductCreateRequest,
  ProductListParams,
  ProductResponse,
  ProductUpdateRequest,
} from '@/types/api'

// Same 'list'/'detail' key-segment convention as features/customers|leads/api.ts.
export const productKeys = {
  list: (params: ProductListParams) => ['products', 'list', params] as const,
  detail: (id: string) => ['products', 'detail', id] as const,
}

// A separate top-level key (not nested under 'products') — a category
// mutation invalidates the picker list without ever touching product list
// queries, and vice versa, unless a product mutation specifically also
// affects category-derived display (see ProductFormDialog).
export const productCategoryKeys = {
  list: () => ['product-categories', 'list'] as const,
}

export function searchProducts(params: ProductListParams): Promise<Page<ProductResponse>> {
  return apiFetch<Page<ProductResponse>>(`/api/v1/products${buildQueryString(params)}`)
}

export function getProduct(id: string): Promise<ProductResponse> {
  return apiFetch<ProductResponse>(`/api/v1/products/${id}`)
}

export function createProduct(payload: ProductCreateRequest): Promise<ProductResponse> {
  return apiFetch<ProductResponse>('/api/v1/products', { method: 'POST', body: payload })
}

export function updateProduct(id: string, payload: ProductUpdateRequest): Promise<ProductResponse> {
  return apiFetch<ProductResponse>(`/api/v1/products/${id}`, { method: 'PATCH', body: payload })
}

/** Deactivates (backend transitions status to INACTIVE) — never a real delete. */
export function deactivateProduct(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/products/${id}`, { method: 'DELETE' })
}

export function listProductCategories(): Promise<Page<ProductCategoryResponse>> {
  // A generous page size: categories are a small, rarely-paginated list used
  // to populate a picker, not a paginated table of their own (CLAUDE.md §13
  // gives categories no dedicated management UI in this phase).
  return apiFetch<Page<ProductCategoryResponse>>(`/api/v1/products/categories${buildQueryString({ size: 100 })}`)
}

export function createProductCategory(payload: ProductCategoryCreateRequest): Promise<ProductCategoryResponse> {
  return apiFetch<ProductCategoryResponse>('/api/v1/products/categories', { method: 'POST', body: payload })
}

export function updateProductCategory(
  id: string,
  payload: ProductCategoryUpdateRequest,
): Promise<ProductCategoryResponse> {
  return apiFetch<ProductCategoryResponse>(`/api/v1/products/categories/${id}`, {
    method: 'PATCH',
    body: payload,
  })
}
