import { useQuery, useQueryClient } from '@tanstack/react-query'
import { FileText, MoreHorizontal, Plus, Search } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { usePermission } from '@/hooks/usePermission'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { deleteDocument, documentKeys, downloadDocument, searchDocuments } from '@/features/documents/api'
import { DocumentUploadDialog } from '@/features/documents/components/DocumentUploadDialog'
import { contentTypeLabel, DOCUMENT_CONTENT_TYPE_OPTIONS, DOCUMENT_STATUS_OPTIONS } from '@/features/documents/schemas'
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
import { formatDateTime } from '@/lib/date'
import { formatBytes } from '@/lib/fileSize'
import { triggerBrowserDownload } from '@/features/documents/download'
import type { DocumentResponse, DocumentStatus } from '@/types/api'

// A capped, non-infinite poll: the backend has no status/polling endpoint,
// so this simply re-runs the existing GET on an interval while any visible
// row is non-terminal (UPLOADED/PROCESSING), and stops — for good — once
// every row is terminal OR the cap elapses. This matters because
// `bizpilot.ai.enabled=false` (the default/local config) means a document
// can legitimately stay UPLOADED forever; without a cap this would poll
// indefinitely.
const POLL_INTERVAL_MS = 3000
const POLL_MAX_DURATION_MS = 30000

function useDocumentListParams() {
  const [searchParams, setSearchParams] = useSearchParams()

  const q = searchParams.get('q') ?? ''
  const status = (searchParams.get('status') as DocumentStatus | null) ?? undefined
  const contentType = searchParams.get('contentType') ?? undefined
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

  return {
    q,
    status,
    contentType,
    page,
    setSearch: (value: string) => setParams({ q: value || undefined }),
    setStatus: (value: string) => setParams({ status: value || undefined }),
    setContentType: (value: string) => setParams({ contentType: value || undefined }),
    // A single combined update — never two sequential setSearchParams calls
    // (that pattern silently loses the first update; see Phase 23's Tasks
    // "Clear filters" bug for the exact failure mode).
    clearFilters: () => setParams({ status: undefined, contentType: undefined }),
    setPage: (value: number) => setParams({ page: String(value) }, false),
    clearAll: () => setSearchParams({}),
  }
}

export function DocumentsListPage() {
  const params = useDocumentListParams()
  const [searchInput, setSearchInput] = useState(params.q)
  const debouncedSearch = useDebouncedValue(searchInput, 300)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const pollStartRef = useRef<number | null>(null)

  const canUpload = usePermission('DOCUMENT_UPLOAD')
  const canDelete = usePermission('DOCUMENT_DELETE')

  useEffect(() => {
    if (debouncedSearch !== params.q) params.setSearch(debouncedSearch)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const queryParams = {
    q: params.q || undefined,
    status: params.status,
    contentType: params.contentType,
    page: params.page,
    size: 20,
  }
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: documentKeys.list(queryParams),
    queryFn: () => searchDocuments(queryParams),
    refetchInterval: (query) => {
      const page = query.state.data
      const hasPending = page?.content.some((d) => d.status === 'UPLOADED' || d.status === 'PROCESSING') ?? false
      if (!hasPending) {
        pollStartRef.current = null
        return false
      }
      if (pollStartRef.current === null) pollStartRef.current = Date.now()
      if (Date.now() - pollStartRef.current > POLL_MAX_DURATION_MS) return false
      return POLL_INTERVAL_MS
    },
  })

  const [uploadOpen, setUploadOpen] = useState(false)
  const [deleting, setDeleting] = useState<DocumentResponse | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  async function handleDownload(doc: DocumentResponse) {
    setDownloadingId(doc.id)
    try {
      const blob = await downloadDocument(doc.id)
      triggerBrowserDownload(blob, doc.originalFilename)
    } catch (err) {
      toast({
        title: 'Could not download document',
        description: err instanceof ApiError ? err.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setDownloadingId(null)
    }
  }

  async function confirmDelete() {
    if (!deleting) return
    setIsDeleting(true)
    try {
      await deleteDocument(deleting.id)
      await queryClient.invalidateQueries({ queryKey: ['documents', 'list'] })
      await queryClient.invalidateQueries({ queryKey: documentKeys.detail(deleting.id) })
      toast({ title: 'Document deleted', variant: 'success' })
      setDeleting(null)
    } catch (err) {
      toast({
        title: 'Could not delete document',
        description: err instanceof ApiError ? err.message : 'Please try again.',
        variant: 'destructive',
      })
    } finally {
      setIsDeleting(false)
    }
  }

  const columns: DataTableColumn<DocumentResponse>[] = [
    {
      key: 'originalFilename',
      header: 'Filename',
      cell: (doc) => (
        <Link to={`/documents/${doc.id}`} className="font-medium text-foreground hover:underline">
          {doc.originalFilename}
        </Link>
      ),
    },
    { key: 'contentType', header: 'Content Type', cell: (doc) => contentTypeLabel(doc.contentType) },
    { key: 'fileSize', header: 'Size', cell: (doc) => formatBytes(doc.fileSize) },
    { key: 'status', header: 'Status', cell: (doc) => <StatusBadge status={doc.status} /> },
    { key: 'createdAt', header: 'Uploaded', cell: (doc) => formatDateTime(doc.createdAt) },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      cell: (doc) => (
        <DropdownMenu>
          <DropdownMenuTrigger
            className="rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={`Actions for ${doc.originalFilename}`}
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
            <DropdownMenuItem onSelect={() => navigate(`/documents/${doc.id}`)}>View details</DropdownMenuItem>
            <DropdownMenuItem onSelect={() => handleDownload(doc)} disabled={downloadingId === doc.id}>
              Download
            </DropdownMenuItem>
            {canDelete && (
              <DropdownMenuItem onSelect={() => setDeleting(doc)} className="text-destructive focus:bg-destructive/10">
                Delete
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ]

  const activeFilterCount = [params.status, params.contentType].filter(Boolean).length
  const hasActiveFilters = Boolean(params.q || activeFilterCount)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Documents"
        description="Upload and manage files for your organization."
        breadcrumb={[{ label: 'Documents' }]}
        actions={
          canUpload ? (
            <Button onClick={() => setUploadOpen(true)}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              Upload document
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
            placeholder="Search documents..."
            aria-label="Search documents"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
        </div>

        <FilterBar activeCount={activeFilterCount} onClear={params.clearFilters}>
          <Select aria-label="Status filter" value={params.status ?? ''} onChange={(e) => params.setStatus(e.target.value)}>
            <option value="">All statuses</option>
            {DOCUMENT_STATUS_OPTIONS.map((status) => (
              <option key={status} value={status}>
                {status.replace(/_/g, ' ')}
              </option>
            ))}
          </Select>
          <Select
            aria-label="Content type filter"
            value={params.contentType ?? ''}
            onChange={(e) => params.setContentType(e.target.value)}
          >
            <option value="">All types</option>
            {DOCUMENT_CONTENT_TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </FilterBar>
      </div>

      {isError ? (
        <ErrorState title="We couldn't load your documents" error={error} onRetry={() => refetch()} />
      ) : !isLoading && data?.content.length === 0 ? (
        <EmptyState
          icon={FileText}
          title={hasActiveFilters ? 'No documents found' : 'No documents yet'}
          description={hasActiveFilters ? 'Try changing your search or filters.' : 'Upload your first document to get started.'}
          action={
            hasActiveFilters ? (
              <Button variant="outline" size="sm" onClick={params.clearAll}>
                Clear filters
              </Button>
            ) : canUpload ? (
              <Button size="sm" onClick={() => setUploadOpen(true)}>
                Upload your first document
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="hidden lg:block">
            <DataTable
              caption="Documents"
              columns={columns}
              rows={data?.content ?? []}
              rowKey={(doc) => doc.id}
              onRowClick={(doc) => navigate(`/documents/${doc.id}`)}
              isLoading={isLoading}
            />
          </div>

          <div className="flex flex-col gap-2 lg:hidden">
            {isLoading
              ? Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="h-28 animate-pulse rounded-lg border border-border bg-muted" />
                ))
              : data?.content.map((doc) => (
                  <Link
                    key={doc.id}
                    to={`/documents/${doc.id}`}
                    className="flex flex-col gap-1.5 rounded-lg border border-border bg-background p-4 hover:bg-muted/40"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="truncate font-medium text-foreground">{doc.originalFilename}</span>
                      <StatusBadge status={doc.status} />
                    </div>
                    <div className="flex items-center justify-between text-xs text-muted-foreground">
                      <span>{contentTypeLabel(doc.contentType)}</span>
                      <span>{formatBytes(doc.fileSize)}</span>
                      <span>{formatDateTime(doc.createdAt)}</span>
                    </div>
                  </Link>
                ))}
          </div>

          {data && <Pagination page={data} onPageChange={params.setPage} />}
        </>
      )}

      <DocumentUploadDialog open={uploadOpen} onOpenChange={setUploadOpen} />

      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete this document?"
        description={`${deleting?.originalFilename ?? 'This document'} will be permanently deleted.`}
        confirmLabel="Delete document"
        isLoading={isDeleting}
        onConfirm={confirmDelete}
      />
    </div>
  )
}
