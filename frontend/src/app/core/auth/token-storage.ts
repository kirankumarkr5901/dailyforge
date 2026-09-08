import { Injectable } from '@angular/core';

export interface StoredTokens {
  accessToken: string;
  refreshToken: string;
}

const ACCESS_KEY = 'dailyforge.access';
const REFRESH_KEY = 'dailyforge.refresh';

/**
 * Where the session lives between page loads.
 *
 * One interface with one web implementation today. At M9 the Capacitor build swaps in
 * `@capacitor/preferences` behind the same interface, which is why nothing outside this
 * file ever touches `localStorage` for tokens — the swap is then a provider change
 * rather than a search across the codebase.
 *
 * Every access is guarded: private browsing and "block site data" both throw on the
 * accessor itself, not just on write, and a thrown storage error must never be the
 * reason someone cannot sign in.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  read(): StoredTokens | null {
    const accessToken = this.get(ACCESS_KEY);
    const refreshToken = this.get(REFRESH_KEY);
    return accessToken && refreshToken ? { accessToken, refreshToken } : null;
  }

  write(tokens: StoredTokens): void {
    this.set(ACCESS_KEY, tokens.accessToken);
    this.set(REFRESH_KEY, tokens.refreshToken);
  }

  clear(): void {
    this.remove(ACCESS_KEY);
    this.remove(REFRESH_KEY);
  }

  private get(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private set(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      // A session that lasts only until the tab closes is worse than one that persists,
      // and much better than a sign-in that fails outright.
    }
  }

  private remove(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch {
      // Nothing to do; the token was never persisted.
    }
  }
}
