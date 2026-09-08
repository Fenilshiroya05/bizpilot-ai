import { expect, test } from '@playwright/test'

import { attachConsoleGuard } from './sanity'

test.describe('smoke', () => {
  test('unauthenticated root redirects to the login page', async ({ page }) => {
    const guard = attachConsoleGuard(page)

    await page.goto('/')
    await page.waitForURL('**/auth/login')

    await expect(page).toHaveTitle('BizPilot AI')
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(guard.errors, `console errors: ${guard.errors.join('; ')}`).toEqual([])
    expect(guard.pageErrors, `page errors: ${guard.pageErrors.join('; ')}`).toEqual([])
  })

  test('the login page never renders a blank shell', async ({ page }) => {
    await page.goto('/auth/login')

    const rootHtml = await page.locator('#root').innerHTML()
    expect(rootHtml.trim().length).toBeGreaterThan(0)
    await expect(page.getByLabel('Email')).toBeVisible()
  })

  test('an unknown route falls back safely instead of a blank/broken page', async ({ page }) => {
    await page.goto('/this-route-does-not-exist')
    await page.waitForURL('**/auth/login')
    await expect(page.getByLabel('Email')).toBeVisible()
  })
})
