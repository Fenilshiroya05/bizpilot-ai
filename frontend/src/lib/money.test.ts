import { describe, expect, it } from 'vitest'

import { formatCount, formatInr, formatPercentage } from './money'

describe('formatInr', () => {
  it('formats a value using Indian rupee grouping and two decimals', () => {
    expect(formatInr(125000)).toBe('₹1,25,000.00')
  })

  it('formats zero correctly', () => {
    expect(formatInr(0)).toBe('₹0.00')
  })
})

describe('formatCount', () => {
  it('formats a count using Indian digit grouping', () => {
    expect(formatCount(100000)).toBe('1,00,000')
  })
})

describe('formatPercentage', () => {
  it('formats a percentage with two decimal places', () => {
    expect(formatPercentage(40)).toBe('40.00%')
  })

  it('returns 0.00% when the value is zero', () => {
    expect(formatPercentage(0)).toBe('0.00%')
  })
})
