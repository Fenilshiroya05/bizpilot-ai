import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { Combobox, type ComboboxOption } from './combobox'

interface Item {
  id: string
}

const OPTIONS: ComboboxOption<Item>[] = [
  { value: '1', label: 'Apple', data: { id: '1' } },
  { value: '2', label: 'Banana', data: { id: '2' } },
]

function Harness({
  options = OPTIONS,
  isLoading = false,
  disabled = false,
  onSelect = vi.fn(),
  onClear,
}: {
  options?: ComboboxOption<Item>[]
  isLoading?: boolean
  disabled?: boolean
  onSelect?: (option: ComboboxOption<Item>) => void
  onClear?: () => void
}) {
  const [query, setQuery] = useState('')
  return (
    <Combobox
      label="Fruit"
      placeholder="Search fruit..."
      query={query}
      onQueryChange={setQuery}
      options={options}
      isLoading={isLoading}
      onSelect={(option) => {
        onSelect(option)
        setQuery(option.label)
      }}
      onClear={onClear}
      disabled={disabled}
    />
  )
}

describe('Combobox', () => {
  it('opens the listbox on focus and shows options', async () => {
    render(<Harness />)
    await userEvent.click(screen.getByRole('combobox', { name: 'Fruit' }))
    expect(await screen.findByRole('option', { name: 'Apple' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Banana' })).toBeInTheDocument()
  })

  it('filters as the caller updates options in response to typed query', async () => {
    const { rerender } = render(<Harness options={OPTIONS} />)
    const input = screen.getByRole('combobox', { name: 'Fruit' })
    await userEvent.type(input, 'App')
    rerender(<Harness options={[OPTIONS[0]!]} />)
    expect(await screen.findByRole('option', { name: 'Apple' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Banana' })).not.toBeInTheDocument()
  })

  it('shows the empty message when there are no options', async () => {
    render(<Harness options={[]} />)
    await userEvent.click(screen.getByRole('combobox', { name: 'Fruit' }))
    expect(await screen.findByText('No results found.')).toBeInTheDocument()
  })

  it('shows a loading indicator instead of options while loading', async () => {
    render(<Harness isLoading options={[]} />)
    await userEvent.click(screen.getByRole('combobox', { name: 'Fruit' }))
    expect(await screen.findByText('Loading...')).toBeInTheDocument()
  })

  it('supports arrow-key navigation and Enter to select', async () => {
    const onSelect = vi.fn()
    render(<Harness onSelect={onSelect} />)
    const input = screen.getByRole('combobox', { name: 'Fruit' })
    await userEvent.click(input)
    await screen.findByRole('option', { name: 'Apple' })
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{Enter}')
    expect(onSelect).toHaveBeenCalledWith(OPTIONS[1]!)
  })

  it('selects an option via mouse click', async () => {
    const onSelect = vi.fn()
    render(<Harness onSelect={onSelect} />)
    await userEvent.click(screen.getByRole('combobox', { name: 'Fruit' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Apple' }))
    expect(onSelect).toHaveBeenCalledWith(OPTIONS[0]!)
  })

  it('closes the listbox on Escape', async () => {
    render(<Harness />)
    const input = screen.getByRole('combobox', { name: 'Fruit' })
    await userEvent.click(input)
    await screen.findByRole('option', { name: 'Apple' })
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('option', { name: 'Apple' })).not.toBeInTheDocument())
  })

  it('shows a Clear button only when onClear is provided, and calls it', async () => {
    const onClear = vi.fn()
    render(<Harness onClear={onClear} />)
    const clearButton = screen.getByRole('button', { name: 'Clear Fruit' })
    await userEvent.click(clearButton)
    expect(onClear).toHaveBeenCalled()
  })

  it('does not render a Clear button when onClear is absent', () => {
    render(<Harness />)
    expect(screen.queryByRole('button', { name: 'Clear Fruit' })).not.toBeInTheDocument()
  })

  it('disables the input and blocks interaction when disabled', () => {
    render(<Harness disabled />)
    expect(screen.getByRole('combobox', { name: 'Fruit' })).toBeDisabled()
  })
})
