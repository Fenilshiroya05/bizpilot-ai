import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { Pagination } from './Pagination'
import type { Page } from '@/types/api'

function makePage(overrides: Partial<Page<unknown>> = {}): Page<unknown> {
  return {
    content: Array.from({ length: 20 }),
    totalElements: 45,
    totalPages: 3,
    number: 0,
    size: 20,
    first: true,
    last: false,
    empty: false,
    ...overrides,
  }
}

describe('Pagination', () => {
  it('renders nothing when there are no records', () => {
    const { container } = render(
      <Pagination page={makePage({ totalElements: 0, content: [] })} onPageChange={vi.fn()} />,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('disables Previous on the first page and Next on the last page', () => {
    render(<Pagination page={makePage()} onPageChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next page' })).not.toBeDisabled()
  })

  it('calls onPageChange with the next/previous page index', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={makePage({ number: 1, first: false, last: false })} onPageChange={onPageChange} />)

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))
    expect(onPageChange).toHaveBeenCalledWith(2)

    await userEvent.click(screen.getByRole('button', { name: 'Previous page' }))
    expect(onPageChange).toHaveBeenCalledWith(0)
  })

  it('shows the current page and total pages', () => {
    render(<Pagination page={makePage()} onPageChange={vi.fn()} />)
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
  })
})
