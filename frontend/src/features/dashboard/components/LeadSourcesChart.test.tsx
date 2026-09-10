import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import * as dashboardApi from '@/features/dashboard/api'
import { LeadSourcesChart } from '@/features/dashboard/components/LeadSourcesChart'
import { renderWithClient } from '@/test/render'

vi.mock('@/features/dashboard/api')

describe('LeadSourcesChart', () => {
  it('renders a loading skeleton before the response resolves', () => {
    vi.mocked(dashboardApi.getLeadSources).mockReturnValue(new Promise(() => {}))

    renderWithClient(<LeadSourcesChart />)

    expect(screen.getByText('Lead Sources')).toBeInTheDocument()
    expect(document.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('renders a retryable error state when the request fails', async () => {
    vi.mocked(dashboardApi.getLeadSources).mockRejectedValue(new Error('boom'))

    renderWithClient(<LeadSourcesChart />)

    expect(await screen.findByText(/couldn't load lead sources/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('renders an empty state when there are no sources at all', async () => {
    vi.mocked(dashboardApi.getLeadSources).mockResolvedValue([])

    renderWithClient(<LeadSourcesChart />)

    expect(await screen.findByText('No leads yet')).toBeInTheDocument()
  })

  it('renders the chart when at least one source has leads', async () => {
    vi.mocked(dashboardApi.getLeadSources).mockResolvedValue([{ source: 'WEBSITE', count: 4 }])

    renderWithClient(<LeadSourcesChart />)

    await waitFor(() => expect(screen.queryByText('No leads yet')).not.toBeInTheDocument())
    expect(screen.getByText('Where your leads come from')).toBeInTheDocument()
  })

  it('renders a single source without fabricating additional categories', async () => {
    vi.mocked(dashboardApi.getLeadSources).mockResolvedValue([{ source: 'REFERRAL', count: 1 }])

    renderWithClient(<LeadSourcesChart />)

    await waitFor(() => expect(screen.queryByText('No leads yet')).not.toBeInTheDocument())
  })
})
