import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';

const STORAGE_KEY = 'dailyforge.rest-timer-default-seconds';
const DEFAULT_SECONDS = 90;
const MIN_SECONDS = 10;
const MAX_SECONDS = 600;

/**
 * The rest timer (spec §8.3 [ADD]): starts automatically after a set is logged,
 * dismissible, with a configurable default. Purely client-side — nothing here is a
 * point value or a fact the server needs to know, so it lives outside the points chain
 * entirely. The default is a lasting preference (Settings' own "Rest timer" control),
 * persisted the same way theme and motion are — a per-device localStorage value, not
 * server state, since it has no bearing on points or any other device's session.
 */
@Injectable({ providedIn: 'root' })
export class RestTimerService {
  private readonly document = inject(DOCUMENT);

  private readonly _secondsLeft = signal<number | null>(null);
  private readonly _defaultSeconds = signal(this.readDefault());
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly secondsLeft = this._secondsLeft.asReadonly();
  readonly defaultSeconds = this._defaultSeconds.asReadonly();
  readonly running = computed(() => this._secondsLeft() !== null);

  start(seconds?: number): void {
    this.clearTimer();
    this._secondsLeft.set(seconds ?? this._defaultSeconds());
    this.timer = setInterval(() => {
      const next = (this._secondsLeft() ?? 1) - 1;
      if (next <= 0) {
        this.dismiss();
      } else {
        this._secondsLeft.set(next);
      }
    }, 1000);
  }

  adjust(deltaSeconds: number): void {
    const current = this._secondsLeft();
    if (current === null) {
      return;
    }
    this._secondsLeft.set(Math.max(0, current + deltaSeconds));
  }

  setDefault(seconds: number): void {
    const clamped = Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, seconds));
    this._defaultSeconds.set(clamped);
    this.safeStorage()?.setItem(STORAGE_KEY, String(clamped));
  }

  dismiss(): void {
    this.clearTimer();
    this._secondsLeft.set(null);
  }

  private clearTimer(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private readDefault(): number {
    const stored = this.safeStorage()?.getItem(STORAGE_KEY);
    const parsed = stored ? Number.parseInt(stored, 10) : NaN;
    return Number.isFinite(parsed) && parsed >= MIN_SECONDS && parsed <= MAX_SECONDS ? parsed : DEFAULT_SECONDS;
  }

  /** Private browsing and blocked site data both throw on access, not on write. */
  private safeStorage(): Storage | null {
    try {
      return this.document.defaultView?.localStorage ?? null;
    } catch {
      return null;
    }
  }
}
