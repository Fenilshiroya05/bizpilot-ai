import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { expect, test } from '@playwright/test'

import {
  goToDocuments,
  loginViaUi,
  mockAuthenticatedSession,
  mockAuthenticatedSessionAs,
  mockDocumentsResource,
  TEST_DOCUMENT,
} from './mocks'
import { attachConsoleGuard } from './sanity'

const FIXTURES = path.join(path.dirname(fileURLToPath(import.meta.url)), 'fixtures')
const PDF_FIXTURE = path.join(FIXTURES, 'sample.pdf')
const TXT_FIXTURE = path.join(FIXTURES, 'sample.txt')
const DOCX_FIXTURE = path.join(FIXTURES, 'sample.docx')
const INVALID_FIXTURE = path.join(FIXTURES, 'invalid.exe')

test.describe('documents', () => {
  test('1. the list renders the mocked documents with the real backend fields, no console/network errors', async ({
    page,
  }) => {
    const guard = attachConsoleGuard(page)
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page)
    await loginViaUi(page)
    await goToDocuments(page)

    await expect(page.getByRole('heading', { name: 'Documents' })).toBeVisible()
    const table = page.getByRole('table')
    await expect(table.getByText(TEST_DOCUMENT.originalFilename)).toBeVisible()
    await expect(table.getByText('PDF', { exact: true })).toBeVisible()
    await expect(table.getByText('COMPLETED')).toBeVisible()

    expect(guard.pageErrors, `page errors: ${guard.pageErrors.join('; ')}`).toEqual([])
    expect(guard.failedRequests, `failed requests: ${guard.failedRequests.join('; ')}`).toEqual([])
  })

  test('2. search filters the list', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [
      TEST_DOCUMENT,
      { ...TEST_DOCUMENT, id: 'e2e-document-2', originalFilename: 'contract.docx' },
    ])
    await loginViaUi(page)
    await goToDocuments(page)

    const table = page.getByRole('table')
    await page.getByLabel('Search documents').fill('contract')
    await expect(table.getByText('contract.docx')).toBeVisible()
    await expect(table.getByText(TEST_DOCUMENT.originalFilename)).toHaveCount(0)
  })

  test('3. status filter changes the request', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [
      TEST_DOCUMENT,
      { ...TEST_DOCUMENT, id: 'e2e-document-2', originalFilename: 'pending.pdf', status: 'PROCESSING' },
    ])
    await loginViaUi(page)
    await goToDocuments(page)

    const table = page.getByRole('table')
    await page.getByLabel('Status filter').selectOption('PROCESSING')
    await expect(table.getByText('pending.pdf')).toBeVisible()
    await expect(table.getByText(TEST_DOCUMENT.originalFilename)).toHaveCount(0)
  })

  test('4. content type filter changes the request', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [
      TEST_DOCUMENT,
      { ...TEST_DOCUMENT, id: 'e2e-document-2', originalFilename: 'notes.txt', contentType: 'text/plain' },
    ])
    await loginViaUi(page)
    await goToDocuments(page)

    const table = page.getByRole('table')
    await page.getByLabel('Content type filter').selectOption('text/plain')
    await expect(table.getByText('notes.txt')).toBeVisible()
    await expect(table.getByText(TEST_DOCUMENT.originalFilename)).toHaveCount(0)
  })

  test('5. clearing filters resets the list in a single update', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [
      TEST_DOCUMENT,
      { ...TEST_DOCUMENT, id: 'e2e-document-2', originalFilename: 'pending.pdf', status: 'PROCESSING' },
    ])
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByLabel('Status filter').selectOption('PROCESSING')
    await expect(page.getByRole('table').getByText(TEST_DOCUMENT.originalFilename)).toHaveCount(0)
    await page.getByRole('button', { name: 'Clear filters' }).click()
    await expect(page.getByRole('table').getByText(TEST_DOCUMENT.originalFilename)).toBeVisible()
  })

  test('6. pagination advances to the next page', async ({ page }) => {
    await mockAuthenticatedSession(page)
    const many = Array.from({ length: 25 }, (_, i) => ({
      ...TEST_DOCUMENT,
      id: `e2e-document-${i}`,
      originalFilename: `file-${String(i).padStart(2, '0')}.pdf`,
    }))
    await mockDocumentsResource(page, many)
    await loginViaUi(page)
    await goToDocuments(page)

    await expect(page.getByText('Page 1 of 2')).toBeVisible()
    await page.getByRole('button', { name: 'Next page' }).click()
    await expect(page.getByText('Page 2 of 2')).toBeVisible()
  })

  test('7. upload: a valid PDF succeeds and shows the real returned status', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [])
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByRole('button', { name: 'Upload document' }).click()
    await page.getByLabel('File').setInputFiles(PDF_FIXTURE)
    await page.getByRole('button', { name: 'Upload' }).click()

    await expect(page.getByRole('heading', { name: 'Upload document' })).not.toBeVisible()
    await expect(page.getByRole('table').getByText('sample.pdf')).toBeVisible()
    await expect(page.getByRole('table').getByText('UPLOADED', { exact: true })).toBeVisible()
  })

  test('8. upload: a valid TXT succeeds', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [])
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByRole('button', { name: 'Upload document' }).click()
    await page.getByLabel('File').setInputFiles(TXT_FIXTURE)
    await page.getByRole('button', { name: 'Upload' }).click()

    await expect(page.getByRole('table').getByText('sample.txt')).toBeVisible()
  })

  test('9. upload: a valid DOCX succeeds', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [])
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByRole('button', { name: 'Upload document' }).click()
    await page.getByLabel('File').setInputFiles(DOCX_FIXTURE)
    await page.getByRole('button', { name: 'Upload' }).click()

    await expect(page.getByRole('table').getByText('sample.docx')).toBeVisible()
  })

  test('10. upload: an unsupported file type is rejected client-side, no request is sent', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [])
    let uploadRequests = 0
    await page.route('**/api/v1/documents', (route) => {
      if (route.request().method() === 'POST') uploadRequests += 1
      return route.fallback()
    })
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByRole('button', { name: 'Upload document' }).click()
    await page.getByLabel('File').setInputFiles(INVALID_FIXTURE)
    await page.getByRole('button', { name: 'Upload' }).click()

    await expect(page.getByText('Unsupported file type. Use PDF, TXT, or DOCX.')).toBeVisible()
    expect(uploadRequests).toBe(0)
  })

  test('11. upload: a file over 20 MB is rejected client-side, no request is sent', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [])
    let uploadRequests = 0
    await page.route('**/api/v1/documents', (route) => {
      if (route.request().method() === 'POST') uploadRequests += 1
      return route.fallback()
    })
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByRole('button', { name: 'Upload document' }).click()
    // Generated in-memory (no oversized fixture committed to the repo).
    await page.getByLabel('File').setInputFiles({
      name: 'huge.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.alloc(20 * 1024 * 1024 + 1, 0),
    })
    await page.getByRole('button', { name: 'Upload' }).click()

    await expect(page.getByText('File exceeds the maximum allowed size of 20 MB')).toBeVisible()
    expect(uploadRequests).toBe(0)
  })

  test('12. detail: shows metadata after upload, including the real status', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [])
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByRole('button', { name: 'Upload document' }).click()
    await page.getByLabel('File').setInputFiles(PDF_FIXTURE)
    await page.getByRole('button', { name: 'Upload' }).click()
    await expect(page.getByRole('table').getByText('sample.pdf')).toBeVisible()

    await page.getByRole('table').getByRole('link', { name: 'sample.pdf' }).click()
    await expect(page.getByRole('heading', { name: 'sample.pdf' })).toBeVisible()
    await expect(page.getByText('PDF', { exact: true })).toBeVisible()
    await expect(page.getByText('UPLOADED', { exact: true })).toBeVisible()
  })

  test('13. download: actually downloads the file with the correct filename', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [TEST_DOCUMENT])
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByRole('table').getByRole('link', { name: TEST_DOCUMENT.originalFilename }).click()
    await expect(page.getByRole('heading', { name: TEST_DOCUMENT.originalFilename })).toBeVisible()

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Download' }).click(),
    ])
    expect(download.suggestedFilename()).toBe(TEST_DOCUMENT.originalFilename)
    const downloadPath = await download.path()
    expect(downloadPath).toBeTruthy()
    const fs = await import('node:fs')
    const stats = fs.statSync(downloadPath!)
    expect(stats.size).toBeGreaterThan(0)
  })

  test('14. delete: requires confirmation and then removes the document', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [TEST_DOCUMENT])
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByRole('table').getByRole('link', { name: TEST_DOCUMENT.originalFilename }).click()
    await page.getByRole('button', { name: 'Delete' }).click()
    await expect(page.getByRole('heading', { name: 'Delete this document?' })).toBeVisible()
    await page.getByRole('button', { name: 'Delete document' }).last().click()

    await page.waitForURL('**/documents')
    await expect(page.getByRole('table').getByText(TEST_DOCUMENT.originalFilename)).toHaveCount(0)
  })

  test('15. RBAC — OWNER has full access including Delete', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [TEST_DOCUMENT])
    await loginViaUi(page)
    await goToDocuments(page)

    await expect(page.getByRole('button', { name: 'Upload document' })).toBeVisible()
    await page.getByRole('table').getByRole('link', { name: TEST_DOCUMENT.originalFilename }).click()
    await expect(page.getByRole('button', { name: 'Download' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Delete' })).toBeVisible()
  })

  test('16. RBAC — SALES can view/upload/download but cannot delete', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['SALES'])
    await mockDocumentsResource(page, [TEST_DOCUMENT])
    await loginViaUi(page)
    await goToDocuments(page)

    await expect(page.getByRole('button', { name: 'Upload document' })).toBeVisible()

    await page.getByRole('table').getByRole('link', { name: TEST_DOCUMENT.originalFilename }).click()
    await expect(page.getByRole('button', { name: 'Download' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Delete' })).toHaveCount(0)

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Download' }).click(),
    ])
    expect(download.suggestedFilename()).toBe(TEST_DOCUMENT.originalFilename)
  })

  test('17. RBAC — EMPLOYEE can view/download but cannot upload or delete', async ({ page }) => {
    await mockAuthenticatedSessionAs(page, ['EMPLOYEE'])
    await mockDocumentsResource(page, [TEST_DOCUMENT])
    await loginViaUi(page)
    await goToDocuments(page)

    await expect(page.getByRole('button', { name: 'Upload document' })).toHaveCount(0)

    await page.getByRole('table').getByRole('link', { name: TEST_DOCUMENT.originalFilename }).click()
    await expect(page.getByRole('button', { name: 'Delete' })).toHaveCount(0)

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Download' }).click(),
    ])
    expect(download.suggestedFilename()).toBe(TEST_DOCUMENT.originalFilename)
  })

  test('18. RBAC — ADMIN and MANAGER have full access including Delete', async ({ page }) => {
    for (const role of ['ADMIN', 'MANAGER']) {
      await mockAuthenticatedSessionAs(page, [role])
      await mockDocumentsResource(page, [TEST_DOCUMENT])
      await loginViaUi(page)
      await goToDocuments(page)

      await expect(page.getByRole('button', { name: 'Upload document' })).toBeVisible()
      await page.getByRole('table').getByRole('link', { name: TEST_DOCUMENT.originalFilename }).click()
      await expect(page.getByRole('button', { name: 'Delete' })).toBeVisible()
    }
  })

  test('19. processing lifecycle: PROCESSING transitions to COMPLETED and polling stops', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [{ ...TEST_DOCUMENT, status: 'PROCESSING' }])
    // Registered AFTER mockDocumentsResource — Playwright tries the most
    // recently registered matching route first, so this override takes
    // priority over the generic detail route for GET only.
    let getCount = 0
    await page.route('**/api/v1/documents/e2e-document-1', async (route) => {
      if (route.request().method() !== 'GET') return route.fallback()
      getCount += 1
      const status = getCount < 3 ? 'PROCESSING' : 'COMPLETED'
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...TEST_DOCUMENT, status }),
      })
    })
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByRole('table').getByRole('link', { name: TEST_DOCUMENT.originalFilename }).click()
    await expect(page.getByRole('heading', { name: TEST_DOCUMENT.originalFilename })).toBeVisible()
    await expect(page.getByText('PROCESSING').first()).toBeVisible()
    await expect(page.getByText('COMPLETED').first()).toBeVisible({ timeout: 15000 })

    const countAfterCompletion = getCount
    await page.waitForTimeout(6000)
    expect(getCount).toBe(countAfterCompletion)
  })

  test('20. processing lifecycle: PROCESSING transitions to FAILED and polling stops', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [{ ...TEST_DOCUMENT, status: 'PROCESSING' }])
    let getCount = 0
    await page.route('**/api/v1/documents/e2e-document-1', async (route) => {
      if (route.request().method() !== 'GET') return route.fallback()
      getCount += 1
      const status = getCount < 2 ? 'PROCESSING' : 'FAILED'
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...TEST_DOCUMENT, status }),
      })
    })
    await loginViaUi(page)
    await goToDocuments(page)

    await page.getByRole('table').getByRole('link', { name: TEST_DOCUMENT.originalFilename }).click()
    await expect(page.getByText('FAILED').first()).toBeVisible({ timeout: 15000 })

    const countAfterFailure = getCount
    await page.waitForTimeout(6000)
    expect(getCount).toBe(countAfterFailure)
  })

  test('21. empty state offers an upload action when there are no documents at all', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await mockDocumentsResource(page, [])
    await loginViaUi(page)
    await goToDocuments(page)

    await expect(page.getByText('No documents yet')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Upload your first document' })).toBeVisible()
  })

  test('22. a failed list request shows a retryable error state', async ({ page }) => {
    await mockAuthenticatedSession(page)
    await page.route('**/api/v1/documents*', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 500,
          code: 'INTERNAL_ERROR',
          message: 'An unexpected error occurred',
          path: '/api/v1/documents',
        }),
      }),
    )
    await loginViaUi(page)
    await goToDocuments(page)

    await expect(page.getByText(/couldn't load your documents/i)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible()
  })
})
