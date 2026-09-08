import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { useState } from 'react'
import { useParams } from 'react-router-dom'

import { getCustomer, customerKeys } from '@/features/customers/api'
import { cancelQuotation, downloadQuotationPdf, getQuotation, quotationKeys, updateQuotation } from '@/features/quotations/api'
import { QuotationForm } from '@/features/quotations/components/QuotationForm'
import { usePermission } from '@/hooks/usePermission'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/components/data-display/StatusBadge'
import { ConfirmDialog } from '@/components/feedback/ConfirmDialog'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import { formatInr } from '@/lib/money'
import type { QuotationStatus } from '@/types/api'

const STATUS_TRANSITIONS: QuotationStatus[] = ['SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED']

export function QuotationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const canUpdate = usePermission('QUOTATION_UPDATE')
  const canCancel = usePermission('QUOTATION_DELETE')

  const [isEditing, setIsEditing] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [isCancelling, setIsCancelling] = useState(false)
  const [isDownloading, setIsDownloading] = useState(false)
  const [pendingStatus, setPendingStatus] = useState<QuotationStatus | ''>('')
  const [statusConfirmOpen, setStatusConfirmOpen] = useState(false)
  const [isChangingStatus, setIsChangingStatus] = useState(false)

  const detailQuery = useQuery({
    queryKey: quotationKeys.detail(id ?? ''),
    queryFn: () => getQuotation(id ?? ''),
    enabled: !!id,
  })
  const quotation = detailQuery.data

  const customerQuery = useQuery({
    queryKey: customerKeys.detail(quotation?.customerId ?? ''),
    queryFn: () => getCustomer(quotation?.customerId ?? ''),
    enabled: !!quotation,
  })

  async function handleCancel() {
    if (!id) return
    setIsCancelling(true)
    try {
      await cancelQuotation(id)
      await queryClient.invalidateQueries({ queryKey: ['quotations', 'list'] })
      await queryClient.invalidateQueries({ queryKey: quotationKeys.detail(id) })
      toast({ title: 'Quotation cancelled', variant: 'success' })
      setCancelOpen(false)
    } catch (error) {
      toast({
        title: 'Could not cancel quotation',
        description: error instanceof ApiError ? error.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsCancelling(false)
    }
  }

  async function handleStatusChange() {
    if (!id || !pendingStatus) return
    setIsChangingStatus(true)
    try {
      await updateQuotation(id, { status: pendingStatus })
      await queryClient.invalidateQueries({ queryKey: ['quotations', 'list'] })
      await queryClient.invalidateQueries({ queryKey: quotationKeys.detail(id) })
      toast({ title: `Quotation marked as ${pendingStatus.toLowerCase()}`, variant: 'success' })
      setStatusConfirmOpen(false)
      setPendingStatus('')
    } catch (error) {
      toast({
        title: 'Could not update status',
        description:
          error instanceof ApiError && error.code === 'QUOTATION_NOT_EDITABLE'
            ? 'Only draft quotations can be edited.'
            : error instanceof ApiError
              ? error.message
              : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsChangingStatus(false)
    }
  }

  async function handleDownload() {
    if (!id) return
    setIsDownloading(true)
    try {
      const blob = await downloadQuotationPdf(id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `quotation-${id}.pdf`
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    } catch (error) {
      toast({
        title: 'Could not download PDF',
        description: error instanceof ApiError ? error.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsDownloading(false)
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

  if (detailQuery.isError || !quotation) {
    return (
      <ErrorState title="We couldn't load this quotation" error={detailQuery.error} onRetry={() => detailQuery.refetch()} />
    )
  }

  const isDraft = quotation.status === 'DRAFT'
  const canEdit = isDraft && canUpdate
  const customerName = customerQuery.data?.name ?? '—'

  if (isEditing) {
    if (customerQuery.isLoading) {
      return (
        <div className="space-y-4">
          <Skeleton className="h-8 w-64" />
          <Skeleton className="h-40 w-full" />
        </div>
      )
    }
    return (
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Edit quotation"
          breadcrumb={[{ label: 'Quotations', to: '/quotations' }, { label: 'Edit' }]}
        />
        <QuotationForm
          quotation={quotation}
          customerLabel={customerName}
          onSaved={() => setIsEditing(false)}
          onCancel={() => setIsEditing(false)}
        />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={`Quotation for ${customerName}`}
        breadcrumb={[{ label: 'Quotations', to: '/quotations' }, { label: quotation.id.slice(0, 8) }]}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={quotation.status} />
            <Button variant="outline" size="sm" onClick={handleDownload} isLoading={isDownloading}>
              <Download className="h-4 w-4" aria-hidden="true" />
              Download PDF
            </Button>
            {canEdit && (
              <Button variant="outline" size="sm" onClick={() => setIsEditing(true)}>
                Edit
              </Button>
            )}
            {canCancel && quotation.status !== 'CANCELLED' && (
              <Button variant="destructive" size="sm" onClick={() => setCancelOpen(true)}>
                Cancel quotation
              </Button>
            )}
          </div>
        }
      />

      {!isDraft && quotation.status !== 'CANCELLED' && (
        <Alert variant="info">This quotation has left draft status and can no longer be edited.</Alert>
      )}

      {isDraft && canUpdate && (
        <Card>
          <CardHeader>
            <CardTitle>Change status</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <Select
              aria-label="New status"
              value={pendingStatus}
              onChange={(e) => setPendingStatus(e.target.value as QuotationStatus)}
              className="sm:max-w-xs"
            >
              <option value="">Select a status...</option>
              {STATUS_TRANSITIONS.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </Select>
            <Button
              type="button"
              variant="outline"
              disabled={!pendingStatus}
              onClick={() => setStatusConfirmOpen(true)}
            >
              Update status
            </Button>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Details</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div className="space-y-1">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Customer</p>
            <p className="text-sm text-foreground">{customerName}</p>
          </div>
          <div className="space-y-1">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Valid until</p>
            <p className="text-sm text-foreground">{quotation.validUntil ?? '—'}</p>
          </div>
          <div className="space-y-1">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Discount</p>
            <p className="text-sm text-foreground">{quotation.discountPercentage}%</p>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Line items</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <div className="hidden grid-cols-5 gap-2 border-b border-border pb-2 text-xs font-medium uppercase tracking-wide text-muted-foreground sm:grid">
            <span className="col-span-2">Product</span>
            <span className="text-right">Qty</span>
            <span className="text-right">Unit price</span>
            <span className="text-right">Line total</span>
          </div>
          {quotation.items.map((item) => (
            <div key={item.id} className="grid grid-cols-2 gap-2 border-b border-border py-2 text-sm last:border-0 sm:grid-cols-5">
              <span className="col-span-2 text-foreground">{item.productNameSnapshot}</span>
              <span className="text-right tabular-nums">{item.quantity}</span>
              <span className="text-right tabular-nums">{formatInr(item.unitPrice)}</span>
              <span className="col-span-2 text-right tabular-nums sm:col-span-1">
                {formatInr(item.lineSubtotal + item.lineTaxAmount)}
              </span>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Totals</CardTitle>
        </CardHeader>
        <CardContent className="ml-auto max-w-xs space-y-2 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Subtotal</span>
            <span className="tabular-nums">{formatInr(quotation.subtotal)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Discount</span>
            <span className="tabular-nums">{formatInr(quotation.discountAmount)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Tax</span>
            <span className="tabular-nums">{formatInr(quotation.taxAmount)}</span>
          </div>
          <div className="flex justify-between border-t border-border pt-2 font-medium text-foreground">
            <span>Grand total</span>
            <span className="tabular-nums">{formatInr(quotation.grandTotal)}</span>
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        title="Cancel this quotation?"
        description="This permanently cancels the quotation. This action cannot be undone."
        confirmLabel="Cancel quotation"
        isLoading={isCancelling}
        onConfirm={handleCancel}
      />

      <ConfirmDialog
        open={statusConfirmOpen}
        onOpenChange={setStatusConfirmOpen}
        title={`Mark as ${pendingStatus || '...'}?`}
        description="Once a quotation leaves Draft status it can no longer be edited. This action cannot be undone."
        confirmLabel="Update status"
        isLoading={isChangingStatus}
        onConfirm={handleStatusChange}
      />
    </div>
  )
}
