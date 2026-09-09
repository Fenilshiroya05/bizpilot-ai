/**
 * Same browser-download mechanism as QuotationDetailPage's PDF download
 * (Phase 22) — reused, not duplicated, since both list and detail pages
 * trigger a download. Unlike the quotation PDF (a synthetic client-side
 * name), Documents have a real server-known filename, so it's passed in
 * rather than invented here.
 */
export function triggerBrowserDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
