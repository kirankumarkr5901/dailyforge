/**
 * Bakes the API origin into the production bundle at build time.
 *
 * An Angular production build is static files: there is no runtime environment to read
 * a variable from, so the value has to be decided when the bundle is made. Doing it
 * here — from API_BASE_URL in the build environment — means pointing the app at a
 * different backend is a Netlify setting, not a commit, and the same source builds for
 * staging, production or a laptop on the LAN.
 *
 * No API_BASE_URL means "leave the committed default alone", which keeps a plain
 * `npm run build` working for anyone who just wants to check the bundle compiles.
 */
import { readFileSync, writeFileSync } from 'node:fs';

const target = new URL('../src/environments/environment.prod.ts', import.meta.url);
const apiBaseUrl = process.env.API_BASE_URL?.trim();

if (!apiBaseUrl) {
  console.log('[set-api-url] API_BASE_URL not set — keeping the committed default.');
  process.exit(0);
}

// A trailing slash here becomes a double slash in every request path.
const normalised = apiBaseUrl.replace(/\/+$/, '');

if (!/^https?:\/\//.test(normalised)) {
  console.error(`[set-api-url] API_BASE_URL must start with http:// or https:// (got "${apiBaseUrl}")`);
  process.exit(1);
}

const source = readFileSync(target, 'utf8');
let updated = source.replace(/apiBaseUrl: '[^']*'/, `apiBaseUrl: '${normalised}'`);

if (updated === source) {
  console.error('[set-api-url] Could not find apiBaseUrl to replace in environment.prod.ts');
  process.exit(1);
}

/**
 * Stamp which commit this bundle came from. Netlify sets COMMIT_REF; anywhere else can
 * pass APP_VERSION. Without it a bug report says "the site" and there is no way to know
 * whether the device is even running the build that has the fix.
 */
const version = (process.env.APP_VERSION || process.env.COMMIT_REF || '').trim().slice(0, 7);
if (version) {
  updated = updated.replace(/appVersion: '[^']*'/, `appVersion: '${version}'`);
  console.log(`[set-api-url] appVersion = ${version}`);
}

writeFileSync(target, updated);
console.log(`[set-api-url] apiBaseUrl = ${normalised}`);
