import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Clock, MoreHorizontal, Plus, Search, Target, UserCheck, UserX } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { useAuth } from '@/app/providers/AuthProvider'
import { usePermission } from '@/hooks/usePermission'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { archiveLead, leadKeys, searchLeads } from '@/features/leads/api'
import { LeadFormDialog } from '@/features/leads/components/LeadFormDialog'
import { LEAD_PRIORITY_OPTIONS, LEAD_SOURCE_OPTIONS, LEAD_STATUS_OPTIONS } from '@/features/leads/schemas'
import { DataTable, type DataTableColumn } from '@/components/data-display/DataTable'
import { Pagination } from '@/components/data-display/Pagination'
import { PriorityBadge } from '@/components/data-display/PriorityBadge'
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
import { formatDate, isOnOrBeforeToday, todayIsoDate } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { LeadPriority, LeadResponse, LeadSource, LeadStatus } from '@/types/api'

function useLeadListParams(currentUserId: string | undefined) {
  const [searchParams, setSearchParams] = useSearchParams()

  const q = searchParams.get('q') ?? ''
  const status = (searchParams.get('status') as LeadStatus | null) ?? undefined
  const source = (searchParams.get('source') as LeadSource | null) ?? undefined
  const priority = (searchParams.get('priority') as LeadPriority | null) ?? undefined
  const assignedToUserId = searchParams.get('assignedToUserId') ?? undefined
  const unassigned = searchParams.get('unassigned') === 'true'
  const followUpBefore = searchParams.get('followUpBefore') ?? undefined
  const page = Number(searchParams.get('page') ?? '0')

  function setParams(updates: Record<string, string | undefined>, resetPage = true) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      for (const [key, value] of Object.entries(updates)) {
        if (value) next.set(key, value)
        else next.delete(key)
      }
      if (resetPage) next.set('page', '0')
      return next
    })
  }

  const isMyLeads = !!currentUserId && assignedToUserId === currentUserId
  const isFollowUpDue = !!followUpBefore

  return {
    q,
    status,
    source,
    priority,
    assignedToUserId,
    unassigned,
    followUpBefore,
    page,
    setSearch: (value: string) => setParams({ q: value || undefined }),
    setStatus: (value: string) => setParams({ status: value || undefined }),
    setSource: (value: string) => setParams({ source: value || undefined }),
    setPriority: (value: string) => setParams({ priority: value || undefined }),
    toggleMyLeads: () =>
      setParams({ assignedToUserId: isMyLeads ? undefined : currentUserId, unassigned: undefined }),
    toggleUnassigned: () =>
      setParams({ unassigned: unassigned ? undefined : 'true', assignedToUserId: undefined }),
    toggleFollowUpDue: () => setParams({ followUpBefore: isFollowUpDue ? undefined : todayIsoDate() }),
    setPage: (value: number) => setParams({ page: String(value) }, false),
    clear: () => setSearchParams({}),
    isMyLeads,
    isFollowUpDue,
  }
}

function assignmentLabel(lead: LeadResponse, currentUserId: string | undefined): string {
  if (lead.assignedToUserId === null) return 'Unassigned'
  if (lead.assignedToUserId === currentUserId) return 'You'
  return 'Assigned'
}

export function LeadsListPage() {
  const { user } = useAuth()
  const params = useLeadListParams(user?.id)
  const [searchInput, setSearchInput] = useState(params.q)
  const debouncedSearch = useDebouncedValue(searchInput, 300)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const canCreate = usePermission('LEAD_CREATE')
  const canUpdate = usePermission('LEAD_UPDATE')
  const canArchive = usePermission('LEAD_DELETE')

  useEffect(() => {
    if (debouncedSearch !== params.q) params.setSearch(debouncedSearch)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const queryParams = {
    q: params.q || undefined,
    status: params.status,
    source: params.source,
    priority: params.priority,
    assignedToUserId: params.assignedToUserId,
    unassigned: params.unassigned || undefined,
    followUpBefore: params.followUpBefore,
    page: params.page,
    size: 20,
  }
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: leadKeys.list(queryParams),
    queryFn: () => searchLeads(queryParams),
  })

  const [formOpen, setFormOpen] = useState(false)
  const [editingLead, setEditingLead] = useState<LeadResponse | undefined>(undefined)
  const [archiving, setArchiving] = useState<LeadResponse | null>(null)
  const [isArchiving, setIsArchiving] = useState(false)

  function openCreate() {
    setEditingLead(undefined)
    setFormOpen(true)
  }

  function openEdit(lead: LeadResponse) {
    setEditingLead(lead)
    setFormOpen(true)
  }

  async function confirmArchive() {
    if (!archiving) return
    setIsArchiving(true)
    try {
      await archiveLead(archiving.id)
      await queryClient.invalidateQueries({ queryKey: ['leads', 'list'] })
      await queryClient.invalidateQueries({ queryKey: leadKeys.detail(archiving.id) })
      toast({ title: 'Lead archived', variant: 'success' })
      setArchiving(null)
    } catch (err) {
      toast({
        title: 'Could not archive lead',
        description: err instanceof ApiError ? err.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsArchiving(false)
    }
  }

  const columns: DataTableColumn<LeadResponse>[] = [
    {
      key: 'name',
      header: 'Name',
      cell: (lead) => (
        <Link to={`/leads/${lead.id}`} className="font-medium text-foreground hover:underline">
          {lead.name}
        </Link>
      ),
    },
    { key: 'company', header: 'Company', cell: (lead) => lead.company ?? '—' },
    { key: 'status', header: 'Status', cell: (lead) => <StatusBadge status={lead.status} /> },
    { key: 'source', header: 'Source', cell: (lead) => lead.source.replace('_', ' ') },
    { key: 'priority', header: 'Priority', cell: (lead) => <PriorityBadge priority={lead.priority} /> },
    {
      key: 'followUpDate',
      header: 'Follow-up',
      cell: (lead) => {
        const overdue =
          lead.followUpDate && isOnOrBeforeToday(lead.followUpDate) && lead.status !== 'WON' && lead.status !== 'LOST'
        return (
          <span className={cn(overdue && 'font-medium text-warning')}>{formatDate(lead.followUpDate)}</span>
        )
      },
    },
    { key: 'assignment', header: 'Assignment', cell: (lead) => assignmentLabel(lead, user?.id) },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      cell: (lead) => (
        <DropdownMenu>
          <DropdownMenuTrigger
            className="rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={`Actions for ${lead.name}`}
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
            <DropdownMenuItem onSelect={() => navigate(`/leads/${lead.id}`)}>View details</DropdownMenuItem>
            {canUpdate && !lead.archivedAt && (
              <DropdownMenuItem onSelect={() => openEdit(lead)}>Edit</DropdownMenuItem>
            )}
            {canArchive && !lead.archivedAt && (
              <DropdownMenuItem
                onSelect={() => setArchiving(lead)}
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

  const activeFilterCount = [params.status, params.source, params.priority].filter(Boolean).length
  const hasActiveFilters = Boolean(params.q || activeFilterCount || params.unassigned || params.assignedToUserId || params.followUpBefore)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Leads"
        description="Manage and track your sales opportunities."
        breadcrumb={[{ label: 'Leads' }]}
        actions={
          canCreate ? (
            <Button onClick={openCreate}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Create lead
            </Button>
          ) : undefined
        }
      />

      <div className="flex flex-col gap-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full sm:max-w-xs">
            <Search
              className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden="true"
            />
            <Input
              className="pl-9"
              placeholder="Search leads..."
              aria-label="Search leads"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
          </div>

          <FilterBar activeCount={activeFilterCount} onClear={() => { params.setStatus(''); params.setSource(''); params.setPriority('') }}>
            <Select aria-label="Status filter" value={params.status ?? ''} onChange={(e) => params.setStatus(e.target.value)}>
              <option value="">All statuses</option>
              {LEAD_STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>
                  {status.replace('_', ' ')}
                </option>
              ))}
            </Select>
            <Select aria-label="Source filter" value={params.source ?? ''} onChange={(e) => params.setSource(e.target.value)}>
              <option value="">All sources</option>
              {LEAD_SOURCE_OPTIONS.map((source) => (
                <option key={source} value={source}>
                  {source.replace('_', ' ')}
                </option>
              ))}
            </Select>
            <Select aria-label="Priority filter" value={params.priority ?? ''} onChange={(e) => params.setPriority(e.target.value)}>
              <option value="">All priorities</option>
              {LEAD_PRIORITY_OPTIONS.map((priority) => (
                <option key={priority} value={priority}>
                  {priority}
                </option>
              ))}
            </Select>
          </FilterBar>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button
            variant={params.isMyLeads ? 'primary' : 'outline'}
            size="sm"
            onClick={params.toggleMyLeads}
          >
            <UserCheck className="h-3.5 w-3.5" aria-hidden="true" />
            My Leads
          </Button>
          <Button
            variant={params.unassigned ? 'primary' : 'outline'}
            size="sm"
            onClick={params.toggleUnassigned}
          >
            <UserX className="h-3.5 w-3.5" aria-hidden="true" />
            Unassigned
          </Button>
          <Button
            variant={params.isFollowUpDue ? 'primary' : 'outline'}
            size="sm"
            onClick={params.toggleFollowUpDue}
          >
            <Clock className="h-3.5 w-3.5" aria-hidden="true" />
            Follow-up Due
          </Button>
        </div>
      </div>

      {isError ? (
        <ErrorState title="We couldn't load your leads" error={error} onRetry={() => refetch()} />
      ) : !isLoading && data?.content.length === 0 ? (
        <EmptyState
          icon={Target}
          title={hasActiveFilters ? 'No leads found' : 'No leads yet'}
          description={hasActiveFilters ? 'Try changing your search or filters.' : 'Add your first lead to get started.'}
          action={
            hasActiveFilters ? (
              <Button variant="outline" size="sm" onClick={params.clear}>
                Clear filters
              </Button>
            ) : canCreate ? (
              <Button size="sm" onClick={openCreate}>
                Add your first lead
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="hidden lg:block">
            <DataTable
              caption="Leads"
              columns={columns}
              rows={data?.content ?? []}
              rowKey={(lead) => lead.id}
              onRowClick={(lead) => navigate(`/leads/${lead.id}`)}
              isLoading={isLoading}
            />
          </div>

          <div className="flex flex-col gap-2 lg:hidden">
            {isLoading
              ? Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="h-28 animate-pulse rounded-lg border border-border bg-muted" />
                ))
              : data?.content.map((lead) => (
                  <Link
                    key={lead.id}
                    to={`/leads/${lead.id}`}
                    className="flex flex-col gap-1.5 rounded-lg border border-border bg-background p-4 hover:bg-muted/40"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-foreground">{lead.name}</span>
                      <StatusBadge status={lead.status} />
                    </div>
                    {lead.company && <span className="text-sm text-muted-foreground">{lead.company}</span>}
                    <div className="flex items-center justify-between text-xs text-muted-foreground">
                      <PriorityBadge priority={lead.priority} />
                      <span>{formatDate(lead.followUpDate)}</span>
                      <span>{assignmentLabel(lead, user?.id)}</span>
                    </div>
                  </Link>
                ))}
          </div>

          {data && <Pagination page={data} onPageChange={params.setPage} />}
        </>
      )}

      <LeadFormDialog open={formOpen} onOpenChange={setFormOpen} lead={editingLead} />

      <ConfirmDialog
        open={!!archiving}
        onOpenChange={(open) => !open && setArchiving(null)}
        title="Archive this lead?"
        description={`${archiving?.name ?? 'This lead'} will be removed from your normal active lead lists.`}
        confirmLabel="Archive"
        isLoading={isArchiving}
        onConfirm={confirmArchive}
      />
    </div>
  )
}
