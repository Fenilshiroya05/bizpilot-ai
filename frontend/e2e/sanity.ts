import type { Page } from '@playwright/test'

/** Collects console errors, uncaught page exceptions, and failed network requests for a page. */
export interface ConsoleGuard {
  errors: string[]
  pageErrors: string[]
  failedRequests: string[]
}

export function attachConsoleGuard(page: Page): ConsoleGuard {
  const guard: ConsoleGuard = { errors: [], pageErrors: [], failedRequests: [] }

  page.on('console', (message) => {
    if (message.type() === 'error') {
      guard.errors.push(message.text())
    }
  })
  page.on('pageerror', (error) => {
    guard.pageErrors.push(error.message)
  })
  page.on('requestfailed', (request) => {
    guard.failedRequests.push(`${request.method()} ${request.url()} — ${request.failure()?.errorText}`)
  })

  return guard
}

/** Positive px = content wider than viewport (horizontal overflow). */
export async function horizontalOverflowPx(page: Page): Promise<number> {
  return page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
}
