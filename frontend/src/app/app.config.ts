import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { SessionStore } from './core/auth/session.store';
import { AppUpdateService } from './core/sync/app-update.service';
import { coldStartInterceptor } from './core/sync/cold-start.interceptor';
import { InstallService } from './core/sync/install.service';
import { staleWriteInterceptor } from './core/sync/stale-write.interceptor';
import { SyncStore } from './core/sync/sync.store';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      // Returning to a list should return to where you were in it.
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
    ),
    provideHttpClient(withInterceptors([authInterceptor, staleWriteInterceptor, coldStartInterceptor])),

    /**
     * Resolve the session before the first render.
     *
     * Without this the shell paints "Sign in" for a moment on every load for a
     * signed-in user, which reads as having been logged out. The promise is not awaited
     * for correctness — the store resolves `unknown` on its own — but starting it here
     * means the answer usually arrives within the first frame.
     */
    provideAppInitializer(() => inject(SessionStore).restore()),

    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),

    /**
     * Both are constructed here rather than by whichever screen happens to load first:
     * knowing when this device's copy is behind, and noticing that a new build has
     * shipped, are properties of the running app, not of any one page.
     */
    provideAppInitializer(() => {
      inject(SyncStore);
      inject(AppUpdateService).start();
      inject(InstallService).start();
    }),
  ],
};
