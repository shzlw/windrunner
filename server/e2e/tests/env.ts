import fs from 'node:fs';
import path from 'node:path';

/**
 * Loads `server/e2e/.env.local` (git-ignored) into process.env without
 * overriding anything already set. Called once at module load from
 * playwright.config.ts and tests/helpers.ts.
 */
export function loadEnvLocal(dir: string = __dirname): void {
  const file = path.join(dir, '.env.local');
  if (!fs.existsSync(file)) return;

  for (const raw of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    if (raw.trim().startsWith('#')) continue;
    const match = raw.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (!match) continue;
    const [, key, value] = match;
    const cleaned = value.replace(/^['"]|['"]$/g, '');
    if (process.env[key] === undefined) {
      process.env[key] = cleaned;
    }
  }
}
