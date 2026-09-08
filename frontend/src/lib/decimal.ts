/**
 * Shared validation for decimal-string form fields (Product price/tax,
 * Quotation discount/quantity). These values are validated and transmitted
 * as strings end-to-end — never parsed into a JS `number` and back — so no
 * precision is ever lost between what a user typed and what the backend's
 * `BigDecimal` field receives (Jackson accepts a JSON string for a
 * `BigDecimal`-typed field and parses it exactly).
 *
 * `Number(value)` is used ONLY for range comparisons here (min/max bound
 * checks), never to reconstruct the value that gets submitted.
 */
export function isValidDecimalString(value: string, maxFractionDigits: number): boolean {
  const pattern = new RegExp(`^\\d+(\\.\\d{1,${maxFractionDigits}})?$`)
  return pattern.test(value.trim())
}
