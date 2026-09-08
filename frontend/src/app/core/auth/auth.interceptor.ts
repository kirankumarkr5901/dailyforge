import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, filter, from, switchMap, take, throwError } from 'rxjs';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiError, ApiErrorCode } from '../api/api.types';
import { AuthApi, SKIP_AUTH_REFRESH } from './auth.api';
import { AuthSheetService } from './auth-sheet.service';
import { SessionStore } from './session.store';
import { TokenStorage } from './token-storage';

/**
 * One refresh at a time, shared by every request that hits a 401 while it runs.
 *
 * Module scope rather than a service field so the state is genuinely global: five
 * parallel requests expiring together must produce one refresh and four waiters, not
 * five refreshes that invalidate each other through token rotation.
 */
let refreshing = false;

/** What a finished refresh tells the requests that queued behind it — including failure. */
interface RefreshOutcome {
  token: string | null;
  error?: unknown;
}

const outcome = new BehaviorSubject<RefreshOutcome | null>(null);

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const storage = inject(TokenStorage);
  const api = inject(AuthApi);
  const sheet = inject(AuthSheetService);
  const session = inject(SessionStore);

  const isOurApi = request.url.startsWith(environment.apiBaseUrl);
  const skipRefresh = request.context.get(SKIP_AUTH_REFRESH);

  const authed = isOurApi ? withToken(request, storage.read()?.accessToken) : request;

  return next(authed).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || !isOurApi) {
        return throwError(() => error);
      }

      const code = errorCodeOf(error);

      // An expired access token is recoverable without involving the user at all.
      if (error.status === 401 && !skipRefresh && storage.read() && code !== 'AUTH_REQUIRED') {
        return retryAfterRefresh(request, next, storage, api, sheet, session);
      }

      // AUTH_REQUIRED means the visitor is genuinely anonymous and tried to write. This
      // is the moment the login sheet opens and the action is held for replay.
      if (error.status === 401 && code === 'AUTH_REQUIRED') {
        sheet.open('write');
      }

      return throwError(() => error);
    }),
  );
};

function retryAfterRefresh(
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
  storage: TokenStorage,
  api: AuthApi,
  sheet: AuthSheetService,
  session: SessionStore,
): Observable<HttpEvent<unknown>> {
  if (refreshing) {
    // Wait for the in-flight refresh, then go again with whatever it produced.
    //
    // `outcome` carries a failure as well as a token. Filtering for a non-null token
    // alone meant that when the lead refresh failed, it emitted nothing the waiters
    // would accept and they hung forever — no response, no error, just requests that
    // never settled and spinners that never stopped. A failed refresh has to reach the
    // waiters as a failure.
    return outcome.pipe(
      filter((state): state is RefreshOutcome => state !== null),
      take(1),
      switchMap((state) =>
        state.token
          ? next(withToken(request, state.token))
          : throwError(() => state.error ?? new Error('Session refresh failed')),
      ),
    );
  }

  const tokens = storage.read();
  if (!tokens) {
    return throwError(() => new Error('No session to refresh'));
  }

  refreshing = true;
  outcome.next(null);

  return from(
    (async () => {
      try {
        const response = await firstValue(api.refresh(tokens.refreshToken));
        storage.write({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
        });
        outcome.next({ token: response.accessToken });
        return response.accessToken;
      } catch (error) {
        // Rotation means a refresh token works exactly once (see RefreshTokenService):
        // presenting an already-used one looks identical to theft, and the backend
        // responds by revoking the whole session. `refreshing` only dedupes concurrent
        // refreshes within this one tab — a second tab (or a request already in flight
        // across a reload) can still present the same now-stale token here. Before
        // treating that as a genuine expiry, check whether storage has since moved on:
        // if another attempt already won and rotated it, this failure is stale, not
        // real, and the session is actually still fine.
        const current = storage.read();
        if (current && current.refreshToken !== tokens.refreshToken) {
          outcome.next({ token: current.accessToken });
          return current.accessToken;
        }
        // Through SessionStore, not storage directly: clearing only the tokens left the
        // store still holding the user, so the header kept showing their name and their
        // data while every request 401'd. "You are signed in" and "sign in again" on the
        // same screen is worse than either one alone.
        session.clear();
        sheet.open('expired');
        // Release the waiters with the failure rather than leaving them queued on a
        // refresh that is never coming.
        outcome.next({ token: null, error });
        throw error;
      } finally {
        refreshing = false;
      }
    })(),
  ).pipe(switchMap((token) => next(withToken(request, token))));
}

function withToken(request: HttpRequest<unknown>, token: string | undefined): HttpRequest<unknown> {
  return token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
}

function errorCodeOf(error: HttpErrorResponse): ApiErrorCode | null {
  const body = error.error as ApiError | null;
  return body && typeof body.code === 'string' ? body.code : null;
}

/** Local helper so this file does not depend on rxjs/firstValueFrom import ordering. */
function firstValue<T>(source: Observable<T>): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const subscription = source.subscribe({
      next: (value) => {
        resolve(value);
        subscription.unsubscribe();
      },
      error: reject,
    });
  });
}
