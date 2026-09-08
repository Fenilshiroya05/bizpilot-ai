import { useQuery } from '@tanstack/react-query'
import { CalendarClock, Percent, Receipt, Target, TrendingUp, Users } from 'lucide-react'

import { ErrorState } from '@/components/feedback/ErrorState'
import { PageHeader } from '@/components/layout/PageHeader'
import { getAnalyticsSummary } from '@/features/dashboard/api'
import { KpiCard } from '@/features/dashboard/KpiCard'
import { formatCount, formatInr, formatPercentage } from '@/lib/money'

export function DashboardPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['analytics', 'summary'],
    queryFn: getAnalyticsSummary,
  })

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Dashboard" description="Business overview for your organization." />

      {isError ? (
        <ErrorState
          title="We couldn't load your business summary"
          error={error}
          onRetry={() => refetch()}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <KpiCard
            label="Total Customers"
            icon={Users}
            isLoading={isLoading}
            value={data ? formatCount(data.totalCustomers) : ''}
          />
          <KpiCard
            label="New Leads"
            icon={Target}
            isLoading={isLoading}
            value={data ? formatCount(data.newLeads) : ''}
            secondaryValue="Last 30 days"
          />
          <KpiCard
            label="Qualified Leads"
            icon={Target}
            isLoading={isLoading}
            value={data ? formatCount(data.qualifiedLeads) : ''}
          />
          <KpiCard
            label="Conversion Rate"
            icon={Percent}
            isLoading={isLoading}
            value={data ? formatPercentage(data.conversionRate) : ''}
            secondaryValue="Won / (Won + Lost)"
          />
          <KpiCard
            label="Revenue"
            icon={TrendingUp}
            isLoading={isLoading}
            value={data ? formatInr(data.revenue) : ''}
            secondaryValue="Paid invoices, last 30 days"
          />
          <KpiCard
            label="Outstanding Invoices"
            icon={Receipt}
            isLoading={isLoading}
            value={data ? formatCount(data.outstandingInvoicesCount) : ''}
            secondaryValue={data ? formatInr(data.outstandingInvoicesTotal) : undefined}
          />
          <KpiCard
            label="Pending Follow-ups"
            icon={CalendarClock}
            isLoading={isLoading}
            value={data ? formatCount(data.pendingFollowUps) : ''}
          />
        </div>
      )}
    </div>
  )
}
