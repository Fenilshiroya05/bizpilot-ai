import { describe, expect, it } from 'vitest'

import { formatDate, formatDateTime, isOnOrBeforeToday, todayIsoDate } from './date'

describe('formatDate', () => {
  it('formats a LocalDate string without a timezone shift', () => {
    // The classic bug this guards against: new Date("2026-10-01") parses as
    // UTC midnight, which renders as 30 Sep in any timezone behind UTC.
    expect(formatDate('2026-10-01')).toBe('1 Oct 2026')
  })

  it('returns a dash for null/undefined', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate(undefined)).toBe('—')
  })
})

describe('formatDateTime', () => {
  it('formats an Instant string', () => {
    expect(formatDateTime('2026-01-01T00:00:00Z')).toContain('2026')
  })

  it('returns a dash for null/undefined', () => {
    expect(formatDateTime(null)).toBe('—')
  })
})

describe('todayIsoDate / isOnOrBeforeToday', () => {
  it('produces a YYYY-MM-DD string', () => {
    expect(todayIsoDate()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('treats a far-past date as on-or-before today', () => {
    expect(isOnOrBeforeToday('2000-01-01')).toBe(true)
  })

  it('treats a far-future date as not on-or-before today', () => {
    expect(isOnOrBeforeToday('2999-01-01')).toBe(false)
  })
})
