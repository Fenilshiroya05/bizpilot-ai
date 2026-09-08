import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { createLead, leadKeys, updateLead } from '@/features/leads/api'
import {
  leadEditFormSchema,
  LEAD_PRIORITY_OPTIONS,
  LEAD_SOURCE_OPTIONS,
  LEAD_STATUS_OPTIONS,
  type LeadEditFormValues,
  type LeadFormValues,
} from '@/features/leads/schemas'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { FormField } from '@/components/forms/FormField'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import type { LeadResponse } from '@/types/api'

const CREATE_DEFAULTS: LeadFormValues = {
  name: '',
  company: '',
  email: '',
  phone: '',
  source: 'WEBSITE',
  priority: 'MEDIUM',
  followUpDate: '',
}

function toEditDefaults(lead: LeadResponse): LeadEditFormValues {
  return {
    name: lead.name,
    company: lead.company ?? '',
    email: lead.email ?? '',
    phone: lead.phone ?? '',
    source: lead.source,
    priority: lead.priority,
    followUpDate: lead.followUpDate ?? '',
    status: lead.status,
  }
}

/** Mounted only while the dialog is open — see CustomerForm's identical rationale. */
function LeadForm({ lead, onOpenChange }: { lead?: LeadResponse; onOpenChange: (open: boolean) => void }) {
  const isEdit = !!lead
  const queryClient = useQueryClient()
  const [apiError, setApiError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LeadEditFormValues>({
    resolver: zodResolver(leadEditFormSchema),
    defaultValues: isEdit ? toEditDefaults(lead) : { ...CREATE_DEFAULTS, status: 'NEW' },
  })

  async function onSubmit(values: LeadEditFormValues) {
    setApiError(null)
    try {
      if (isEdit && lead) {
        const clearFollowUpDate = values.followUpDate === '' && lead.followUpDate !== null
        await updateLead(lead.id, {
          name: values.name,
          company: values.company,
          email: values.email,
          phone: values.phone,
          source: values.source,
          priority: values.priority,
          status: values.status,
          followUpDate: values.followUpDate || undefined,
          clearFollowUpDate,
        })
        await queryClient.invalidateQueries({ queryKey: leadKeys.detail(lead.id) })
        toast({ title: 'Lead updated', variant: 'success' })
      } else {
        await createLead({
          name: values.name,
          company: values.company,
          email: values.email,
          phone: values.phone,
          source: values.source,
          priority: values.priority,
          followUpDate: values.followUpDate || undefined,
        })
        toast({ title: 'Lead created', variant: 'success' })
      }
      await queryClient.invalidateQueries({ queryKey: ['leads', 'list'] })
      onOpenChange(false)
    } catch (error) {
      setApiError(error instanceof ApiError ? error.message : 'Unable to save this lead. Please try again.')
    }
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      {apiError && <Alert variant="destructive">{apiError}</Alert>}

      <FormField label="Name" htmlFor="lead-name" error={errors.name?.message}>
        <Input
          id="lead-name"
          aria-invalid={!!errors.name}
          aria-describedby={errors.name ? 'lead-name-error' : undefined}
          {...register('name')}
        />
      </FormField>

      <div className="grid grid-cols-2 gap-3">
        <FormField label="Company" htmlFor="lead-company" error={errors.company?.message}>
          <Input
            id="lead-company"
            aria-invalid={!!errors.company}
            aria-describedby={errors.company ? 'lead-company-error' : undefined}
            {...register('company')}
          />
        </FormField>
        <FormField label="Phone" htmlFor="lead-phone" error={errors.phone?.message}>
          <Input
            id="lead-phone"
            aria-invalid={!!errors.phone}
            aria-describedby={errors.phone ? 'lead-phone-error' : undefined}
            {...register('phone')}
          />
        </FormField>
      </div>

      <FormField label="Email" htmlFor="lead-email" error={errors.email?.message}>
        <Input
          id="lead-email"
          type="email"
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'lead-email-error' : undefined}
          {...register('email')}
        />
      </FormField>

      <div className="grid grid-cols-2 gap-3">
        <FormField label="Source" htmlFor="lead-source" error={errors.source?.message}>
          <Select
            id="lead-source"
            aria-invalid={!!errors.source}
            aria-describedby={errors.source ? 'lead-source-error' : undefined}
            {...register('source')}
          >
            {LEAD_SOURCE_OPTIONS.map((source) => (
              <option key={source} value={source}>
                {source.replace('_', ' ')}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField label="Priority" htmlFor="lead-priority" error={errors.priority?.message}>
          <Select id="lead-priority" {...register('priority')}>
            {LEAD_PRIORITY_OPTIONS.map((priority) => (
              <option key={priority} value={priority}>
                {priority}
              </option>
            ))}
          </Select>
        </FormField>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <FormField label="Follow-up date" htmlFor="lead-followup" hint="Leave empty to clear">
          <Input id="lead-followup" type="date" {...register('followUpDate')} />
        </FormField>
        {isEdit && (
          <FormField label="Status" htmlFor="lead-status" error={errors.status?.message}>
            <Select id="lead-status" {...register('status')}>
              {LEAD_STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>
                  {status.replace('_', ' ')}
                </option>
              ))}
            </Select>
          </FormField>
        )}
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" isLoading={isSubmitting}>
          {isEdit ? 'Save changes' : 'Create lead'}
        </Button>
      </div>
    </form>
  )
}

export function LeadFormDialog({
  open,
  onOpenChange,
  lead,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Present = edit mode; absent = create mode. */
  lead?: LeadResponse
}) {
  const isEdit = !!lead

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Edit lead' : 'Create lead'}</DialogTitle>
          <DialogDescription>
            {isEdit ? 'Update this lead’s details.' : 'Add a new sales lead.'}
          </DialogDescription>
        </DialogHeader>
        {open && <LeadForm key={lead?.id ?? 'create'} lead={lead} onOpenChange={onOpenChange} />}
      </DialogContent>
    </Dialog>
  )
}
