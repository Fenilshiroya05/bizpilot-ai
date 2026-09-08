import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { customerKeys, searchCustomers } from '@/features/customers/api'
import { Combobox, type ComboboxOption } from '@/components/ui/combobox'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import type { CustomerResponse } from '@/types/api'

/**
 * Reuses the existing Customer list API/query-key infrastructure from
 * Phase 21 verbatim (`features/customers/api.ts`) — no new backend
 * endpoint, no duplicated customer data fetching.
 */
export function CustomerCombobox({
  initialLabel = '',
  onSelect,
  onClear,
  disabled,
}: {
  initialLabel?: string
  onSelect: (customer: CustomerResponse) => void
  onClear?: () => void
  disabled?: boolean
}) {
  const [query, setQuery] = useState(initialLabel)
  const debounced = useDebouncedValue(query, 300)

  const params = { q: debounced || undefined, page: 0, size: 10 }
  const { data, isFetching } = useQuery({
    queryKey: customerKeys.list(params),
    queryFn: () => searchCustomers(params),
    enabled: debounced.trim().length > 0,
  })

  const options: ComboboxOption<CustomerResponse>[] = (data?.content ?? []).map((customer) => ({
    value: customer.id,
    label: customer.name,
    description: customer.company ?? customer.email ?? undefined,
    data: customer,
  }))

  return (
    <Combobox
      label="Customer"
      placeholder="Search customers..."
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
      emptyMessage={debounced.trim() ? 'No customers found.' : 'Type to search customers.'}
    />
  )
}
