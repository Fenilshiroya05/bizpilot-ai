import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { customerKeys, createCustomer, updateCustomer } from '@/features/customers/api'
import {
  customerEditFormSchema,
  type CustomerEditFormValues,
  type CustomerFormValues,
} from '@/features/customers/schemas'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { FormField } from '@/components/forms/FormField'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import type { CustomerResponse } from '@/types/api'

const CREATE_DEFAULTS: CustomerFormValues = {
  name: '',
  company: '',
  email: '',
  phone: '',
  address: '',
  gstin: '',
  notes: '',
}

function toEditDefaults(customer: CustomerResponse): CustomerEditFormValues {
  return {
    name: customer.name,
    company: customer.company ?? '',
    email: customer.email ?? '',
    phone: customer.phone ?? '',
    address: customer.address ?? '',
    gstin: customer.gstin ?? '',
    notes: customer.notes ?? '',
    status: customer.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE',
  }
}

/**
 * Deliberately mounted only while `open` is true (see the parent
 * `CustomerFormDialog`, not Radix's own internal open/close animation
 * timing) so every open is a fresh component instance — fresh `useForm`
 * defaults, fresh `apiError` state — with no reset-on-open effect needed.
 */
function CustomerForm({
  customer,
  onOpenChange,
}: {
  customer?: CustomerResponse
  onOpenChange: (open: boolean) => void
}) {
  const isEdit = !!customer
  const queryClient = useQueryClient()
  const [apiError, setApiError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CustomerEditFormValues>({
    // One schema for both modes (status always defaults to ACTIVE and is
    // simply not rendered/relevant in create mode) — keeps the form's field
    // type consistent regardless of mode.
    resolver: zodResolver(customerEditFormSchema),
    defaultValues: isEdit ? toEditDefaults(customer) : { ...CREATE_DEFAULTS, status: 'ACTIVE' },
  })

  async function onSubmit(values: CustomerEditFormValues) {
    setApiError(null)
    try {
      if (isEdit && customer) {
        await updateCustomer(customer.id, {
          name: values.name,
          company: values.company,
          email: values.email,
          phone: values.phone,
          address: values.address,
          gstin: values.gstin,
          notes: values.notes,
          status: values.status,
        })
        await queryClient.invalidateQueries({ queryKey: customerKeys.detail(customer.id) })
        toast({ title: 'Customer updated', variant: 'success' })
      } else {
        await createCustomer({
          name: values.name,
          company: values.company,
          email: values.email,
          phone: values.phone,
          address: values.address,
          gstin: values.gstin,
          notes: values.notes,
        })
        toast({ title: 'Customer created', variant: 'success' })
      }
      await queryClient.invalidateQueries({ queryKey: ['customers', 'list'] })
      onOpenChange(false)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'DUPLICATE_CUSTOMER') {
        setError('email', { message: error.message })
        return
      }
      setApiError(
        error instanceof ApiError ? error.message : 'Unable to save this customer. Please try again.',
      )
    }
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      {apiError && <Alert variant="destructive">{apiError}</Alert>}

      <FormField label="Name" htmlFor="customer-name" error={errors.name?.message}>
        <Input
          id="customer-name"
          aria-invalid={!!errors.name}
          aria-describedby={errors.name ? 'customer-name-error' : undefined}
          {...register('name')}
        />
      </FormField>

      <div className="grid grid-cols-2 gap-3">
        <FormField label="Company" htmlFor="customer-company" error={errors.company?.message}>
          <Input
            id="customer-company"
            aria-invalid={!!errors.company}
            aria-describedby={errors.company ? 'customer-company-error' : undefined}
            {...register('company')}
          />
        </FormField>
        <FormField label="Phone" htmlFor="customer-phone" error={errors.phone?.message}>
          <Input
            id="customer-phone"
            aria-invalid={!!errors.phone}
            aria-describedby={errors.phone ? 'customer-phone-error' : undefined}
            {...register('phone')}
          />
        </FormField>
      </div>

      <FormField label="Email" htmlFor="customer-email" error={errors.email?.message}>
        <Input
          id="customer-email"
          type="email"
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'customer-email-error' : undefined}
          {...register('email')}
        />
      </FormField>

      <FormField label="Address" htmlFor="customer-address" error={errors.address?.message}>
        <Input
          id="customer-address"
          aria-invalid={!!errors.address}
          aria-describedby={errors.address ? 'customer-address-error' : undefined}
          {...register('address')}
        />
      </FormField>

      <FormField
        label="GSTIN"
        htmlFor="customer-gstin"
        error={errors.gstin?.message}
        hint="15-character GSTIN, if applicable"
      >
        <Input
          id="customer-gstin"
          aria-invalid={!!errors.gstin}
          aria-describedby={errors.gstin ? 'customer-gstin-error' : undefined}
          {...register('gstin')}
        />
      </FormField>

      <FormField label="Notes" htmlFor="customer-notes" error={errors.notes?.message}>
        <Input
          id="customer-notes"
          aria-invalid={!!errors.notes}
          aria-describedby={errors.notes ? 'customer-notes-error' : undefined}
          {...register('notes')}
        />
      </FormField>

      {isEdit && (
        <FormField label="Status" htmlFor="customer-status" error={errors.status?.message}>
          <Select id="customer-status" {...register('status')}>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </Select>
        </FormField>
      )}

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" isLoading={isSubmitting}>
          {isEdit ? 'Save changes' : 'Create customer'}
        </Button>
      </div>
    </form>
  )
}

export function CustomerFormDialog({
  open,
  onOpenChange,
  customer,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Present = edit mode; absent = create mode. */
  customer?: CustomerResponse
}) {
  const isEdit = !!customer

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Edit customer' : 'Create customer'}</DialogTitle>
          <DialogDescription>
            {isEdit ? 'Update this customer’s details.' : 'Add a new customer to your organization.'}
          </DialogDescription>
        </DialogHeader>
        {open && <CustomerForm key={customer?.id ?? 'create'} customer={customer} onOpenChange={onOpenChange} />}
      </DialogContent>
    </Dialog>
  )
}
