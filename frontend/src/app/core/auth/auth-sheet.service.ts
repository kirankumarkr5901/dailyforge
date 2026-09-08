import { Injectable, signal } from '@angular/core';

export type AuthSheetReason = 'manual' | 'write' | 'expired';
export type AuthSheetMode = 'login' | 'signup';

/**
 * Owns whether the sign-in sheet is showing, and why.
 *
 * The reason changes the copy: someone who tapped "Sign in" is not in the same situation
 * as someone whose session expired mid-action, and telling them the same thing wastes
 * the one sentence that could explain what just happened.
 *
 * It lives apart from the sheet component so the HTTP interceptor can open it without
 * importing UI — a service reaching into a component is how circular dependencies start.
 */
@Injectable({ providedIn: 'root' })
export class AuthSheetService {
  private readonly _open = signal(false);
  private readonly _reason = signal<AuthSheetReason>('manual');
  private readonly _mode = signal<AuthSheetMode>('login');

  readonly isOpen = this._open.asReadonly();
  readonly reason = this._reason.asReadonly();
  readonly mode = this._mode.asReadonly();

  open(reason: AuthSheetReason = 'manual', mode: AuthSheetMode = 'login'): void {
    this._reason.set(reason);
    this._mode.set(mode);
    this._open.set(true);
  }

  setMode(mode: AuthSheetMode): void {
    this._mode.set(mode);
  }

  close(): void {
    this._open.set(false);
  }
}
