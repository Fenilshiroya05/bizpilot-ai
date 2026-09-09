import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { leadKeys, searchLeads } from '@/features/leads/api'
import { Combobox, type ComboboxOption } from '@/components/ui/combobox'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import type { LeadResponse } from '@/types/api'

/**
 * Reuses the existing Lead list API/query-key infrastructure from Phase 21
 * verbatim (`features/leads/api.ts`) — no new backend endpoint, no
 * duplicated lead data fetching. Architecturally identical to
 * `CustomerCombobox`/`ProductCombobox` (same ARIA 1.2 combobox pattern via
 * the shared `Combobox` primitive, same debounce, same controlled
 * query/onSelect/onClear contract).
 */
export function LeadCombobox({
  initialLabel = '',
  onSelect,
  onClear,
  disabled,
}: {
  initialLabel?: string
  onSelect: (lead: LeadResponse) => void
  onClear?: () => void
  disabled?: boolean
}) {
  const [query, setQuery] = useState(initialLabel)
  const debounced = useDebouncedValue(query, 300)

  const params = { q: debounced || undefined, page: 0, size: 10 }
  const { data, isFetching } = useQuery({
    queryKey: leadKeys.list(params),
    queryFn: () => searchLeads(params),
    enabled: debounced.trim().length > 0,
  })

  const options: ComboboxOption<LeadResponse>[] = (data?.content ?? []).map((lead) => ({
    value: lead.id,
    label: lead.name,
    description: lead.company ?? lead.email ?? undefined,
    data: lead,
  }))

  return (
    <Combobox
      label="Lead"
      placeholder="Search leads..."
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
      emptyMessage={debounced.trim() ? 'No leads found.' : 'Type to search leads.'}
    />
  )
}
