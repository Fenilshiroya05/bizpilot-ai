import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router-dom'

import { usePermission } from '@/hooks/usePermission'
import {
  addCustomerNote,
  archiveCustomer,
  customerKeys,
  getCustomer,
  getCustomerHistory,
} from '@/features/customers/api'
import { CustomerFormDialog } from '@/features/customers/components/CustomerFormDialog'
import { CustomerHistoryFeed } from '@/features/customers/components/CustomerHistoryFeed'
import { customerNoteSchema } from '@/features/customers/schemas'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/components/data-display/StatusBadge'
import { Pagination } from '@/components/data-display/Pagination'
import { ConfirmDialog } from '@/components/feedback/ConfirmDialog'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'

function OverviewField({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="text-sm text-foreground">{value ?? '—'}</p>
    </div>
  )
}

export function CustomerDetailPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const canUpdate = usePermission('CUSTOMER_UPDATE')
  const canArchive = usePermission('CUSTOMER_DELETE')

  const [historyPage, setHistoryPage] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const [archiveOpen, setArchiveOpen] = useState(false)
  const [isArchiving, setIsArchiving] = useState(false)
  const [noteContent, setNoteContent] = useState('')
  const [noteError, setNoteError] = useState<string | null>(null)
  const [isAddingNote, setIsAddingNote] = useState(false)

  const detailQuery = useQuery({
    queryKey: customerKeys.detail(id ?? ''),
    queryFn: () => getCustomer(id ?? ''),
    enabled: !!id,
  })

  const historyQuery = useQuery({
    queryKey: customerKeys.history(id ?? '', historyPage),
    queryFn: () => getCustomerHistory(id ?? '', historyPage),
    enabled: !!id,
  })

  async function handleAddNote() {
    const result = customerNoteSchema.safeParse({ content: noteContent })
    if (!result.success) {
      setNoteError(result.error.issues[0]?.message ?? 'Invalid note')
      return
    }
    setNoteError(null)
    setIsAddingNote(true)
    try {
      await addCustomerNote(id ?? '', result.data.content)
      await queryClient.invalidateQueries({ queryKey: customerKeys.detail(id ?? '') })
      setNoteContent('')
    } catch (error) {
      toast({
        title: 'Could not add note',
        description: error instanceof ApiError ? error.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsAddingNote(false)
    }
  }

  async function confirmArchive() {
    if (!id) return
    setIsArchiving(true)
    try {
      await archiveCustomer(id)
      await queryClient.invalidateQueries({ queryKey: ['customers', 'list'] })
      await queryClient.invalidateQueries({ queryKey: customerKeys.detail(id) })
      toast({ title: 'Customer archived', variant: 'success' })
      setArchiveOpen(false)
    } catch (error) {
      toast({
        title: 'Could not archive customer',
        description: error instanceof ApiError ? error.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsArchiving(false)
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
      <ErrorState
        title="We couldn't load this customer"
        error={detailQuery.error}
        onRetry={() => detailQuery.refetch()}
      />
    )
  }

  const customer = detailQuery.data
  const isArchived = customer.status === 'ARCHIVED'
  const canAddNote = canUpdate && !isArchived

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={customer.name}
        breadcrumb={[{ label: 'Customers', to: '/customers' }, { label: customer.name }]}
        actions={
          <div className="flex items-center gap-2">
            <StatusBadge status={customer.status} />
            {canUpdate && !isArchived && (
              <Button variant="outline" size="sm" onClick={() => setFormOpen(true)}>
                Edit
              </Button>
            )}
            {canArchive && !isArchived && (
              <Button variant="destructive" size="sm" onClick={() => setArchiveOpen(true)}>
                Archive
              </Button>
            )}
          </div>
        }
      />

      {isArchived && (
        <Alert variant="warning">
          This customer is archived. Its details are read-only and no new notes can be added.
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Overview</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <OverviewField label="Company" value={customer.company} />
          <OverviewField label="Email" value={customer.email} />
          <OverviewField label="Phone" value={customer.phone} />
          <OverviewField label="Address" value={customer.address} />
          <OverviewField label="GSTIN" value={customer.gstin} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>History</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {canAddNote && (
            <div className="flex gap-2">
              <Input
                placeholder="Write a note..."
                aria-label="Write a note"
                aria-invalid={!!noteError}
                aria-describedby={noteError ? 'note-error' : undefined}
                value={noteContent}
                onChange={(e) => setNoteContent(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleAddNote()
                }}
              />
              <Button onClick={handleAddNote} isLoading={isAddingNote}>
                Add note
              </Button>
            </div>
          )}
          {noteError && (
            <p id="note-error" className="text-sm text-destructive">
              {noteError}
            </p>
          )}

          {historyQuery.isLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : historyQuery.isError ? (
            <ErrorState title="We couldn't load this customer's history" error={historyQuery.error} onRetry={() => historyQuery.refetch()} />
          ) : (
            <>
              <CustomerHistoryFeed activities={historyQuery.data?.content ?? []} />
              {historyQuery.data && historyQuery.data.totalElements > historyQuery.data.size && (
                <Pagination page={historyQuery.data} onPageChange={setHistoryPage} />
              )}
            </>
          )}
        </CardContent>
      </Card>

      <CustomerFormDialog open={formOpen} onOpenChange={setFormOpen} customer={customer} />

      <ConfirmDialog
        open={archiveOpen}
        onOpenChange={setArchiveOpen}
        title="Archive this customer?"
        description={`${customer.name} will be removed from your normal active customer lists. This can be viewed later by filtering for archived customers.`}
        confirmLabel="Archive"
        isLoading={isArchiving}
        onConfirm={confirmArchive}
      />
    </div>
  )
}
