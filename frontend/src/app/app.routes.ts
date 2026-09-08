import { Routes } from '@angular/router';
import { environment } from '../environments/environment';

/**
 * Every route is lazy except Home (spec §10). Home does not exist until M6, so at M0 the
 * root redirects to the design gallery.
 *
 * The gallery is registered only when `environment.showDevRoutes` is true, which is
 * false in the production configuration — the route is absent from the production
 * bundle rather than merely hidden behind a guard.
 */
export const routes: Routes = [
  ...(environment.showDevRoutes
    ? [
        {
          path: 'dev/ui',
          loadComponent: () =>
            import('./dev/ui/dev-ui.component').then((m) => m.DevUiComponent),
          title: 'Design system — DailyForge',
        },
      ]
    : []),

  {
    path: '',
    pathMatch: 'full' as const,
    redirectTo: environment.showDevRoutes ? 'dev/ui' : 'home',
  },

  // Home lands at M6. Until then anything unmatched goes where there is something to see.
  {
    path: '**',
    redirectTo: environment.showDevRoutes ? 'dev/ui' : '',
  },
];
