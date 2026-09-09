import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach } from 'vitest'

import { createTestQueryClient } from '@/test/render'
import type { LeadResponse, Page } from '@/types/api'
import { LeadCombobox } from './LeadCombobox'
import * as leadsApi from '@/features/leads/api'

// Preserves the real `leadKeys` builder (needed as a valid TanStack Query
// key) while only mocking the network call itself — same rationale as
// QuotationForm.test.tsx's identical pattern for CustomerCombobox/ProductCombobox.
vi.mock('@/features/leads/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/leads/api')>()
  return { ...actual, searchLeads: vi.fn() }
})

const LEAD: LeadResponse = {
  id: 'l1',
  name: 'Priya Sharma',
  company: 'Sharma Textiles',
  email: 'priya@sharmatextiles.example',
  phone: null,
  status: 'NEW',
  source: 'WEBSITE',
  priority: 'MEDIUM',
  followUpDate: null,
  assignedToUserId: null,
  archivedAt: null,
  organizationId: 'org-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function leadPage(content: LeadResponse[]): Page<LeadResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 10, first: true, last: true, empty: content.length === 0 }
}

function renderCombobox(props: Partial<Parameters<typeof LeadCombobox>[0]> = {}) {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <LeadCombobox onSelect={vi.fn()} {...props} />
    </QueryClientProvider>,
  )
}

describe('LeadCombobox', () => {
  beforeEach(() => {
    vi.mocked(leadsApi.searchLeads).mockResolvedValue(leadPage([LEAD]))
  })

  it('has proper ARIA combobox semantics', () => {
    renderCombobox()
    const input = screen.getByRole('combobox', { name: 'Lead' })
    expect(input).toHaveAttribute('aria-autocomplete', 'list')
    expect(input).toHaveAttribute('aria-expanded', 'false')
  })

  it('does not search until the user types (empty query is not searched)', async () => {
    renderCombobox()
    await userEvent.click(screen.getByRole('combobox', { name: 'Lead' }))
    expect(await screen.findByText('Type to search leads.')).toBeInTheDocument()
    expect(leadsApi.searchLeads).not.toHaveBeenCalled()
  })

  it('searches and shows matching leads with a description line', async () => {
    renderCombobox()
    await userEvent.type(screen.getByRole('combobox', { name: 'Lead' }), 'Priya')
    await waitFor(() => expect(leadsApi.searchLeads).toHaveBeenCalledWith(expect.objectContaining({ q: 'Priya' })))
    expect(await screen.findByRole('option', { name: /Priya Sharma/ })).toBeInTheDocument()
    expect(screen.getByText('Sharma Textiles')).toBeInTheDocument()
  })

  it('shows a loading state while the search is in flight', async () => {
    let resolveSearch!: (value: Page<LeadResponse>) => void
    vi.mocked(leadsApi.searchLeads).mockReturnValue(new Promise((resolve) => { resolveSearch = resolve }))
    renderCombobox()
    await userEvent.type(screen.getByRole('combobox', { name: 'Lead' }), 'Priya')
    expect(await screen.findByText('Loading...')).toBeInTheDocument()
    resolveSearch(leadPage([LEAD]))
  })

  it('shows an empty message when no leads match', async () => {
    vi.mocked(leadsApi.searchLeads).mockResolvedValue(leadPage([]))
    renderCombobox()
    await userEvent.type(screen.getByRole('combobox', { name: 'Lead' }), 'Nobody')
    expect(await screen.findByText('No leads found.')).toBeInTheDocument()
  })

  it('calls onSelect with the full lead and fills the input with its name', async () => {
    const onSelect = vi.fn()
    renderCombobox({ onSelect })
    await userEvent.type(screen.getByRole('combobox', { name: 'Lead' }), 'Priya')
    await userEvent.click(await screen.findByRole('option', { name: /Priya Sharma/ }))
    expect(onSelect).toHaveBeenCalledWith(LEAD)
    expect(screen.getByRole('combobox', { name: 'Lead' })).toHaveValue('Priya Sharma')
  })

  it('supports keyboard navigation (arrow keys + Enter)', async () => {
    const onSelect = vi.fn()
    renderCombobox({ onSelect })
    const input = screen.getByRole('combobox', { name: 'Lead' })
    await userEvent.type(input, 'Priya')
    await screen.findByRole('option', { name: /Priya Sharma/ })
    await userEvent.keyboard('{ArrowDown}{Enter}')
    expect(onSelect).toHaveBeenCalledWith(LEAD)
  })

  it('shows a Clear button only when onClear is provided, and calls it', async () => {
    const onClear = vi.fn()
    renderCombobox({ onClear, initialLabel: 'Priya Sharma' })
    await userEvent.click(screen.getByRole('button', { name: 'Clear Lead' }))
    expect(onClear).toHaveBeenCalled()
  })

  it('does not render a Clear button when onClear is absent', () => {
    renderCombobox()
    expect(screen.queryByRole('button', { name: 'Clear Lead' })).not.toBeInTheDocument()
  })

  it('disables the input when disabled', () => {
    renderCombobox({ disabled: true })
    expect(screen.getByRole('combobox', { name: 'Lead' })).toBeDisabled()
  })
})
