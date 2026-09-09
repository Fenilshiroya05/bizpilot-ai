import { expect, test } from '@playwright/test'

import {
  goToCustomers,
  goToLeads,
  goToProducts,
  goToQuotations,
  goToTasks,
  loginViaUi,
  mockAuthenticatedSession,
  mockCustomersResource,
  mockLeadsResource,
  mockLeadScoreSuccess,
  mockProductsResource,
  mockQuotationsResource,
  mockTasksResource,
  TEST_CUSTOMER,
  TEST_LEAD,
  TEST_PRODUCT,
  TEST_TASK,
} from './mocks'
import { attachConsoleGuard, horizontalOverflowPx } from './sanity'

const DESKTOP_BREAKPOINTS = [
  { width: 1440, height: 900 },
  { width: 1280, height: 800 },
  { width: 1024, height: 768 },
]

const MOBILE_BREAKPOINTS = [
  { width: 768, height: 1024 },
  { width: 390, height: 844 },
  { width: 375, height: 812 },
]

test.describe('responsive — desktop', () => {
  for (const { width, height } of DESKTOP_BREAKPOINTS) {
    test(`dashboard at ${width}x${height}: sidebar visible, no horizontal overflow`, async ({ page }) => {
      await page.setViewportSize({ width, height })
      await mockAuthenticatedSession(page)
      await loginViaUi(page)

      await expect(page.getByRole('complementary')).toBeVisible()
      await expect(page.getByRole('button', { name: 'Open navigation menu' })).not.toBeVisible()
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)

      await page.screenshot({ path: `test-results/screenshots/dashboard-${width}x${height}.png`, fullPage: true })
    })
  }
})

test.describe('responsive — mobile/tablet', () => {
  for (const { width, height } of MOBILE_BREAKPOINTS) {
    test(`dashboard at ${width}x${height}: drawer navigation replaces the sidebar`, async ({ page }) => {
      await page.setViewportSize({ width, height })
      const guard = attachConsoleGuard(page)
      await mockAuthenticatedSession(page)
      await loginViaUi(page)

      await expect(page.getByRole('complementary')).not.toBeVisible()
      const menuButton = page.getByRole('button', { name: 'Open navigation menu' })
      await expect(menuButton).toBeVisible()
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)

      await page.screenshot({ path: `test-results/screenshots/dashboard-${width}x${height}.png`, fullPage: true })
      expect(guard.errors, `console errors: ${guard.errors.join('; ')}`).toEqual([])
    })
  }

  test('the mobile drawer opens, is keyboard-navigable, and closes on Escape with focus restored', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await mockAuthenticatedSession(page)
    await loginViaUi(page)

    const menuButton = page.getByRole('button', { name: 'Open navigation menu' })
    await menuButton.focus()
    await page.keyboard.press('Enter')

    const drawer = page.getByRole('dialog', { name: 'Navigation' })
    await expect(drawer).toBeVisible()

    // Focus should have moved into the drawer (Radix Dialog default behavior).
    const focusedInDrawer = await page.evaluate(() => {
      const dialog = document.querySelector('[role="dialog"]')
      return dialog?.contains(document.activeElement) ?? false
    })
    expect(focusedInDrawer).toBe(true)

    await expect(drawer.getByRole('link', { name: 'Dashboard' })).toBeVisible()

    await page.keyboard.press('Escape')
    await expect(drawer).not.toBeVisible()
    await expect(menuButton).toBeFocused()
  })

  test('clicking the backdrop closes the mobile drawer', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await mockAuthenticatedSession(page)
    await loginViaUi(page)

    await page.getByRole('button', { name: 'Open navigation menu' }).click()
    const drawer = page.getByRole('dialog', { name: 'Navigation' })
    await expect(drawer).toBeVisible()

    // Click far outside the drawer panel (which is anchored to the left edge).
    await page.mouse.click(page.viewportSize()!.width - 10, page.viewportSize()!.height / 2)
    await expect(drawer).not.toBeVisible()
  })

  test('navigating from the mobile drawer closes it and renders the destination', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await mockAuthenticatedSession(page)
    await loginViaUi(page)

    await page.getByRole('button', { name: 'Open navigation menu' }).click()
    const drawer = page.getByRole('dialog', { name: 'Navigation' })
    await drawer.getByRole('link', { name: 'Settings' }).click()

    await expect(drawer).not.toBeVisible()
    await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible()
  })
})

test.describe('responsive — login and settings screenshots', () => {
  test('login page at 1440x900 and 390x844', async ({ page }) => {
    for (const { width, height } of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      await page.setViewportSize({ width, height })
      await page.goto('/auth/login')
      await expect(page.getByLabel('Email')).toBeVisible()
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/login-${width}x${height}.png`, fullPage: true })
    }
  })

  test('settings page at 1440x900 and 390x844', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await loginViaUi(page)

    // Navigate via the sidebar link once (SPA client-side routing) — tokens
    // are memory-only, so a page.goto()/reload between viewports would lose
    // the session. Resizing the already-loaded page is the realistic
    // equivalent of a user resizing their window.
    await page.getByRole('complementary').getByRole('link', { name: 'Settings' }).click()
    await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible()

    for (const { width, height } of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      await page.setViewportSize({ width, height })
      await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible()
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/settings-${width}x${height}.png`, fullPage: true })
    }
  })
})

test.describe('responsive — customers and leads (Phase 21)', () => {
  test('customers list: table on desktop, cards on mobile, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER])
    await loginViaUi(page)
    await goToCustomers(page)

    for (const { width, height } of [...DESKTOP_BREAKPOINTS, ...MOBILE_BREAKPOINTS]) {
      await page.setViewportSize({ width, height })
      await expect(page.getByRole('heading', { name: 'Customers' })).toBeVisible()
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/customers-list-${width}x${height}.png`, fullPage: true })
    }

    // At a mobile width, the desktop table is not in the accessible tree —
    // the row is reachable only via the stacked card link.
    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('table')).not.toBeVisible()
    await expect(page.getByRole('link', { name: new RegExp(TEST_CUSTOMER.name) })).toBeVisible()
  })

  test('customer detail: single column on mobile, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER])
    await loginViaUi(page)
    await goToCustomers(page)
    await page.getByRole('table').getByRole('link', { name: TEST_CUSTOMER.name }).click()
    await expect(page.getByRole('heading', { name: TEST_CUSTOMER.name })).toBeVisible()

    for (const { width, height } of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      await page.setViewportSize({ width, height })
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/customer-detail-${width}x${height}.png`, fullPage: true })
    }
  })

  test('leads list: table on desktop, cards on mobile, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page, [TEST_LEAD])
    await loginViaUi(page)
    await goToLeads(page)

    for (const { width, height } of [...DESKTOP_BREAKPOINTS, ...MOBILE_BREAKPOINTS]) {
      await page.setViewportSize({ width, height })
      await expect(page.getByRole('heading', { name: 'Leads' })).toBeVisible()
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/leads-list-${width}x${height}.png`, fullPage: true })
    }

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('table')).not.toBeVisible()
    await expect(page.getByRole('link', { name: new RegExp(TEST_LEAD.name) })).toBeVisible()
  })

  test('lead detail (with AI scoring success): single column on mobile, no horizontal overflow, screenshots', async ({
    page,
  }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page, [TEST_LEAD])
    await mockLeadScoreSuccess(page, TEST_LEAD.id)
    await loginViaUi(page)
    await goToLeads(page)
    await page.getByRole('table').getByRole('link', { name: TEST_LEAD.name }).click()
    await expect(page.getByRole('heading', { name: TEST_LEAD.name })).toBeVisible()

    await page.getByRole('button', { name: 'Score with AI' }).click()
    await expect(page.getByText('82 / 100')).toBeVisible()

    for (const { width, height } of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      await page.setViewportSize({ width, height })
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/lead-detail-ai-score-${width}x${height}.png`, fullPage: true })
    }
  })
})

test.describe('responsive — products and quotations (Phase 22)', () => {
  test('products list: table on desktop, cards on mobile, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [TEST_PRODUCT])
    await loginViaUi(page)
    await goToProducts(page)

    for (const { width, height } of [...DESKTOP_BREAKPOINTS, ...MOBILE_BREAKPOINTS]) {
      await page.setViewportSize({ width, height })
      await expect(page.getByRole('heading', { name: 'Products' })).toBeVisible()
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/products-list-${width}x${height}.png`, fullPage: true })
    }

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('table')).not.toBeVisible()
    await expect(page.getByRole('link', { name: TEST_PRODUCT.sku })).toBeVisible()
  })

  test('product detail: single column on mobile, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [TEST_PRODUCT])
    await loginViaUi(page)
    await goToProducts(page)
    await page.getByRole('table').getByRole('link', { name: TEST_PRODUCT.sku }).click()
    await expect(page.getByRole('heading', { name: TEST_PRODUCT.name })).toBeVisible()

    for (const { width, height } of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      await page.setViewportSize({ width, height })
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/product-detail-${width}x${height}.png`, fullPage: true })
    }
  })

  test('quotations list: table on desktop, cards on mobile, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER])
    const { products } = await mockProductsResource(page, [TEST_PRODUCT])
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)

    for (const { width, height } of [...DESKTOP_BREAKPOINTS, ...MOBILE_BREAKPOINTS]) {
      await page.setViewportSize({ width, height })
      await expect(page.getByRole('heading', { name: 'Quotations' })).toBeVisible()
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/quotations-list-${width}x${height}.png`, fullPage: true })
    }
  })

  test('quotation detail: single column on mobile, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER])
    const { products } = await mockProductsResource(page, [TEST_PRODUCT])
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()
    await expect(page.getByRole('heading', { name: `Quotation for ${TEST_CUSTOMER.name}` })).toBeVisible()

    for (const { width, height } of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      await page.setViewportSize({ width, height })
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/quotation-detail-${width}x${height}.png`, fullPage: true })
    }
  })

  test('quotation create form: responsive layout, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER])
    const { products } = await mockProductsResource(page, [TEST_PRODUCT])
    await mockQuotationsResource(page, products, [])
    await loginViaUi(page)
    await goToQuotations(page)
    await page.getByRole('button', { name: 'Create quotation' }).click()
    await expect(page.getByRole('heading', { name: 'New quotation' })).toBeVisible()

    for (const { width, height } of [...DESKTOP_BREAKPOINTS, ...MOBILE_BREAKPOINTS]) {
      await page.setViewportSize({ width, height })
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/quotation-form-${width}x${height}.png`, fullPage: true })
    }
  })
})

test.describe('responsive — tasks (Phase 23)', () => {
  test('tasks list: table on desktop, cards on mobile, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [TEST_TASK])
    await loginViaUi(page)
    await goToTasks(page)

    for (const { width, height } of [...DESKTOP_BREAKPOINTS, ...MOBILE_BREAKPOINTS]) {
      await page.setViewportSize({ width, height })
      await expect(page.getByRole('heading', { name: 'Tasks' })).toBeVisible()
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/tasks-list-${width}x${height}.png`, fullPage: true })
    }

    // At a mobile width, the desktop table is not in the accessible tree —
    // the row is reachable only via the stacked card link.
    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('table')).not.toBeVisible()
    await expect(page.getByRole('link', { name: new RegExp(TEST_TASK.title) })).toBeVisible()
  })

  test('task detail: single column on mobile, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [TEST_TASK])
    await loginViaUi(page)
    await goToTasks(page)
    await page.getByRole('table').getByRole('link', { name: TEST_TASK.title }).click()
    await expect(page.getByRole('heading', { name: TEST_TASK.title })).toBeVisible()

    for (const { width, height } of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      await page.setViewportSize({ width, height })
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/task-detail-${width}x${height}.png`, fullPage: true })
    }
  })

  test('task create dialog: responsive layout, no horizontal overflow, screenshots', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [])
    await loginViaUi(page)
    await goToTasks(page)
    await page.getByRole('button', { name: 'Create task' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()

    for (const { width, height } of [...DESKTOP_BREAKPOINTS, ...MOBILE_BREAKPOINTS]) {
      await page.setViewportSize({ width, height })
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await page.screenshot({ path: `test-results/screenshots/task-form-${width}x${height}.png`, fullPage: true })
    }
  })
})
