import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { uploadDocument } from '@/features/documents/api'
import { documentUploadSchema, type DocumentUploadFormValues } from '@/features/documents/schemas'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { FormField } from '@/components/forms/FormField'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import { formatBytes } from '@/lib/fileSize'

/**
 * Mounted only while the dialog is open — same pattern as
 * TaskFormDialog/ProductFormDialog. The backend accepts only a single
 * `file` multipart part (no title/description/tags), so this form has
 * exactly one field.
 */
function UploadForm({ onOpenChange }: { onOpenChange: (open: boolean) => void }) {
  const queryClient = useQueryClient()
  const [apiError, setApiError] = useState<string | null>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

  const {
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<DocumentUploadFormValues>({
    resolver: zodResolver(documentUploadSchema),
  })

  function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null
    setSelectedFile(file)
    setValue('file', file as unknown as File, { shouldValidate: true })
  }

  async function onSubmit(values: DocumentUploadFormValues) {
    setApiError(null)
    try {
      const created = await uploadDocument(values.file)
      await queryClient.invalidateQueries({ queryKey: ['documents', 'list'] })
      toast({
        title: 'Document uploaded',
        description: `${created.originalFilename} — status: ${created.status}`,
        variant: 'success',
      })
      onOpenChange(false)
    } catch (error) {
      setApiError(error instanceof ApiError ? error.message : 'Unable to upload this document. Please try again.')
    }
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      {apiError && <Alert variant="destructive">{apiError}</Alert>}

      <FormField label="File" htmlFor="document-file" error={errors.file?.message} hint="PDF, TXT, or DOCX — up to 20 MB">
        <Input
          id="document-file"
          type="file"
          accept=".pdf,.txt,.docx"
          aria-invalid={!!errors.file}
          aria-describedby={errors.file ? 'document-file-error' : undefined}
          onChange={handleFileChange}
          disabled={isSubmitting}
          className="cursor-pointer file:mr-3 file:cursor-pointer file:rounded-md file:border-0 file:bg-muted file:px-3 file:py-1 file:text-sm file:font-medium file:text-foreground"
        />
      </FormField>

      {selectedFile && !errors.file && (
        <p className="text-xs text-muted-foreground">{formatBytes(selectedFile.size)}</p>
      )}

      <div aria-live="polite" className="sr-only">
        {isSubmitting ? 'Uploading document…' : ''}
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" isLoading={isSubmitting}>
          {isSubmitting ? 'Uploading...' : 'Upload'}
        </Button>
      </div>
    </form>
  )
}

export function DocumentUploadDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Upload document</DialogTitle>
          <DialogDescription>Add a PDF, TXT, or DOCX file to your organization&rsquo;s documents.</DialogDescription>
        </DialogHeader>
        {open && <UploadForm key="upload" onOpenChange={onOpenChange} />}
      </DialogContent>
    </Dialog>
  )
}
