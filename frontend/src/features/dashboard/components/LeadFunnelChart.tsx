import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/feedback/EmptyState'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { getLeadFunnel } from '@/features/dashboard/api'
import { formatCount } from '@/lib/money'

/** Same literal-color constraint as RevenueTrendChart — kept in sync with `--color-primary`. */
const BAR_COLOR = '#1d4ed8'
const GRID_COLOR = '#e5e7eb'
const AXIS_COLOR = '#6b7280'

/** Mirrors StatusBadge's own label normalization (`status.replace(/_/g, ' ')`) — no new labeling convention. */
function formatStatusLabel(status: string): string {
  return status.replace(/_/g, ' ')
}

export function LeadFunnelChart() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['analytics', 'lead-funnel'],
    queryFn: getLeadFunnel,
  })

  const hasLeads = data?.some((stage) => stage.count > 0) ?? false

  return (
    <Card>
      <CardHeader>
        <CardTitle>Lead Funnel</CardTitle>
        <CardDescription>Current status distribution</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-64 w-full" />
        ) : isError ? (
          <ErrorState title="We couldn't load the lead funnel" error={error} onRetry={() => refetch()} />
        ) : !hasLeads ? (
          <EmptyState title="No leads yet" />
        ) : (
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 24 }}>
                <CartesianGrid stroke={GRID_COLOR} vertical={false} />
                <XAxis
                  dataKey="status"
                  tickFormatter={formatStatusLabel}
                  tick={{ fontSize: 10, fill: AXIS_COLOR }}
                  angle={-30}
                  textAnchor="end"
                  tickLine={false}
                  axisLine={{ stroke: GRID_COLOR }}
                />
                <YAxis
                  tickFormatter={(value: number) => formatCount(value)}
                  tick={{ fontSize: 11, fill: AXIS_COLOR }}
                  allowDecimals={false}
                  width={40}
                  tickLine={false}
                  axisLine={{ stroke: GRID_COLOR }}
                />
                <Tooltip
                  labelFormatter={(label) => formatStatusLabel(String(label))}
                  formatter={(value) => [formatCount(Number(value)), 'Leads']}
                />
                <Bar dataKey="count" fill={BAR_COLOR} radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
