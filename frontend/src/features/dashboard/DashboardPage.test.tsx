import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import * as dashboardApi from '@/features/dashboard/api'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { renderWithClient } from '@/test/render'

vi.mock('@/features/dashboard/api')

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

    renderWithClient(<DashboardPage />)

    await waitFor(() => expect(screen.getByText('12')).toBeInTheDocument())
    expect(screen.getByText('40.00%')).toBeInTheDocument()
    expect(screen.getByText('₹12,500.00')).toBeInTheDocument()
    expect(screen.getByText('₹4,200.00')).toBeInTheDocument()
  })

  it('renders a retryable error state when the request fails', async () => {
    vi.mocked(dashboardApi.getAnalyticsSummary).mockRejectedValue(new Error('boom'))

    renderWithClient(<DashboardPage />)

    expect(await screen.findByText(/couldn't load your business summary/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })
})
