import { expect, test } from '@playwright/test'

import {
  goToCustomers,
  goToLeads,
  loginViaUi,
  mockAuthenticatedSessionAs,
  mockCustomersResource,
  mockLeadsResource,
  mockLeadScoreSuccess,
  TEST_CUSTOMER,
  TEST_LEAD,
} from './mocks'

/**
 * Uses the app's real permission system end-to-end (the actual
 * ROLE_PERMISSIONS map, RequirePermission, usePermission) — nothing here is
 * a hardcoded role check in the test itself. Frontend hiding is a UX hint
 * only; the backend's own @PreAuthorize remains authoritative regardless of
 * what this suite observes.
 */
test.describe('RBAC — SALES', () => {
  test('customer archive hidden, lead archive hidden; create + lead assignment visible', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['SALES'])
    await mockCustomersResource(page, [TEST_CUSTOMER])
    await mockLeadsResource(page, [TEST_LEAD])
    await loginViaUi(page)

    await goToCustomers(page)
    await expect(page.getByRole('button', { name: 'Create customer' })).toBeVisible()
    await page.getByRole('button', { name: `Actions for ${TEST_CUSTOMER.name}` }).click()
    await expect(page.getByRole('menuitem', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: 'Archive' })).toHaveCount(0)
    await page.keyboard.press('Escape')

    await goToLeads(page)
    await expect(page.getByRole('button', { name: 'Create lead' })).toBeVisible()
    await page.getByRole('button', { name: `Actions for ${TEST_LEAD.name}` }).click()
    await expect(page.getByRole('menuitem', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: 'Archive' })).toHaveCount(0)
    await page.keyboard.press('Escape')

    await page.getByRole('table').getByRole('link', { name: TEST_LEAD.name }).click()
    await expect(page.getByRole('button', { name: 'Assign to me' })).toBeVisible()
  })
})

test.describe('RBAC — EMPLOYEE', () => {
  test('read-only everywhere: no create/edit/archive/note/assignment, but AI scoring remains available', async ({
    page,
  }) => {
    await mockAuthenticatedSessionAs(page, ['EMPLOYEE'])
    await mockCustomersResource(page, [TEST_CUSTOMER])
    await mockLeadsResource(page, [TEST_LEAD])
    await mockLeadScoreSuccess(page, TEST_LEAD.id)
    await loginViaUi(page)

    await goToCustomers(page)
    await expect(page.getByRole('table').getByText(TEST_CUSTOMER.name)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create customer' })).toHaveCount(0)
    await page.getByRole('button', { name: `Actions for ${TEST_CUSTOMER.name}` }).click()
    await expect(page.getByRole('menuitem', { name: 'View details' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: 'Edit' })).toHaveCount(0)
    await expect(page.getByRole('menuitem', { name: 'Archive' })).toHaveCount(0)
    await page.keyboard.press('Escape')

    await page.getByRole('table').getByRole('link', { name: TEST_CUSTOMER.name }).click()
    await expect(page.getByRole('heading', { name: TEST_CUSTOMER.name })).toBeVisible()
    await expect(page.getByLabel('Write a note')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Edit' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Archive' })).toHaveCount(0)

    await goToLeads(page)
    await expect(page.getByRole('button', { name: 'Create lead' })).toHaveCount(0)
    await page.getByRole('button', { name: `Actions for ${TEST_LEAD.name}` }).click()
    await expect(page.getByRole('menuitem', { name: 'Edit' })).toHaveCount(0)
    await expect(page.getByRole('menuitem', { name: 'Archive' })).toHaveCount(0)
    await page.keyboard.press('Escape')

    await page.getByRole('table').getByRole('link', { name: TEST_LEAD.name }).click()
    await expect(page.getByRole('heading', { name: TEST_LEAD.name })).toBeVisible()
    await expect(page.getByLabel('Write a note')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Assign to me' })).toHaveCount(0)
    await expect(page.getByText('Unassigned')).toBeVisible()

    // AI scoring requires only LEAD_READ + AI_USE — EMPLOYEE has both.
    await expect(page.getByRole('button', { name: 'Score with AI' })).toBeVisible()
    await page.getByRole('button', { name: 'Score with AI' }).click()
    await expect(page.getByText('82 / 100')).toBeVisible()
  })
})
