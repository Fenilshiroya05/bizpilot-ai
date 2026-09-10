import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import * as dashboardApi from '@/features/dashboard/api'
import { LeadFunnelChart } from '@/features/dashboard/components/LeadFunnelChart'
import { renderWithClient } from '@/test/render'

vi.mock('@/features/dashboard/api')

describe('LeadFunnelChart', () => {
  it('renders a loading skeleton before the response resolves', () => {
    vi.mocked(dashboardApi.getLeadFunnel).mockReturnValue(new Promise(() => {}))

    renderWithClient(<LeadFunnelChart />)

    expect(screen.getByText('Lead Funnel')).toBeInTheDocument()
    expect(document.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('renders a retryable error state when the request fails', async () => {
    vi.mocked(dashboardApi.getLeadFunnel).mockRejectedValue(new Error('boom'))

    renderWithClient(<LeadFunnelChart />)

    expect(await screen.findByText(/couldn't load the lead funnel/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('renders an empty state when every status has zero leads', async () => {
    vi.mocked(dashboardApi.getLeadFunnel).mockResolvedValue([
      { status: 'NEW', count: 0 },
      { status: 'WON', count: 0 },
    ])

    renderWithClient(<LeadFunnelChart />)

    expect(await screen.findByText('No leads yet')).toBeInTheDocument()
  })

  it('renders the chart when at least one status has leads', async () => {
    vi.mocked(dashboardApi.getLeadFunnel).mockResolvedValue([{ status: 'NEW', count: 4 }])

    renderWithClient(<LeadFunnelChart />)

    await waitFor(() => expect(screen.queryByText('No leads yet')).not.toBeInTheDocument())
    expect(screen.getByText('Current status distribution')).toBeInTheDocument()
  })
})
