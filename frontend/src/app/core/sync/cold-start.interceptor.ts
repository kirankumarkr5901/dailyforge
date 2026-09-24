import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';

import { ToastService } from '../../shared/ui/df-toast/toast.service';

/**
 * Explains the free tier's cold start instead of letting it look like a broken app.
 *
 * The API sleeps after a spell of no traffic and takes the better part of a minute to
 * wake. A skeleton screen that sits there for fifty seconds is indistinguishable from
 * a hang, and the honest thing to do is say what is happening — a spinner that never
 * explains itself is how a working app earns a reputation for being broken.
 *
 * Once per app load, and only for a request that is genuinely dragging: this is an
 * explanation for an unusual wait, not a running commentary on every request.
 */
export const coldStartInterceptor: HttpInterceptorFn = (request, next) => {
  const toasts = inject(ToastService);

  if (alreadyExplained) {
    return next(request);
  }

  let toastId: number | null = null;
  const timer = setTimeout(() => {
    alreadyExplained = true;
    toastId = toasts.show('Waking the server — it sleeps when idle, so this first one is slow.', {
      durationMs: 0, // cleared when the request lands, however long that takes
    });
  }, SLOW_AFTER_MS);

  return next(request).pipe(
    finalize(() => {
      clearTimeout(timer);
      if (toastId !== null) {
        toasts.dismiss(toastId);
      }
    }),
  );
};

/** Long enough that a warm API never trips it; short enough to beat the user's patience. */
const SLOW_AFTER_MS = 4_000;

let alreadyExplained = false;
