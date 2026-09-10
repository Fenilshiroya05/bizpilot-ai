import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import * as dashboardApi from '@/features/dashboard/api'
import { AiInsightsPanel } from '@/features/dashboard/components/AiInsightsPanel'
import { renderWithClient } from '@/test/render'

vi.mock('@/features/dashboard/api')

const SUMMARY = {
  totalCustomers: 10,
  newLeads: 3,
  qualifiedLeads: 2,
  conversionRate: 40,
  revenue: 12500,
  outstandingInvoicesCount: 1,
  outstandingInvoicesTotal: 800,
  pendingFollowUps: 1,
}

describe('AiInsightsPanel', () => {
  it('renders a loading skeleton before the summary resolves', () => {
    vi.mocked(dashboardApi.getAnalyticsSummary).mockReturnValue(new Promise(() => {}))
    vi.mocked(dashboardApi.getLeadSources).mockResolvedValue([])
    vi.mocked(dashboardApi.getSalesPipeline).mockResolvedValue([])
    vi.mocked(dashboardApi.getTopCustomers).mockResolvedValue([])

    renderWithClient(<AiInsightsPanel />)

    expect(screen.getByText('Business Insights')).toBeInTheDocument()
    expect(document.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('renders a retryable error state when the summary request fails', async () => {
    vi.mocked(dashboardApi.getAnalyticsSummary).mockRejectedValue(new Error('boom'))
    vi.mocked(dashboardApi.getLeadSources).mockResolvedValue([])
    vi.mocked(dashboardApi.getSalesPipeline).mockResolvedValue([])
    vi.mocked(dashboardApi.getTopCustomers).mockResolvedValue([])

    renderWithClient(<AiInsightsPanel />)

    expect(await screen.findByText(/couldn't load your business insights/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('always renders the base, always-available insights once the summary loads', async () => {
    vi.mocked(dashboardApi.getAnalyticsSummary).mockResolvedValue(SUMMARY)
    vi.mocked(dashboardApi.getLeadSources).mockResolvedValue([])
    vi.mocked(dashboardApi.getSalesPipeline).mockResolvedValue([])
    vi.mocked(dashboardApi.getTopCustomers).mockResolvedValue([])

    renderWithClient(<AiInsightsPanel />)

    expect(await screen.findByText('Revenue for the last 30 days is ₹12,500.00.')).toBeInTheDocument()
    expect(screen.getByText('2 lead(s) are currently in the qualified status.')).toBeInTheDocument()
    expect(screen.getByText('1 invoice(s) are outstanding, totaling ₹800.00.')).toBeInTheDocument()
    expect(screen.getByText('1 lead(s) are due for follow-up.')).toBeInTheDocument()
  })

  it('adds the largest-source, largest-pipeline-stage, and top-customer sentences only when that data exists', async () => {
    vi.mocked(dashboardApi.getAnalyticsSummary).mockResolvedValue(SUMMARY)
    vi.mocked(dashboardApi.getLeadSources).mockResolvedValue([
      { source: 'WEBSITE', count: 5 },
      { source: 'REFERRAL', count: 2 },
    ])
    vi.mocked(dashboardApi.getSalesPipeline).mockResolvedValue([
      { status: 'DRAFT', count: 1, amount: 300 },
      { status: 'ACCEPTED', count: 1, amount: 900 },
    ])
    vi.mocked(dashboardApi.getTopCustomers).mockResolvedValue([
      { customerId: 'c1', customerName: 'Acme Retail', revenue: 900 },
    ])

    renderWithClient(<AiInsightsPanel />)

    expect(await screen.findByText('The largest lead source is website with 5 lead(s).')).toBeInTheDocument()
    expect(screen.getByText('The largest pipeline stage by value is accepted at ₹900.00.')).toBeInTheDocument()
    expect(screen.getByText('Customer Acme Retail has the highest recorded revenue at ₹900.00.')).toBeInTheDocument()
  })

  it('never renders a fabricated percentage, trend, or forecast', async () => {
    vi.mocked(dashboardApi.getAnalyticsSummary).mockResolvedValue(SUMMARY)
    vi.mocked(dashboardApi.getLeadSources).mockResolvedValue([])
    vi.mocked(dashboardApi.getSalesPipeline).mockResolvedValue([])
    vi.mocked(dashboardApi.getTopCustomers).mockResolvedValue([])

    renderWithClient(<AiInsightsPanel />)

    await screen.findByText('Revenue for the last 30 days is ₹12,500.00.')
    expect(screen.queryByText(/increase|decrease|forecast|predict|expect/i)).not.toBeInTheDocument()
  })
})
