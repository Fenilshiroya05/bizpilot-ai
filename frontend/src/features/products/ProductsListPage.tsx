import { useQuery, useQueryClient } from '@tanstack/react-query'
import { MoreHorizontal, Package, Plus, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { usePermission } from '@/hooks/usePermission'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import {
  deactivateProduct,
  listProductCategories,
  productCategoryKeys,
  productKeys,
  searchProducts,
  updateProduct,
} from '@/features/products/api'
import { ProductFormDialog } from '@/features/products/components/ProductFormDialog'
import { DataTable, type DataTableColumn } from '@/components/data-display/DataTable'
import { Pagination } from '@/components/data-display/Pagination'
import { StatusBadge } from '@/components/data-display/StatusBadge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { FilterBar } from '@/components/forms/FilterBar'
import { ConfirmDialog } from '@/components/feedback/ConfirmDialog'
import { EmptyState } from '@/components/feedback/EmptyState'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import { formatInr } from '@/lib/money'
import type { ProductResponse, ProductStatus } from '@/types/api'

function useProductListParams() {
  const [searchParams, setSearchParams] = useSearchParams()

  const q = searchParams.get('q') ?? ''
  const status = (searchParams.get('status') as ProductStatus | null) ?? undefined
  const categoryId = searchParams.get('categoryId') ?? undefined
  const page = Number(searchParams.get('page') ?? '0')

  function setParam(key: string, value: string | undefined, resetPage: boolean) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      if (value) next.set(key, value)
      else next.delete(key)
      if (resetPage) next.set('page', '0')
      return next
    })
  }

  return {
    q,
    status,
    categoryId,
    page,
    setSearch: (value: string) => setParam('q', value || undefined, true),
    setStatus: (value: string) => setParam('status', value || undefined, true),
    setCategory: (value: string) => setParam('categoryId', value || undefined, true),
    setPage: (value: number) => setParam('page', String(value), false),
    clear: () => setSearchParams({}),
  }
}

export function ProductsListPage() {
  const { q, status, categoryId, page, setSearch, setStatus, setCategory, setPage, clear } = useProductListParams()
  const [searchInput, setSearchInput] = useState(q)
  const debouncedSearch = useDebouncedValue(searchInput, 300)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const canCreate = usePermission('PRODUCT_CREATE')
  const canUpdate = usePermission('PRODUCT_UPDATE')
  const canDeactivate = usePermission('PRODUCT_DELETE')

  useEffect(() => {
    if (debouncedSearch !== q) setSearch(debouncedSearch)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const params = { q: q || undefined, status, categoryId, page, size: 20 }
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: productKeys.list(params),
    queryFn: () => searchProducts(params),
  })

  const categoriesQuery = useQuery({
    queryKey: productCategoryKeys.list(),
    queryFn: listProductCategories,
  })
  const categories = categoriesQuery.data?.content ?? []
  const categoryName = (id: string | null) => categories.find((c) => c.id === id)?.name ?? '—'

  const [formOpen, setFormOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState<ProductResponse | undefined>(undefined)
  const [deactivating, setDeactivating] = useState<ProductResponse | null>(null)
  const [isMutating, setIsMutating] = useState(false)

  function openCreate() {
    setEditingProduct(undefined)
    setFormOpen(true)
  }

  function openEdit(product: ProductResponse) {
    setEditingProduct(product)
    setFormOpen(true)
  }

  async function confirmDeactivate() {
    if (!deactivating) return
    setIsMutating(true)
    try {
      await deactivateProduct(deactivating.id)
      await queryClient.invalidateQueries({ queryKey: ['products', 'list'] })
      await queryClient.invalidateQueries({ queryKey: productKeys.detail(deactivating.id) })
      toast({ title: 'Product deactivated', variant: 'success' })
      setDeactivating(null)
    } catch (err) {
      toast({
        title: 'Could not deactivate product',
        description: err instanceof ApiError ? err.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsMutating(false)
    }
  }

  async function reactivate(product: ProductResponse) {
    try {
      await updateProduct(product.id, { status: 'ACTIVE' })
      await queryClient.invalidateQueries({ queryKey: ['products', 'list'] })
      await queryClient.invalidateQueries({ queryKey: productKeys.detail(product.id) })
      toast({ title: 'Product reactivated', variant: 'success' })
    } catch (err) {
      toast({
        title: 'Could not reactivate product',
        description: err instanceof ApiError ? err.message : 'Please try again.',
        variant: 'destructive',
      })
    }
  }

  const columns: DataTableColumn<ProductResponse>[] = [
    {
      key: 'sku',
      header: 'SKU',
      cell: (p) => (
        <Link to={`/products/${p.id}`} className="font-medium text-foreground hover:underline">
          {p.sku}
        </Link>
      ),
    },
    { key: 'name', header: 'Name', cell: (p) => p.name },
    { key: 'unit', header: 'Unit', cell: (p) => p.unit },
    { key: 'price', header: 'Price', className: 'text-right tabular-nums', cell: (p) => formatInr(p.price) },
    { key: 'category', header: 'Category', cell: (p) => categoryName(p.categoryId) },
    { key: 'status', header: 'Status', cell: (p) => <StatusBadge status={p.status} /> },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      cell: (p) => (
        <DropdownMenu>
          <DropdownMenuTrigger
            className="rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={`Actions for ${p.name}`}
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
            <DropdownMenuItem onSelect={() => navigate(`/products/${p.id}`)}>View details</DropdownMenuItem>
            {canUpdate && <DropdownMenuItem onSelect={() => openEdit(p)}>Edit</DropdownMenuItem>}
            {canUpdate && p.status === 'INACTIVE' && (
              <DropdownMenuItem onSelect={() => reactivate(p)}>Reactivate</DropdownMenuItem>
            )}
            {canDeactivate && p.status === 'ACTIVE' && (
              <DropdownMenuItem
                onSelect={() => setDeactivating(p)}
                className="text-destructive focus:bg-destructive/10"
              >
                Deactivate
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ]

  const hasActiveFilters = Boolean(q || status || categoryId)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Products"
        description="Manage your product catalog."
        breadcrumb={[{ label: 'Products' }]}
        actions={
          canCreate ? (
            <Button onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create product
            </Button>
          ) : undefined
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-xs">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            className="pl-9"
            placeholder="Search products..."
            aria-label="Search products"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
        </div>

        <FilterBar
          activeCount={(status ? 1 : 0) + (categoryId ? 1 : 0)}
          onClear={clear}
        >
          <Select aria-label="Status filter" value={status ?? ''} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </Select>
          <Select aria-label="Category filter" value={categoryId ?? ''} onChange={(e) => setCategory(e.target.value)}>
            <option value="">All categories</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
        </FilterBar>
      </div>

      {isError ? (
        <ErrorState title="We couldn't load your products" error={error} onRetry={() => refetch()} />
      ) : !isLoading && data?.content.length === 0 ? (
        <EmptyState
          icon={Package}
          title={hasActiveFilters ? 'No products found' : 'No products yet'}
          description={hasActiveFilters ? 'Try changing your search or filters.' : 'Add your first product to get started.'}
          action={
            hasActiveFilters ? (
              <Button variant="outline" size="sm" onClick={clear}>
                Clear filters
              </Button>
            ) : canCreate ? (
              <Button size="sm" onClick={openCreate}>
                Add your first product
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="hidden lg:block">
            <DataTable
              caption="Products"
              columns={columns}
              rows={data?.content ?? []}
              rowKey={(p) => p.id}
              onRowClick={(p) => navigate(`/products/${p.id}`)}
              isLoading={isLoading}
            />
          </div>

          <div className="flex flex-col gap-2 lg:hidden">
            {isLoading
              ? Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="h-24 animate-pulse rounded-lg border border-border bg-muted" />
                ))
              : data?.content.map((p) => (
                  <Link
                    key={p.id}
                    to={`/products/${p.id}`}
                    className="flex flex-col gap-1 rounded-lg border border-border bg-background p-4 hover:bg-muted/40"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-foreground">{p.name}</span>
                      <StatusBadge status={p.status} />
                    </div>
                    <span className="text-sm text-muted-foreground">{p.sku}</span>
                    <span className="text-sm tabular-nums text-foreground">{formatInr(p.price)}</span>
                  </Link>
                ))}
          </div>

          {data && <Pagination page={data} onPageChange={setPage} />}
        </>
      )}

      <ProductFormDialog open={formOpen} onOpenChange={setFormOpen} product={editingProduct} />

      <ConfirmDialog
        open={!!deactivating}
        onOpenChange={(open) => !open && setDeactivating(null)}
        title="Deactivate this product?"
        description={`${deactivating?.name ?? 'This product'} will be set to inactive and hidden from quotation creation. This can be reversed at any time by reactivating it.`}
        confirmLabel="Deactivate"
        isLoading={isMutating}
        onConfirm={confirmDeactivate}
      />
    </div>
  )
}
