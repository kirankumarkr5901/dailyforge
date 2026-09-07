import { DOCUMENT, Injectable, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../auth/auth.api';
import { SessionStore } from '../auth/session.store';

const STORAGE_KEY = 'dailyforge.onboarding-dismissed';

/**
 * The first-launch guide (spec §8.11): shown once, automatically, then never again
 * unless replayed from Settings. Completion is stored on `user_settings` for a signed-in
 * visitor (spec's own words: "Store completion in user_settings.onboardingCompletedAt")
 * and locally for an anonymous one ("so it does not repeat") — an anonymous visitor who
 * later signs up starts a fresh, real account with nothing to migrate.
 */
@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(SessionStore);
  private readonly document = inject(DOCUMENT);

  private readonly _open = signal(false);
  readonly isOpen = this._open.asReadonly();

  constructor() {
    // Waits for the session to resolve (unknown -> anonymous or authenticated) before
    // deciding whether to show the tour — the same reason every other "wait for a real
    // session" effect in this app is written this way (see e.g. HabitsPageComponent).
    let checked = false;
    effect(() => {
      if (!this.session.isResolved() || checked) {
        return;
      }
      checked = true;
      if (!this.hasSeenTour()) {
        this._open.set(true);
      }
    });
  }

  private hasSeenTour(): boolean {
    if (this.session.isAuthenticated()) {
      return this.session.user()?.settings.onboardingCompletedAt != null;
    }
    return this.safeStorage()?.getItem(STORAGE_KEY) === 'true';
  }

  /** Reopens the tour regardless of whether it was already completed (Settings' "Replay tour"). */
  replay(): void {
    this._open.set(true);
  }

  async finish(): Promise<void> {
    this._open.set(false);
    if (this.session.isAuthenticated()) {
      try {
        const settings = await firstValueFrom(this.authApi.updateSettings({ onboardingCompleted: true }));
        const user = this.session.user();
        if (user) {
          this.session.patchUser({ ...user, settings });
        }
      } catch {
        // The tour still closes even if this fails to persist — it will simply be
        // offered again next time, which is a far smaller cost than blocking on it.
      }
    } else {
      this.safeStorage()?.setItem(STORAGE_KEY, 'true');
    }
  }

  skip(): void {
    void this.finish();
  }

  private safeStorage(): Storage | null {
    try {
      return this.document.defaultView?.localStorage ?? null;
    } catch {
      return null;
    }
  }
}
