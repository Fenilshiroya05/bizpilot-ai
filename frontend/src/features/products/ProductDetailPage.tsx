import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router-dom'

import { usePermission } from '@/hooks/usePermission'
import {
  deactivateProduct,
  getProduct,
  listProductCategories,
  productCategoryKeys,
  productKeys,
  updateProduct,
} from '@/features/products/api'
import { ProductFormDialog } from '@/features/products/components/ProductFormDialog'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/components/data-display/StatusBadge'
import { ConfirmDialog } from '@/components/feedback/ConfirmDialog'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import { formatInr } from '@/lib/money'

function OverviewField({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="text-sm text-foreground">{value ?? '—'}</p>
    </div>
  )
}

export function ProductDetailPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const canUpdate = usePermission('PRODUCT_UPDATE')
  const canDeactivate = usePermission('PRODUCT_DELETE')

  const [formOpen, setFormOpen] = useState(false)
  const [deactivateOpen, setDeactivateOpen] = useState(false)
  const [isMutating, setIsMutating] = useState(false)

  const detailQuery = useQuery({
    queryKey: productKeys.detail(id ?? ''),
    queryFn: () => getProduct(id ?? ''),
    enabled: !!id,
  })

  const categoriesQuery = useQuery({
    queryKey: productCategoryKeys.list(),
    queryFn: listProductCategories,
  })

  async function confirmDeactivate() {
    if (!id) return
    setIsMutating(true)
    try {
      await deactivateProduct(id)
      await queryClient.invalidateQueries({ queryKey: ['products', 'list'] })
      await queryClient.invalidateQueries({ queryKey: productKeys.detail(id) })
      toast({ title: 'Product deactivated', variant: 'success' })
      setDeactivateOpen(false)
    } catch (error) {
      toast({
        title: 'Could not deactivate product',
        description: error instanceof ApiError ? error.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsMutating(false)
    }
  }

  async function reactivate() {
    if (!id) return
    try {
      await updateProduct(id, { status: 'ACTIVE' })
      await queryClient.invalidateQueries({ queryKey: ['products', 'list'] })
      await queryClient.invalidateQueries({ queryKey: productKeys.detail(id) })
      toast({ title: 'Product reactivated', variant: 'success' })
    } catch (error) {
      toast({
        title: 'Could not reactivate product',
        description: error instanceof ApiError ? error.message : 'Please try again.',
        variant: 'destructive',
      })
    }
  }

  if (detailQuery.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <ErrorState title="We couldn't load this product" error={detailQuery.error} onRetry={() => detailQuery.refetch()} />
    )
  }

  const product = detailQuery.data
  const categoryName = categoriesQuery.data?.content.find((c) => c.id === product.categoryId)?.name ?? null
  const isInactive = product.status === 'INACTIVE'

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={product.name}
        breadcrumb={[{ label: 'Products', to: '/products' }, { label: product.name }]}
        actions={
          <div className="flex items-center gap-2">
            <StatusBadge status={product.status} />
            {canUpdate && (
              <Button variant="outline" size="sm" onClick={() => setFormOpen(true)}>
                Edit
              </Button>
            )}
            {canUpdate && isInactive && (
              <Button variant="outline" size="sm" onClick={reactivate}>
                Reactivate
              </Button>
            )}
            {canDeactivate && !isInactive && (
              <Button variant="destructive" size="sm" onClick={() => setDeactivateOpen(true)}>
                Deactivate
              </Button>
            )}
          </div>
        }
      />

      {isInactive && (
        <Alert variant="info">
          This product is inactive. It remains fully editable but cannot be added to a new or updated quotation
          until reactivated.
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Overview</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <OverviewField label="SKU" value={product.sku} />
          <OverviewField label="Unit" value={product.unit} />
          <OverviewField label="Price" value={formatInr(product.price)} />
          <OverviewField label="Tax %" value={`${product.taxPercentage}%`} />
          <OverviewField label="Category" value={categoryName} />
          <OverviewField label="Description" value={product.description} />
        </CardContent>
      </Card>

      <ProductFormDialog open={formOpen} onOpenChange={setFormOpen} product={product} />

      <ConfirmDialog
        open={deactivateOpen}
        onOpenChange={setDeactivateOpen}
        title="Deactivate this product?"
        description={`${product.name} will be set to inactive and hidden from quotation creation. This can be reversed at any time by reactivating it.`}
        confirmLabel="Deactivate"
        isLoading={isMutating}
        onConfirm={confirmDeactivate}
      />
    </div>
  )
}
