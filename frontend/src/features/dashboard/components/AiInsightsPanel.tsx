import { useQuery } from '@tanstack/react-query'
import { Info } from 'lucide-react'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ErrorState } from '@/components/feedback/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import {
  getAnalyticsSummary,
  getLeadSources,
  getSalesPipeline,
  getTopCustomers,
} from '@/features/dashboard/api'
import { formatInr } from '@/lib/money'
import type {
  AnalyticsSummaryResponse,
  LeadSourceBreakdownResponse,
  SalesPipelineStageResponse,
  TopCustomerResponse,
} from '@/types/api'

function formatLabel(value: string): string {
  return value.replace(/_/g, ' ').toLowerCase()
}

/**
 * Every sentence here is a direct, deterministic read of an already-fetched
 * metric — no LLM, no Ollama, no OpenAI, no AI Assistant call of any kind
 * (project instructions §13/§40: never invent statistics, percentages,
 * trends, or recommendations unsupported by data). A sentence is included
 * only when its underlying data actually exists — see the individual
 * `if` guards below, not a fixed-length list.
 */
function buildInsights(
  summary: AnalyticsSummaryResponse,
  leadSources: LeadSourceBreakdownResponse[] | undefined,
  salesPipeline: SalesPipelineStageResponse[] | undefined,
  topCustomers: TopCustomerResponse[] | undefined,
): string[] {
  const insights: string[] = []

  insights.push(`Revenue for the last 30 days is ${formatInr(summary.revenue)}.`)
  insights.push(`${summary.qualifiedLeads} lead(s) are currently in the qualified status.`)

  if (summary.outstandingInvoicesCount > 0) {
    insights.push(
      `${summary.outstandingInvoicesCount} invoice(s) are outstanding, totaling ${formatInr(summary.outstandingInvoicesTotal)}.`,
    )
  }

  if (summary.pendingFollowUps > 0) {
    insights.push(`${summary.pendingFollowUps} lead(s) are due for follow-up.`)
  }

  if (leadSources && leadSources.length > 0) {
    const largest = leadSources.reduce((max, row) => (row.count > max.count ? row : max), leadSources[0]!)
    insights.push(`The largest lead source is ${formatLabel(largest.source)} with ${largest.count} lead(s).`)
  }

  if (salesPipeline && salesPipeline.length > 0) {
    const largest = salesPipeline.reduce((max, row) => (row.amount > max.amount ? row : max), salesPipeline[0]!)
    insights.push(`The largest pipeline stage by value is ${formatLabel(largest.status)} at ${formatInr(largest.amount)}.`)
  }

  if (topCustomers && topCustomers.length > 0) {
    const top = topCustomers[0]!
    insights.push(`Customer ${top.customerName} has the highest recorded revenue at ${formatInr(top.revenue)}.`)
  }

  return insights
}

export function AiInsightsPanel() {
  const summaryQuery = useQuery({ queryKey: ['analytics', 'summary'], queryFn: getAnalyticsSummary })
  const leadSourcesQuery = useQuery({ queryKey: ['analytics', 'lead-sources'], queryFn: getLeadSources })
  const salesPipelineQuery = useQuery({ queryKey: ['analytics', 'sales-pipeline'], queryFn: getSalesPipeline })
  const topCustomersQuery = useQuery({ queryKey: ['analytics', 'top-customers'], queryFn: getTopCustomers })

  return (
    <Card>
      <CardHeader className="flex-row items-center gap-2 space-y-0">
        <Info className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
        <div>
          <CardTitle>Business Insights</CardTitle>
          <CardDescription>Based on your data — not AI-generated</CardDescription>
        </div>
      </CardHeader>
      <CardContent>
        {summaryQuery.isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-5/6" />
            <Skeleton className="h-4 w-2/3" />
          </div>
        ) : summaryQuery.isError || !summaryQuery.data ? (
          <ErrorState
            title="We couldn't load your business insights"
            error={summaryQuery.error}
            onRetry={() => summaryQuery.refetch()}
          />
        ) : (
          <ul className="list-disc space-y-1.5 pl-4 text-sm text-foreground">
            {buildInsights(
              summaryQuery.data,
              leadSourcesQuery.data,
              salesPipelineQuery.data,
              topCustomersQuery.data,
            ).map((insight, index) => (
              <li key={index}>{insight}</li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
