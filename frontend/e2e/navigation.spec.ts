import { expect, test } from '@playwright/test'

import { loginViaUi, mockAuthenticatedSession, mockLoginSuccess, TEST_ANALYTICS } from './mocks'

// Customers, Leads, Products, Quotations, Tasks, and Documents are
// deliberately absent here — Phase 21 (Customers/Leads), Phase 22
// (Products/Quotations), Phase 23 (Tasks), and Phase 24 (Documents)
// replaced those ComingSoon placeholders with real pages (see
// customers.spec.ts, leads.spec.ts, products.spec.ts, quotations.spec.ts,
// tasks.spec.ts, documents.spec.ts). Only Invoices remains ComingSoon.
const COMING_SOON_ROUTES: Array<{ label: string; title: string; phase: string }> = [
  { label: 'Invoices', title: 'Invoices', phase: 'Phase 23' },
]

/** A user with no roles at all — proves the RBAC nav mirror fails closed. */
async function mockNoRoleSession(page: import('@playwright/test').Page) {
  await mockLoginSuccess(page)
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'e2e-no-role-user',
        email: 'no-roles@example.com',
        firstName: 'No',
        lastName: 'Roles',
        roles: [],
        status: 'ACTIVE',
        organizationId: 'e2e-org-1111-2222-3333-444444444444',
        createdAt: '2026-01-01T00:00:00Z',
      }),
    }),
  )
  await page.route('**/api/v1/organizations/current', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ id: 'e2e-org-1', name: 'Acme Testing Co', createdAt: '2026-01-01T00:00:00Z' }),
    }),
  )
  await page.route('**/api/v1/analytics/summary', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(TEST_ANALYTICS) }),
  )
}

test.describe('navigation', () => {
  test('the sidebar lists every primary destination and marks Dashboard active', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await loginViaUi(page)

    const sidebar = page.getByRole('complementary')
    for (const label of [
      'Dashboard',
      'Customers',
      'Leads',
      'Products',
      'Quotations',
      'Invoices',
      'Tasks',
      'Documents',
      'Settings',
    ]) {
      await expect(sidebar.getByRole('link', { name: label })).toBeVisible()
    }
    await expect(sidebar.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
  })

  for (const route of COMING_SOON_ROUTES) {
    test(`clicking ${route.label} in the sidebar renders the honest Coming Soon state for ${route.phase}`, async ({
      page,
    }) => {
      await mockAuthenticatedSession(page)
      await loginViaUi(page)

      // Client-side SPA navigation (not page.goto) — tokens live in memory
      // only, so a real page reload would lose the session, exactly as
      // documented for this backend contract.
      await page.getByRole('complementary').getByRole('link', { name: route.label }).click()

      await expect(page.getByRole('heading', { name: route.title })).toBeVisible()
      await expect(page.getByText(`${route.title} is coming in ${route.phase}`)).toBeVisible()
    })
  }

  test('Settings renders the real read-only profile and organization info', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await loginViaUi(page)

    await page.getByRole('complementary').getByRole('link', { name: 'Settings' }).click()

    const main = page.getByRole('main')
    await expect(main.getByRole('heading', { name: 'Settings' })).toBeVisible()
    await expect(main.getByRole('heading', { name: 'Profile' })).toBeVisible()
    await expect(main.getByRole('heading', { name: 'Organization' })).toBeVisible()
    await expect(main.getByText('Ellie Example')).toBeVisible()
    await expect(main.getByText('Acme Testing Co')).toBeVisible()
    await expect(main.getByText('OWNER')).toBeVisible()
  })

  // Phase 25 replaced the placeholder with the real assistant (see
  // ai-assistant.spec.ts for full coverage) — this test now only confirms
  // the entry point itself: opens the real drawer, and merely opening it
  // (without sending a message) never calls the chat API.
  test('the AI Assistant entry opens the real drawer without calling the chat API until a message is sent', async ({
    page,
  }) => {
    let chatCalled = false
    await page.route('**/api/v1/ai/chat', (route) => {
      chatCalled = true
      return route.continue()
    })
    await mockAuthenticatedSession(page)
    await loginViaUi(page)

    await page.getByRole('complementary').getByRole('button', { name: 'Open AI Assistant' }).click()

    const drawer = page.getByRole('dialog', { name: 'AI Assistant' })
    await expect(drawer).toBeVisible()
    await expect(drawer.getByText('Ask about your business')).toBeVisible()
    expect(chatCalled).toBe(false)

    await page.keyboard.press('Escape')
    await expect(drawer).not.toBeVisible()
    expect(chatCalled).toBe(false)
  })

  test('the desktop sidebar can be collapsed and expanded, and collapsed links keep an accessible name', async ({
    page,
  }) => {
    await mockAuthenticatedSession(page)
    await loginViaUi(page)

    await page.getByRole('button', { name: 'Collapse sidebar' }).click()

    const sidebar = page.getByRole('complementary')
    await expect(sidebar.getByRole('link', { name: 'Dashboard' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Expand sidebar' })).toBeVisible()

    await page.getByRole('button', { name: 'Expand sidebar' }).click()
    await expect(page.getByRole('button', { name: 'Collapse sidebar' })).toBeVisible()
  })

  test('permission-gated nav items are hidden for a user with no roles (UI hint only)', async ({ page }) => {
    await mockNoRoleSession(page)
    await loginViaUi(page)

    const sidebar = page.getByRole('complementary')
    await expect(sidebar.getByRole('link', { name: 'Customers' })).toHaveCount(0)
    await expect(sidebar.getByRole('link', { name: 'Dashboard' })).toHaveCount(0)
    // Settings has no permission gate — every authenticated user keeps it.
    await expect(sidebar.getByRole('link', { name: 'Settings' })).toBeVisible()
  })
})
