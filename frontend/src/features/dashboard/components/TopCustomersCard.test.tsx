import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import * as dashboardApi from '@/features/dashboard/api'
import { TopCustomersCard } from '@/features/dashboard/components/TopCustomersCard'
import { renderWithClient } from '@/test/render'

vi.mock('@/features/dashboard/api')

describe('TopCustomersCard', () => {
  it('renders a loading skeleton before the response resolves', () => {
    vi.mocked(dashboardApi.getTopCustomers).mockReturnValue(new Promise(() => {}))

    renderWithClient(<TopCustomersCard />)

    expect(screen.getByText('Top Customers')).toBeInTheDocument()
    expect(document.querySelector('.animate-pulse')).toBeTruthy()
  })

  it('renders a retryable error state when the request fails', async () => {
    vi.mocked(dashboardApi.getTopCustomers).mockRejectedValue(new Error('boom'))

    renderWithClient(<TopCustomersCard />)

    expect(await screen.findByText(/couldn't load your top customers/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('renders an empty state when there are no paid invoices', async () => {
    vi.mocked(dashboardApi.getTopCustomers).mockResolvedValue([])

    renderWithClient(<TopCustomersCard />)

    expect(await screen.findByText('No paid invoices yet')).toBeInTheDocument()
  })

  it('renders each customer ranked with their formatted revenue', async () => {
    vi.mocked(dashboardApi.getTopCustomers).mockResolvedValue([
      { customerId: 'c1', customerName: 'Acme Retail', revenue: 5000 },
      { customerId: 'c2', customerName: 'Bright Traders', revenue: 2000 },
    ])

    renderWithClient(<TopCustomersCard />)

    expect(await screen.findByText('Acme Retail')).toBeInTheDocument()
    expect(screen.getByText('₹5,000.00')).toBeInTheDocument()
    expect(screen.getByText('Bright Traders')).toBeInTheDocument()
    expect(screen.getByText('₹2,000.00')).toBeInTheDocument()
  })

  it('truncates long customer names instead of overflowing the card', async () => {
    const longName = 'A Very Long Customer Name That Should Truncate Gracefully On Narrow Screens Pvt Ltd'
    vi.mocked(dashboardApi.getTopCustomers).mockResolvedValue([
      { customerId: 'c1', customerName: longName, revenue: 100 },
    ])

    renderWithClient(<TopCustomersCard />)

    const nameElement = await screen.findByText(longName)
    expect(nameElement.className).toContain('truncate')
  })
})
