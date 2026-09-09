import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { renderWithClient } from '@/test/render'
import { ApiError } from '@/lib/api-client'
import type { DocumentResponse } from '@/types/api'
import { DocumentUploadDialog } from './DocumentUploadDialog'
import * as documentsApi from '@/features/documents/api'

vi.mock('@/features/documents/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/documents/api')>()
  return { ...actual, uploadDocument: vi.fn() }
})

const UPLOADED: DocumentResponse = {
  id: 'd1',
  originalFilename: 'report.pdf',
  contentType: 'application/pdf',
  fileSize: 1024,
  status: 'UPLOADED',
  uploadedByUserId: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function makeFile(name: string, sizeBytes: number, type: string): File {
  const file = new File([new Uint8Array(Math.max(sizeBytes, 1))], name, { type })
  Object.defineProperty(file, 'size', { value: sizeBytes })
  return file
}

describe('DocumentUploadDialog', () => {
  it('requires a file before submitting', async () => {
    renderWithClient(<DocumentUploadDialog open onOpenChange={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Upload' }))
    expect(await screen.findByText('A file is required')).toBeInTheDocument()
    expect(documentsApi.uploadDocument).not.toHaveBeenCalled()
  })

  it('accepts a PDF file', async () => {
    vi.mocked(documentsApi.uploadDocument).mockResolvedValue(UPLOADED)
    const onOpenChange = vi.fn()
    renderWithClient(<DocumentUploadDialog open onOpenChange={onOpenChange} />)

    const file = makeFile('report.pdf', 1024, 'application/pdf')
    await userEvent.upload(screen.getByLabelText('File'), file)
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }))

    await waitFor(() => expect(documentsApi.uploadDocument).toHaveBeenCalledWith(file))
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })

  it('accepts a TXT file', async () => {
    vi.mocked(documentsApi.uploadDocument).mockResolvedValue({ ...UPLOADED, originalFilename: 'notes.txt', contentType: 'text/plain' })
    renderWithClient(<DocumentUploadDialog open onOpenChange={vi.fn()} />)

    const file = makeFile('notes.txt', 100, 'text/plain')
    await userEvent.upload(screen.getByLabelText('File'), file)
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }))

    await waitFor(() => expect(documentsApi.uploadDocument).toHaveBeenCalledWith(file))
  })

  it('accepts a DOCX file', async () => {
    vi.mocked(documentsApi.uploadDocument).mockResolvedValue({
      ...UPLOADED,
      originalFilename: 'contract.docx',
      contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    renderWithClient(<DocumentUploadDialog open onOpenChange={vi.fn()} />)

    const file = makeFile(
      'contract.docx',
      2048,
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    )
    await userEvent.upload(screen.getByLabelText('File'), file)
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }))

    await waitFor(() => expect(documentsApi.uploadDocument).toHaveBeenCalledWith(file))
  })

  it('rejects an unsupported file type', async () => {
    // The `accept` attribute is a browser file-picker hint, not a security
    // control (the real defense is server-side) — user-event's default
    // config silently filters non-matching files at the picker level, so
    // this test disables that filtering to exercise the Zod schema's own
    // extension check instead, exactly as a user pasting/dropping a
    // disguised file would.
    const user = userEvent.setup({ applyAccept: false })
    renderWithClient(<DocumentUploadDialog open onOpenChange={vi.fn()} />)

    const file = makeFile('malware.exe', 100, 'application/octet-stream')
    await user.upload(screen.getByLabelText('File'), file)
    await user.click(screen.getByRole('button', { name: 'Upload' }))

    expect(await screen.findByText('Unsupported file type. Use PDF, TXT, or DOCX.')).toBeInTheDocument()
    expect(documentsApi.uploadDocument).not.toHaveBeenCalled()
  })

  it('rejects a file over 20 MB', async () => {
    renderWithClient(<DocumentUploadDialog open onOpenChange={vi.fn()} />)

    const file = makeFile('large.pdf', 20 * 1024 * 1024 + 1, 'application/pdf')
    await userEvent.upload(screen.getByLabelText('File'), file)
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }))

    expect(await screen.findByText('File exceeds the maximum allowed size of 20 MB')).toBeInTheDocument()
    expect(documentsApi.uploadDocument).not.toHaveBeenCalled()
  })

  it('accepts a file exactly at the 20 MB boundary', async () => {
    vi.mocked(documentsApi.uploadDocument).mockResolvedValue(UPLOADED)
    renderWithClient(<DocumentUploadDialog open onOpenChange={vi.fn()} />)

    const file = makeFile('exact.pdf', 20 * 1024 * 1024, 'application/pdf')
    await userEvent.upload(screen.getByLabelText('File'), file)
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }))

    await waitFor(() => expect(documentsApi.uploadDocument).toHaveBeenCalledWith(file))
  })

  it('shows an uploading state and disables the submit button while in flight', async () => {
    let resolveUpload!: (value: DocumentResponse) => void
    vi.mocked(documentsApi.uploadDocument).mockImplementation(
      () => new Promise((resolve) => { resolveUpload = resolve }),
    )
    renderWithClient(<DocumentUploadDialog open onOpenChange={vi.fn()} />)

    const file = makeFile('report.pdf', 1024, 'application/pdf')
    await userEvent.upload(screen.getByLabelText('File'), file)
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }))

    expect(await screen.findByRole('button', { name: 'Uploading...' })).toBeDisabled()
    resolveUpload(UPLOADED)
  })

  it('prevents a duplicate submission while an upload is already in flight', async () => {
    let callCount = 0
    let resolveUpload!: (value: DocumentResponse) => void
    vi.mocked(documentsApi.uploadDocument).mockImplementation(() => {
      callCount += 1
      return new Promise((resolve) => { resolveUpload = resolve })
    })
    renderWithClient(<DocumentUploadDialog open onOpenChange={vi.fn()} />)

    const file = makeFile('report.pdf', 1024, 'application/pdf')
    await userEvent.upload(screen.getByLabelText('File'), file)
    const button = screen.getByRole('button', { name: 'Upload' })
    await userEvent.click(button)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Uploading...' })).toBeDisabled())
    // The button is disabled once submitting — a second click cannot fire another submit.
    await userEvent.click(screen.getByRole('button', { name: 'Uploading...' }))

    expect(callCount).toBe(1)
    resolveUpload(UPLOADED)
  })

  it('shows an error and keeps the dialog open when the upload fails', async () => {
    vi.mocked(documentsApi.uploadDocument).mockRejectedValue(
      new ApiError({
        timestamp: '2026-01-01T00:00:00Z',
        status: 400,
        code: 'INVALID_DOCUMENT',
        message: 'File content does not match the PDF format',
        path: '/api/v1/documents',
      }),
    )
    const onOpenChange = vi.fn()
    renderWithClient(<DocumentUploadDialog open onOpenChange={onOpenChange} />)

    const file = makeFile('spoofed.pdf', 100, 'application/pdf')
    await userEvent.upload(screen.getByLabelText('File'), file)
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }))

    expect(await screen.findByText('File content does not match the PDF format')).toBeInTheDocument()
    expect(onOpenChange).not.toHaveBeenCalledWith(false)
  })
})
