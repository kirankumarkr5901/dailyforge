import { Injectable, computed, signal } from '@angular/core';

/**
 * The rest timer (spec §8.3 [ADD]): starts automatically after a set is logged,
 * dismissible, with a configurable default. Purely client-side — nothing here is a
 * point value or a fact the server needs to know, so it lives outside the points chain
 * entirely.
 */
@Injectable({ providedIn: 'root' })
export class RestTimerService {
  private readonly _secondsLeft = signal<number | null>(null);
  private readonly _defaultSeconds = signal(90);
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
    this._defaultSeconds.set(Math.max(10, seconds));
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
}
