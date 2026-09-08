import { expect, test } from '@playwright/test'

import { loginViaUi, mockAuthenticatedSession, mockLoginFailure, TEST_EMAIL, TEST_USER } from './mocks'
import { attachConsoleGuard } from './sanity'

test.describe('authentication', () => {
  test('login page renders email, password, and a Sign in button', async ({ page }) => {
    await page.goto('/auth/login')

    await expect(page.getByLabel('Email')).toBeVisible()
    await expect(page.getByLabel('Password', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
  })

  test('submitting an empty form shows validation errors and makes no API call', async ({ page }) => {
    let loginCalled = false
    await page.route('**/api/v1/auth/login', (route) => {
      loginCalled = true
      return route.continue()
    })

    await page.goto('/auth/login')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByText('Email is required')).toBeVisible()
    await expect(page.getByText('Password is required')).toBeVisible()
    expect(loginCalled).toBe(false)
  })

  test('an invalid login shows the backend error message, not a raw exception', async ({ page }) => {
    await mockLoginFailure(page)

    await page.goto('/auth/login')
    await page.getByLabel('Email').fill(TEST_EMAIL)
    await page.getByLabel('Password', { exact: true }).fill('wrong-password')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByRole('alert')).toContainText('Invalid email or password')
    await expect(page).toHaveURL(/\/auth\/login$/)
  })

  test('a successful login reaches the dashboard and renders the authenticated shell', async ({ page }) => {
    const guard = attachConsoleGuard(page)
    await mockAuthenticatedSession(page)

    await loginViaUi(page)

    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    // The authenticated shell: sidebar landmark + user menu with the mocked profile initials.
    await expect(page.getByRole('complementary')).toBeVisible()
    const userMenuTrigger = page.getByRole('button', { name: 'Open user menu' })
    await expect(userMenuTrigger).toBeVisible()

    await userMenuTrigger.click()
    await expect(page.getByText(`${TEST_USER.firstName} ${TEST_USER.lastName}`)).toBeVisible()
    await expect(page.getByText(TEST_USER.email)).toBeVisible()

    expect(guard.errors, `console errors: ${guard.errors.join('; ')}`).toEqual([])
  })

  test('logout revokes the session and returns to the login page', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await loginViaUi(page)

    await page.getByRole('button', { name: 'Open user menu' }).click()
    await page.getByRole('menuitem', { name: 'Log out' }).click()

    await page.waitForURL('**/auth/login')
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  })

  test('unauthenticated access to /dashboard redirects to /auth/login', async ({ page }) => {
    await page.goto('/dashboard')
    await page.waitForURL('**/auth/login')
    await expect(page.getByLabel('Email')).toBeVisible()
  })

  test('the login form is keyboard-navigable in a sensible order', async ({ page }) => {
    await page.goto('/auth/login')

    await page.getByLabel('Email').focus()
    await expect(page.getByLabel('Email')).toBeFocused()

    await page.keyboard.press('Tab')
    await expect(page.getByLabel('Password', { exact: true })).toBeFocused()

    await page.keyboard.press('Tab') // show/hide password toggle
    await page.keyboard.press('Tab')
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeFocused()
  })
})
