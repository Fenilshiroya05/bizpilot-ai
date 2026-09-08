/**
 * Centralized date/time formatting — same philosophy as lib/money.ts: format
 * only, never recompute or reinterpret a backend value.
 *
 * The backend sends two different shapes and they are handled differently on
 * purpose:
 *   - `createdAt`/`updatedAt`/`generatedAt` are Java `Instant`s serialized as
 *     ISO-8601 UTC timestamps (e.g. "2026-09-08T08:15:03.10Z") — a real
 *     instant, safe to parse with `new Date(value)` and display in the
 *     browser's local timezone.
 *   - `followUpDate` is a `LocalDate` — a plain calendar date with no time or
 *     timezone component (e.g. "2026-10-01"). Passing that string straight
 *     into `new Date(value)` parses it as UTC midnight, which then renders as
 *     the *previous* day in any timezone behind UTC — a classic bug. Every
 *     LocalDate-shaped value here is parsed by its Y-M-D components directly
 *     into a local `Date`, never through a UTC round-trip.
 */

const dateFormatter = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

const dateTimeFormatter = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
})

function parseLocalDateOnly(value: string): Date {
  // Trusted invariant: every LocalDate the backend sends is exactly
  // "YYYY-MM-DD" — always 3 parts.
  const parts = value.split('-')
  return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]))
}

/** Formats a `LocalDate`-shaped value (e.g. `followUpDate`), timezone-safe. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  return dateFormatter.format(parseLocalDateOnly(value))
}

/** Formats an `Instant`-shaped value (e.g. `createdAt`). */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return dateTimeFormatter.format(new Date(value))
}

/** Today's calendar date as `YYYY-MM-DD`, in the browser's local timezone (never UTC). */
export function todayIsoDate(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** Lexicographic comparison of two `YYYY-MM-DD` strings is safe and timezone-free. */
export function isOnOrBeforeToday(value: string): boolean {
  return value <= todayIsoDate()
}
