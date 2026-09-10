import { useQuery } from '@tanstack/react-query'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/feedback/EmptyState'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { getTopCustomers } from '@/features/dashboard/api'
import { formatInr } from '@/lib/money'

export function TopCustomersCard() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['analytics', 'top-customers'],
    queryFn: getTopCustomers,
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Top Customers</CardTitle>
        <CardDescription>Ranked by paid revenue, all time</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, index) => (
              <Skeleton key={index} className="h-6 w-full" />
            ))}
          </div>
        ) : isError ? (
          <ErrorState title="We couldn't load your top customers" error={error} onRetry={() => refetch()} />
        ) : !data || data.length === 0 ? (
          <EmptyState title="No paid invoices yet" />
        ) : (
          <ol className="divide-y divide-border">
            {data.map((customer, index) => (
              <li key={customer.customerId} className="flex items-center justify-between gap-3 py-2">
                <span className="flex items-center gap-3 overflow-hidden">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-sm bg-muted text-xs font-medium text-muted-foreground">
                    {index + 1}
                  </span>
                  <span className="truncate text-sm text-foreground">{customer.customerName}</span>
                </span>
                <span className="shrink-0 text-sm font-medium tabular-nums text-foreground">
                  {formatInr(customer.revenue)}
                </span>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}
