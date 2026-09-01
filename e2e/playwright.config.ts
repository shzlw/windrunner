import { defineConfig } from '@playwright/test';
import { loadEnvLocal } from './tests/support/env';

loadEnvLocal(__dirname);

/**
 * API-level e2e configuration for Windrunner.
 *
 * Targets a running Windrunner server. No browsers required.
 *
 * Credentials come from `e2e/.env.local` (git-ignored) or the
 * environment. Two mutually exclusive auth modes:
 *
 *   E2E_API_KEY           – pre-existing API key (skips login entirely);
 *                           optionally E2E_USER_ID for tests that need an owner id
 *   E2E_LOGIN/E2E_PASSWORD – session login; a scoped key is minted per test
 *                           and revoked afterwards
 *
 *   WINDRUNNER_BASE_URL   – server under test (default http://localhost:8066)
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.WINDRUNNER_BASE_URL ?? 'http://localhost:8066',
    trace: 'off',
  },
  projects: [
    {
      name: 'api',
      testMatch: /api[\\/].*\.spec\.ts$/,
      use: {},
    },
    {
      name: 'cli',
      testMatch: /cli[\\/].*\.spec\.ts$/,
      use: {},
    },
  ],
});
