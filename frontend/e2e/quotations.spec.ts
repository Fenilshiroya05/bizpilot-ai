import { expect, test } from '@playwright/test'

import {
  goToQuotations,
  loginViaUi,
  mockAuthenticatedSession,
  mockAuthenticatedSessionAs,
  mockCustomersResource,
  mockProductsResource,
  mockQuotationsResource,
  TEST_CUSTOMER,
  TEST_PRODUCT,
} from './mocks'

async function setUpProductsAndCustomers(page: import('@playwright/test').Page) {
  await mockCustomersResource(page, [TEST_CUSTOMER])
  const { products } = await mockProductsResource(page, [TEST_PRODUCT])
  return products
}

/**
 * Clicks "Create quotation" and waits for the destination page to fully
 * settle before returning control to the test. This isn't just a courtesy
 * wait: React Router v7 navigates inside `React.startTransition`, and if a
 * test interacts with a freshly-navigated lazy-loaded route's fields before
 * that transition finishes committing, the interaction can be discarded when
 * the transition settles a moment later — a real, reproducible risk for any
 * fast-typing user, not just a test-timing artifact. See the Phase 22
 * implementation report's "Known Limitations" section.
 */
async function openCreateQuotationForm(page: import('@playwright/test').Page) {
  await page.getByRole('button', { name: 'Create quotation' }).click()
  await expect(page.getByRole('heading', { name: 'New quotation' })).toBeVisible()
}

test.describe('quotations', () => {
  test('1. the list renders the mocked quotations with the resolved customer name', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 2 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)

    await expect(page.getByRole('heading', { name: 'Quotations' })).toBeVisible()
    const table = page.getByRole('table')
    await expect(table.getByText(TEST_CUSTOMER.name)).toBeVisible()
    await expect(table.getByText('₹590.00')).toBeVisible() // 2 x 250 = 500 + 18% tax = 590
  })

  test('2. there is no free-text search input', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products)
    await loginViaUi(page)
    await goToQuotations(page)

    await expect(page.getByRole('textbox', { name: /search/i })).toHaveCount(0)
  })

  test('3. the status filter changes the request', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
      { id: 'e2e-quotation-2', customerId: TEST_CUSTOMER.id, status: 'SENT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)

    const table = page.getByRole('table')
    await expect(table.getByText('DRAFT')).toBeVisible()
    await expect(table.getByText('SENT')).toBeVisible()

    await page.getByLabel('Status filter').selectOption('SENT')
    await expect(table.getByText('DRAFT')).toHaveCount(0)
    await expect(table.getByText('SENT')).toBeVisible()
  })

  test('4. the customer filter changes the request', async ({ page }) => {
    const otherCustomer = { ...TEST_CUSTOMER, id: 'e2e-customer-2', name: 'Beta Distributors', email: 'beta@example.com' }
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER, otherCustomer])
    const { products } = await mockProductsResource(page, [TEST_PRODUCT])
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
      { id: 'e2e-quotation-2', customerId: otherCustomer.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)

    await page.getByRole('combobox', { name: 'Customer' }).fill('Beta')
    await page.getByRole('option', { name: /Beta Distributors/ }).click()

    const table = page.getByRole('table')
    await expect(table.getByText('Beta Distributors')).toBeVisible()
    await expect(table.getByText(TEST_CUSTOMER.name)).toHaveCount(0)
  })

  test('5. clearing filters resets the list', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
      { id: 'e2e-quotation-2', customerId: TEST_CUSTOMER.id, status: 'SENT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)

    await page.getByLabel('Status filter').selectOption('SENT')
    await expect(page.getByRole('table').getByText('DRAFT')).toHaveCount(0)
    await page.getByRole('button', { name: 'Clear filters' }).click()
    await expect(page.getByRole('table').getByText('DRAFT')).toBeVisible()
  })

  test('6. create: requires a customer and at least one line item', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [])
    await loginViaUi(page)
    await goToQuotations(page)

    await openCreateQuotationForm(page)
    await page.getByRole('button', { name: 'Create quotation' }).click()

    await expect(page.getByText('Select a customer')).toBeVisible()
    await expect(page.getByText('Add at least one line item')).toBeVisible()
  })

  test('7. create: customer combobox search and selection works', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [])
    await loginViaUi(page)
    await goToQuotations(page)
    await openCreateQuotationForm(page)

    const customerCombobox = page.getByRole('combobox', { name: 'Customer' })
    await customerCombobox.fill('Acme')
    await page.getByRole('option', { name: new RegExp(TEST_CUSTOMER.name) }).click()
    await expect(customerCombobox).toHaveValue(TEST_CUSTOMER.name)
  })

  test('8. create: product combobox search and selection populates the line preview', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [])
    await loginViaUi(page)
    await goToQuotations(page)
    await openCreateQuotationForm(page)

    await page.getByRole('button', { name: 'Add line item' }).click()
    const productCombobox = page.getByRole('combobox', { name: 'Product' })
    await productCombobox.fill('Steel')
    await page.getByRole('option', { name: new RegExp(TEST_PRODUCT.name) }).click()
    await expect(productCombobox).toHaveValue(TEST_PRODUCT.name)
  })

  test('9. add/remove line items, and remove buttons carry unique accessible names', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [])
    await loginViaUi(page)
    await goToQuotations(page)
    await openCreateQuotationForm(page)

    await page.getByRole('button', { name: 'Add line item' }).click()
    await page.getByRole('button', { name: 'Add line item' }).click()
    await expect(page.getByRole('button', { name: 'Remove line 1' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Remove line 2' })).toBeVisible()

    await page.getByRole('button', { name: 'Remove line 2' }).click()
    await expect(page.getByRole('button', { name: 'Remove line 2' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Remove line 1' })).toBeVisible()
  })

  test('10. quantity change updates the estimated total, using local math only', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [])
    await loginViaUi(page)
    await goToQuotations(page)
    await openCreateQuotationForm(page)

    await page.getByRole('button', { name: 'Add line item' }).click()
    await page.getByRole('combobox', { name: 'Product' }).fill('Steel')
    await page.getByRole('option', { name: new RegExp(TEST_PRODUCT.name) }).click()

    // qty 1 x 250 = 250, +18% tax = 295
    await expect(page.getByText('₹295.00')).toBeVisible()

    await page.getByLabel('Quantity for line 1').fill('3')
    // qty 3 x 250 = 750, +18% tax = 885
    await expect(page.getByText('₹885.00')).toBeVisible()
  })

  test('11. discount validation rejects a value above 100', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [])
    await loginViaUi(page)
    await goToQuotations(page)
    await openCreateQuotationForm(page)

    await page.getByLabel('Discount %').fill('150')
    await page.getByRole('button', { name: 'Create quotation' }).click()

    await expect(page.getByText('Discount must be at most 100')).toBeVisible()
  })

  test('12. create: a valid quotation is created and redirects to its detail page', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [])
    await loginViaUi(page)
    await goToQuotations(page)
    await openCreateQuotationForm(page)

    await page.getByRole('combobox', { name: 'Customer' }).fill('Acme')
    await page.getByRole('option', { name: new RegExp(TEST_CUSTOMER.name) }).click()
    await page.getByRole('button', { name: 'Add line item' }).click()
    await page.getByRole('combobox', { name: 'Product' }).fill('Steel')
    await page.getByRole('option', { name: new RegExp(TEST_PRODUCT.name) }).click()
    await page.getByRole('button', { name: 'Create quotation' }).click()

    await expect(page.getByRole('heading', { name: `Quotation for ${TEST_CUSTOMER.name}` })).toBeVisible()
    await expect(page.getByText('DRAFT')).toBeVisible()
  })

  test('13. cancel on the create page returns to the list without creating anything', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [])
    await loginViaUi(page)
    await goToQuotations(page)
    await openCreateQuotationForm(page)
    await page.getByRole('button', { name: 'Cancel' }).click()

    await expect(page.getByRole('heading', { name: 'Quotations' })).toBeVisible()
    await expect(page.getByText('No quotations yet')).toBeVisible()
  })

  test('14. quotation detail renders the line-item breakdown and totals', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 2 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()

    await expect(page.getByRole('heading', { name: `Quotation for ${TEST_CUSTOMER.name}` })).toBeVisible()
    await expect(page.getByText(TEST_PRODUCT.name)).toBeVisible()
    await expect(page.getByText('Grand total').locator('..').getByText('₹590.00')).toBeVisible()
  })

  test('15. edit is available only for a DRAFT quotation and toggles the shared form in place', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()

    await page.getByRole('button', { name: 'Edit' }).click()
    await expect(page.getByRole('heading', { name: 'Edit quotation' })).toBeVisible()
    await expect(page.getByRole('combobox', { name: 'Customer' })).toHaveValue(TEST_CUSTOMER.name)

    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByRole('heading', { name: `Quotation for ${TEST_CUSTOMER.name}` })).toBeVisible()
  })

  test('16. a non-DRAFT quotation is read-only: no Edit button, no status control', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'SENT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()

    await expect(page.getByRole('button', { name: 'Edit' })).toHaveCount(0)
    await expect(page.getByRole('combobox', { name: 'New status' })).toHaveCount(0)
    await expect(page.getByText(/left draft status and can no longer be edited/)).toBeVisible()
  })

  test('17. editing a quotation that raced to non-DRAFT server-side surfaces the specific conflict message', async ({
    page,
  }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()
    await page.getByRole('button', { name: 'Edit' }).click()

    // Simulate the quotation having left DRAFT on the server between page load and save.
    await page.route('**/api/v1/quotations/e2e-quotation-1', (route) => {
      if (route.request().method() === 'PATCH') {
        return route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({
            timestamp: new Date().toISOString(),
            status: 409,
            code: 'QUOTATION_NOT_EDITABLE',
            message: 'Only draft quotations can be edited.',
            path: '/api/v1/quotations/e2e-quotation-1',
          }),
        })
      }
      return route.fallback()
    })

    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByText('Only draft quotations can be edited.')).toBeVisible()
  })

  test('18. status transition requires an explicit confirmation and is irreversible-worded', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()

    await page.getByRole('combobox', { name: 'New status' }).selectOption('SENT')
    await page.getByRole('button', { name: 'Update status' }).click()
    await expect(page.getByText(/can no longer be edited/)).toBeVisible()
    await page.getByRole('dialog').getByRole('button', { name: 'Update status' }).click()

    await expect(page.getByRole('combobox', { name: 'New status' })).toHaveCount(0)
    await expect(page.getByText('SENT', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Edit' })).toHaveCount(0)
  })

  test('19. cancel requires confirmation and then succeeds', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()

    await page.getByRole('button', { name: 'Cancel quotation' }).click()
    await expect(page.getByRole('heading', { name: 'Cancel this quotation?' })).toBeVisible()
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel quotation' }).click()

    await expect(page.getByText('CANCELLED', { exact: true })).toBeVisible()
  })

  test('20. Download PDF triggers a download for any quotation status', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'CANCELLED', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: /Download PDF/ }).click(),
    ])
    expect(download.suggestedFilename()).toBe('quotation-e2e-quotation-1.pdf')
  })

  test('21. RBAC — EMPLOYEE is read-only: no Create, no Edit, no status control, no Cancel', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['EMPLOYEE'])
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)

    await expect(page.getByRole('button', { name: 'Create quotation' })).toHaveCount(0)
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()
    await expect(page.getByRole('button', { name: 'Edit' })).toHaveCount(0)
    await expect(page.getByRole('combobox', { name: 'New status' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Cancel quotation' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: /Download PDF/ })).toBeVisible()
  })

  test('22. RBAC — SALES can create and edit quotations, but cannot cancel', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['SALES'])
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)

    await expect(page.getByRole('button', { name: 'Create quotation' })).toBeVisible()
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()
    await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Cancel quotation' })).toHaveCount(0)
  })

  test('23. RBAC — OWNER has full access (Create, Edit, status transitions, Cancel)', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['OWNER'])
    const products = await setUpProductsAndCustomers(page)
    await mockQuotationsResource(page, products, [
      { id: 'e2e-quotation-1', customerId: TEST_CUSTOMER.id, status: 'DRAFT', validUntil: null, discountPercentage: 0, items: [{ productId: TEST_PRODUCT.id, quantity: 1 }] },
    ])
    await loginViaUi(page)
    await goToQuotations(page)
    await page.getByRole('table').getByRole('link', { name: 'e2e-quot' }).click()

    await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('combobox', { name: 'New status' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Cancel quotation' })).toBeVisible()
  })

  test('24. Combobox keyboard smoke: arrow keys + Enter select a customer without using the mouse', async ({ page }) => {
    const otherCustomer = { ...TEST_CUSTOMER, id: 'e2e-customer-2', name: 'Beta Distributors', email: 'beta@example.com' }
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER, otherCustomer])
    const { products } = await mockProductsResource(page, [TEST_PRODUCT])
    await mockQuotationsResource(page, products, [])
    await loginViaUi(page)
    await goToQuotations(page)
    await openCreateQuotationForm(page)

    const customerCombobox = page.getByRole('combobox', { name: 'Customer' })
    await customerCombobox.fill(TEST_CUSTOMER.name.slice(0, 4))
    await expect(page.getByRole('option').first()).toBeVisible()
    await customerCombobox.press('ArrowDown')
    await customerCombobox.press('Enter')

    await expect(customerCombobox).toHaveValue(TEST_CUSTOMER.name)

    await customerCombobox.press('Escape')
    await expect(page.getByRole('listbox')).not.toBeVisible()
  })

  test('25. a failed list request shows a retryable error state', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await page.route('**/api/v1/quotations*', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 500,
          code: 'INTERNAL_ERROR',
          message: 'An unexpected error occurred',
          path: '/api/v1/quotations',
        }),
      }),
    )
    await loginViaUi(page)
    await goToQuotations(page)

    await expect(page.getByText(/couldn't load your quotations/i)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible()
  })
})
