import { describe, expect, it } from 'vitest'

import { calculatePreviewTotals } from './previewTotals'

// Tests the local, non-authoritative preview estimate only — never the
// backend's real QuotationCalculator, which is the actual source of truth.
describe('calculatePreviewTotals (preview-only estimate)', () => {
  it('computes subtotal/discount/tax/grand-total for a single line with no discount', () => {
    const result = calculatePreviewTotals([{ quantity: 2, unitPrice: 100, taxPercentage: 18 }], 0)
    expect(result.subtotal).toBe(200)
    expect(result.discountAmount).toBe(0)
    expect(result.taxAmount).toBe(36)
    expect(result.grandTotal).toBe(236)
  })

  it('applies tax on the discounted line amount, not the raw subtotal', () => {
    const result = calculatePreviewTotals([{ quantity: 1, unitPrice: 100, taxPercentage: 10 }], 10)
    // discount = 10, taxable = 90, tax = 9
    expect(result.discountAmount).toBe(10)
    expect(result.taxAmount).toBe(9)
    expect(result.grandTotal).toBe(99)
  })

  it('sums multiple lines with different tax rates', () => {
    const result = calculatePreviewTotals(
      [
        { quantity: 2, unitPrice: 50, taxPercentage: 5 },
        { quantity: 1, unitPrice: 200, taxPercentage: 18 },
      ],
      0,
    )
    expect(result.subtotal).toBe(300)
    expect(result.taxAmount).toBe(5 + 36)
    expect(result.grandTotal).toBe(300 + 5 + 36)
  })

  it('ignores lines with zero or negative quantity', () => {
    const result = calculatePreviewTotals(
      [
        { quantity: 0, unitPrice: 100, taxPercentage: 18 },
        { quantity: -1, unitPrice: 100, taxPercentage: 18 },
      ],
      0,
    )
    expect(result.subtotal).toBe(0)
    expect(result.grandTotal).toBe(0)
  })

  it('ignores lines with non-finite quantity or price (e.g. mid-typing NaN)', () => {
    const result = calculatePreviewTotals([{ quantity: NaN, unitPrice: 100, taxPercentage: 18 }], 0)
    expect(result.subtotal).toBe(0)
    expect(result.grandTotal).toBe(0)
  })

  it('returns all zeros for an empty line list', () => {
    const result = calculatePreviewTotals([], 0)
    expect(result).toEqual({ subtotal: 0, discountAmount: 0, taxAmount: 0, grandTotal: 0 })
  })
})
