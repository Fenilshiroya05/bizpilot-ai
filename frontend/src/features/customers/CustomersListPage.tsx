import { useQuery, useQueryClient } from '@tanstack/react-query'
import { MoreHorizontal, Plus, Search, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { usePermission } from '@/hooks/usePermission'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { archiveCustomer, customerKeys, searchCustomers } from '@/features/customers/api'
import { CustomerFormDialog } from '@/features/customers/components/CustomerFormDialog'
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
import type { CustomerResponse, CustomerStatus } from '@/types/api'

function useCustomerListParams() {
  const [searchParams, setSearchParams] = useSearchParams()

  const q = searchParams.get('q') ?? ''
  const status = (searchParams.get('status') as CustomerStatus | null) ?? undefined
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
    page,
    setSearch: (value: string) => setParam('q', value || undefined, true),
    setStatus: (value: string) => setParam('status', value || undefined, true),
    setPage: (value: number) => setParam('page', String(value), false),
    clear: () => setSearchParams({}),
  }
}

export function CustomersListPage() {
  const { q, status, page, setSearch, setStatus, setPage, clear } = useCustomerListParams()
  const [searchInput, setSearchInput] = useState(q)
  const debouncedSearch = useDebouncedValue(searchInput, 300)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const canCreate = usePermission('CUSTOMER_CREATE')
  const canUpdate = usePermission('CUSTOMER_UPDATE')
  const canArchive = usePermission('CUSTOMER_DELETE')

  useEffect(() => {
    if (debouncedSearch !== q) setSearch(debouncedSearch)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const params = { q: q || undefined, status, page, size: 20 }
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: customerKeys.list(params),
    queryFn: () => searchCustomers(params),
  })

  const [formOpen, setFormOpen] = useState(false)
  const [editingCustomer, setEditingCustomer] = useState<CustomerResponse | undefined>(undefined)
  const [archiving, setArchiving] = useState<CustomerResponse | null>(null)
  const [isArchiving, setIsArchiving] = useState(false)

  function openCreate() {
    setEditingCustomer(undefined)
    setFormOpen(true)
  }

  function openEdit(customer: CustomerResponse) {
    setEditingCustomer(customer)
    setFormOpen(true)
  }

  async function confirmArchive() {
    if (!archiving) return
    setIsArchiving(true)
    try {
      await archiveCustomer(archiving.id)
      await queryClient.invalidateQueries({ queryKey: ['customers', 'list'] })
      await queryClient.invalidateQueries({ queryKey: customerKeys.detail(archiving.id) })
      toast({ title: 'Customer archived', variant: 'success' })
      setArchiving(null)
    } catch (err) {
      toast({
        title: 'Could not archive customer',
        description: err instanceof ApiError ? err.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsArchiving(false)
    }
  }

  const columns: DataTableColumn<CustomerResponse>[] = [
    {
      key: 'name',
      header: 'Name',
      cell: (c) => (
        <Link to={`/customers/${c.id}`} className="font-medium text-foreground hover:underline">
          {c.name}
        </Link>
      ),
    },
    { key: 'company', header: 'Company', cell: (c) => c.company ?? '—' },
    { key: 'email', header: 'Email', cell: (c) => c.email ?? '—' },
    { key: 'phone', header: 'Phone', cell: (c) => c.phone ?? '—' },
    { key: 'status', header: 'Status', cell: (c) => <StatusBadge status={c.status} /> },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      cell: (c) => (
        <DropdownMenu>
          <DropdownMenuTrigger
            className="rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={`Actions for ${c.name}`}
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
            <DropdownMenuItem onSelect={() => navigate(`/customers/${c.id}`)}>View details</DropdownMenuItem>
            {canUpdate && c.status !== 'ARCHIVED' && (
              <DropdownMenuItem onSelect={() => openEdit(c)}>Edit</DropdownMenuItem>
            )}
            {canArchive && c.status !== 'ARCHIVED' && (
              <DropdownMenuItem
                onSelect={() => setArchiving(c)}
                className="text-destructive focus:bg-destructive/10"
              >
                Archive
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ]

  const hasActiveFilters = Boolean(q || status)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Customers"
        description="Manage your customer relationships."
        breadcrumb={[{ label: 'Customers' }]}
        actions={
          canCreate ? (
            <Button onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create customer
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
            placeholder="Search customers..."
            aria-label="Search customers"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
        </div>

        <FilterBar activeCount={status ? 1 : 0} onClear={() => setStatus('')}>
          <Select aria-label="Status filter" value={status ?? ''} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Active &amp; Inactive</option>
            <option value="ACTIVE">Active only</option>
            <option value="INACTIVE">Inactive only</option>
            <option value="ARCHIVED">Archived</option>
          </Select>
        </FilterBar>
      </div>

      {isError ? (
        <ErrorState title="We couldn't load your customers" error={error} onRetry={() => refetch()} />
      ) : !isLoading && data?.content.length === 0 ? (
        <EmptyState
          icon={Users}
          title={hasActiveFilters ? 'No customers found' : 'No customers yet'}
          description={
            hasActiveFilters
              ? 'Try changing your search or filters.'
              : 'Add your first customer to get started.'
          }
          action={
            hasActiveFilters ? (
              <Button variant="outline" size="sm" onClick={clear}>
                Clear filters
              </Button>
            ) : canCreate ? (
              <Button size="sm" onClick={openCreate}>
                Add your first customer
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          {/* Desktop table */}
          <div className="hidden lg:block">
            <DataTable
              caption="Customers"
              columns={columns}
              rows={data?.content ?? []}
              rowKey={(c) => c.id}
              onRowClick={(c) => navigate(`/customers/${c.id}`)}
              isLoading={isLoading}
            />
          </div>

          {/* Mobile card list */}
          <div className="flex flex-col gap-2 lg:hidden">
            {isLoading
              ? Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="h-24 animate-pulse rounded-lg border border-border bg-muted" />
                ))
              : data?.content.map((c) => (
                  <Link
                    key={c.id}
                    to={`/customers/${c.id}`}
                    className="flex flex-col gap-1 rounded-lg border border-border bg-background p-4 hover:bg-muted/40"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-foreground">{c.name}</span>
                      <StatusBadge status={c.status} />
                    </div>
                    {c.company && <span className="text-sm text-muted-foreground">{c.company}</span>}
                    {c.email && <span className="text-sm text-muted-foreground">{c.email}</span>}
                  </Link>
                ))}
          </div>

          {data && <Pagination page={data} onPageChange={setPage} />}
        </>
      )}

      <CustomerFormDialog open={formOpen} onOpenChange={setFormOpen} customer={editingCustomer} />

      <ConfirmDialog
        open={!!archiving}
        onOpenChange={(open) => !open && setArchiving(null)}
        title="Archive this customer?"
        description={`${archiving?.name ?? 'This customer'} will be removed from your normal active customer lists. This can be viewed later by filtering for archived customers.`}
        confirmLabel="Archive"
        isLoading={isArchiving}
        onConfirm={confirmArchive}
      />
    </div>
  )
}
