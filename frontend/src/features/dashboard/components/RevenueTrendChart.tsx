import { useQuery } from '@tanstack/react-query'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/feedback/EmptyState'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { getRevenueTrend } from '@/features/dashboard/api'
import { formatInr } from '@/lib/money'

/**
 * Recharts requires a literal SVG color, not a Tailwind class — this is the
 * same blue as the `--color-primary` design token in index.css, kept in
 * sync manually since Recharts cannot read CSS custom properties directly.
 */
const LINE_COLOR = '#1d4ed8'
const GRID_COLOR = '#e5e7eb'
const AXIS_COLOR = '#6b7280'

function formatDayLabel(period: string): string {
  return new Date(period).toLocaleDateString('en-IN', { month: 'short', day: 'numeric' })
}

export function RevenueTrendChart() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['analytics', 'revenue-trend'],
    queryFn: getRevenueTrend,
  })

  const hasRevenue = data?.some((point) => point.revenue > 0) ?? false

  return (
    <Card>
      <CardHeader>
        <CardTitle>Revenue Trend</CardTitle>
        <CardDescription>Paid invoices, last 30 days</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-64 w-full" />
        ) : isError ? (
          <ErrorState title="We couldn't load the revenue trend" error={error} onRetry={() => refetch()} />
        ) : !hasRevenue ? (
          <EmptyState title="No revenue recorded in the last 30 days" />
        ) : (
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 8 }}>
                <CartesianGrid stroke={GRID_COLOR} vertical={false} />
                <XAxis
                  dataKey="period"
                  tickFormatter={formatDayLabel}
                  tick={{ fontSize: 11, fill: AXIS_COLOR }}
                  interval="preserveStartEnd"
                  tickLine={false}
                  axisLine={{ stroke: GRID_COLOR }}
                />
                <YAxis
                  tickFormatter={(value: number) => formatInr(value)}
                  tick={{ fontSize: 11, fill: AXIS_COLOR }}
                  width={90}
                  tickLine={false}
                  axisLine={{ stroke: GRID_COLOR }}
                />
                <Tooltip
                  labelFormatter={(label) => formatDayLabel(String(label))}
                  formatter={(value) => [formatInr(Number(value)), 'Revenue']}
                />
                <Line type="monotone" dataKey="revenue" stroke={LINE_COLOR} strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
