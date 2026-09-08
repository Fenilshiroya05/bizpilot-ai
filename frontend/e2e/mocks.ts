import type { Page, Route } from '@playwright/test'

/**
 * Deterministic API fixtures for browser smoke tests only. These mocks live
 * entirely in frontend test code (`page.route`, intercepted client-side by
 * the browser before any request leaves the machine) — they never touch
 * production API code, and a real backend is not required to run these
 * tests. Test-only credentials; nothing here is a real secret.
 */

export const TEST_EMAIL = 'e2e-test@example.com'
export const TEST_PASSWORD = 'E2ePassw0rd!'

export const TEST_USER = {
  id: 'e2e-user-1111-2222-3333-444444444444',
  email: TEST_EMAIL,
  firstName: 'Ellie',
  lastName: 'Example',
  roles: ['OWNER'],
  status: 'ACTIVE',
  organizationId: 'e2e-org-1111-2222-3333-444444444444',
  createdAt: '2026-01-01T00:00:00Z',
}

export const TEST_ORGANIZATION = {
  id: TEST_USER.organizationId,
  name: 'Acme Testing Co',
  createdAt: '2026-01-01T00:00:00Z',
}

/** Realistic, non-zero values for all seven backend-supported metrics. */
export const TEST_ANALYTICS = {
  totalCustomers: 128,
  newLeads: 14,
  qualifiedLeads: 6,
  conversionRate: 42.5,
  revenue: 184500.5,
  outstandingInvoicesCount: 5,
  outstandingInvoicesTotal: 32750.75,
  pendingFollowUps: 3,
}

export const TEST_ANALYTICS_ZERO = {
  totalCustomers: 0,
  newLeads: 0,
  qualifiedLeads: 0,
  conversionRate: 0,
  revenue: 0,
  outstandingInvoicesCount: 0,
  outstandingInvoicesTotal: 0,
  pendingFollowUps: 0,
}

function json(route: Route, status: number, body: unknown) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

function apiError(status: number, code: string, message: string, path: string) {
  return { timestamp: new Date().toISOString(), status, code, message, path }
}

export async function mockLoginSuccess(page: Page) {
  await page.route('**/api/v1/auth/login', (route) =>
    json(route, 200, { accessToken: 'e2e-access-token', refreshToken: 'e2e-refresh-token' }),
  )
}

export async function mockLoginFailure(page: Page) {
  await page.route('**/api/v1/auth/login', (route) =>
    json(route, 401, apiError(401, 'INVALID_CREDENTIALS', 'Invalid email or password', '/api/v1/auth/login')),
  )
}

export async function mockLogoutSuccess(page: Page) {
  await page.route('**/api/v1/auth/logout', (route) => route.fulfill({ status: 204 }))
}

export async function mockSession(page: Page) {
  await page.route('**/api/v1/auth/me', (route) => json(route, 200, TEST_USER))
  await page.route('**/api/v1/organizations/current', (route) => json(route, 200, TEST_ORGANIZATION))
}

export async function mockAnalytics(
  page: Page,
  body: unknown = TEST_ANALYTICS,
  options: { status?: number; delayMs?: number } = {},
) {
  await page.route('**/api/v1/analytics/summary', async (route) => {
    if (options.delayMs) {
      await new Promise((resolve) => setTimeout(resolve, options.delayMs))
    }
    if (options.status && options.status >= 400) {
      return json(
        route,
        options.status,
        apiError(options.status, 'INTERNAL_ERROR', 'An unexpected error occurred', '/api/v1/analytics/summary'),
      )
    }
    return json(route, 200, body)
  })
}

/** Full mock set for a successful, authenticated session landing on the dashboard. */
export async function mockAuthenticatedSession(page: Page, analytics: unknown = TEST_ANALYTICS) {
  await mockLoginSuccess(page)
  await mockSession(page)
  await mockAnalytics(page, analytics)
  await mockLogoutSuccess(page)
}

export async function loginViaUi(page: Page) {
  await page.goto('/auth/login')
  await page.getByLabel('Email').fill(TEST_EMAIL)
  await page.getByLabel('Password', { exact: true }).fill(TEST_PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.waitForURL('**/dashboard')
}
