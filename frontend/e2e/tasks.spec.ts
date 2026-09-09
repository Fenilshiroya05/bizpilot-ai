import { expect, test } from '@playwright/test'

import {
  goToTasks,
  loginViaUi,
  mockAuthenticatedSession,
  mockAuthenticatedSessionAs,
  mockCustomersResource,
  mockLeadsResource,
  mockTasksResource,
  TEST_CUSTOMER,
  TEST_LEAD,
  TEST_TASK,
  TEST_USER,
} from './mocks'
import { attachConsoleGuard } from './sanity'

test.describe('tasks', () => {
  test('1. the list renders the mocked tasks with the real backend fields, no console/network errors', async ({
    page,
  }) => {
    const guard = attachConsoleGuard(page)
    await mockAuthenticatedSession(page)
    await mockTasksResource(page)
    await loginViaUi(page)
    await goToTasks(page)

    await expect(page.getByRole('heading', { name: 'Tasks' })).toBeVisible()
    const table = page.getByRole('table')
    await expect(table.getByText(TEST_TASK.title)).toBeVisible()
    await expect(table.getByText('MEDIUM')).toBeVisible()
    await expect(table.getByText('TODO')).toBeVisible()

    expect(guard.pageErrors, `page errors: ${guard.pageErrors.join('; ')}`).toEqual([])
    expect(guard.failedRequests, `failed requests: ${guard.failedRequests.join('; ')}`).toEqual([])
  })

  test('2. search filters the list', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [
      TEST_TASK,
      { ...TEST_TASK, id: 'e2e-task-2', title: 'Send the proposal' },
    ])
    await loginViaUi(page)
    await goToTasks(page)

    const table = page.getByRole('table')
    await page.getByLabel('Search tasks').fill('proposal')
    await expect(table.getByText('Send the proposal')).toBeVisible()
    await expect(table.getByText(TEST_TASK.title)).toHaveCount(0)
  })

  test('3. status filter changes the request', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [
      TEST_TASK,
      { ...TEST_TASK, id: 'e2e-task-2', title: 'In progress task', status: 'IN_PROGRESS' },
    ])
    await loginViaUi(page)
    await goToTasks(page)

    const table = page.getByRole('table')
    await page.getByLabel('Status filter').selectOption('IN_PROGRESS')
    await expect(table.getByText('In progress task')).toBeVisible()
    await expect(table.getByText(TEST_TASK.title)).toHaveCount(0)
  })

  test('4. priority filter changes the request', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [
      TEST_TASK,
      { ...TEST_TASK, id: 'e2e-task-2', title: 'Urgent task', priority: 'URGENT' },
    ])
    await loginViaUi(page)
    await goToTasks(page)

    const table = page.getByRole('table')
    await page.getByLabel('Priority filter').selectOption('URGENT')
    await expect(table.getByText('Urgent task')).toBeVisible()
    await expect(table.getByText(TEST_TASK.title)).toHaveCount(0)
  })

  test('5. pagination advances to the next page', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const many = Array.from({ length: 25 }, (_, i) => ({
      ...TEST_TASK,
      id: `e2e-task-${i}`,
      title: `Task ${String(i).padStart(2, '0')}`,
    }))
    await mockTasksResource(page, many)
    await loginViaUi(page)
    await goToTasks(page)

    await expect(page.getByText('Page 1 of 2')).toBeVisible()
    await page.getByRole('button', { name: 'Next page' }).click()
    await expect(page.getByText('Page 2 of 2')).toBeVisible()
  })

  test('6. My Tasks quick filter shows only tasks assigned to the current user', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [
      TEST_TASK, // unassigned
      { ...TEST_TASK, id: 'e2e-task-2', title: 'My own task', assignedToUserId: TEST_USER.id },
    ])
    await loginViaUi(page)
    await goToTasks(page)

    const table = page.getByRole('table')
    await page.getByRole('button', { name: 'My Tasks' }).click()
    await expect(table.getByText('My own task')).toBeVisible()
    await expect(table.getByText(TEST_TASK.title)).toHaveCount(0)
  })

  test('7. Unassigned quick filter shows only unassigned tasks', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [
      TEST_TASK, // unassigned
      { ...TEST_TASK, id: 'e2e-task-2', title: 'My own task', assignedToUserId: TEST_USER.id },
    ])
    await loginViaUi(page)
    await goToTasks(page)

    const table = page.getByRole('table')
    await page.getByRole('button', { name: 'Unassigned' }).click()
    await expect(table.getByText(TEST_TASK.title)).toBeVisible()
    await expect(table.getByText('My own task')).toHaveCount(0)
  })

  test('8. Due Today / Overdue quick filter shows only due-or-overdue tasks', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [
      { ...TEST_TASK, dueDate: '2020-01-01' }, // overdue
      { ...TEST_TASK, id: 'e2e-task-2', title: 'Future task', dueDate: '2999-01-01' },
    ])
    await loginViaUi(page)
    await goToTasks(page)

    const table = page.getByRole('table')
    await page.getByRole('button', { name: 'Due Today / Overdue' }).click()
    await expect(table.getByText(TEST_TASK.title)).toBeVisible()
    await expect(table.getByText('Future task')).toHaveCount(0)
  })

  test('9. create: validation error for missing title, then a valid submission succeeds', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [])
    await loginViaUi(page)
    await goToTasks(page)

    await page.getByRole('button', { name: 'Create task' }).click()
    await page.getByRole('button', { name: 'Create task' }).last().click()
    await expect(page.getByText('Title is required')).toBeVisible()

    await page.getByLabel('Title').fill('New task created via UI')
    await page.getByRole('button', { name: 'Create task' }).last().click()

    await expect(page.getByRole('heading', { name: 'Create task' })).not.toBeVisible()
    await expect(page.getByRole('table').getByText('New task created via UI')).toBeVisible()
  })

  test('10. create: a task with a related Customer and Lead', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockCustomersResource(page, [TEST_CUSTOMER])
    await mockLeadsResource(page, [TEST_LEAD])
    await mockTasksResource(page, [])
    await loginViaUi(page)
    await goToTasks(page)

    await page.getByRole('button', { name: 'Create task' }).click()
    await page.getByLabel('Title').fill('Task with relationships')

    await page.getByRole('combobox', { name: 'Customer' }).fill(TEST_CUSTOMER.name)
    await page.getByRole('option', { name: new RegExp(TEST_CUSTOMER.name) }).click()

    await page.getByRole('combobox', { name: 'Lead' }).fill(TEST_LEAD.name)
    await page.getByRole('option', { name: new RegExp(TEST_LEAD.name) }).click()

    await page.getByRole('button', { name: 'Create task' }).last().click()
    await expect(page.getByRole('table').getByText('Task with relationships')).toBeVisible()

    await page.getByRole('table').getByRole('link', { name: 'Task with relationships' }).click()
    await expect(page.getByRole('link', { name: TEST_CUSTOMER.name })).toHaveAttribute(
      'href',
      `/customers/${TEST_CUSTOMER.id}`,
    )
    await expect(page.getByRole('link', { name: TEST_LEAD.name })).toHaveAttribute('href', `/leads/${TEST_LEAD.id}`)
  })

  test('11. edit: updates title, priority, due date, status, and notes', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [TEST_TASK])
    await loginViaUi(page)
    await goToTasks(page)

    await page.getByRole('button', { name: `Actions for ${TEST_TASK.title}` }).click()
    await page.getByRole('menuitem', { name: 'Edit' }).click()

    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('Title').fill('Updated task title')
    await dialog.getByLabel('Priority').selectOption('URGENT')
    await dialog.getByLabel('Due date').fill('2026-12-31')
    await dialog.getByLabel('Status').selectOption('IN_PROGRESS')
    await dialog.getByLabel('Notes').fill('Updated via edit dialog.')
    await dialog.getByRole('button', { name: 'Save changes' }).click()

    await expect(page.getByRole('heading', { name: 'Edit task' })).not.toBeVisible()
    const table = page.getByRole('table')
    await expect(table.getByText('Updated task title')).toBeVisible()
    await expect(table.getByText('URGENT')).toBeVisible()
    await expect(table.getByText('IN PROGRESS')).toBeVisible()
  })

  test('12. assignment: Assign to me, then Unassign', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [TEST_TASK])
    await loginViaUi(page)
    await goToTasks(page)

    await page.getByRole('table').getByRole('link', { name: TEST_TASK.title }).click()
    await expect(page.getByRole('button', { name: 'Assign to me' })).toBeVisible()

    await page.getByRole('button', { name: 'Assign to me' }).click()
    await expect(page.getByRole('main').getByText('Assigned to you')).toBeVisible()

    await page.getByRole('button', { name: 'Unassign' }).click()
    await expect(page.getByRole('button', { name: 'Assign to me' })).toBeVisible()
  })

  test('13. cancel requires confirmation and then succeeds (OWNER)', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [TEST_TASK])
    await loginViaUi(page)
    await goToTasks(page)

    await page.getByRole('table').getByRole('link', { name: TEST_TASK.title }).click()
    await page.getByRole('button', { name: 'Cancel task' }).click()
    await expect(page.getByRole('heading', { name: 'Cancel this task?' })).toBeVisible()
    await page.getByRole('button', { name: 'Cancel task' }).last().click()

    await expect(page.getByRole('heading', { name: 'Cancel this task?' })).not.toBeVisible()
    await expect(page.getByText('CANCELLED', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Edit' })).toHaveCount(0)
  })

  test('14. RBAC — SALES can read/create/edit but cannot cancel', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['SALES'])
    await mockTasksResource(page, [TEST_TASK])
    await loginViaUi(page)
    await goToTasks(page)

    await expect(page.getByRole('button', { name: 'Create task' })).toBeVisible()
    await page.getByRole('button', { name: `Actions for ${TEST_TASK.title}` }).click()
    await expect(page.getByRole('menuitem', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: 'Cancel' })).toHaveCount(0)
    await page.keyboard.press('Escape')

    await page.getByRole('table').getByRole('link', { name: TEST_TASK.title }).click()
    await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Cancel task' })).toHaveCount(0)
  })

  test('15. RBAC — EMPLOYEE can read/create/edit but cannot cancel', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['EMPLOYEE'])
    await mockTasksResource(page, [TEST_TASK])
    await loginViaUi(page)
    await goToTasks(page)

    await expect(page.getByRole('button', { name: 'Create task' })).toBeVisible()
    await page.getByRole('button', { name: `Actions for ${TEST_TASK.title}` }).click()
    await expect(page.getByRole('menuitem', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: 'Cancel' })).toHaveCount(0)
    await page.keyboard.press('Escape')

    await page.getByRole('table').getByRole('link', { name: TEST_TASK.title }).click()
    await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Cancel task' })).toHaveCount(0)
  })

  test('16. RBAC — MANAGER has full access including Cancel', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['MANAGER'])
    await mockTasksResource(page, [TEST_TASK])
    await loginViaUi(page)
    await goToTasks(page)

    await expect(page.getByRole('button', { name: 'Create task' })).toBeVisible()
    await page.getByRole('table').getByRole('link', { name: TEST_TASK.title }).click()
    await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Cancel task' })).toBeVisible()
  })

  test('17. clearing filters resets the list', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [
      TEST_TASK,
      { ...TEST_TASK, id: 'e2e-task-2', title: 'In progress task', status: 'IN_PROGRESS' },
    ])
    await loginViaUi(page)
    await goToTasks(page)

    await page.getByLabel('Status filter').selectOption('IN_PROGRESS')
    await expect(page.getByRole('table').getByText(TEST_TASK.title)).toHaveCount(0)
    await page.getByRole('button', { name: 'Clear filters' }).click()
    await expect(page.getByRole('table').getByText(TEST_TASK.title)).toBeVisible()
  })

  test('18. empty state offers a create action when there are no tasks at all', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockTasksResource(page, [])
    await loginViaUi(page)
    await goToTasks(page)

    await expect(page.getByText('No tasks yet')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Add your first task' })).toBeVisible()
  })

  test('19. a failed list request shows a retryable error state', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await page.route('**/api/v1/tasks*', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 500,
          code: 'INTERNAL_ERROR',
          message: 'An unexpected error occurred',
          path: '/api/v1/tasks',
        }),
      }),
    )
    await loginViaUi(page)
    await goToTasks(page)

    await expect(page.getByText(/couldn't load your tasks/i)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible()
  })
})
