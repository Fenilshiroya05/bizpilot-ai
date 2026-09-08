import { useQuery } from '@tanstack/react-query'
import { FileText, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { customerKeys, getCustomer } from '@/features/customers/api'
import { CustomerCombobox } from '@/features/quotations/components/CustomerCombobox'
import { quotationKeys, searchQuotations } from '@/features/quotations/api'
import { usePermission } from '@/hooks/usePermission'
import { DataTable, type DataTableColumn } from '@/components/data-display/DataTable'
import { Pagination } from '@/components/data-display/Pagination'
import { StatusBadge } from '@/components/data-display/StatusBadge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { FilterBar } from '@/components/forms/FilterBar'
import { EmptyState } from '@/components/feedback/EmptyState'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { formatInr } from '@/lib/money'
import type { QuotationStatus, QuotationSummaryResponse } from '@/types/api'

/** Quotation list/summary responses only carry `customerId` — this resolves
 * the display name via the same per-id GET already used on the detail page,
 * relying on TanStack Query's cache so the same customer across multiple
 * quotations is only fetched once. */
function CustomerName({ customerId }: { customerId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: customerKeys.detail(customerId),
    queryFn: () => getCustomer(customerId),
  })
  if (isLoading) return <span className="text-muted-foreground">Loading...</span>
  return <span>{data?.name ?? '—'}</span>
}

function useQuotationListParams() {
  const [searchParams, setSearchParams] = useSearchParams()

  const status = (searchParams.get('status') as QuotationStatus | null) ?? undefined
  const customerId = searchParams.get('customerId') ?? undefined
  const validUntilBefore = searchParams.get('validUntilBefore') ?? undefined
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
    status,
    customerId,
    validUntilBefore,
    page,
    setStatus: (value: string) => setParam('status', value || undefined, true),
    setCustomerId: (value: string) => setParam('customerId', value || undefined, true),
    setValidUntilBefore: (value: string) => setParam('validUntilBefore', value || undefined, true),
    setPage: (value: number) => setParam('page', String(value), false),
    clear: () => setSearchParams({}),
  }
}

export function QuotationsListPage() {
  const { status, customerId, validUntilBefore, page, setStatus, setCustomerId, setValidUntilBefore, setPage, clear } =
    useQuotationListParams()
  const navigate = useNavigate()
  const canCreate = usePermission('QUOTATION_CREATE')
  const [customerFilterLabel, setCustomerFilterLabel] = useState('')

  function clearAll() {
    clear()
    setCustomerFilterLabel('')
  }

  const params = { status, customerId, validUntilBefore, page, size: 20 }
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: quotationKeys.list(params),
    queryFn: () => searchQuotations(params),
  })

  const columns: DataTableColumn<QuotationSummaryResponse>[] = [
    {
      key: 'id',
      header: 'Quotation',
      cell: (q) => (
        <Link to={`/quotations/${q.id}`} className="font-medium text-foreground hover:underline">
          {q.id.slice(0, 8)}
        </Link>
      ),
    },
    { key: 'customer', header: 'Customer', cell: (q) => <CustomerName customerId={q.customerId} /> },
    { key: 'validUntil', header: 'Valid until', cell: (q) => q.validUntil ?? '—' },
    { key: 'grandTotal', header: 'Total', className: 'text-right tabular-nums', cell: (q) => formatInr(q.grandTotal) },
    { key: 'status', header: 'Status', cell: (q) => <StatusBadge status={q.status} /> },
  ]

  const hasActiveFilters = Boolean(status || customerId || validUntilBefore)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Quotations"
        description="Create and manage customer quotations."
        breadcrumb={[{ label: 'Quotations' }]}
        actions={
          canCreate ? (
            <Button onClick={() => navigate('/quotations/new')}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create quotation
            </Button>
          ) : undefined
        }
      />

      <FilterBar
        activeCount={(status ? 1 : 0) + (customerId ? 1 : 0) + (validUntilBefore ? 1 : 0)}
        onClear={clearAll}
      >
        <Select aria-label="Status filter" value={status ?? ''} onChange={(e) => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="SENT">Sent</option>
          <option value="ACCEPTED">Accepted</option>
          <option value="REJECTED">Rejected</option>
          <option value="EXPIRED">Expired</option>
          <option value="CANCELLED">Cancelled</option>
        </Select>
        <div className="w-full sm:w-64">
          <CustomerCombobox
            initialLabel={customerFilterLabel}
            onSelect={(customer) => {
              setCustomerId(customer.id)
              setCustomerFilterLabel(customer.name)
            }}
            onClear={() => {
              setCustomerId('')
              setCustomerFilterLabel('')
            }}
          />
        </div>
        <Input
          type="date"
          aria-label="Valid until before"
          value={validUntilBefore ?? ''}
          onChange={(e) => setValidUntilBefore(e.target.value)}
        />
      </FilterBar>

      {isError ? (
        <ErrorState title="We couldn't load your quotations" error={error} onRetry={() => refetch()} />
      ) : !isLoading && data?.content.length === 0 ? (
        <EmptyState
          icon={FileText}
          title={hasActiveFilters ? 'No quotations found' : 'No quotations yet'}
          description={hasActiveFilters ? 'Try changing your filters.' : 'Create your first quotation to get started.'}
          action={
            hasActiveFilters ? (
              <Button variant="outline" size="sm" onClick={clearAll}>
                Clear filters
              </Button>
            ) : canCreate ? (
              <Button size="sm" onClick={() => navigate('/quotations/new')}>
                Create your first quotation
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="hidden lg:block">
            <DataTable
              caption="Quotations"
              columns={columns}
              rows={data?.content ?? []}
              rowKey={(q) => q.id}
              onRowClick={(q) => navigate(`/quotations/${q.id}`)}
              isLoading={isLoading}
            />
          </div>

          <div className="flex flex-col gap-2 lg:hidden">
            {isLoading
              ? Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="h-24 animate-pulse rounded-lg border border-border bg-muted" />
                ))
              : data?.content.map((q) => (
                  <Link
                    key={q.id}
                    to={`/quotations/${q.id}`}
                    className="flex flex-col gap-1 rounded-lg border border-border bg-background p-4 hover:bg-muted/40"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-foreground">
                        <CustomerName customerId={q.customerId} />
                      </span>
                      <StatusBadge status={q.status} />
                    </div>
                    <span className="text-sm text-muted-foreground">Valid until {q.validUntil ?? '—'}</span>
                    <span className="text-sm tabular-nums text-foreground">{formatInr(q.grandTotal)}</span>
                  </Link>
                ))}
          </div>

          {data && <Pagination page={data} onPageChange={setPage} />}
        </>
      )}
    </div>
  )
}
