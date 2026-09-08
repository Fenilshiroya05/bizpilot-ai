/**
 * Centralized money/number formatting. The backend is always the source of
 * truth for every monetary figure (NUMERIC(19,4), computed server-side) —
 * this module only ever formats an already-computed value for display. It
 * never adds, multiplies, rounds for business purposes, or otherwise
 * recomputes anything; `Intl.NumberFormat` here is a presentation transform,
 * not a calculation.
 */

const inrCurrencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const inrNumberFormatter = new Intl.NumberFormat('en-IN')

export function formatInr(value: number): string {
  return inrCurrencyFormatter.format(value)
}

/** Indian digit grouping (e.g. 1,00,000) for plain counts — not currency. */
export function formatCount(value: number): string {
  return inrNumberFormatter.format(value)
}

/** `conversionRate` arrives as a plain number already rounded server-side (e.g. 40.00). */
export function formatPercentage(value: number): string {
  return `${value.toFixed(2)}%`
}
