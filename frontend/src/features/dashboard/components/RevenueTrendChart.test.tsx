import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import * as dashboardApi from '@/features/dashboard/api'
import { RevenueTrendChart } from '@/features/dashboard/components/RevenueTrendChart'
import { renderWithClient } from '@/test/render'

vi.mock('@/features/dashboard/api')

describe('RevenueTrendChart', () => {
  it('renders a loading skeleton before the response resolves', () => {
    vi.mocked(dashboardApi.getRevenueTrend).mockReturnValue(new Promise(() => {}))

    renderWithClient(<RevenueTrendChart />)

    expect(screen.getByText('Revenue Trend')).toBeInTheDocument()
    expect(document.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('renders a retryable error state when the request fails', async () => {
    vi.mocked(dashboardApi.getRevenueTrend).mockRejectedValue(new Error('boom'))

    renderWithClient(<RevenueTrendChart />)

    expect(await screen.findByText(/couldn't load the revenue trend/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('renders an empty state when every point has zero revenue', async () => {
    vi.mocked(dashboardApi.getRevenueTrend).mockResolvedValue([
      { period: '2026-08-01', revenue: 0 },
      { period: '2026-08-02', revenue: 0 },
    ])

    renderWithClient(<RevenueTrendChart />)

    expect(await screen.findByText('No revenue recorded in the last 30 days')).toBeInTheDocument()
  })

  it('renders the chart when at least one point has revenue', async () => {
    vi.mocked(dashboardApi.getRevenueTrend).mockResolvedValue([
      { period: '2026-08-01', revenue: 0 },
      { period: '2026-08-02', revenue: 1500 },
    ])

    renderWithClient(<RevenueTrendChart />)

    await waitFor(() =>
      expect(screen.queryByText('No revenue recorded in the last 30 days')).not.toBeInTheDocument(),
    )
    expect(screen.getByText('Paid invoices, last 30 days')).toBeInTheDocument()
  })
})
