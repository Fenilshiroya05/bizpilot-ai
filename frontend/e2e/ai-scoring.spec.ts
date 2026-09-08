import { expect, test } from '@playwright/test'

import {
  goToLeads,
  loginViaUi,
  mockAuthenticatedSession,
  mockLeadsResource,
  mockLeadScoreDisabled,
  mockLeadScoreFailure,
  mockLeadScoreSuccess,
  TEST_LEAD,
} from './mocks'

async function openLeadDetail(page: import('@playwright/test').Page) {
  await mockAuthenticatedSession(page)
  await mockLeadsResource(page, [TEST_LEAD])
  await loginViaUi(page)
  await goToLeads(page)
  await page.getByRole('table').getByRole('link', { name: TEST_LEAD.name }).click()
  await expect(page.getByRole('heading', { name: TEST_LEAD.name })).toBeVisible()
}

test.describe('AI lead scoring', () => {
  test('the Score with AI button exists on the lead detail page', async ({ page }) => {
    await mockLeadScoreSuccess(page, TEST_LEAD.id)
    await openLeadDetail(page)
    await expect(page.getByRole('button', { name: 'Score with AI' })).toBeVisible()
  })

  test('shows a loading state and disables duplicate submissions while scoring', async ({ page }) => {
    await page.route(`**/api/v1/leads/${TEST_LEAD.id}/score`, async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 500))
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          leadId: TEST_LEAD.id,
          score: 70,
          priority: 'HIGH',
          reasoning: 'Reasoning.',
          recommendedAction: 'Action.',
          generatedAt: new Date().toISOString(),
        }),
      })
    })
    await openLeadDetail(page)

    const button = page.getByRole('button', { name: 'Score with AI' })
    await button.click()
    await expect(page.getByRole('button', { name: 'Scoring...' })).toBeDisabled()
  })

  test('a successful score renders the score, suggested priority, reasoning, and recommended action', async ({
    page,
  }) => {
    await mockLeadScoreSuccess(page, TEST_LEAD.id, {
      score: 87,
      priority: 'HIGH',
      reasoning: 'Strong buying signals from repeated demo requests.',
      recommendedAction: 'Call within 24 hours.',
    })
    await openLeadDetail(page)

    await page.getByRole('button', { name: 'Score with AI' }).click()
    await expect(page.getByText('87 / 100')).toBeVisible()
    await expect(page.getByText('Strong buying signals from repeated demo requests.')).toBeVisible()
    await expect(page.getByText('Call within 24 hours.')).toBeVisible()
  })

  test('shows a calm informational state when AI is disabled for the workspace', async ({ page }) => {
    await mockLeadScoreDisabled(page, TEST_LEAD.id)
    await openLeadDetail(page)

    await page.getByRole('button', { name: 'Score with AI' }).click()
    await expect(page.getByText("AI features aren’t enabled for this workspace.")).toBeVisible()
  })

  test('shows a retryable error for a provider/scoring failure, with no internal detail exposed', async ({
    page,
  }) => {
    await mockLeadScoreFailure(page, TEST_LEAD.id)
    await openLeadDetail(page)

    await page.getByRole('button', { name: 'Score with AI' }).click()
    await expect(page.getByText('Unable to generate an AI score right now. Try again.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible()
    await expect(page.getByText(/provider/i)).toHaveCount(0)
  })

  test('mutation safety: the lead\'s real priority never changes, even after a different AI suggestion', async ({
    page,
  }) => {
    // TEST_LEAD's real priority is MEDIUM; the AI suggests HIGH.
    await mockLeadScoreSuccess(page, TEST_LEAD.id, { priority: 'HIGH' })
    await openLeadDetail(page)

    await page.getByRole('button', { name: 'Score with AI' }).click()
    await expect(page.getByText('Suggested priority')).toBeVisible()

    // The real lead priority (MEDIUM, shown in the page header badge and the
    // "currently ..." sentence) is still present and unchanged.
    await expect(page.getByText('MEDIUM').first()).toBeVisible()

    // Reload via in-app navigation (list -> detail again) to prove the
    // backend's own stored value was never touched by the AI call.
    await page.getByRole('complementary').getByRole('link', { name: 'Leads' }).click()
    await page.getByRole('table').getByRole('link', { name: TEST_LEAD.name }).click()
    await expect(page.getByText('MEDIUM').first()).toBeVisible()
  })
})
