import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, firstValueFrom, throwError } from 'rxjs';

import { ApiError } from '../api/api.types';
import { AuthApi } from '../auth/auth.api';
import { SessionStore } from '../auth/session.store';
import { ToastService } from '../../shared/ui/df-toast/toast.service';

/**
 * The server refused a structural change because "Enable edit" is off (403
 * EDITING_LOCKED). That can only reach here from a screen that still believed editing
 * was on — typically a tab opened before it was switched off on another device. Say so
 * plainly, then re-read the user so this tab's own screens hide the same controls.
 */
export const editingLockInterceptor: HttpInterceptorFn = (request, next) => {
  const toasts = inject(ToastService);
  const session = inject(SessionStore);
  const api = inject(AuthApi);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 403) {
        const body = error.error as ApiError | null;
        if (body?.code === 'EDITING_LOCKED') {
          toasts.show('Editing is off. Turn on "Enable edit" in Settings to change this.', { tone: 'penalty' });
          void firstValueFrom(api.me())
            .then((user) => session.patchUser(user))
            .catch(() => undefined);
        }
      }
      return throwError(() => error);
    }),
  );
};
