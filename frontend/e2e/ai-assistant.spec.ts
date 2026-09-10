import { expect, test } from '@playwright/test'

import {
  loginViaUi,
  mockAiChatDisabled,
  mockAiChatProviderError,
  mockAiChatSuccess,
  mockAuthenticatedSession,
  mockDocumentsResource,
  mockLoginSuccess,
  TEST_ANALYTICS,
  TEST_DOCUMENT,
} from './mocks'
import { attachConsoleGuard, horizontalOverflowPx } from './sanity'

/** A user with no roles at all — proves the AI_USE nav gate fails closed, mirroring navigation.spec.ts's identical helper. */
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

async function openDrawer(page: import('@playwright/test').Page) {
  // Both the TopBar and the desktop Sidebar render an "Open AI Assistant"
  // trigger simultaneously at desktop widths — scope to the TopBar (a
  // <header>, semantic role "banner") to avoid the strict-mode ambiguity.
  await page.getByRole('banner').getByRole('button', { name: 'Open AI Assistant' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
}

test.describe('ai-assistant', () => {
  test('1. the trigger opens the drawer and the placeholder is gone, no console/network errors', async ({
    page,
  }) => {
    const guard = attachConsoleGuard(page)
    await mockAuthenticatedSession(page)
    await mockAiChatSuccess(page, { answer: 'x' })
    await loginViaUi(page)

    await openDrawer(page)
    await expect(page.getByText('Coming in Phase 25')).toHaveCount(0)
    await expect(page.getByText('Ask about your business')).toBeVisible()

    expect(guard.pageErrors, `page errors: ${guard.pageErrors.join('; ')}`).toEqual([])
    expect(guard.failedRequests, `failed requests: ${guard.failedRequests.join('; ')}`).toEqual([])
  })

  test('2. sending a message shows the user message, assistant answer, and a source', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockAiChatSuccess(page, {
      answer: 'You have 12 active customers.',
      sources: [{ documentId: TEST_DOCUMENT.id, documentName: TEST_DOCUMENT.originalFilename, chunkIndex: 0 }],
    })
    await loginViaUi(page)
    await openDrawer(page)

    await page.getByLabel('Message').fill('How many active customers?')
    await page.getByRole('button', { name: 'Send' }).click()

    await expect(page.getByText('How many active customers?')).toBeVisible()
    await expect(page.getByText('You have 12 active customers.')).toBeVisible()
    await expect(page.getByRole('status')).toHaveCount(0)
    await expect(page.getByRole('link', { name: TEST_DOCUMENT.originalFilename })).toBeVisible()
  })

  test('3. clicking a source navigates to the real document detail page', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [TEST_DOCUMENT])
    await mockAiChatSuccess(page, {
      answer: 'See the attached document.',
      sources: [{ documentId: TEST_DOCUMENT.id, documentName: TEST_DOCUMENT.originalFilename, chunkIndex: 0 }],
    })
    await loginViaUi(page)
    await openDrawer(page)

    await page.getByLabel('Message').fill('What does the document say?')
    await page.getByRole('button', { name: 'Send' }).click()
    await page.getByRole('link', { name: TEST_DOCUMENT.originalFilename }).click()
    await page.waitForURL(`**/documents/${TEST_DOCUMENT.id}`)

    // The drawer stays open on top of the destination page after a source
    // link navigation (no auto-close behavior is wired, and none was asked
    // for) — while it's open, Radix correctly marks the rest of the page
    // `aria-hidden` (standard modal-dialog semantics), so the destination
    // heading is legitimately not in the accessibility tree yet. Close the
    // drawer first, exactly as a real user would to see the page beneath.
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).not.toBeVisible()
    await expect(page.getByRole('heading', { name: TEST_DOCUMENT.originalFilename })).toBeVisible()
  })

  test('4. AI_DISABLED (503) shows the informational alert', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockAiChatDisabled(page)
    await loginViaUi(page)
    await openDrawer(page)

    await page.getByLabel('Message').fill('Hello')
    await page.getByRole('button', { name: 'Send' }).click()

    await expect(page.getByText("AI features aren't enabled for this workspace.")).toBeVisible()
    await expect(page.getByRole('button', { name: 'Retry' })).toHaveCount(0)
  })

  test('5. AI_PROVIDER_ERROR (502) shows the destructive alert with Retry', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockAiChatProviderError(page)
    await loginViaUi(page)
    await openDrawer(page)

    await page.getByLabel('Message').fill('Hello')
    await page.getByRole('button', { name: 'Send' }).click()

    await expect(page.getByText('Unable to get a response right now. Try again.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible()
  })

  test('6. Retry resends exactly the same failed latest message', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const requests: string[] = []
    let callCount = 0
    await page.route('**/api/v1/ai/chat', async (route) => {
      callCount += 1
      const body = route.request().postDataJSON() as { message: string }
      requests.push(body.message)
      if (callCount === 1) {
        return route.fulfill({
          status: 502,
          contentType: 'application/json',
          body: JSON.stringify({
            timestamp: new Date().toISOString(),
            status: 502,
            code: 'AI_PROVIDER_ERROR',
            message: 'The AI assistant is temporarily unavailable',
            path: '/api/v1/ai/chat',
          }),
        })
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ answer: 'Recovered answer.', sources: [] }),
      })
    })
    await loginViaUi(page)
    await openDrawer(page)

    await page.getByLabel('Message').fill('flaky question')
    await page.getByRole('button', { name: 'Send' }).click()
    await page.getByRole('button', { name: 'Retry' }).click()

    await expect(page.getByText('Recovered answer.')).toBeVisible()
    expect(requests).toEqual(['flaky question', 'flaky question'])
  })

  test('7. empty message is blocked client-side, no request sent', async ({ page }) => {
    await mockAuthenticatedSession(page)
    let requestCount = 0
    await page.route('**/api/v1/ai/chat', (route) => {
      requestCount += 1
      return route.fallback()
    })
    await loginViaUi(page)
    await openDrawer(page)

    await page.getByRole('button', { name: 'Send' }).click()
    await expect(page.getByText('Message is required')).toBeVisible()
    expect(requestCount).toBe(0)
  })

  test('8. a message over 2000 characters is blocked client-side, no request sent', async ({ page }) => {
    await mockAuthenticatedSession(page)
    let requestCount = 0
    await page.route('**/api/v1/ai/chat', (route) => {
      requestCount += 1
      return route.fallback()
    })
    await loginViaUi(page)
    await openDrawer(page)

    await page.getByLabel('Message').fill('x'.repeat(2001))
    await page.getByRole('button', { name: 'Send' }).click()
    await expect(page.getByText('Message must be 2000 characters or fewer')).toBeVisible()
    expect(requestCount).toBe(0)
  })

  test('9. exactly 2000 characters is accepted', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockAiChatSuccess(page, { answer: 'Received.' })
    await loginViaUi(page)
    await openDrawer(page)

    await page.getByLabel('Message').fill('x'.repeat(2000))
    await page.getByRole('button', { name: 'Send' }).click()
    await expect(page.getByText('Received.')).toBeVisible()
  })

  test('10. statelessness: each send transmits only the latest message, never prior turns or a conversationId', async ({
    page,
  }) => {
    await mockAuthenticatedSession(page)
    const requestBodies: Record<string, unknown>[] = []
    let callCount = 0
    await page.route('**/api/v1/ai/chat', async (route) => {
      callCount += 1
      requestBodies.push(route.request().postDataJSON() as Record<string, unknown>)
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ answer: callCount === 1 ? 'First answer.' : 'Second answer.', sources: [] }),
      })
    })
    await loginViaUi(page)
    await openDrawer(page)

    await page.getByLabel('Message').fill('first question')
    await page.getByRole('button', { name: 'Send' }).click()
    await expect(page.getByText('First answer.')).toBeVisible()

    await page.getByLabel('Message').fill('second question')
    await page.getByRole('button', { name: 'Send' }).click()
    await expect(page.getByText('Second answer.')).toBeVisible()

    expect(requestBodies).toEqual([{ message: 'first question' }, { message: 'second question' }])
    expect(requestBodies[1]).not.toHaveProperty('conversationId')
    expect(JSON.stringify(requestBodies[1])).not.toContain('first question')
  })

  test('11. Clear conversation resets the view and generates no network request', async ({ page }) => {
    await mockAuthenticatedSession(page)
    let callCount = 0
    await page.route('**/api/v1/ai/chat', (route) => {
      callCount += 1
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ answer: 'An answer.', sources: [] }),
      })
    })
    await loginViaUi(page)
    await openDrawer(page)

    await page.getByLabel('Message').fill('a question')
    await page.getByRole('button', { name: 'Send' }).click()
    await expect(page.getByText('An answer.')).toBeVisible()
    const countAfterSend = callCount

    await page.getByRole('button', { name: 'Clear conversation' }).click()
    await expect(page.getByText('Ask about your business')).toBeVisible()
    expect(callCount).toBe(countAfterSend)
  })

  test('12. a user without AI_USE never sees the AI Assistant trigger', async ({ page }) => {
    await mockNoRoleSession(page)
    await loginViaUi(page)

    await expect(page.getByRole('button', { name: 'Open AI Assistant' })).toHaveCount(0)
  })

  test('13. responsive: the drawer works at all six breakpoints, no horizontal overflow, screenshots', async ({
    page,
  }) => {
    await mockAuthenticatedSession(page)
    await mockAiChatSuccess(page, {
      answer:
        'This is a longer assistant response used to verify text wraps correctly inside the drawer at every required breakpoint without causing horizontal overflow.',
      sources: [{ documentId: TEST_DOCUMENT.id, documentName: 'a-fairly-long-document-name-for-wrapping.pdf', chunkIndex: 0 }],
    })
    await loginViaUi(page)
    await openDrawer(page)
    await page.getByLabel('Message').fill('Tell me something long')
    await page.getByRole('button', { name: 'Send' }).click()
    await expect(page.getByText('a-fairly-long-document-name-for-wrapping.pdf')).toBeVisible()

    const breakpoints = [
      { width: 1440, height: 900 },
      { width: 1280, height: 800 },
      { width: 1024, height: 768 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 },
      { width: 375, height: 812 },
    ]
    for (const { width, height } of breakpoints) {
      await page.setViewportSize({ width, height })
      expect(await horizontalOverflowPx(page)).toBeLessThanOrEqual(1)
      await expect(page.getByRole('button', { name: 'Send' })).toBeVisible()
      await page.screenshot({ path: `test-results/screenshots/ai-assistant-${width}x${height}.png`, fullPage: true })
    }
  })
})
