import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { createQuotation, quotationKeys, updateQuotation } from '@/features/quotations/api'
import { CustomerCombobox } from '@/features/quotations/components/CustomerCombobox'
import { LineItemEditor } from '@/features/quotations/components/LineItemEditor'
import { calculatePreviewTotals } from '@/features/quotations/previewTotals'
import { quotationFormSchema, type QuotationFormValues } from '@/features/quotations/schemas'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { FormField } from '@/components/forms/FormField'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import { formatInr } from '@/lib/money'
import type { QuotationResponse } from '@/types/api'

function toDefaults(quotation: QuotationResponse | undefined, customerLabel: string): QuotationFormValues {
  if (!quotation) {
    return { customerId: '', customerLabel: '', validUntil: '', discountPercentage: '', items: [] }
  }
  return {
    customerId: quotation.customerId,
    customerLabel,
    validUntil: quotation.validUntil ?? '',
    discountPercentage: String(quotation.discountPercentage),
    items: quotation.items.map((item) => ({
      productId: item.productId,
      productLabel: item.productNameSnapshot,
      quantity: String(item.quantity),
      unitPrice: item.unitPrice,
      taxPercentage: item.taxPercentage,
    })),
  }
}

/**
 * Shared by QuotationFormPage (create) and QuotationDetailPage's in-place
 * DRAFT edit mode. The caller is responsible for only mounting this once any
 * data it needs up front (the edit-mode customer label) has already loaded —
 * same "mount only when ready" rationale as ProductForm (Phase 22).
 */
export function QuotationForm({
  quotation,
  customerLabel = '',
  onSaved,
  onCancel,
}: {
  quotation?: QuotationResponse
  /** Required for edit mode — the customer's display name, since QuotationResponse only carries customerId. */
  customerLabel?: string
  onSaved: (quotation: QuotationResponse) => void
  onCancel: () => void
}) {
  const isEdit = !!quotation
  const queryClient = useQueryClient()
  const [apiError, setApiError] = useState<string | null>(null)

  const form = useForm<QuotationFormValues>({
    resolver: zodResolver(quotationFormSchema),
    defaultValues: toDefaults(quotation, customerLabel),
  })
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = form

  const items = watch('items')
  const discountPercentage = Number(watch('discountPercentage') || 0)
  const preview = calculatePreviewTotals(
    (items ?? []).map((i) => ({ quantity: Number(i.quantity) || 0, unitPrice: i.unitPrice, taxPercentage: i.taxPercentage })),
    discountPercentage,
  )

  async function onSubmit(values: QuotationFormValues) {
    setApiError(null)
    const payload = {
      customerId: values.customerId,
      validUntil: values.validUntil || undefined,
      discountPercentage: values.discountPercentage || undefined,
      items: values.items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
    }
    try {
      let saved: QuotationResponse
      if (isEdit && quotation) {
        saved = await updateQuotation(quotation.id, { ...payload, clearValidUntil: !values.validUntil })
        toast({ title: 'Quotation updated', variant: 'success' })
      } else {
        saved = await createQuotation(payload)
        toast({ title: 'Quotation created', variant: 'success' })
      }
      await queryClient.invalidateQueries({ queryKey: ['quotations', 'list'] })
      if (isEdit && quotation) {
        await queryClient.invalidateQueries({ queryKey: quotationKeys.detail(quotation.id) })
      }
      onSaved(saved)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'QUOTATION_NOT_EDITABLE') {
        setApiError('Only draft quotations can be edited.')
        return
      }
      setApiError(error instanceof ApiError ? error.message : 'Unable to save this quotation. Please try again.')
    }
  }

  return (
    <form className="grid gap-6 lg:grid-cols-3" onSubmit={handleSubmit(onSubmit)} noValidate>
      <div className="space-y-4 lg:col-span-2">
        {apiError && <Alert variant="destructive">{apiError}</Alert>}

        <Card>
          <CardHeader>
            <CardTitle>Details</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <FormField label="Customer" htmlFor="quotation-customer" error={errors.customerId?.message}>
              <CustomerCombobox
                initialLabel={customerLabel}
                onSelect={(customer) => {
                  setValue('customerId', customer.id, { shouldValidate: true })
                  setValue('customerLabel', customer.name)
                }}
                onClear={() => {
                  setValue('customerId', '')
                  setValue('customerLabel', '')
                }}
              />
            </FormField>

            <div className="grid grid-cols-2 gap-3">
              <FormField label="Valid until" htmlFor="quotation-valid-until" hint="Optional">
                <Input id="quotation-valid-until" type="date" {...register('validUntil')} />
              </FormField>
              <FormField
                label="Discount %"
                htmlFor="quotation-discount"
                error={errors.discountPercentage?.message}
                hint="Defaults to 0 if left empty"
              >
                <Input
                  id="quotation-discount"
                  type="text"
                  inputMode="decimal"
                  aria-invalid={!!errors.discountPercentage}
                  aria-describedby={errors.discountPercentage ? 'quotation-discount-error' : undefined}
                  {...register('discountPercentage')}
                />
              </FormField>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Line items</CardTitle>
          </CardHeader>
          <CardContent>
            <LineItemEditor form={form} />
          </CardContent>
        </Card>
      </div>

      <div className="lg:col-span-1">
        <Card className="lg:sticky lg:top-4">
          <CardHeader>
            <CardTitle>Estimated total</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <p className="text-xs text-muted-foreground">
              This is a local estimate for your reference only. The final amounts are always calculated by the
              server.
            </p>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Subtotal</span>
              <span className="tabular-nums">{formatInr(preview.subtotal)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Discount</span>
              <span className="tabular-nums">{formatInr(preview.discountAmount)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Tax</span>
              <span className="tabular-nums">{formatInr(preview.taxAmount)}</span>
            </div>
            <div className="flex justify-between border-t border-border pt-2 font-medium text-foreground">
              <span>Estimated total</span>
              <span className="tabular-nums">{formatInr(preview.grandTotal)}</span>
            </div>

            <div className="flex flex-col gap-2 pt-4">
              <Button type="submit" isLoading={isSubmitting}>
                {isEdit ? 'Save changes' : 'Create quotation'}
              </Button>
              <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
                Cancel
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </form>
  )
}
