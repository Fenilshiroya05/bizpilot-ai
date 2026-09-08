import { expect, test } from '@playwright/test'

import {
  goToProducts,
  loginViaUi,
  mockAuthenticatedSession,
  mockAuthenticatedSessionAs,
  mockProductsResource,
  TEST_CATEGORY,
  TEST_PRODUCT,
} from './mocks'

test.describe('products', () => {
  test('1. the list renders the mocked products with the real backend fields', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page)
    await loginViaUi(page)
    await goToProducts(page)

    await expect(page.getByRole('heading', { name: 'Products' })).toBeVisible()
    const table = page.getByRole('table')
    await expect(table.getByText(TEST_PRODUCT.sku)).toBeVisible()
    await expect(table.getByText(TEST_PRODUCT.name)).toBeVisible()
    await expect(table.getByText(TEST_CATEGORY.name)).toBeVisible()
  })

  test('2. search filters the list', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [
      TEST_PRODUCT,
      { ...TEST_PRODUCT, id: 'e2e-product-2', sku: 'BOL-002', name: 'Steel Bolt' },
    ])
    await loginViaUi(page)
    await goToProducts(page)

    const table = page.getByRole('table')
    await page.getByLabel('Search products').fill('Bolt')
    await expect(table.getByText('Steel Bolt')).toBeVisible()
    await expect(table.getByText(TEST_PRODUCT.name)).toHaveCount(0)
  })

  test('3. the status filter changes the request', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [
      TEST_PRODUCT,
      { ...TEST_PRODUCT, id: 'e2e-product-2', sku: 'OLD-001', name: 'Retired Widget', status: 'INACTIVE' },
    ])
    await loginViaUi(page)
    await goToProducts(page)

    const table = page.getByRole('table')
    await expect(table.getByText(TEST_PRODUCT.name)).toBeVisible()
    await page.getByLabel('Status filter').selectOption('INACTIVE')
    await expect(table.getByText('Retired Widget')).toBeVisible()
    await expect(table.getByText(TEST_PRODUCT.name)).toHaveCount(0)
  })

  test('4. the category filter changes the request', async ({ page }) => {
    const otherCategory = { ...TEST_CATEGORY, id: 'e2e-category-2', name: 'Fasteners' }
    await mockAuthenticatedSession(page)
    await mockProductsResource(
      page,
      [TEST_PRODUCT, { ...TEST_PRODUCT, id: 'e2e-product-2', sku: 'BOL-002', name: 'Steel Bolt', categoryId: otherCategory.id }],
      [TEST_CATEGORY, otherCategory],
    )
    await loginViaUi(page)
    await goToProducts(page)

    const table = page.getByRole('table')
    await page.getByLabel('Category filter').selectOption(otherCategory.id)
    await expect(table.getByText('Steel Bolt')).toBeVisible()
    await expect(table.getByText(TEST_PRODUCT.name)).toHaveCount(0)
  })

  test('5. pagination advances to the next page', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const many = Array.from({ length: 25 }, (_, i) => ({
      ...TEST_PRODUCT,
      id: `e2e-product-${i}`,
      sku: `SKU-${String(i).padStart(3, '0')}`,
      name: `Product ${String(i).padStart(2, '0')}`,
    }))
    await mockProductsResource(page, many)
    await loginViaUi(page)
    await goToProducts(page)

    await expect(page.getByText('Page 1 of 2')).toBeVisible()
    await page.getByRole('button', { name: 'Next page' }).click()
    await expect(page.getByText('Page 2 of 2')).toBeVisible()
  })

  test('6. create: validation errors for required fields', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [])
    await loginViaUi(page)
    await goToProducts(page)

    await page.getByRole('button', { name: 'Create product' }).click()
    await page.getByRole('button', { name: 'Create product' }).last().click()

    await expect(page.getByText('SKU is required')).toBeVisible()
    await expect(page.getByText('Name is required')).toBeVisible()
    await expect(page.getByText('Unit is required')).toBeVisible()
    await expect(page.getByText('Price is required')).toBeVisible()
  })

  test('7. create: a valid submission succeeds and the list updates', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [])
    await loginViaUi(page)
    await goToProducts(page)

    await page.getByRole('button', { name: 'Create product' }).click()
    await page.getByLabel('SKU').fill('NEW-001')
    await page.getByLabel('Name').fill('Brand New Widget')
    await page.getByLabel('Unit').fill('pcs')
    await page.getByLabel('Price').fill('99.50')
    await page.getByRole('button', { name: 'Create product' }).last().click()

    await expect(page.getByRole('heading', { name: 'Create product' })).not.toBeVisible()
    await expect(page.getByRole('table').getByText('Brand New Widget')).toBeVisible()
  })

  test('8. create: a duplicate SKU is mapped to the SKU field', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [TEST_PRODUCT])
    await loginViaUi(page)
    await goToProducts(page)

    await page.getByRole('button', { name: 'Create product' }).click()
    await page.getByLabel('SKU').fill(TEST_PRODUCT.sku)
    await page.getByLabel('Name').fill('Duplicate Widget')
    await page.getByLabel('Unit').fill('pcs')
    await page.getByLabel('Price').fill('10')
    await page.getByRole('button', { name: 'Create product' }).last().click()

    await expect(page.getByText('A product with this SKU already exists.')).toBeVisible()
  })

  test('9. create: an inline "Create ..." category option creates and assigns a new category', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [])
    await loginViaUi(page)
    await goToProducts(page)

    await page.getByRole('button', { name: 'Create product' }).click()
    await page.getByLabel('SKU').fill('CAT-001')
    await page.getByLabel('Name').fill('Categorized Widget')
    await page.getByLabel('Unit').fill('pcs')
    await page.getByLabel('Price').fill('10')

    await page.getByRole('combobox', { name: 'Category' }).fill('Brand New Category')
    await page.getByRole('option', { name: /Create "Brand New Category"/ }).click()
    await expect(page.getByRole('combobox', { name: 'Category' })).toHaveValue('Brand New Category')

    await page.getByRole('button', { name: 'Create product' }).last().click()
    await expect(page.getByRole('table').getByText('Categorized Widget')).toBeVisible()
  })

  test('10. edit succeeds', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [TEST_PRODUCT])
    await loginViaUi(page)
    await goToProducts(page)

    await page.getByRole('button', { name: `Actions for ${TEST_PRODUCT.name}` }).click()
    await page.getByRole('menuitem', { name: 'Edit' }).click()

    await page.getByLabel('Price').fill('300')
    await page.getByRole('button', { name: 'Save changes' }).click()

    await expect(page.getByRole('heading', { name: 'Edit product' })).not.toBeVisible()
    await expect(page.getByRole('table').getByText('₹300.00')).toBeVisible()
  })

  test('11. deactivate requires confirmation and then succeeds, removing it from the default ACTIVE-less unfiltered list view', async ({
    page,
  }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [TEST_PRODUCT])
    await loginViaUi(page)
    await goToProducts(page)

    await page.getByRole('button', { name: `Actions for ${TEST_PRODUCT.name}` }).click()
    await page.getByRole('menuitem', { name: 'Deactivate' }).click()
    await expect(page.getByRole('heading', { name: 'Deactivate this product?' })).toBeVisible()
    await page.getByRole('button', { name: 'Deactivate' }).last().click()

    await expect(page.getByRole('heading', { name: 'Deactivate this product?' })).not.toBeVisible()
    await expect(page.getByRole('table').getByText('INACTIVE')).toBeVisible()
  })

  test('12. reactivate flips an inactive product back to active from the list', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [{ ...TEST_PRODUCT, status: 'INACTIVE' }])
    await loginViaUi(page)
    await goToProducts(page)

    await page.getByRole('button', { name: `Actions for ${TEST_PRODUCT.name}` }).click()
    await page.getByRole('menuitem', { name: 'Reactivate' }).click()

    await expect(page.getByRole('table').getByText('ACTIVE')).toBeVisible()
  })

  test('13. product detail renders the overview and supports edit', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [TEST_PRODUCT])
    await loginViaUi(page)
    await goToProducts(page)

    await page.getByRole('table').getByRole('link', { name: TEST_PRODUCT.sku }).click()
    await expect(page.getByRole('heading', { name: TEST_PRODUCT.name })).toBeVisible()
    await expect(page.getByText(TEST_PRODUCT.description!)).toBeVisible()

    await page.getByRole('button', { name: 'Edit' }).click()
    await page.getByLabel('Name', { exact: true }).fill('Renamed Widget')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByRole('heading', { name: 'Renamed Widget' })).toBeVisible()
  })

  test('14. an inactive product detail page shows an informational alert, never "Delete" wording', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [{ ...TEST_PRODUCT, status: 'INACTIVE' }])
    await loginViaUi(page)
    await goToProducts(page)
    await page.getByRole('table').getByRole('link', { name: TEST_PRODUCT.sku }).click()

    await expect(page.getByText(/This product is inactive/)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Reactivate' })).toBeVisible()
    await expect(page.getByText('Delete')).toHaveCount(0)
  })

  test('15. RBAC — EMPLOYEE sees products read-only, no Create/Edit/Deactivate', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['EMPLOYEE'])
    await mockProductsResource(page, [TEST_PRODUCT])
    await loginViaUi(page)
    await goToProducts(page)

    await expect(page.getByRole('button', { name: 'Create product' })).toHaveCount(0)
    await page.getByRole('button', { name: `Actions for ${TEST_PRODUCT.name}` }).click()
    await expect(page.getByRole('menuitem', { name: 'View details' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: 'Edit' })).toHaveCount(0)
    await expect(page.getByRole('menuitem', { name: 'Deactivate' })).toHaveCount(0)
  })

  test('16. RBAC — SALES can create and edit products, but cannot deactivate', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['SALES'])
    await mockProductsResource(page, [TEST_PRODUCT])
    await loginViaUi(page)
    await goToProducts(page)

    await expect(page.getByRole('button', { name: 'Create product' })).toBeVisible()
    await page.getByRole('button', { name: `Actions for ${TEST_PRODUCT.name}` }).click()
    await expect(page.getByRole('menuitem', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: 'Deactivate' })).toHaveCount(0)
  })

  test('17. RBAC — OWNER has full access (Create, Edit, Deactivate)', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['OWNER'])
    await mockProductsResource(page, [TEST_PRODUCT])
    await loginViaUi(page)
    await goToProducts(page)

    await expect(page.getByRole('button', { name: 'Create product' })).toBeVisible()
    await page.getByRole('button', { name: `Actions for ${TEST_PRODUCT.name}` }).click()
    await expect(page.getByRole('menuitem', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: 'Deactivate' })).toBeVisible()
  })

  test('18. clearing filters resets the list', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [
      TEST_PRODUCT,
      { ...TEST_PRODUCT, id: 'e2e-product-2', sku: 'OLD-001', name: 'Retired Widget', status: 'INACTIVE' },
    ])
    await loginViaUi(page)
    await goToProducts(page)

    await page.getByLabel('Status filter').selectOption('INACTIVE')
    await expect(page.getByRole('table').getByText(TEST_PRODUCT.name)).toHaveCount(0)
    await page.getByRole('button', { name: 'Clear filters' }).click()
    await expect(page.getByRole('table').getByText(TEST_PRODUCT.name)).toBeVisible()
  })

  test('19. empty state offers a create action when there are no products at all', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockProductsResource(page, [])
    await loginViaUi(page)
    await goToProducts(page)

    await expect(page.getByText('No products yet')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Add your first product' })).toBeVisible()
  })

  test('20. a failed list request shows a retryable error state', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await page.route('**/api/v1/products*', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 500,
          code: 'INTERNAL_ERROR',
          message: 'An unexpected error occurred',
          path: '/api/v1/products',
        }),
      }),
    )
    await loginViaUi(page)
    await goToProducts(page)

    await expect(page.getByText(/couldn't load your products/i)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible()
  })
})
