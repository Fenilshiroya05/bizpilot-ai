import { defineConfig, devices } from '@playwright/test'

/**
 * Phase 22.5 — runs ONLY e2e/local-integration.spec.ts, the one spec file
 * that mocks nothing and requires the real stack (PostgreSQL + backend with
 * demo users seeded + frontend) already running — see README.md's "Local
 * Demo Data" section. Deliberately separate from playwright.config.ts (the
 * mocked-backend-only default `npm run test:e2e`), so a routine CI/dev run
 * never depends on an unavailable local database.
 *
 * Usage: npx playwright test --config=playwright.local-integration.config.ts
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/local-integration.spec.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [['html', { open: 'never', outputFolder: 'playwright-report-local-integration' }], ['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // No webServer here on purpose: the frontend must already be running,
  // pointed at your real backend via VITE_API_BASE_URL (see README.md).
})
