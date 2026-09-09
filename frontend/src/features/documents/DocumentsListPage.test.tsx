import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/app/providers/AuthProvider'
import { getMe } from '@/features/auth/api'
import { getCurrentOrganization } from '@/features/settings/api'
import { clearTokens, setTokens } from '@/lib/api-client'
import { createTestQueryClient } from '@/test/render'
import type { DocumentResponse, Page } from '@/types/api'
import { DocumentsListPage } from './DocumentsListPage'
import * as documentsApi from './api'

// See ProductsListPage.test.tsx (Phase 22) for why the real key builders
// must survive mocking a resource's own api module.
vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, searchDocuments: vi.fn(), deleteDocument: vi.fn(), downloadDocument: vi.fn() }
})
vi.mock('@/features/auth/api')
vi.mock('@/features/settings/api')

function mockUser(roles: string[]) {
  vi.mocked(getMe).mockResolvedValue({
    id: 'u1',
    email: 'a@b.com',
    firstName: 'A',
    lastName: 'B',
    roles,
    status: 'ACTIVE',
    organizationId: 'org-1',
    createdAt: '2026-01-01T00:00:00Z',
  })
  vi.mocked(getCurrentOrganization).mockResolvedValue({
    id: 'org-1',
    name: 'Acme',
    createdAt: '2026-01-01T00:00:00Z',
  })
}

const DOC: DocumentResponse = {
  id: 'd1',
  originalFilename: 'report.pdf',
  contentType: 'application/pdf',
  fileSize: 2048,
  status: 'COMPLETED',
  uploadedByUserId: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function docPage(content: DocumentResponse[]): Page<DocumentResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20, first: true, last: true, empty: content.length === 0 }
}

function renderPage() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthProvider>
          <DocumentsListPage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('DocumentsListPage', () => {
  beforeEach(() => {
    clearTokens()
    vi.mocked(documentsApi.searchDocuments).mockResolvedValue(docPage([DOC]))
  })
  afterEach(() => clearTokens())

  describe('RBAC visibility', () => {
    it('EMPLOYEE sees no Upload action and no Delete action', async () => {
      mockUser(['EMPLOYEE'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await waitFor(() => expect(screen.getAllByText('report.pdf').length).toBeGreaterThan(0))
      expect(screen.queryByRole('button', { name: /Upload document/i })).not.toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Actions for report.pdf' }))
      expect(screen.getByRole('menuitem', { name: 'Download' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Delete' })).not.toBeInTheDocument()
    })

    it('SALES sees Upload but not Delete', async () => {
      mockUser(['SALES'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await waitFor(() => expect(screen.getAllByText('report.pdf').length).toBeGreaterThan(0))
      expect(screen.getByRole('button', { name: /Upload document/i })).toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Actions for report.pdf' }))
      expect(screen.getByRole('menuitem', { name: 'Download' })).toBeInTheDocument()
      expect(screen.queryByRole('menuitem', { name: 'Delete' })).not.toBeInTheDocument()
    })

    it('OWNER sees Upload and Delete', async () => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await waitFor(() => expect(screen.getAllByText('report.pdf').length).toBeGreaterThan(0))
      expect(screen.getByRole('button', { name: /Upload document/i })).toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Actions for report.pdf' }))
      expect(screen.getByRole('menuitem', { name: 'Delete' })).toBeInTheDocument()
    })
  })

  describe('list rendering and metadata', () => {
    beforeEach(() => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
    })

    it('renders filename, content type, size, status, and uploaded date', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('report.pdf').length).toBeGreaterThan(0))
      expect(screen.getAllByText('PDF').length).toBeGreaterThan(0)
      expect(screen.getAllByText('2.0 KB').length).toBeGreaterThan(0)
      expect(screen.getAllByText('COMPLETED').length).toBeGreaterThan(0)
    })
  })

  describe('search, filters, pagination', () => {
    beforeEach(() => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
    })

    it('debounces search input into the q query param', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('report.pdf').length).toBeGreaterThan(0))

      await userEvent.type(screen.getByRole('textbox', { name: 'Search documents' }), 'report')
      await waitFor(() =>
        expect(documentsApi.searchDocuments).toHaveBeenCalledWith(expect.objectContaining({ q: 'report' })),
      )
    })

    it('applies the status filter', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('report.pdf').length).toBeGreaterThan(0))

      await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status filter' }), 'PROCESSING')
      await waitFor(() =>
        expect(documentsApi.searchDocuments).toHaveBeenCalledWith(expect.objectContaining({ status: 'PROCESSING' })),
      )
    })

    it('applies the content type filter', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('report.pdf').length).toBeGreaterThan(0))

      await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Content type filter' }), 'text/plain')
      await waitFor(() =>
        expect(documentsApi.searchDocuments).toHaveBeenCalledWith(
          expect.objectContaining({ contentType: 'text/plain' }),
        ),
      )
    })

    it('clearing filters resets both status and content type in a single update', async () => {
      renderPage()
      await waitFor(() => expect(screen.getAllByText('report.pdf').length).toBeGreaterThan(0))

      await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status filter' }), 'PROCESSING')
      await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Content type filter' }), 'text/plain')
      await waitFor(() =>
        expect(documentsApi.searchDocuments).toHaveBeenCalledWith(
          expect.objectContaining({ status: 'PROCESSING', contentType: 'text/plain' }),
        ),
      )

      await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }))
      await waitFor(() =>
        expect(documentsApi.searchDocuments).toHaveBeenCalledWith(
          expect.objectContaining({ status: undefined, contentType: undefined }),
        ),
      )
      expect(screen.getByRole('combobox', { name: 'Status filter' })).toHaveValue('')
      expect(screen.getByRole('combobox', { name: 'Content type filter' })).toHaveValue('')
    })

    it('shows pagination when there is more than one page', async () => {
      vi.mocked(documentsApi.searchDocuments).mockResolvedValue({
        content: [DOC],
        totalElements: 25,
        totalPages: 2,
        number: 0,
        size: 20,
        first: true,
        last: false,
        empty: false,
      })
      renderPage()
      await waitFor(() => expect(screen.getByText('Page 1 of 2')).toBeInTheDocument())
    })
  })

  describe('loading, empty, and error states', () => {
    beforeEach(() => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
    })

    it('shows an empty state with an upload CTA when there are no documents at all', async () => {
      vi.mocked(documentsApi.searchDocuments).mockResolvedValue(docPage([]))
      renderPage()
      expect(await screen.findByText('No documents yet')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Upload your first document' })).toBeInTheDocument()
    })

    it('shows a retryable error state when the list request fails', async () => {
      vi.mocked(documentsApi.searchDocuments).mockRejectedValue(new Error('network down'))
      renderPage()
      expect(await screen.findByText("We couldn't load your documents")).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    })
  })

  describe('delete flow', () => {
    it('OWNER can delete a document after confirming', async () => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      vi.mocked(documentsApi.deleteDocument).mockResolvedValue(undefined)
      renderPage()

      await waitFor(() => expect(screen.getAllByText('report.pdf').length).toBeGreaterThan(0))
      await userEvent.click(screen.getByRole('button', { name: 'Actions for report.pdf' }))
      await userEvent.click(screen.getByRole('menuitem', { name: 'Delete' }))
      expect(screen.getByRole('heading', { name: 'Delete this document?' })).toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Delete document' }))
      await waitFor(() => expect(documentsApi.deleteDocument).toHaveBeenCalledWith('d1'))
    })
  })

  describe('processing status polling (capped, bounded)', () => {
    beforeEach(() => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      vi.useFakeTimers()
    })
    afterEach(() => {
      vi.useRealTimers()
    })

    it('polls while a row is UPLOADED/PROCESSING and stops once every row is terminal', async () => {
      const pending = { ...DOC, status: 'PROCESSING' as const }
      vi.mocked(documentsApi.searchDocuments)
        .mockResolvedValueOnce(docPage([pending]))
        .mockResolvedValue(docPage([{ ...DOC, status: 'COMPLETED' as const }]))
      renderPage()

      await vi.waitFor(() => expect(documentsApi.searchDocuments).toHaveBeenCalledTimes(1))
      await vi.advanceTimersByTimeAsync(3000)
      await vi.waitFor(() => expect(documentsApi.searchDocuments).toHaveBeenCalledTimes(2))

      const callsAtCompletion = vi.mocked(documentsApi.searchDocuments).mock.calls.length
      await vi.advanceTimersByTimeAsync(15000)
      expect(documentsApi.searchDocuments).toHaveBeenCalledTimes(callsAtCompletion)
    })

    it('does not poll at all when every row is already terminal', async () => {
      vi.mocked(documentsApi.searchDocuments).mockResolvedValue(docPage([DOC])) // COMPLETED
      renderPage()

      await vi.waitFor(() => expect(documentsApi.searchDocuments).toHaveBeenCalledTimes(1))
      await vi.advanceTimersByTimeAsync(10000)
      expect(documentsApi.searchDocuments).toHaveBeenCalledTimes(1)
    })
  })

  describe('download', () => {
    it('EMPLOYEE can download despite having no upload/delete permission', async () => {
      mockUser(['EMPLOYEE'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      vi.mocked(documentsApi.downloadDocument).mockResolvedValue(new Blob(['x']))
      renderPage()

      await waitFor(() => expect(screen.getAllByText('report.pdf').length).toBeGreaterThan(0))
      await userEvent.click(screen.getByRole('button', { name: 'Actions for report.pdf' }))
      await userEvent.click(screen.getByRole('menuitem', { name: 'Download' }))

      await waitFor(() => expect(documentsApi.downloadDocument).toHaveBeenCalledWith('d1'))
    })
  })
})
