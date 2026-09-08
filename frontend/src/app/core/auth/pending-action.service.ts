import { Injectable, signal } from '@angular/core';

/**
 * The action a visitor was taking when the server asked them to sign in.
 *
 * Spec §4.1 is blunt about this: "Do not lose the user's input." Someone who fills in a
 * set and taps Log should not have to retype it because the app noticed mid-tap that
 * they were anonymous. The write is captured here, the login sheet opens, and the same
 * closure runs afterwards.
 *
 * Only one is held. A queue would let a stale action fire long after the user moved on,
 * which is worse than dropping it.
 */
@Injectable({ providedIn: 'root' })
export class PendingActionService {
  private readonly _pending = signal<PendingAction | null>(null);
  readonly pending = this._pending.asReadonly();

  capture(action: PendingAction): void {
    this._pending.set(action);
  }

  /** Runs and clears whatever was captured. Safe to call when nothing is pending. */
  async replay(): Promise<void> {
    const action = this._pending();
    this._pending.set(null);
    if (action) {
      await action.run();
    }
  }

  discard(): void {
    this._pending.set(null);
  }
}

export interface PendingAction {
  /** Shown in the sign-in sheet, e.g. "Log this set once you're signed in." */
  readonly description: string;
  readonly run: () => Promise<unknown> | unknown;
}
