import { APIRequestContext, test as base, expect } from '@playwright/test';
import { loadEnvLocal } from './env';

loadEnvLocal(__dirname);

/**
 * Shared e2e fixtures. Two auth modes:
 *
 * 1. Pre-existing API key (E2E_API_KEY in .env.local or environment):
 *    skips login entirely. Provide E2E_USER_ID if a test needs the owning
 *    user's id.
 *
 * 2. Session login (E2E_LOGIN + E2E_PASSWORD): logs in, mints a scoped
 *    API key per test, and revokes it afterwards.
 */

export type E2EContext = {
  api: APIRequestContext;
  apiKey: string;
  /** Present only in session-login mode. */
  apiKeyId?: string;
  /** Present when known; required by tests that create owned resources. */
  userId?: string;
};

/** Headers for calling the external API with the key under test. */
export function bearer(ctx: E2EContext): Record<string, string> {
  return { Authorization: `Bearer ${ctx.apiKey}` };
}

export const test = base.extend<{ authenticated: E2EContext }>({
  authenticated: [
    async ({ request }, use) => {
      const envKey = process.env.E2E_API_KEY;
      if (envKey) {
        await use({
          api: request,
          apiKey: envKey,
          userId: process.env.E2E_USER_ID,
        });
        return;
      }

      const login = process.env.E2E_LOGIN;
      const password = process.env.E2E_PASSWORD;
      if (!login || !password) {
        throw new Error(
          'No credentials configured. Put E2E_API_KEY (or E2E_LOGIN + E2E_PASSWORD) ' +
            'in server/e2e/.env.local or the environment.',
        );
      }

      // 1. Session login (sets cookie on this request context).
      const loginResponse = await request.post('/api/v1/auth/login', {
        data: { login, password },
      });
      expect(loginResponse.status(), 'login succeeded').toBe(200);

      // 2. Resolve the account's user id (used as project owner below).
      const me = await request.get('/api/v1/auth/me');
      expect(me.status()).toBe(200);
      const meBody = await me.json();
      const userId = meBody.data.id;
      expect(userId).toBeTruthy();

      // 3. Mint a scoped API key.
      const keyResponse = await request.post('/internal-api/v1/me/api-keys', {
        data: {
          name: `e2e-${Date.now()}`,
          scopes: ['projects:read', 'projects:write'],
        },
      });
      expect(keyResponse.status(), 'api key created').toBe(200);
      const keyBody = await keyResponse.json();
      const apiKey = keyBody.data.rawKey;
      const apiKeyId = keyBody.data.id;
      expect(apiKey).toBeTruthy();

      await use({ api: request, apiKey, apiKeyId, userId });

      // 4. Cleanup: revoke the key. Created projects are left behind on
      //    purpose when a test fails so they can be inspected.
      if (apiKeyId) {
        await request.delete(`/internal-api/v1/me/api-keys/${apiKeyId}`);
      }
    },
    { scope: 'test' },
  ],
});

export { expect };
