import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against the full stack (`docker compose up` → http://localhost:8080),
 * i.e. nginx + Angular + Spring Boot + PostgreSQL with the demo data set.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    // to run against a local HTTPS proxy with a self-signed certificate
    ignoreHTTPSErrors: process.env.E2E_IGNORE_HTTPS_ERRORS === 'true',
    locale: 'fr-FR',
    timezoneId: 'Europe/Paris',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'e2e',
      testIgnore: /screenshots\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
    {
      // `npm run screenshots`: regenerates the README pictures (docs/screenshots)
      name: 'screenshots',
      testMatch: /screenshots\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
});
