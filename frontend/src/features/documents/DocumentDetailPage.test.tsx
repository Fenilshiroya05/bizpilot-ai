import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/app/providers/AuthProvider'
import { getMe } from '@/features/auth/api'
import { getCurrentOrganization } from '@/features/settings/api'
import { clearTokens, setTokens } from '@/lib/api-client'
import { createTestQueryClient } from '@/test/render'
import type { DocumentResponse, DocumentStatus } from '@/types/api'
import { DocumentDetailPage } from './DocumentDetailPage'
import * as documentsApi from './api'

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>()
  return { ...actual, getDocument: vi.fn(), deleteDocument: vi.fn(), downloadDocument: vi.fn() }
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
  vi.mocked(getCurrentOrganization).mockResolvedValue({ id: 'org-1', name: 'Acme', createdAt: '2026-01-01T00:00:00Z' })
}

function baseDoc(overrides: Partial<DocumentResponse> = {}): DocumentResponse {
  return {
    id: 'd1',
    originalFilename: 'report.pdf',
    contentType: 'application/pdf',
    fileSize: 2048,
    status: 'COMPLETED',
    uploadedByUserId: 'u1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function renderPage() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/documents/d1']}>
        <AuthProvider>
          <Routes>
            <Route path="/documents/:id" element={<DocumentDetailPage />} />
            <Route path="/documents" element={<div>Documents list</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('DocumentDetailPage', () => {
  beforeEach(() => clearTokens())
  afterEach(() => clearTokens())

  it('renders the document metadata', async () => {
    vi.mocked(documentsApi.getDocument).mockResolvedValue(baseDoc())
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    expect(await screen.findByRole('heading', { name: 'report.pdf' })).toBeInTheDocument()
    expect(screen.getByText('PDF')).toBeInTheDocument()
    expect(screen.getByText('2.0 KB')).toBeInTheDocument()
    expect(screen.getByText('You')).toBeInTheDocument()
  })

  it.each<DocumentStatus>(['UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED'])(
    'renders the %s status',
    async (status) => {
      vi.mocked(documentsApi.getDocument).mockResolvedValue(baseDoc({ status }))
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await screen.findByRole('heading', { name: 'report.pdf' })
      expect(screen.getAllByText(status).length).toBeGreaterThan(0)
    },
  )

  it('shows a teammate label (never a fabricated name) for a document uploaded by someone else', async () => {
    vi.mocked(documentsApi.getDocument).mockResolvedValue(baseDoc({ uploadedByUserId: 'someone-else' }))
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await screen.findByRole('heading', { name: 'report.pdf' })
    expect(screen.getByText('A teammate')).toBeInTheDocument()
  })

  it('downloads the document when Download is clicked', async () => {
    vi.mocked(documentsApi.getDocument).mockResolvedValue(baseDoc())
    vi.mocked(documentsApi.downloadDocument).mockResolvedValue(new Blob(['x']))
    mockUser(['OWNER'])
    setTokens({ accessToken: 't', refreshToken: 'r' })
    renderPage()

    await screen.findByRole('heading', { name: 'report.pdf' })
    await userEvent.click(screen.getByRole('button', { name: 'Download' }))
    await waitFor(() => expect(documentsApi.downloadDocument).toHaveBeenCalledWith('d1'))
  })

  describe('delete visibility and confirmation', () => {
    it('OWNER sees Delete', async () => {
      vi.mocked(documentsApi.getDocument).mockResolvedValue(baseDoc())
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await screen.findByRole('heading', { name: 'report.pdf' })
      expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
    })

    it('SALES does not see Delete', async () => {
      vi.mocked(documentsApi.getDocument).mockResolvedValue(baseDoc())
      mockUser(['SALES'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await screen.findByRole('heading', { name: 'report.pdf' })
      expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    })

    it('EMPLOYEE does not see Delete but Download still works', async () => {
      vi.mocked(documentsApi.getDocument).mockResolvedValue(baseDoc())
      vi.mocked(documentsApi.downloadDocument).mockResolvedValue(new Blob(['x']))
      mockUser(['EMPLOYEE'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await screen.findByRole('heading', { name: 'report.pdf' })
      expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
      await userEvent.click(screen.getByRole('button', { name: 'Download' }))
      await waitFor(() => expect(documentsApi.downloadDocument).toHaveBeenCalledWith('d1'))
    })

    it('requires confirmation before deleting, then navigates back to the list', async () => {
      vi.mocked(documentsApi.getDocument).mockResolvedValue(baseDoc())
      vi.mocked(documentsApi.deleteDocument).mockResolvedValue(undefined)
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      renderPage()

      await screen.findByRole('heading', { name: 'report.pdf' })
      await userEvent.click(screen.getByRole('button', { name: 'Delete' }))
      expect(screen.getByRole('heading', { name: 'Delete this document?' })).toBeInTheDocument()

      const dialogConfirm = screen.getAllByRole('button', { name: 'Delete document' })
      await userEvent.click(dialogConfirm[dialogConfirm.length - 1]!)
      await waitFor(() => expect(documentsApi.deleteDocument).toHaveBeenCalledWith('d1'))
      await waitFor(() => expect(screen.getByText('Documents list')).toBeInTheDocument())
    })
  })

  describe('processing status polling (capped, bounded — no status endpoint exists)', () => {
    beforeEach(() => {
      mockUser(['OWNER'])
      setTokens({ accessToken: 't', refreshToken: 'r' })
      vi.useFakeTimers()
    })
    afterEach(() => {
      vi.useRealTimers()
    })

    it('polls while PROCESSING and stops once COMPLETED', async () => {
      vi.mocked(documentsApi.getDocument)
        .mockResolvedValueOnce(baseDoc({ status: 'PROCESSING' }))
        .mockResolvedValueOnce(baseDoc({ status: 'PROCESSING' }))
        .mockResolvedValue(baseDoc({ status: 'COMPLETED' }))
      renderPage()

      await vi.waitFor(() => expect(documentsApi.getDocument).toHaveBeenCalledTimes(1))
      await vi.advanceTimersByTimeAsync(3000)
      await vi.waitFor(() => expect(documentsApi.getDocument).toHaveBeenCalledTimes(2))
      await vi.advanceTimersByTimeAsync(3000)
      await vi.waitFor(() => expect(documentsApi.getDocument).toHaveBeenCalledTimes(3))

      const callsAtCompletion = vi.mocked(documentsApi.getDocument).mock.calls.length
      await vi.advanceTimersByTimeAsync(15000)
      expect(documentsApi.getDocument).toHaveBeenCalledTimes(callsAtCompletion)
    })

    it('stops polling once FAILED', async () => {
      vi.mocked(documentsApi.getDocument)
        .mockResolvedValueOnce(baseDoc({ status: 'PROCESSING' }))
        .mockResolvedValue(baseDoc({ status: 'FAILED' }))
      renderPage()

      await vi.waitFor(() => expect(documentsApi.getDocument).toHaveBeenCalledTimes(1))
      await vi.advanceTimersByTimeAsync(3000)
      await vi.waitFor(() => expect(documentsApi.getDocument).toHaveBeenCalledTimes(2))

      const callsAfterFailed = vi.mocked(documentsApi.getDocument).mock.calls.length
      await vi.advanceTimersByTimeAsync(15000)
      expect(documentsApi.getDocument).toHaveBeenCalledTimes(callsAfterFailed)
    })

    it('stops polling after the capped duration even if still UPLOADED (bizpilot.ai.enabled=false locally)', async () => {
      vi.mocked(documentsApi.getDocument).mockResolvedValue(baseDoc({ status: 'UPLOADED' }))
      renderPage()

      await vi.waitFor(() => expect(documentsApi.getDocument).toHaveBeenCalledTimes(1))
      // Advance well past the 30s cap — polling must not continue indefinitely.
      await vi.advanceTimersByTimeAsync(60000)
      const callsAtCap = vi.mocked(documentsApi.getDocument).mock.calls.length
      expect(callsAtCap).toBeGreaterThan(1)
      expect(callsAtCap).toBeLessThan(15)

      await vi.advanceTimersByTimeAsync(60000)
      expect(documentsApi.getDocument).toHaveBeenCalledTimes(callsAtCap)
    })
  })
})
