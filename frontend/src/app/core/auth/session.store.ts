import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthCapabilities, CurrentUser, LoginPayload, SignupPayload } from '../api/api.types';
import { AuthApi } from './auth.api';
import { TokenStorage } from './token-storage';

export type SessionStatus = 'unknown' | 'anonymous' | 'authenticated';

/**
 * Who is using the app.
 *
 * `unknown` matters: on a cold load there is a token in storage but nobody has asked the
 * server whether it is still good. Rendering "Sign in" during that window makes a
 * signed-in user's app flicker as if they had been logged out, so the shell waits for
 * `restore()` to settle instead of assuming the pessimistic answer.
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly api = inject(AuthApi);
  private readonly storage = inject(TokenStorage);

  private readonly _status = signal<SessionStatus>('unknown');
  private readonly _user = signal<CurrentUser | null>(null);
  private readonly _capabilities = signal<AuthCapabilities>({
    passwordEnabled: true,
    googleEnabled: false,
  });

  readonly status = this._status.asReadonly();
  readonly user = this._user.asReadonly();
  readonly capabilities = this._capabilities.asReadonly();

  readonly isAuthenticated = computed(() => this._status() === 'authenticated');
  readonly isResolved = computed(() => this._status() !== 'unknown');
  readonly displayName = computed(() => this._user()?.displayName ?? null);

  /**
   * The user's time zone. Everything day-shaped in this app belongs to it, so it is read
   * from the browser only for a brand-new signup — after that the server's stored value
   * is the authority (spec §4.2).
   */
  readonly timeZone = computed(
    () => this._user()?.settings.timeZone ?? SessionStore.browserTimeZone(),
  );

  static browserTimeZone(): string {
    try {
      return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    } catch {
      return 'UTC';
    }
  }

  /** Called once at startup. Resolves `unknown` into one of the two real answers. */
  async restore(): Promise<void> {
    void this.loadCapabilities();

    if (!this.storage.read()) {
      this._status.set('anonymous');
      return;
    }

    try {
      const user = await firstValueFrom(this.api.me());
      this._user.set(user);
      this._status.set('authenticated');
    } catch {
      // The interceptor already tried to refresh. Reaching here means the session is
      // genuinely over, so clear it rather than leaving a token that cannot work.
      this.storage.clear();
      this._user.set(null);
      this._status.set('anonymous');
    }
  }

  async signup(payload: SignupPayload): Promise<void> {
    const response = await firstValueFrom(this.api.signup(payload));
    this.adopt(response.accessToken, response.refreshToken, response.user);
  }

  async login(payload: LoginPayload): Promise<void> {
    const response = await firstValueFrom(this.api.login(payload));
    this.adopt(response.accessToken, response.refreshToken, response.user);
  }

  async loginWithGoogle(idToken: string): Promise<void> {
    const response = await firstValueFrom(
      this.api.loginWithGoogle(idToken, SessionStore.browserTimeZone()),
    );
    this.adopt(response.accessToken, response.refreshToken, response.user);
  }

  async logout(): Promise<void> {
    const tokens = this.storage.read();
    if (tokens) {
      try {
        await firstValueFrom(this.api.logout(tokens.refreshToken));
      } catch {
        // The local session ends either way; a failed revocation is the server's problem
        // to expire, not a reason to keep the user signed in against their wishes.
      }
    }
    this.clear();
  }

  /** Applied after a settings change so the shell reflects it without a reload. */
  patchUser(user: CurrentUser): void {
    this._user.set(user);
  }

  clear(): void {
    this.storage.clear();
    this._user.set(null);
    this._status.set('anonymous');
  }

  private adopt(accessToken: string, refreshToken: string, user: CurrentUser): void {
    this.storage.write({ accessToken, refreshToken });
    this._user.set(user);
    this._status.set('authenticated');
  }

  private async loadCapabilities(): Promise<void> {
    try {
      this._capabilities.set(await firstValueFrom(this.api.capabilities()));
    } catch {
      // Offline or the API is down. Password sign-in is the safe assumption; a Google
      // button that cannot work is worse than one that is briefly missing.
    }
  }
}
