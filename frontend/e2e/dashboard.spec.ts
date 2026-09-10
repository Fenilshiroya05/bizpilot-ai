import { expect, test } from '@playwright/test'

import {
  loginViaUi,
  mockAnalytics,
  mockDashboardAnalytics,
  mockDashboardAnalyticsEmpty,
  mockLoginSuccess,
  mockLogoutSuccess,
  mockSession,
  TEST_ANALYTICS,
  TEST_ANALYTICS_ZERO,
} from './mocks'
import { attachConsoleGuard, horizontalOverflowPx } from './sanity'

async function loginToDashboard(page: import('@playwright/test').Page) {
  await mockLoginSuccess(page)
  await mockSession(page)
  await mockLogoutSuccess(page)
}

test.describe('dashboard', () => {
  test('shows the seven KPI concepts, each driven by the mocked analytics response', async ({ page }) => {
    const guard = attachConsoleGuard(page)
    await loginToDashboard(page)
    await mockAnalytics(page, TEST_ANALYTICS)
    await mockDashboardAnalytics(page)
    await loginViaUi(page)

    await expect(page.getByText('Total Customers')).toBeVisible()
    await expect(page.getByText('New Leads')).toBeVisible()
    await expect(page.getByText('Qualified Leads')).toBeVisible()
    await expect(page.getByText('Conversion Rate')).toBeVisible()
    await expect(page.getByText('Revenue', { exact: true })).toBeVisible()
    await expect(page.getByText('Outstanding Invoices')).toBeVisible()
    await expect(page.getByText('Pending Follow-ups')).toBeVisible()

    // Values come straight from the mocked response — not hardcoded strings.
    // `exact: true` disambiguates from the Business Insights sentences below,
    // which also embed these same figures in full-sentence text nodes.
    await expect(page.getByText('128')).toBeVisible() // totalCustomers
    await expect(page.getByText('42.50%')).toBeVisible() // conversionRate
    await expect(page.getByText('₹1,84,500.50', { exact: true })).toBeVisible() // revenue
    await expect(page.getByText('₹32,750.75', { exact: true })).toBeVisible() // outstandingInvoicesTotal

    expect(guard.errors, `console errors: ${guard.errors.join('; ')}`).toEqual([])
  })

  test('the dashboard reflects whatever the API returns — proving it is not hardcoded', async ({ page }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, { ...TEST_ANALYTICS, totalCustomers: 777, newLeads: 99 })
    await mockDashboardAnalytics(page)
    await loginViaUi(page)

    await expect(page.getByText('777')).toBeVisible()
    await expect(page.getByText('99')).toBeVisible()
    await expect(page.getByText('128')).toHaveCount(0)
  })

  test('renders a loading skeleton before the analytics response resolves', async ({ page }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, TEST_ANALYTICS, { delayMs: 800 })
    await mockDashboardAnalytics(page)
    await loginViaUi(page)

    // Immediately after landing, the real value hasn't arrived yet.
    await expect(page.getByText('128')).toHaveCount(0)
    await expect(page.getByText('128')).toBeVisible({ timeout: 3000 })
  })

  test('all-zero data renders correctly instead of an empty state', async ({ page }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, TEST_ANALYTICS_ZERO)
    await mockDashboardAnalyticsEmpty(page)
    await loginViaUi(page)

    await expect(page.getByText('Total Customers')).toBeVisible()
    await expect(page.getByText('0.00%')).toBeVisible()
    await expect(page.getByText('₹0.00').first()).toBeVisible()
  })

  test('a failed request shows a retryable error state, and retry recovers', async ({ page }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, undefined, { status: 500 })
    await mockDashboardAnalytics(page)
    await loginViaUi(page)

    await expect(page.getByText(/couldn't load your business summary/i)).toBeVisible()
    // AiInsightsPanel shares the same analytics/summary query key and shows
    // its own independent error too (self-contained widgets) — at least one
    // Retry button is guaranteed; `.first()` still recovers the shared cache.
    const retryButton = page.getByRole('button', { name: 'Retry' }).first()
    await expect(retryButton).toBeVisible()

    await mockAnalytics(page, TEST_ANALYTICS)
    await retryButton.click()

    await expect(page.getByText('128')).toBeVisible()
    await expect(page.getByText(/couldn't load your business summary/i)).toHaveCount(0)
  })

  test('Phase 26: renders every chart/widget alongside the still-unchanged KPI row', async ({ page }) => {
    const guard = attachConsoleGuard(page)
    await loginToDashboard(page)
    await mockAnalytics(page, TEST_ANALYTICS)
    await mockDashboardAnalytics(page)
    await loginViaUi(page)

    // KPI row (Phase 20) is untouched.
    await expect(page.getByText('128')).toBeVisible()
    await expect(page.getByText('Total Customers')).toBeVisible()

    // Phase 26 charts/widgets, each independently rendered.
    await expect(page.getByText('Revenue Trend')).toBeVisible()
    await expect(page.getByText('Lead Funnel')).toBeVisible()
    await expect(page.getByText('Lead Sources')).toBeVisible()
    await expect(page.getByText('Sales Pipeline')).toBeVisible()
    await expect(page.getByText('Top Customers')).toBeVisible()
    await expect(page.getByText('Business Insights')).toBeVisible()
    // exact: true — the customer name also appears inside a Business
    // Insights sentence, a distinct text node from the Top Customers row.
    await expect(page.getByText('Acme Retail Pvt Ltd', { exact: true })).toBeVisible()

    // Deterministic, data-grounded insight text — never claims to be AI-generated.
    await expect(page.getByText(/Revenue for the last 30 days is/)).toBeVisible()
    await expect(page.getByText('Based on your data — not AI-generated')).toBeVisible()

    // Real chart SVGs are actually mounted (Recharts is now a real dependency).
    await expect(page.locator('svg.recharts-surface').first()).toBeVisible()

    expect(guard.errors, `console errors: ${guard.errors.join('; ')}`).toEqual([])
    expect(guard.pageErrors, `page errors: ${guard.pageErrors.join('; ')}`).toEqual([])
    expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
  })

  test('Phase 26: each new widget shows its own empty state when the tenant has no matching data', async ({
    page,
  }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, TEST_ANALYTICS_ZERO)
    await mockDashboardAnalyticsEmpty(page)
    await loginViaUi(page)

    await expect(page.getByText('No revenue recorded in the last 30 days')).toBeVisible()
    await expect(page.getByText('No leads yet').first()).toBeVisible()
    await expect(page.getByText('No quotations yet')).toBeVisible()
    await expect(page.getByText('No paid invoices yet')).toBeVisible()
    // Insights still render the base, always-available sentences from the summary.
    await expect(page.getByText('Revenue for the last 30 days is ₹0.00.')).toBeVisible()
  })

  test('Phase 26: a failed chart request shows its own retryable error without breaking the KPI row', async ({
    page,
  }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, TEST_ANALYTICS)
    await mockDashboardAnalytics(page)
    await page.route('**/api/v1/analytics/revenue-trend', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 500,
          code: 'INTERNAL_ERROR',
          message: 'An unexpected error occurred',
          path: '/api/v1/analytics/revenue-trend',
        }),
      }),
    )
    await loginViaUi(page)

    await expect(page.getByText('128')).toBeVisible()
    await expect(page.getByText(/couldn't load the revenue trend/i)).toBeVisible()
    // Every other widget is unaffected — self-contained error handling.
    await expect(page.getByText('Lead Funnel')).toBeVisible()
    await expect(page.getByText('Top Customers')).toBeVisible()
  })
})
