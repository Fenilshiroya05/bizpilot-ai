import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import {
  createProduct,
  createProductCategory,
  listProductCategories,
  productCategoryKeys,
  productKeys,
  updateProduct,
  updateProductCategory,
} from '@/features/products/api'
import { productFormSchema, type ProductFormValues } from '@/features/products/schemas'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Combobox, type ComboboxOption } from '@/components/ui/combobox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { FormField } from '@/components/forms/FormField'
import { toast } from '@/hooks/use-toast'
import { ApiError } from '@/lib/api-client'
import type { ProductCategoryResponse, ProductResponse } from '@/types/api'

const CREATE_DEFAULTS: ProductFormValues = {
  sku: '',
  name: '',
  description: '',
  unit: '',
  price: '',
  taxPercentage: '',
  categoryId: '',
  status: 'ACTIVE',
}

function toEditDefaults(product: ProductResponse): ProductFormValues {
  return {
    sku: product.sku,
    name: product.name,
    description: product.description ?? '',
    unit: product.unit,
    price: String(product.price),
    taxPercentage: String(product.taxPercentage),
    categoryId: product.categoryId ?? '',
    status: product.status,
  }
}

/** Rename an existing category — a small dedicated dialog, not a second CRUD page. */
function RenameCategoryDialog({
  category,
  open,
  onOpenChange,
}: {
  category: ProductCategoryResponse
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(category.name)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSave() {
    if (!name.trim()) {
      setError('Category name is required')
      return
    }
    setIsSaving(true)
    setError(null)
    try {
      await updateProductCategory(category.id, { name: name.trim() })
      await queryClient.invalidateQueries({ queryKey: productCategoryKeys.list() })
      await queryClient.invalidateQueries({ queryKey: ['products', 'list'] })
      toast({ title: 'Category renamed', variant: 'success' })
      onOpenChange(false)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to rename this category.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Rename category</DialogTitle>
          <DialogDescription>Update this category&rsquo;s name across all products.</DialogDescription>
        </DialogHeader>
        {error && <Alert variant="destructive">{error}</Alert>}
        <FormField label="Name" htmlFor="category-rename-name">
          <Input
            id="category-rename-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoFocus
          />
        </FormField>
        <div className="mt-4 flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSaving}>
            Cancel
          </Button>
          <Button type="button" onClick={handleSave} isLoading={isSaving}>
            Save
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}

function CategoryPicker({
  categoryId,
  onChange,
}: {
  categoryId: string
  onChange: (categoryId: string) => void
}) {
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [renaming, setRenaming] = useState<ProductCategoryResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)

  const categoriesQuery = useQuery({
    queryKey: productCategoryKeys.list(),
    queryFn: listProductCategories,
  })
  const categories = categoriesQuery.data?.content ?? []
  const selected = categories.find((c) => c.id === categoryId)

  const filtered = query.trim()
    ? categories.filter((c) => c.name.toLowerCase().includes(query.trim().toLowerCase()))
    : categories
  const exactMatch = categories.some((c) => c.name.toLowerCase() === query.trim().toLowerCase())

  const options: ComboboxOption<ProductCategoryResponse | 'create'>[] = filtered.map((c) => ({
    value: c.id,
    label: c.name,
    data: c,
  }))
  if (query.trim() && !exactMatch) {
    options.push({ value: '__create__', label: `Create "${query.trim()}"`, data: 'create' })
  }

  async function handleSelect(option: ComboboxOption<ProductCategoryResponse | 'create'>) {
    if (option.data === 'create') {
      setIsCreating(true)
      try {
        const created = await createProductCategory({ name: query.trim() })
        await queryClient.invalidateQueries({ queryKey: productCategoryKeys.list() })
        onChange(created.id)
        setQuery(created.name)
        toast({ title: 'Category created', variant: 'success' })
      } catch (err) {
        toast({
          title: 'Could not create category',
          description: err instanceof ApiError ? err.message : 'Please try again.',
          variant: 'destructive',
        })
      } finally {
        setIsCreating(false)
      }
      return
    }
    onChange(option.value)
    setQuery(option.label)
  }

  return (
    <div className="flex items-end gap-2">
      <div className="flex-1">
        <Combobox
          id="product-category"
          label="Category"
          placeholder={selected ? selected.name : 'Search or create a category...'}
          query={selected && !query ? '' : query}
          onQueryChange={setQuery}
          options={options}
          isLoading={categoriesQuery.isLoading || isCreating}
          onSelect={handleSelect}
          onClear={selected ? () => { onChange(''); setQuery('') } : undefined}
          emptyMessage="Type to create a new category."
        />
      </div>
      {selected && (
        <Button
          type="button"
          variant="outline"
          size="icon"
          onClick={() => setRenaming(selected)}
          aria-label={`Rename category ${selected.name}`}
        >
          <Pencil className="h-4 w-4" aria-hidden="true" />
        </Button>
      )}
      {renaming && (
        <RenameCategoryDialog category={renaming} open={!!renaming} onOpenChange={(open) => !open && setRenaming(null)} />
      )}
    </div>
  )
}

/** Mounted only while the dialog is open — see CustomerForm's identical rationale (Phase 21). */
function ProductForm({ product, onOpenChange }: { product?: ProductResponse; onOpenChange: (open: boolean) => void }) {
  const isEdit = !!product
  const queryClient = useQueryClient()
  const [apiError, setApiError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: isEdit ? toEditDefaults(product) : CREATE_DEFAULTS,
  })

  const categoryId = watch('categoryId')

  async function onSubmit(values: ProductFormValues) {
    setApiError(null)
    try {
      if (isEdit && product) {
        await updateProduct(product.id, {
          sku: values.sku,
          name: values.name,
          description: values.description,
          unit: values.unit,
          price: values.price,
          taxPercentage: values.taxPercentage || '0',
          status: values.status,
          categoryId: values.categoryId || undefined,
          clearCategory: !values.categoryId,
        })
        await queryClient.invalidateQueries({ queryKey: productKeys.detail(product.id) })
        toast({ title: 'Product updated', variant: 'success' })
      } else {
        await createProduct({
          sku: values.sku,
          name: values.name,
          description: values.description,
          unit: values.unit,
          price: values.price,
          taxPercentage: values.taxPercentage || undefined,
          categoryId: values.categoryId || undefined,
        })
        toast({ title: 'Product created', variant: 'success' })
      }
      await queryClient.invalidateQueries({ queryKey: ['products', 'list'] })
      onOpenChange(false)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'DUPLICATE_SKU') {
        setError('sku', { message: error.message })
        return
      }
      setApiError(error instanceof ApiError ? error.message : 'Unable to save this product. Please try again.')
    }
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
      {apiError && <Alert variant="destructive">{apiError}</Alert>}

      <div className="grid grid-cols-2 gap-3">
        <FormField label="SKU" htmlFor="product-sku" error={errors.sku?.message}>
          <Input
            id="product-sku"
            aria-invalid={!!errors.sku}
            aria-describedby={errors.sku ? 'product-sku-error' : undefined}
            {...register('sku')}
          />
        </FormField>
        <FormField label="Unit" htmlFor="product-unit" error={errors.unit?.message} hint="e.g. pcs, kg, litre">
          <Input
            id="product-unit"
            aria-invalid={!!errors.unit}
            aria-describedby={errors.unit ? 'product-unit-error' : undefined}
            {...register('unit')}
          />
        </FormField>
      </div>

      <FormField label="Name" htmlFor="product-name" error={errors.name?.message}>
        <Input
          id="product-name"
          aria-invalid={!!errors.name}
          aria-describedby={errors.name ? 'product-name-error' : undefined}
          {...register('name')}
        />
      </FormField>

      <FormField label="Description" htmlFor="product-description" error={errors.description?.message}>
        <Input id="product-description" {...register('description')} />
      </FormField>

      <div className="grid grid-cols-2 gap-3">
        <FormField label="Price" htmlFor="product-price" error={errors.price?.message}>
          <Input
            id="product-price"
            type="text"
            inputMode="decimal"
            aria-invalid={!!errors.price}
            aria-describedby={errors.price ? 'product-price-error' : undefined}
            {...register('price')}
          />
        </FormField>
        <FormField
          label="Tax %"
          htmlFor="product-tax"
          error={errors.taxPercentage?.message}
          hint="Defaults to 0 if left empty"
        >
          <Input
            id="product-tax"
            type="text"
            inputMode="decimal"
            aria-invalid={!!errors.taxPercentage}
            aria-describedby={errors.taxPercentage ? 'product-tax-error' : undefined}
            {...register('taxPercentage')}
          />
        </FormField>
      </div>

      <FormField label="Category" htmlFor="product-category">
        <CategoryPicker categoryId={categoryId} onChange={(value) => setValue('categoryId', value)} />
      </FormField>

      {isEdit && (
        <FormField label="Status" htmlFor="product-status">
          <Select id="product-status" {...register('status')}>
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
          {isEdit ? 'Save changes' : 'Create product'}
        </Button>
      </div>
    </form>
  )
}

export function ProductFormDialog({
  open,
  onOpenChange,
  product,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Present = edit mode; absent = create mode. */
  product?: ProductResponse
}) {
  const isEdit = !!product

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Edit product' : 'Create product'}</DialogTitle>
          <DialogDescription>
            {isEdit ? 'Update this product’s details.' : 'Add a new product to your catalog.'}
          </DialogDescription>
        </DialogHeader>
        {open && <ProductForm key={product?.id ?? 'create'} product={product} onOpenChange={onOpenChange} />}
      </DialogContent>
    </Dialog>
  )
}
