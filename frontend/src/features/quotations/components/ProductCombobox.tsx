import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { productKeys, searchProducts } from '@/features/products/api'
import { Combobox, type ComboboxOption } from '@/components/ui/combobox'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { formatInr } from '@/lib/money'
import type { ProductResponse } from '@/types/api'

/**
 * Reuses the existing Product list API/query-key infrastructure (this
 * phase's own `features/products/api.ts`) — filters to `status=ACTIVE`
 * server-side (a real, backend-supported filter), matching the locked rule
 * that inactive products are never offered as a selectable line item.
 */
export function ProductCombobox({
  initialLabel = '',
  onSelect,
  onClear,
  disabled,
}: {
  initialLabel?: string
  onSelect: (product: ProductResponse) => void
  onClear?: () => void
  disabled?: boolean
}) {
  const [query, setQuery] = useState(initialLabel)
  const debounced = useDebouncedValue(query, 300)

  const params = { q: debounced || undefined, status: 'ACTIVE' as const, page: 0, size: 10 }
  const { data, isFetching } = useQuery({
    queryKey: productKeys.list(params),
    queryFn: () => searchProducts(params),
    enabled: debounced.trim().length > 0,
  })

  const options: ComboboxOption<ProductResponse>[] = (data?.content ?? []).map((product) => ({
    value: product.id,
    label: product.name,
    description: `${product.sku} · ${formatInr(product.price)} / ${product.unit}`,
    data: product,
  }))

  return (
    <Combobox
      label="Product"
      placeholder="Search products..."
      query={query}
      onQueryChange={setQuery}
      options={options}
      isLoading={isFetching}
      onSelect={(option) => {
        onSelect(option.data)
        setQuery(option.label)
      }}
      onClear={onClear}
      disabled={disabled}
      emptyMessage={debounced.trim() ? 'No active products found.' : 'Type to search products.'}
    />
  )
}
