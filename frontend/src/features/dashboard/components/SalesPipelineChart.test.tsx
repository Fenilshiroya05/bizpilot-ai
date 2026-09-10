import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import * as dashboardApi from '@/features/dashboard/api'
import { SalesPipelineChart } from '@/features/dashboard/components/SalesPipelineChart'
import { renderWithClient } from '@/test/render'

vi.mock('@/features/dashboard/api')

describe('SalesPipelineChart', () => {
  it('renders a loading skeleton before the response resolves', () => {
    vi.mocked(dashboardApi.getSalesPipeline).mockReturnValue(new Promise(() => {}))

    renderWithClient(<SalesPipelineChart />)

    expect(screen.getByText('Sales Pipeline')).toBeInTheDocument()
    expect(document.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('renders a retryable error state when the request fails', async () => {
    vi.mocked(dashboardApi.getSalesPipeline).mockRejectedValue(new Error('boom'))

    renderWithClient(<SalesPipelineChart />)

    expect(await screen.findByText(/couldn't load the sales pipeline/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('renders an empty state when there are no quotations at all', async () => {
    vi.mocked(dashboardApi.getSalesPipeline).mockResolvedValue([])

    renderWithClient(<SalesPipelineChart />)

    expect(await screen.findByText('No quotations yet')).toBeInTheDocument()
  })

  it('renders the chart when at least one stage has quotations', async () => {
    vi.mocked(dashboardApi.getSalesPipeline).mockResolvedValue([
      { status: 'DRAFT', count: 2, amount: 500 },
    ])

    renderWithClient(<SalesPipelineChart />)

    await waitFor(() => expect(screen.queryByText('No quotations yet')).not.toBeInTheDocument())
    expect(screen.getByText('Quotations by status')).toBeInTheDocument()
  })
})
