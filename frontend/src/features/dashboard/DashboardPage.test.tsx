import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import * as dashboardApi from '@/features/dashboard/api'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { renderWithClient } from '@/test/render'

vi.mock('@/features/dashboard/api')

/** Every Phase 26 widget is self-contained with its own query — give each a resolved default so no test triggers an unhandled/pending fetch. */
function mockPhase26WidgetsWithEmptyData() {
  vi.mocked(dashboardApi.getRevenueTrend).mockResolvedValue([])
  vi.mocked(dashboardApi.getLeadFunnel).mockResolvedValue([])
  vi.mocked(dashboardApi.getLeadSources).mockResolvedValue([])
  vi.mocked(dashboardApi.getSalesPipeline).mockResolvedValue([])
  vi.mocked(dashboardApi.getTopCustomers).mockResolvedValue([])
}

describe('DashboardPage', () => {
  it('renders every KPI formatted with the backend-provided values', async () => {
    vi.mocked(dashboardApi.getAnalyticsSummary).mockResolvedValue({
      totalCustomers: 12,
      newLeads: 4,
      qualifiedLeads: 2,
      conversionRate: 40,
      revenue: 12500,
      outstandingInvoicesCount: 3,
      outstandingInvoicesTotal: 4200,
      pendingFollowUps: 2,
    })
    mockPhase26WidgetsWithEmptyData()

    renderWithClient(<DashboardPage />)

    await waitFor(() => expect(screen.getByText('12')).toBeInTheDocument())
    expect(screen.getByText('40.00%')).toBeInTheDocument()
    expect(screen.getByText('₹12,500.00')).toBeInTheDocument()
    expect(screen.getByText('₹4,200.00')).toBeInTheDocument()
  })

  it('renders a retryable error state when the request fails', async () => {
    vi.mocked(dashboardApi.getAnalyticsSummary).mockRejectedValue(new Error('boom'))
    mockPhase26WidgetsWithEmptyData()

    renderWithClient(<DashboardPage />)

    expect(await screen.findByText(/couldn't load your business summary/i)).toBeInTheDocument()
    // AiInsightsPanel shares the same ['analytics', 'summary'] query key and
    // independently shows its own retryable error too (self-contained
    // widgets, per Phase 26 scope) — at least one Retry button is expected,
    // not exactly one.
    expect(screen.getAllByRole('button', { name: 'Retry' }).length).toBeGreaterThanOrEqual(1)
  })

  it('renders the Phase 26 analytics section with real chart/insight data below the KPI row', async () => {
    vi.mocked(dashboardApi.getAnalyticsSummary).mockResolvedValue({
      totalCustomers: 12,
      newLeads: 4,
      qualifiedLeads: 2,
      conversionRate: 40,
      revenue: 12500,
      outstandingInvoicesCount: 3,
      outstandingInvoicesTotal: 4200,
      pendingFollowUps: 2,
    })
    vi.mocked(dashboardApi.getRevenueTrend).mockResolvedValue([{ period: '2026-09-01', revenue: 5000 }])
    vi.mocked(dashboardApi.getLeadFunnel).mockResolvedValue([{ status: 'NEW', count: 3 }])
    vi.mocked(dashboardApi.getLeadSources).mockResolvedValue([{ source: 'WEBSITE', count: 3 }])
    vi.mocked(dashboardApi.getSalesPipeline).mockResolvedValue([{ status: 'DRAFT', count: 1, amount: 500 }])
    vi.mocked(dashboardApi.getTopCustomers).mockResolvedValue([
      { customerId: 'c1', customerName: 'Acme Retail', revenue: 500 },
    ])

    renderWithClient(<DashboardPage />)

    expect(await screen.findByText('Revenue Trend')).toBeInTheDocument()
    expect(await screen.findByText('Lead Funnel')).toBeInTheDocument()
    expect(await screen.findByText('Lead Sources')).toBeInTheDocument()
    expect(await screen.findByText('Sales Pipeline')).toBeInTheDocument()
    expect(await screen.findByText('Top Customers')).toBeInTheDocument()
    expect(await screen.findByText('Business Insights')).toBeInTheDocument()
    expect(await screen.findByText('Acme Retail')).toBeInTheDocument()
    expect(screen.getByText(/Revenue for the last 30 days is ₹12,500\.00\./)).toBeInTheDocument()
  })
})
