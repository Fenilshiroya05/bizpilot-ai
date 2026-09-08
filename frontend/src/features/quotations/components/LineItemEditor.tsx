import { useFieldArray, type UseFormReturn } from 'react-hook-form'
import { Trash2 } from 'lucide-react'

import { ProductCombobox } from '@/features/quotations/components/ProductCombobox'
import type { QuotationFormValues } from '@/features/quotations/schemas'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { formatInr } from '@/lib/money'

/**
 * Quotation-specific — deliberately not a generic "repeatable field array"
 * component, since nothing else in this app needs one yet. Add/remove/
 * quantity-change only, per the locked Phase 22 scope (no reorder, no
 * per-line notes, no bulk paste).
 */
export function LineItemEditor({ form }: { form: UseFormReturn<QuotationFormValues> }) {
  const {
    control,
    register,
    watch,
    setValue,
    formState: { errors },
  } = form
  const { fields, append, remove } = useFieldArray({ control, name: 'items' })
  const items = watch('items')

  function addLine() {
    append({ productId: '', productLabel: '', quantity: '1', unitPrice: 0, taxPercentage: 0 })
  }

  return (
    <div className="space-y-3">
      {fields.length === 0 && (
        <p className="rounded-md border border-dashed border-border p-4 text-center text-sm text-muted-foreground">
          No line items yet.
        </p>
      )}

      {fields.map((field, index) => {
        const line = items?.[index]
        const quantity = Number(line?.quantity)
        const lineTotal =
          line?.productId && Number.isFinite(quantity) && quantity > 0
            ? formatInr(quantity * (line.unitPrice ?? 0))
            : null
        const removeLabel = line?.productLabel ? `Remove line ${index + 1}: ${line.productLabel}` : `Remove line ${index + 1}`

        return (
          <div key={field.id} className="flex flex-col gap-3 rounded-lg border border-border p-3 sm:flex-row sm:items-start">
            <div className="flex-1 space-y-1">
              <ProductCombobox
                initialLabel={line?.productLabel}
                onSelect={(product) => {
                  setValue(`items.${index}.productId`, product.id, { shouldValidate: true })
                  setValue(`items.${index}.productLabel`, product.name)
                  setValue(`items.${index}.unitPrice`, product.price)
                  setValue(`items.${index}.taxPercentage`, product.taxPercentage)
                }}
                onClear={() => {
                  setValue(`items.${index}.productId`, '')
                  setValue(`items.${index}.productLabel`, '')
                }}
              />
              {errors.items?.[index]?.productId && (
                <p className="text-sm text-destructive">{errors.items[index]?.productId?.message}</p>
              )}
            </div>

            <div className="w-full space-y-1 sm:w-28">
              <Input
                type="text"
                inputMode="decimal"
                aria-label={`Quantity for line ${index + 1}`}
                aria-invalid={!!errors.items?.[index]?.quantity}
                {...register(`items.${index}.quantity`)}
              />
              {errors.items?.[index]?.quantity && (
                <p className="text-sm text-destructive">{errors.items[index]?.quantity?.message}</p>
              )}
            </div>

            <div className="flex items-center gap-2 sm:pt-1.5">
              {lineTotal && <span className="text-sm tabular-nums text-muted-foreground sm:hidden">{lineTotal}</span>}
              <span className="hidden w-24 text-right text-sm tabular-nums text-muted-foreground sm:inline">
                {lineTotal ?? '—'}
              </span>
              <Button type="button" variant="ghost" size="icon" onClick={() => remove(index)} aria-label={removeLabel}>
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          </div>
        )
      })}

      <Button type="button" variant="outline" size="sm" onClick={addLine}>
        Add line item
      </Button>
      {typeof errors.items?.message === 'string' && (
        <p className="text-sm text-destructive">{errors.items.message}</p>
      )}
    </div>
  )
}
