/**
 * Staging. Same shape as production; the gallery stays reachable here because staging is
 * where both themes and reduced motion get checked on a real device.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'https://dailyforge-api-staging.example/api/v1',
  /** Stamped at build time so a screenshot can say which build it came from. */
  appVersion: 'dev',
  showDevRoutes: true,
};
