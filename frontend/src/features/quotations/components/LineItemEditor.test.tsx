import { zodResolver } from '@hookform/resolvers/zod'
import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useForm } from 'react-hook-form'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { quotationFormSchema, type QuotationFormValues } from '@/features/quotations/schemas'
import { createTestQueryClient } from '@/test/render'
import type { Page, ProductResponse } from '@/types/api'
import { LineItemEditor } from './LineItemEditor'
import * as productsApi from '@/features/products/api'

// Preserves the real `productKeys` export (needed as a valid TanStack Query
// key) while only mocking the network call itself.
vi.mock('@/features/products/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/products/api')>()
  return { ...actual, searchProducts: vi.fn() }
})

const WIDGET: ProductResponse = {
  id: 'p1',
  sku: 'SKU-1',
  name: 'Widget',
  description: null,
  unit: 'pcs',
  price: 100,
  taxPercentage: 18,
  status: 'ACTIVE',
  categoryId: null,
  organizationId: 'org-1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function page(content: ProductResponse[]): Page<ProductResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 10, first: true, last: true, empty: content.length === 0 }
}

function Harness() {
  const form = useForm<QuotationFormValues>({
    resolver: zodResolver(quotationFormSchema),
    defaultValues: { customerId: 'c1', customerLabel: 'Acme', validUntil: '', discountPercentage: '', items: [] },
  })
  return (
    <form onSubmit={form.handleSubmit(() => {})} noValidate>
      <LineItemEditor form={form} />
      <button type="submit">Save</button>
    </form>
  )
}

function renderHarness() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <Harness />
    </QueryClientProvider>,
  )
}

async function selectProduct() {
  await userEvent.type(screen.getByRole('combobox', { name: 'Product' }), 'Wid')
  await userEvent.click(await screen.findByRole('option', { name: /Widget/ }))
}

describe('LineItemEditor', () => {
  beforeEach(() => {
    vi.mocked(productsApi.searchProducts).mockResolvedValue(page([WIDGET]))
  })

  it('starts with no lines and shows a validation message when submitted empty', async () => {
    renderHarness()
    expect(screen.getByText('No line items yet.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Add at least one line item')).toBeInTheDocument()
  })

  it('adds a line item and allows selecting a product and entering a quantity', async () => {
    renderHarness()
    await userEvent.click(screen.getByRole('button', { name: 'Add line item' }))
    expect(screen.queryByText('No line items yet.')).not.toBeInTheDocument()

    await selectProduct()
    expect(screen.getByRole('combobox', { name: 'Product' })).toHaveValue('Widget')

    const quantityInput = screen.getByRole('textbox', { name: 'Quantity for line 1' })
    await userEvent.clear(quantityInput)
    await userEvent.type(quantityInput, '3')
    expect(quantityInput).toHaveValue('3')
  })

  it('gives each remove button a unique, distinguishing accessible name', async () => {
    renderHarness()
    await userEvent.click(screen.getByRole('button', { name: 'Add line item' }))
    await userEvent.click(screen.getByRole('button', { name: 'Add line item' }))

    expect(screen.getByRole('button', { name: 'Remove line 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remove line 2' })).toBeInTheDocument()
  })

  it('updates the remove-button label to include the selected product name', async () => {
    renderHarness()
    await userEvent.click(screen.getByRole('button', { name: 'Add line item' }))
    await selectProduct()
    expect(screen.getByRole('button', { name: 'Remove line 1: Widget' })).toBeInTheDocument()
  })

  it('removes a line item', async () => {
    renderHarness()
    await userEvent.click(screen.getByRole('button', { name: 'Add line item' }))
    expect(screen.getAllByRole('combobox', { name: 'Product' })).toHaveLength(1)

    await userEvent.click(screen.getByRole('button', { name: 'Remove line 1' }))
    await waitFor(() => expect(screen.getByText('No line items yet.')).toBeInTheDocument())
  })

  it('rejects a non-numeric quantity on submit', async () => {
    renderHarness()
    await userEvent.click(screen.getByRole('button', { name: 'Add line item' }))
    await selectProduct()
    const quantityInput = screen.getByRole('textbox', { name: 'Quantity for line 1' })
    await userEvent.clear(quantityInput)
    await userEvent.type(quantityInput, 'abc')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Enter a valid quantity (up to 4 decimal places)')).toBeInTheDocument()
  })
})
