import { expect, test } from '@playwright/test'

import { goToCustomers, loginViaUi, mockAuthenticatedSession, mockCustomersResource, TEST_CUSTOMER } from './mocks'

test.describe('customers', () => {
  test('the list renders the mocked customers with the real backend fields', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page)
    await loginViaUi(page)
    await goToCustomers(page)

    await expect(page.getByRole('heading', { name: 'Customers' })).toBeVisible()

    const table = page.getByRole('table')
    await expect(table.getByText(TEST_CUSTOMER.name)).toBeVisible()
    await expect(table.getByText(TEST_CUSTOMER.company!, { exact: true })).toBeVisible()
    await expect(table.getByText(TEST_CUSTOMER.email!)).toBeVisible()
  })

  test('search changes the request and filters the list', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [
      TEST_CUSTOMER,
      { ...TEST_CUSTOMER, id: 'e2e-customer-2', name: 'Beta Distributors', company: 'Beta', email: 'beta@example.com' },
    ])
    await loginViaUi(page)
    await goToCustomers(page)

    const table = page.getByRole('table')
    await expect(table.getByText('Beta Distributors')).toBeVisible()

    await page.getByLabel('Search customers').fill('Beta')
    // Debounced (~300ms) — assert the filtered result, not the raw request.
    await expect(table.getByText('Beta Distributors')).toBeVisible()
    await expect(table.getByText(TEST_CUSTOMER.name)).toHaveCount(0)
  })

  test('the status filter changes the request', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [
      TEST_CUSTOMER,
      { ...TEST_CUSTOMER, id: 'e2e-customer-2', name: 'Inactive Co', status: 'INACTIVE' },
    ])
    await loginViaUi(page)
    await goToCustomers(page)

    const table = page.getByRole('table')
    await expect(table.getByText(TEST_CUSTOMER.name)).toBeVisible()
    await expect(table.getByText('Inactive Co')).toBeVisible()

    await page.getByLabel('Status filter').selectOption('INACTIVE')
    await expect(table.getByText('Inactive Co')).toBeVisible()
    await expect(table.getByText(TEST_CUSTOMER.name)).toHaveCount(0)
  })

  test('pagination advances to the next page', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const many = Array.from({ length: 25 }, (_, i) => ({
      ...TEST_CUSTOMER,
      id: `e2e-customer-${i}`,
      name: `Customer ${String(i).padStart(2, '0')}`,
    }))
    await mockCustomersResource(page, many)
    await loginViaUi(page)
    await goToCustomers(page)

    await expect(page.getByText('Page 1 of 2')).toBeVisible()
    await page.getByRole('button', { name: 'Next page' }).click()
    await expect(page.getByText('Page 2 of 2')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Next page' })).toBeDisabled()
  })

  test('create: validation errors, then a valid submission succeeds and the list updates', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [])
    await loginViaUi(page)
    await goToCustomers(page)

    await page.getByRole('button', { name: 'Create customer' }).click()
    await expect(page.getByRole('heading', { name: 'Create customer' })).toBeVisible()

    await page.getByRole('button', { name: 'Create customer' }).last().click()
    await expect(page.getByText('Name is required')).toBeVisible()

    await page.getByLabel('Name').fill('New Customer Ltd')
    await page.getByRole('button', { name: 'Create customer' }).last().click()

    await expect(page.getByRole('heading', { name: 'Create customer' })).not.toBeVisible()
    await expect(page.getByRole('table').getByText('New Customer Ltd')).toBeVisible()
  })

  test('create: a duplicate email is mapped to the email field', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER])
    await loginViaUi(page)
    await goToCustomers(page)

    await page.getByRole('button', { name: 'Create customer' }).click()
    await page.getByLabel('Name').fill('Duplicate Co')
    await page.getByLabel('Email').fill(TEST_CUSTOMER.email!)
    await page.getByRole('button', { name: 'Create customer' }).last().click()

    await expect(page.getByText(/already exists in this organization/)).toBeVisible()
  })

  test('edit succeeds', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER])
    await loginViaUi(page)
    await goToCustomers(page)

    await page.getByRole('button', { name: `Actions for ${TEST_CUSTOMER.name}` }).click()
    await page.getByRole('menuitem', { name: 'Edit' }).click()

    const companyInput = page.getByLabel('Company')
    await companyInput.fill('Updated Company Name')
    await page.getByRole('button', { name: 'Save changes' }).click()

    await expect(page.getByRole('heading', { name: 'Edit customer' })).not.toBeVisible()
    await expect(page.getByRole('table').getByText('Updated Company Name')).toBeVisible()
  })

  test('archive requires confirmation and then succeeds', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER])
    await loginViaUi(page)
    await goToCustomers(page)

    await page.getByRole('button', { name: `Actions for ${TEST_CUSTOMER.name}` }).click()
    await page.getByRole('menuitem', { name: 'Archive' }).click()

    await expect(page.getByRole('heading', { name: 'Archive this customer?' })).toBeVisible()
    await page.getByRole('button', { name: 'Archive' }).last().click()

    await expect(page.getByRole('heading', { name: 'Archive this customer?' })).not.toBeVisible()
    // The default list view excludes ARCHIVED — the now-archived customer disappears from it.
    await expect(page.getByRole('table').getByText(TEST_CUSTOMER.name)).toHaveCount(0)
  })

  test('customer detail renders the overview, history, and add-note works', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER])
    await loginViaUi(page)
    await goToCustomers(page)

    await page.getByRole('table').getByRole('link', { name: TEST_CUSTOMER.name }).click()
    await expect(page.getByRole('heading', { name: TEST_CUSTOMER.name })).toBeVisible()
    await expect(page.getByText(TEST_CUSTOMER.company!, { exact: true })).toBeVisible()
    await expect(page.getByText('Customer created')).toBeVisible()

    await page.getByLabel('Write a note').fill('Called the customer, follow up next week.')
    await page.getByRole('button', { name: 'Add note' }).click()

    await expect(page.getByText('Called the customer, follow up next week.')).toBeVisible()
  })

  test('a failed list request shows a retryable error state', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await page.route('**/api/v1/customers*', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 500,
          code: 'INTERNAL_ERROR',
          message: 'An unexpected error occurred',
          path: '/api/v1/customers',
        }),
      }),
    )
    await loginViaUi(page)
    await goToCustomers(page)

    await expect(page.getByText(/couldn't load your customers/i)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible()
  })
})
