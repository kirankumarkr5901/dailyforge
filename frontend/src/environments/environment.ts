/**
 * Development. The API base URL is never hardcoded to localhost in a committed build
 * configuration — see environment.staging.ts and environment.prod.ts, which read the
 * deployed host. Local dev goes through the proxy, so the base is relative.
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  /** Stamped at build time so a screenshot can say which build it came from. */
  appVersion: 'dev',
  showDevRoutes: true,
};
