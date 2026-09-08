/**
 * Production. `apiBaseUrl` is replaced at build time by the deploy pipeline
 * (see docs/DEPLOYMENT.md, phase 1c) so the same source builds for any host.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'https://api.dailyforge.example/api/v1',
  showDevRoutes: false,
};
