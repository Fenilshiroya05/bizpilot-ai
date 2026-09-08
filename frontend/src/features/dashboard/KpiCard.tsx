import type { LucideIcon } from 'lucide-react'

import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function KpiCard({
  label,
  value,
  secondaryValue,
  icon: Icon,
  isLoading,
}: {
  label: string
  value: string
  secondaryValue?: string
  icon: LucideIcon
  isLoading?: boolean
}) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0 pb-2">
        <span className="text-sm font-medium text-muted-foreground">{label}</span>
        <Icon className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-8 w-24" />
        ) : (
          <div className="space-y-0.5">
            <p className="text-2xl font-semibold tabular-nums text-foreground">{value}</p>
            {secondaryValue && (
              <p className="text-xs tabular-nums text-muted-foreground">{secondaryValue}</p>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
