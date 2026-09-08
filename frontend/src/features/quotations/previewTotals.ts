/**
 * PREVIEW ONLY — never sent to the backend, never treated as authoritative.
 * Mirrors the intent of backend/src/main/java/com/bizpilot/sales/service/QuotationCalculator.java
 * (line subtotal → per-line discount share → tax on the discounted amount →
 * summed quotation totals) closely enough for a responsive live estimate
 * while the user is still editing. The backend's own response after
 * create/update is always what gets displayed and stored — this function's
 * output is discarded the moment a real response arrives.
 */
export interface PreviewLineInput {
  quantity: number
  unitPrice: number
  taxPercentage: number
}

export interface PreviewTotals {
  subtotal: number
  discountAmount: number
  taxAmount: number
  grandTotal: number
}

function round4(value: number): number {
  return Math.round(value * 10000) / 10000
}

export function calculatePreviewTotals(lines: PreviewLineInput[], discountPercentage: number): PreviewTotals {
  let subtotal = 0
  let taxAmount = 0

  for (const line of lines) {
    if (!Number.isFinite(line.quantity) || !Number.isFinite(line.unitPrice) || line.quantity <= 0) continue
    const lineSubtotal = round4(line.quantity * line.unitPrice)
    const lineDiscount = round4((lineSubtotal * discountPercentage) / 100)
    const lineTaxableAmount = lineSubtotal - lineDiscount
    const lineTax = round4((lineTaxableAmount * line.taxPercentage) / 100)
    subtotal += lineSubtotal
    taxAmount += lineTax
  }

  subtotal = round4(subtotal)
  taxAmount = round4(taxAmount)
  const discountAmount = round4((subtotal * discountPercentage) / 100)
  const grandTotal = round4(subtotal - discountAmount + taxAmount)

  return { subtotal, discountAmount, taxAmount, grandTotal }
}
