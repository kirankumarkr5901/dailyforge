import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { CurrentUser } from '../api/api.types';
import { AuthApi } from './auth.api';
import { SessionStore } from './session.store';
import { TokenStorage } from './token-storage';

const user: CurrentUser = {
  id: 'u1',
  email: 'kiran@example.com',
  displayName: 'Kiran',
  hasPassword: true,
  googleLinked: false,
  settings: {
    timeZone: 'Asia/Kolkata',
    unitSystem: 'METRIC',
    theme: 'SYSTEM',
    weekStart: 'MONDAY',
    commitmentBonus: 0,
    heightCm: null,
    reminderTime: null,
    onboardingCompletedAt: null,
    version: 0,
  },
};

class FakeStorage {
  private tokens: { accessToken: string; refreshToken: string } | null = null;
  read() {
    return this.tokens;
  }
  write(tokens: { accessToken: string; refreshToken: string }) {
    this.tokens = tokens;
  }
  clear() {
    this.tokens = null;
  }
}

describe('SessionStore', () => {
  let api: {
    me: ReturnType<typeof vi.fn>;
    login: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
    capabilities: ReturnType<typeof vi.fn>;
  };
  let storage: FakeStorage;
  let store: SessionStore;

  beforeEach(() => {
    api = {
      me: vi.fn(() => of(user)),
      login: vi.fn(() =>
        of({ accessToken: 'a', expiresInSeconds: 900, refreshToken: 'r', user }),
      ),
      logout: vi.fn(() => of(void 0)),
      capabilities: vi.fn(() => of({ passwordEnabled: true, googleEnabled: false })),
    };
    storage = new FakeStorage();

    TestBed.configureTestingModule({
      providers: [
        SessionStore,
        { provide: AuthApi, useValue: api },
        { provide: TokenStorage, useValue: storage },
      ],
    });
    store = TestBed.inject(SessionStore);
  });

  it('starts unknown rather than assuming the visitor is signed out', () => {
    // Painting "Sign in" before asking the server makes a signed-in user's app look
    // like it logged them out.
    expect(store.status()).toBe('unknown');
    expect(store.isResolved()).toBe(false);
  });

  it('restores to anonymous when there is no stored token, without calling the server', async () => {
    await store.restore();

    expect(store.status()).toBe('anonymous');
    expect(api.me).not.toHaveBeenCalled();
  });

  it('restores an existing session from storage', async () => {
    storage.write({ accessToken: 'a', refreshToken: 'r' });

    await store.restore();

    expect(store.status()).toBe('authenticated');
    expect(store.displayName()).toBe('Kiran');
    expect(store.timeZone()).toBe('Asia/Kolkata');
  });

  it('clears a token the server no longer accepts', async () => {
    storage.write({ accessToken: 'stale', refreshToken: 'stale' });
    api.me.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));

    await store.restore();

    expect(store.status()).toBe('anonymous');
    // Leaving a dead token behind means every later request fails in the same way.
    expect(storage.read()).toBeNull();
  });

  it('stores tokens on login and exposes the user', async () => {
    await store.login({ email: 'kiran@example.com', password: 'a-long-password' });

    expect(storage.read()).toEqual({ accessToken: 'a', refreshToken: 'r' });
    expect(store.isAuthenticated()).toBe(true);
  });

  it('signs out locally even when revoking on the server fails', async () => {
    await store.login({ email: 'kiran@example.com', password: 'a-long-password' });
    api.logout.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    await store.logout();

    // The user asked to sign out; a server error is not a reason to keep them in.
    expect(store.isAuthenticated()).toBe(false);
    expect(storage.read()).toBeNull();
  });

  it('falls back to the browser time zone before a user is known', () => {
    expect(store.timeZone()).toBe(SessionStore.browserTimeZone());
  });
});
