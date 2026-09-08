import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { useParams } from 'react-router-dom'

import { usePermission } from '@/hooks/usePermission'
import { addLeadNote, archiveLead, getLead, getLeadHistory, leadKeys } from '@/features/leads/api'
import { AssignmentControls } from '@/features/leads/components/AssignmentControls'
import { LeadFormDialog } from '@/features/leads/components/LeadFormDialog'
import { LeadHistoryFeed } from '@/features/leads/components/LeadHistoryFeed'
import { LeadScorePanel } from '@/features/leads/components/LeadScorePanel'
import { leadNoteSchema } from '@/features/leads/schemas'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { PriorityBadge } from '@/components/data-display/PriorityBadge'
import { StatusBadge } from '@/components/data-display/StatusBadge'
import { Pagination } from '@/components/data-display/Pagination'
import { ConfirmDialog } from '@/components/feedback/ConfirmDialog'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import { formatDate } from '@/lib/date'

function OverviewField({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="text-sm text-foreground">{value ?? '—'}</div>
    </div>
  )
}

export function LeadDetailPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  const canUpdate = usePermission('LEAD_UPDATE')
  const canArchive = usePermission('LEAD_DELETE')

  const [historyPage, setHistoryPage] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const [archiveOpen, setArchiveOpen] = useState(false)
  const [isArchiving, setIsArchiving] = useState(false)
  const [noteContent, setNoteContent] = useState('')
  const [noteError, setNoteError] = useState<string | null>(null)
  const [isAddingNote, setIsAddingNote] = useState(false)

  const detailQuery = useQuery({
    queryKey: leadKeys.detail(id ?? ''),
    queryFn: () => getLead(id ?? ''),
    enabled: !!id,
  })

  const historyQuery = useQuery({
    queryKey: leadKeys.history(id ?? '', historyPage),
    queryFn: () => getLeadHistory(id ?? '', historyPage),
    enabled: !!id,
  })

  async function handleAddNote() {
    const result = leadNoteSchema.safeParse({ content: noteContent })
    if (!result.success) {
      setNoteError(result.error.issues[0]?.message ?? 'Invalid note')
      return
    }
    setNoteError(null)
    setIsAddingNote(true)
    try {
      await addLeadNote(id ?? '', result.data.content)
      await queryClient.invalidateQueries({ queryKey: leadKeys.detail(id ?? '') })
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
      await archiveLead(id)
      await queryClient.invalidateQueries({ queryKey: ['leads', 'list'] })
      await queryClient.invalidateQueries({ queryKey: leadKeys.detail(id) })
      toast({ title: 'Lead archived', variant: 'success' })
      setArchiveOpen(false)
    } catch (error) {
      toast({
        title: 'Could not archive lead',
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
      <ErrorState title="We couldn't load this lead" error={detailQuery.error} onRetry={() => detailQuery.refetch()} />
    )
  }

  const lead = detailQuery.data
  const isArchived = !!lead.archivedAt
  const canAddNote = canUpdate && !isArchived

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={lead.name}
        breadcrumb={[{ label: 'Leads', to: '/leads' }, { label: lead.name }]}
        actions={
          <div className="flex items-center gap-2">
            <StatusBadge status={lead.status} />
            <PriorityBadge priority={lead.priority} />
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
          This lead is archived. Its details are read-only and no new notes can be added.
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Overview</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <OverviewField label="Company" value={lead.company} />
          <OverviewField label="Email" value={lead.email} />
          <OverviewField label="Phone" value={lead.phone} />
          <OverviewField label="Source" value={lead.source.replace('_', ' ')} />
          <OverviewField label="Follow-up" value={formatDate(lead.followUpDate)} />
          <OverviewField label="Assignment" value={!isArchived ? <AssignmentControls lead={lead} /> : 'Unassigned'} />
        </CardContent>
      </Card>

      <LeadScorePanel lead={lead} />

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
                aria-describedby={noteError ? 'lead-note-error' : undefined}
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
            <p id="lead-note-error" className="text-sm text-destructive">
              {noteError}
            </p>
          )}

          {historyQuery.isLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : historyQuery.isError ? (
            <ErrorState
              title="We couldn't load this lead's history"
              error={historyQuery.error}
              onRetry={() => historyQuery.refetch()}
            />
          ) : (
            <>
              <LeadHistoryFeed activities={historyQuery.data?.content ?? []} />
              {historyQuery.data && historyQuery.data.totalElements > historyQuery.data.size && (
                <Pagination page={historyQuery.data} onPageChange={setHistoryPage} />
              )}
            </>
          )}
        </CardContent>
      </Card>

      <LeadFormDialog open={formOpen} onOpenChange={setFormOpen} lead={lead} />

      <ConfirmDialog
        open={archiveOpen}
        onOpenChange={setArchiveOpen}
        title="Archive this lead?"
        description={`${lead.name} will be removed from your normal active lead lists.`}
        confirmLabel="Archive"
        isLoading={isArchiving}
        onConfirm={confirmArchive}
      />
    </div>
  )
}
