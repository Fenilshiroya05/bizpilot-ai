import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/feedback/EmptyState'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { getSalesPipeline } from '@/features/dashboard/api'
import { formatCount, formatInr } from '@/lib/money'

/** Same literal-color constraint as RevenueTrendChart — kept in sync with `--color-primary`. */
const BAR_COLOR = '#1d4ed8'
const GRID_COLOR = '#e5e7eb'
const AXIS_COLOR = '#6b7280'

function formatStatusLabel(status: string): string {
  return status.replace(/_/g, ' ')
}

export function SalesPipelineChart() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['analytics', 'sales-pipeline'],
    queryFn: getSalesPipeline,
  })

  const hasQuotations = (data?.length ?? 0) > 0

  return (
    <Card>
      <CardHeader>
        <CardTitle>Sales Pipeline</CardTitle>
        <CardDescription>Quotations by status</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-64 w-full" />
        ) : isError ? (
          <ErrorState title="We couldn't load the sales pipeline" error={error} onRetry={() => refetch()} />
        ) : !hasQuotations ? (
          <EmptyState title="No quotations yet" />
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
                  formatter={(value, _name, item) => {
                    const amount = (item.payload as { amount: number }).amount
                    return [`${formatCount(Number(value))} · ${formatInr(amount)}`, 'Quotations']
                  }}
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
