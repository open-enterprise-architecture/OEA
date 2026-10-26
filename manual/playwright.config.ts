import { defineConfig } from '@playwright/test'

// Walkthrough tests of the manual (ADR-045 rule 5; change s2c-manual-release, design D1). They drive
// the running development stack once from a fresh instance to the catalog table and take the
// screenshots the manual references; started by scripts/walkthrough.sh. One run renders one
// language of the user interface (OEA_WALKTHROUGH_LANGUAGE, default en).

const language = process.env.OEA_WALKTHROUGH_LANGUAGE || 'en'

export default defineConfig({
  testDir: './walkthrough',
  // The flows build on each other (setup before login before catalogs)
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env.CI,
  timeout: 120_000,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.OEA_WALKTHROUGH_URL || 'http://localhost:5173',
    viewport: { width: 1280, height: 800 },
    colorScheme: 'light',
    reducedMotion: 'reduce',
    locale: language,
    timezoneId: 'UTC',
    trace: 'retain-on-failure',
  },
  projects: [{ name: language, use: { browserName: 'chromium' } }],
})
