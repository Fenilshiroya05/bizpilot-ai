import { defineConfig, devices } from '@playwright/test'

/**
 * Lightweight browser smoke-test layer for Phase 20 (CLAUDE.md's own Phase
 * 26 owns the full E2E strategy — full Customer/Lead/Quotation/Invoice/
 * Document/AI flows, a role matrix, performance, and visual-regression
 * baselines are deliberately NOT here). This config exists to make Phase
 * 20's foundation independently verifiable in a real browser: does the app
 * actually render, navigate, and behave, not just "does React technically
 * mount" in jsdom (Vitest already covers that, see src/**\/*.test.tsx).
 *
 * Every test mocks the backend at the Playwright network layer
 * (`page.route`) with realistic, deterministic fixtures (see
 * e2e/mocks.ts) — no production/API code changes, and no real backend
 * dependency for a routine `npm run test:e2e` run.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
    env: {
      VITE_API_BASE_URL: 'http://localhost:8080',
    },
  },
})
