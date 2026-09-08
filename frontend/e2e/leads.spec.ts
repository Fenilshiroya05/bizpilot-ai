import { expect, test } from '@playwright/test'

import { goToLeads, loginViaUi, mockAuthenticatedSession, mockLeadsResource, TEST_LEAD, TEST_USER } from './mocks'

test.describe('leads', () => {
  test('the list renders the mocked leads with the real backend fields', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page)
    await loginViaUi(page)
    await goToLeads(page)

    await expect(page.getByRole('heading', { name: 'Leads' })).toBeVisible()

    const table = page.getByRole('table')
    await expect(table.getByText(TEST_LEAD.name)).toBeVisible()
    await expect(table.getByText(TEST_LEAD.company!)).toBeVisible()
    await expect(table.getByText('WEBSITE')).toBeVisible()
    await expect(table.getByText('MEDIUM')).toBeVisible()
  })

  test('search filters the list', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page, [
      TEST_LEAD,
      { ...TEST_LEAD, id: 'e2e-lead-2', name: 'Rahul Verma', company: 'Verma Exports' },
    ])
    await loginViaUi(page)
    await goToLeads(page)

    const table = page.getByRole('table')
    await page.getByLabel('Search leads').fill('Rahul')
    await expect(table.getByText('Rahul Verma')).toBeVisible()
    await expect(table.getByText(TEST_LEAD.name)).toHaveCount(0)
  })

  test('status, source, and priority filters each change the request', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page, [
      TEST_LEAD,
      { ...TEST_LEAD, id: 'e2e-lead-2', name: 'Qualified Lead', status: 'QUALIFIED', source: 'REFERRAL', priority: 'HIGH' },
    ])
    await loginViaUi(page)
    await goToLeads(page)

    const table = page.getByRole('table')
    await page.getByLabel('Status filter').selectOption('QUALIFIED')
    await expect(table.getByText('Qualified Lead')).toBeVisible()
    await expect(table.getByText(TEST_LEAD.name)).toHaveCount(0)

    await page.getByLabel('Status filter').selectOption('')
    await page.getByLabel('Source filter').selectOption('REFERRAL')
    await expect(table.getByText('Qualified Lead')).toBeVisible()
    await expect(table.getByText(TEST_LEAD.name)).toHaveCount(0)

    await page.getByLabel('Source filter').selectOption('')
    await page.getByLabel('Priority filter').selectOption('HIGH')
    await expect(table.getByText('Qualified Lead')).toBeVisible()
    await expect(table.getByText(TEST_LEAD.name)).toHaveCount(0)
  })

  test('My Leads and Unassigned quick filters work', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page, [
      TEST_LEAD, // unassigned
      { ...TEST_LEAD, id: 'e2e-lead-2', name: 'My Own Lead', assignedToUserId: TEST_USER.id },
    ])
    await loginViaUi(page)
    await goToLeads(page)

    const table = page.getByRole('table')
    await page.getByRole('button', { name: 'My Leads' }).click()
    await expect(table.getByText('My Own Lead')).toBeVisible()
    await expect(table.getByText(TEST_LEAD.name)).toHaveCount(0)

    await page.getByRole('button', { name: 'My Leads' }).click() // toggle off
    await page.getByRole('button', { name: 'Unassigned' }).click()
    await expect(table.getByText(TEST_LEAD.name)).toBeVisible()
    await expect(table.getByText('My Own Lead')).toHaveCount(0)
  })

  test('Follow-up Due quick filter works', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page, [
      { ...TEST_LEAD, followUpDate: '2020-01-01' }, // overdue
      { ...TEST_LEAD, id: 'e2e-lead-2', name: 'Future Follow-up', followUpDate: '2999-01-01' },
    ])
    await loginViaUi(page)
    await goToLeads(page)

    const table = page.getByRole('table')
    await page.getByRole('button', { name: 'Follow-up Due' }).click()
    await expect(table.getByText(TEST_LEAD.name)).toBeVisible()
    await expect(table.getByText('Future Follow-up')).toHaveCount(0)
  })

  test('pagination advances to the next page', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const many = Array.from({ length: 25 }, (_, i) => ({
      ...TEST_LEAD,
      id: `e2e-lead-${i}`,
      name: `Lead ${String(i).padStart(2, '0')}`,
    }))
    await mockLeadsResource(page, many)
    await loginViaUi(page)
    await goToLeads(page)

    await expect(page.getByText('Page 1 of 2')).toBeVisible()
    await page.getByRole('button', { name: 'Next page' }).click()
    await expect(page.getByText('Page 2 of 2')).toBeVisible()
  })

  test('create: validation errors, then a valid submission succeeds', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page, [])
    await loginViaUi(page)
    await goToLeads(page)

    await page.getByRole('button', { name: 'Create lead' }).click()
    await page.getByRole('button', { name: 'Create lead' }).last().click()
    await expect(page.getByText('Name is required')).toBeVisible()

    await page.getByLabel('Name').fill('New Lead Co')
    await page.getByRole('button', { name: 'Create lead' }).last().click()

    await expect(page.getByRole('heading', { name: 'Create lead' })).not.toBeVisible()
    await expect(page.getByRole('table').getByText('New Lead Co')).toBeVisible()
  })

  test('edit: updates fields and sets a follow-up date; a later edit clears it', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page, [TEST_LEAD])
    await loginViaUi(page)
    await goToLeads(page)

    await page.getByRole('button', { name: `Actions for ${TEST_LEAD.name}` }).click()
    await page.getByRole('menuitem', { name: 'Edit' }).click()
    await page.getByLabel('Follow-up date').fill('2026-12-31')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByRole('heading', { name: 'Edit lead' })).not.toBeVisible()

    await page.getByRole('button', { name: `Actions for ${TEST_LEAD.name}` }).click()
    await page.getByRole('menuitem', { name: 'Edit' }).click()
    await expect(page.getByLabel('Follow-up date')).toHaveValue('2026-12-31')
    await page.getByLabel('Follow-up date').fill('')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByRole('heading', { name: 'Edit lead' })).not.toBeVisible()

    await page.getByRole('button', { name: `Actions for ${TEST_LEAD.name}` }).click()
    await page.getByRole('menuitem', { name: 'Edit' }).click()
    await expect(page.getByLabel('Follow-up date')).toHaveValue('')
  })

  test('archive requires confirmation and then succeeds', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page, [TEST_LEAD])
    await loginViaUi(page)
    await goToLeads(page)

    await page.getByRole('button', { name: `Actions for ${TEST_LEAD.name}` }).click()
    await page.getByRole('menuitem', { name: 'Archive' }).click()
    await expect(page.getByRole('heading', { name: 'Archive this lead?' })).toBeVisible()
    await page.getByRole('button', { name: 'Archive' }).last().click()

    await expect(page.getByRole('heading', { name: 'Archive this lead?' })).not.toBeVisible()
    await expect(page.getByRole('table').getByText(TEST_LEAD.name)).toHaveCount(0)
  })

  test('lead detail renders overview/history, add-note works, and assign-to-me/unassign work', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockLeadsResource(page, [TEST_LEAD])
    await loginViaUi(page)
    await goToLeads(page)

    await page.getByRole('table').getByRole('link', { name: TEST_LEAD.name }).click()
    await expect(page.getByRole('heading', { name: TEST_LEAD.name })).toBeVisible()
    await expect(page.getByText('Lead created')).toBeVisible()

    await page.getByLabel('Write a note').fill('Left a voicemail.')
    await page.getByRole('button', { name: 'Add note' }).click()
    await expect(page.getByText('Left a voicemail.')).toBeVisible()

    await expect(page.getByRole('button', { name: 'Assign to me' })).toBeVisible()
    await page.getByRole('button', { name: 'Assign to me' }).click()
    await expect(page.getByRole('main').getByText('Assigned to you')).toBeVisible()

    await page.getByRole('button', { name: 'Unassign' }).click()
    await expect(page.getByRole('button', { name: 'Assign to me' })).toBeVisible()
  })

  test('a failed list request shows a retryable error state', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await page.route('**/api/v1/leads*', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 500,
          code: 'INTERNAL_ERROR',
          message: 'An unexpected error occurred',
          path: '/api/v1/leads',
        }),
      }),
    )
    await loginViaUi(page)
    await goToLeads(page)

    await expect(page.getByText(/couldn't load your leads/i)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible()
  })
})
