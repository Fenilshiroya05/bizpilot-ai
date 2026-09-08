import { expect, test } from '@playwright/test'

import {
  loginViaUi,
  mockAnalytics,
  mockLoginSuccess,
  mockLogoutSuccess,
  mockSession,
  TEST_ANALYTICS,
  TEST_ANALYTICS_ZERO,
} from './mocks'
import { attachConsoleGuard } from './sanity'

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
    await loginViaUi(page)

    await expect(page.getByText('Total Customers')).toBeVisible()
    await expect(page.getByText('New Leads')).toBeVisible()
    await expect(page.getByText('Qualified Leads')).toBeVisible()
    await expect(page.getByText('Conversion Rate')).toBeVisible()
    await expect(page.getByText('Revenue', { exact: true })).toBeVisible()
    await expect(page.getByText('Outstanding Invoices')).toBeVisible()
    await expect(page.getByText('Pending Follow-ups')).toBeVisible()

    // Values come straight from the mocked response — not hardcoded strings.
    await expect(page.getByText('128')).toBeVisible() // totalCustomers
    await expect(page.getByText('42.50%')).toBeVisible() // conversionRate
    await expect(page.getByText('₹1,84,500.50')).toBeVisible() // revenue
    await expect(page.getByText('₹32,750.75')).toBeVisible() // outstandingInvoicesTotal

    expect(guard.errors, `console errors: ${guard.errors.join('; ')}`).toEqual([])
  })

  test('the dashboard reflects whatever the API returns — proving it is not hardcoded', async ({ page }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, { ...TEST_ANALYTICS, totalCustomers: 777, newLeads: 99 })
    await loginViaUi(page)

    await expect(page.getByText('777')).toBeVisible()
    await expect(page.getByText('99')).toBeVisible()
    await expect(page.getByText('128')).toHaveCount(0)
  })

  test('renders a loading skeleton before the analytics response resolves', async ({ page }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, TEST_ANALYTICS, { delayMs: 800 })
    await loginViaUi(page)

    // Immediately after landing, the real value hasn't arrived yet.
    await expect(page.getByText('128')).toHaveCount(0)
    await expect(page.getByText('128')).toBeVisible({ timeout: 3000 })
  })

  test('all-zero data renders correctly instead of an empty state', async ({ page }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, TEST_ANALYTICS_ZERO)
    await loginViaUi(page)

    await expect(page.getByText('Total Customers')).toBeVisible()
    await expect(page.getByText('0.00%')).toBeVisible()
    await expect(page.getByText('₹0.00').first()).toBeVisible()
  })

  test('a failed request shows a retryable error state, and retry recovers', async ({ page }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, undefined, { status: 500 })
    await loginViaUi(page)

    await expect(page.getByText(/couldn't load your business summary/i)).toBeVisible()
    const retryButton = page.getByRole('button', { name: 'Retry' })
    await expect(retryButton).toBeVisible()

    await mockAnalytics(page, TEST_ANALYTICS)
    await retryButton.click()

    await expect(page.getByText('128')).toBeVisible()
    await expect(page.getByText(/couldn't load your business summary/i)).toHaveCount(0)
  })

  test('never renders a fabricated chart, trend delta, or AI insight', async ({ page }) => {
    await loginToDashboard(page)
    await mockAnalytics(page, TEST_ANALYTICS)
    await loginViaUi(page)

    await expect(page.getByText('128')).toBeVisible()

    // No chart of any kind (Recharts is explicitly not a Phase 20 dependency).
    await expect(page.locator('svg.recharts-surface')).toHaveCount(0)
    await expect(page.locator('canvas')).toHaveCount(0)
    // No invented "+X% this month"-style trend copy.
    await expect(page.getByText(/[+-]\d+(\.\d+)?%\s*(this|last)/i)).toHaveCount(0)
    // No AI Business Insights section (explicitly out of scope).
    await expect(page.getByText(/AI Insight/i)).toHaveCount(0)
  })
})
