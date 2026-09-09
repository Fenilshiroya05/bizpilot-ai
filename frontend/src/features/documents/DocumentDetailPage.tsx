import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { useAuth } from '@/app/providers/AuthProvider'
import { usePermission } from '@/hooks/usePermission'
import { deleteDocument, documentKeys, downloadDocument, getDocument } from '@/features/documents/api'
import { contentTypeLabel } from '@/features/documents/schemas'
import { triggerBrowserDownload } from '@/features/documents/download'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/components/data-display/StatusBadge'
import { ConfirmDialog } from '@/components/feedback/ConfirmDialog'
import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import { formatDateTime } from '@/lib/date'
import { formatBytes } from '@/lib/fileSize'
import type { DocumentResponse } from '@/types/api'

// Same capped-polling rationale as DocumentsListPage — no status endpoint
// exists, so this re-runs the existing GET on an interval while the
// document is non-terminal, and stops permanently once it's terminal or
// the cap elapses (bizpilot.ai.enabled=false locally means a document can
// legitimately stay UPLOADED forever).
const POLL_INTERVAL_MS = 3000
const POLL_MAX_DURATION_MS = 30000

function OverviewField({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="text-sm text-foreground">{value ?? '—'}</div>
    </div>
  )
}

/** No user-directory API exists (same limitation as Task/Lead assignment) — never fabricate a name for another user. */
function uploadedByLabel(doc: DocumentResponse, currentUserId: string | undefined): string {
  if (!doc.uploadedByUserId) return 'Unknown'
  if (doc.uploadedByUserId === currentUserId) return 'You'
  return 'A teammate'
}

export function DocumentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const canDelete = usePermission('DOCUMENT_DELETE')
  const pollStartRef = useRef<number | null>(null)

  const [deleteOpen, setDeleteOpen] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [isDownloading, setIsDownloading] = useState(false)

  const detailQuery = useQuery({
    queryKey: documentKeys.detail(id ?? ''),
    queryFn: () => getDocument(id ?? ''),
    enabled: !!id,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      const isPending = status === 'UPLOADED' || status === 'PROCESSING'
      if (!isPending) {
        pollStartRef.current = null
        return false
      }
      if (pollStartRef.current === null) pollStartRef.current = Date.now()
      if (Date.now() - pollStartRef.current > POLL_MAX_DURATION_MS) return false
      return POLL_INTERVAL_MS
    },
  })
  const doc = detailQuery.data

  async function handleDownload() {
    if (!doc) return
    setIsDownloading(true)
    try {
      const blob = await downloadDocument(doc.id)
      triggerBrowserDownload(blob, doc.originalFilename)
    } catch (error) {
      toast({
        title: 'Could not download document',
        description: error instanceof ApiError ? error.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsDownloading(false)
    }
  }

  async function confirmDelete() {
    if (!id) return
    setIsDeleting(true)
    try {
      await deleteDocument(id)
      await queryClient.invalidateQueries({ queryKey: ['documents', 'list'] })
      queryClient.removeQueries({ queryKey: documentKeys.detail(id) })
      toast({ title: 'Document deleted', variant: 'success' })
      navigate('/documents')
    } catch (error) {
      toast({
        title: 'Could not delete document',
        description: error instanceof ApiError ? error.message : 'Please try again.',
        variant: 'destructive',
      })
      setIsDeleting(false)
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

  if (detailQuery.isError || !doc) {
    return (
      <ErrorState title="We couldn't load this document" error={detailQuery.error} onRetry={() => detailQuery.refetch()} />
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={doc.originalFilename}
        breadcrumb={[{ label: 'Documents', to: '/documents' }, { label: doc.originalFilename }]}
        actions={
          <div className="flex items-center gap-2">
            <div aria-live="polite">
              <StatusBadge status={doc.status} />
            </div>
            <Button variant="outline" size="sm" onClick={handleDownload} isLoading={isDownloading}>
              Download
            </Button>
            {canDelete && (
              <Button variant="destructive" size="sm" onClick={() => setDeleteOpen(true)}>
                Delete
              </Button>
            )}
          </div>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>Overview</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <OverviewField label="Content type" value={contentTypeLabel(doc.contentType)} />
          <OverviewField label="Size" value={formatBytes(doc.fileSize)} />
          <OverviewField label="Uploaded by" value={uploadedByLabel(doc, user?.id)} />
          <OverviewField label="Uploaded" value={formatDateTime(doc.createdAt)} />
          <OverviewField label="Last updated" value={formatDateTime(doc.updatedAt)} />
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete this document?"
        description={`${doc.originalFilename} will be permanently deleted.`}
        confirmLabel="Delete document"
        isLoading={isDeleting}
        onConfirm={confirmDelete}
      />
    </div>
  )
}
