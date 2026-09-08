import { X } from 'lucide-react'
import { useId, useState } from 'react'

import { Popover, PopoverAnchor, PopoverContent } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

export interface ComboboxOption<T> {
  value: string
  label: string
  description?: string
  data: T
}

/**
 * A searchable, accessible combobox built on Radix Popover (approved
 * dependency) — not a Radix `Combobox`/`Command` primitive, since neither
 * exists in this package family. Follows the ARIA 1.2 combobox pattern: the
 * search `<input>` itself carries `role="combobox"`, with a separate
 * `role="listbox"` popover positioned via Radix's `Anchor` (not `Trigger` —
 * a combobox opens on typing/focus, not on a discrete click-to-toggle).
 *
 * Fully controlled: the caller owns both the current search text (`query`)
 * and the currently-selected option's display label — this component only
 * renders and handles interaction, it never fetches data itself.
 */
export function Combobox<T>({
  label,
  placeholder,
  query,
  onQueryChange,
  options,
  isLoading,
  onSelect,
  onClear,
  disabled,
  emptyMessage = 'No results found.',
  id,
}: {
  label: string
  placeholder?: string
  query: string
  onQueryChange: (value: string) => void
  options: ComboboxOption<T>[]
  isLoading?: boolean
  onSelect: (option: ComboboxOption<T>) => void
  /** Present = a value is currently selected and can be cleared. */
  onClear?: () => void
  disabled?: boolean
  emptyMessage?: string
  id?: string
}) {
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const listboxId = useId()
  const generatedInputId = useId()
  const inputId = id ?? generatedInputId

  function openWith(nextQuery?: string) {
    if (nextQuery !== undefined) onQueryChange(nextQuery)
    setOpen(true)
    setActiveIndex(-1)
  }

  function selectAt(index: number) {
    const option = options[index]
    if (!option) return
    onSelect(option)
    setOpen(false)
    setActiveIndex(-1)
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      if (!open) {
        setOpen(true)
        return
      }
      setActiveIndex((index) => Math.min(index + 1, options.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((index) => Math.max(index - 1, 0))
    } else if (event.key === 'Enter') {
      if (open && activeIndex >= 0) {
        event.preventDefault()
        selectAt(activeIndex)
      }
    } else if (event.key === 'Escape') {
      if (open) {
        event.preventDefault()
        setOpen(false)
      }
    }
  }

  const activeOptionId =
    open && activeIndex >= 0 && options[activeIndex] ? `${listboxId}-option-${activeIndex}` : undefined

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverAnchor asChild>
        <div className="relative">
          <input
            id={inputId}
            role="combobox"
            aria-expanded={open}
            aria-controls={listboxId}
            aria-autocomplete="list"
            aria-activedescendant={activeOptionId}
            aria-label={label}
            aria-disabled={disabled}
            autoComplete="off"
            disabled={disabled}
            value={query}
            placeholder={placeholder}
            onChange={(event) => openWith(event.target.value)}
            onFocus={() => setOpen(true)}
            onKeyDown={handleKeyDown}
            className={cn(
              'flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm shadow-sm',
              'placeholder:text-muted-foreground',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
              'disabled:cursor-not-allowed disabled:opacity-50',
              onClear && 'pr-8',
            )}
          />
          {onClear && (
            <button
              type="button"
              onClick={onClear}
              className="absolute inset-y-0 right-0 flex w-8 items-center justify-center text-muted-foreground hover:text-foreground"
              aria-label={`Clear ${label}`}
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          )}
        </div>
      </PopoverAnchor>

      <PopoverContent
        className="p-1"
        onOpenAutoFocus={(event) => event.preventDefault()}
        onCloseAutoFocus={(event) => event.preventDefault()}
      >
        <ul role="listbox" id={listboxId} className="max-h-64 overflow-auto">
          {isLoading ? (
            <li className="px-2 py-2 text-sm text-muted-foreground">Loading...</li>
          ) : options.length === 0 ? (
            <li className="px-2 py-2 text-sm text-muted-foreground">{emptyMessage}</li>
          ) : (
            options.map((option, index) => (
              <li
                key={option.value}
                id={`${listboxId}-option-${index}`}
                role="option"
                aria-selected={index === activeIndex}
                onMouseEnter={() => setActiveIndex(index)}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => selectAt(index)}
                className={cn(
                  'cursor-pointer rounded-sm px-2 py-1.5 text-sm',
                  index === activeIndex ? 'bg-muted' : 'hover:bg-muted/60',
                )}
              >
                <div className="text-foreground">{option.label}</div>
                {option.description && (
                  <div className="text-xs text-muted-foreground">{option.description}</div>
                )}
              </li>
            ))
          )}
        </ul>
      </PopoverContent>
    </Popover>
  )
}
