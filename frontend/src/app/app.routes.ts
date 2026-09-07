import { Routes } from '@angular/router';
import { environment } from '../environments/environment';

/**
 * Every route is lazy except the shell itself (spec §10).
 *
 * Screens that later milestones build are routed to a placeholder that says which
 * milestone owns them, rather than being absent. That keeps the navigation honest and
 * exercisable now: a nav item that goes nowhere cannot be tested, and one that is hidden
 * until its feature lands hides layout problems until the worst moment to find them.
 */
const notBuiltYet = (title: string, body: string) => ({
  loadComponent: () =>
    import('./features/placeholder/placeholder.component').then((m) => m.PlaceholderComponent),
  data: { title, body },
});

export const routes: Routes = [
  ...(environment.showDevRoutes
    ? [
        {
          path: 'dev/ui',
          loadComponent: () => import('./dev/ui/dev-ui.component').then((m) => m.DevUiComponent),
          title: 'Design system — DailyForge',
        },
        {
          path: 'dev/points',
          loadComponent: () =>
            import('./dev/points/dev-points.component').then((m) => m.DevPointsComponent),
          title: 'Points debug — DailyForge',
        },
      ]
    : []),

  {
    path: '',
    loadComponent: () =>
      import('./shared/layout/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'home' },

      {
        path: 'home',
        title: 'DailyForge',
        ...notBuiltYet(
          'Home lands at M6',
          'Your quote, score, heatmap and activity log live here once the points engine and the first trackers exist.',
        ),
      },
      {
        path: 'workout',
        title: 'Workout — DailyForge',
        loadComponent: () =>
          import('./features/workouts/workouts-page/workouts-page.component').then(
            (m) => m.WorkoutsPageComponent,
          ),
      },
      {
        path: 'habits',
        title: 'Habits — DailyForge',
        loadComponent: () =>
          import('./features/habits/habits-page/habits-page.component').then(
            (m) => m.HabitsPageComponent,
          ),
      },
      {
        path: 'run',
        title: 'Runs — DailyForge',
        ...notBuiltYet(
          'The run tracker lands at M5',
          'Distance points, milestone bonuses and records by bracket.',
        ),
      },
      {
        path: 'goals',
        title: 'Goals — DailyForge',
        ...notBuiltYet('Goals land at M7', 'Targets that track themselves from what you log.'),
      },
      {
        path: 'jobs',
        title: 'Jobs — DailyForge',
        ...notBuiltYet(
          'The job pipeline lands at M7',
          'Applications, stage transitions and follow-ups that surface on Home.',
        ),
      },
      {
        path: 'body',
        title: 'Body — DailyForge',
        ...notBuiltYet(
          'Body metrics land at M7',
          'Weight with a moving average, and a BMI scale that does not moralise.',
        ),
      },

      {
        path: 'settings',
        title: 'Settings — DailyForge',
        loadComponent: () =>
          import('./features/settings/settings.component').then((m) => m.SettingsComponent),
      },

      {
        path: '**',
        title: 'Not found — DailyForge',
        ...notBuiltYet(
          'That page does not exist',
          'Use the navigation to get back to something that does.',
        ),
      },
    ],
  },
];
