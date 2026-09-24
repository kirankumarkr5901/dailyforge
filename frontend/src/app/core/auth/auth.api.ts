import { HttpClient, HttpContext, HttpContextToken } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ifMatch } from '../sync/if-match';
import {
  AuthCapabilities,
  AuthResponse,
  CurrentUser,
  LoginPayload,
  SignupPayload,
  UpdateSettingsPayload,
  UserSettings,
} from '../api/api.types';

/**
 * Marks a request the auth interceptor must leave alone.
 *
 * Without it, a failing refresh would trigger a refresh, which would fail, which would
 * trigger a refresh — the loop that turns one expired session into a hundred requests.
 */
export const SKIP_AUTH_REFRESH = new HttpContextToken<boolean>(() => false);

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  private readonly skipRefresh = new HttpContext().set(SKIP_AUTH_REFRESH, true);

  capabilities(): Observable<AuthCapabilities> {
    return this.http.get<AuthCapabilities>(`${this.base}/auth/capabilities`, {
      context: this.skipRefresh,
    });
  }

  signup(payload: SignupPayload): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/auth/signup`, payload, {
      context: this.skipRefresh,
    });
  }

  login(payload: LoginPayload): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/auth/login`, payload, {
      context: this.skipRefresh,
    });
  }

  loginWithGoogle(idToken: string, timeZone: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(
      `${this.base}/auth/google`,
      { idToken, timeZone },
      { context: this.skipRefresh },
    );
  }

  refresh(refreshToken: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(
      `${this.base}/auth/refresh`,
      { refreshToken },
      { context: this.skipRefresh },
    );
  }

  logout(refreshToken: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/auth/logout`,
      { refreshToken },
      { context: this.skipRefresh },
    );
  }

  me(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${this.base}/me`);
  }

  updateSettings(payload: UpdateSettingsPayload, version?: number): Observable<UserSettings> {
    return this.http.patch<UserSettings>(`${this.base}/me/settings`, payload, ifMatch(version));
  }

  /** The signed-in user's current local date (spec §4.2) — never computed client-side. */
  today(): Observable<{ date: string }> {
    return this.http.get<{ date: string }>(`${this.base}/me/today`);
  }
}
