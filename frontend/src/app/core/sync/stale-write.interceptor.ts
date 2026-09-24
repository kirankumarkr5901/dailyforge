import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { ApiError } from '../api/api.types';
import { ToastService } from '../../shared/ui/df-toast/toast.service';
import { SyncStore } from './sync.store';

/**
 * The recovery half of the multi-device rule.
 *
 * The server refuses a write made against a copy of a row that another device has since
 * changed (409 STALE_WRITE). On its own that is just a failure the user has to make
 * sense of. Here it becomes an explanation and a fix: say plainly that the other device
 * got there first, and re-read everything on screen so what they are looking at is the
 * truth before they decide what to do about it.
 *
 * Deliberately not automatic retry. The write was refused precisely because the data
 * underneath it moved; replaying it would be the overwrite this whole mechanism exists
 * to prevent.
 */
export const staleWriteInterceptor: HttpInterceptorFn = (request, next) => {
  const toasts = inject(ToastService);
  const sync = inject(SyncStore);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 409) {
        const body = error.error as ApiError | null;
        if (body?.code === 'STALE_WRITE') {
          toasts.show('That was changed on another device. Showing you the latest.', { tone: 'penalty' });
          sync.request('conflict');
        }
      }
      return throwError(() => error);
    }),
  );
};
