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
        this.finish();
      } else {
        this._secondsLeft.set(next);
      }
    }, 1000);
  }

  /**
   * Reaching zero has to be noticeable without the screen being watched — the phone is
   * usually face-down on a bench by then.
   *
   * Both channels are used because each fails where the other works. Vibration needs
   * the page to be visible on most browsers, so it covers "phone in hand, screen on";
   * a notification is what survives the screen being off or the app backgrounded, but
   * it needs permission the user may never have granted. Neither is guaranteed, so
   * asking for both is the only way to make the common cases work.
   *
   * Nothing here throws: an unsupported API or a denied permission must not take the
   * timer down with it, and a rest timer that silently keeps working is fine.
   */
  private finish(): void {
    this.dismiss();

    const view = this.document.defaultView;
    try {
      // A double buzz — long enough to feel through a pocket, distinct from a
      // notification's own single tap.
      view?.navigator?.vibrate?.([300, 120, 300]);
    } catch {
      // Unsupported (iOS Safari, most desktops). The notification below still stands.
    }

    try {
      const Ctor = (view as WindowWithNotification | undefined)?.Notification;
      if (Ctor && Ctor.permission === 'granted') {
        new Ctor('Rest is over', {
          body: 'Time for your next set.',
          icon: '/icon-192.png',
          badge: '/icon-192.png',
          tag: 'dailyforge-rest-timer', // replaces, never stacks
          requireInteraction: false,
        });
      }
    } catch {
      // Some browsers throw when constructing a Notification outside a service worker.
    }
  }

  /**
   * Asks for notification permission, and reports what the user chose.
   *
   * Deliberately called from a button rather than on load: an unprompted permission
   * dialog on first visit is the fastest way to get permanently denied, and a denial
   * cannot be undone from script.
   */
  async requestNotificationPermission(): Promise<NotificationPermission | 'unsupported'> {
    const Ctor = (this.document.defaultView as WindowWithNotification | undefined)?.Notification;
    if (!Ctor) {
      return 'unsupported';
    }
    if (Ctor.permission !== 'default') {
      return Ctor.permission;
    }
    try {
      return await Ctor.requestPermission();
    } catch {
      return 'denied';
    }
  }

  notificationPermission(): NotificationPermission | 'unsupported' {
    const Ctor = (this.document.defaultView as WindowWithNotification | undefined)?.Notification;
    return Ctor ? Ctor.permission : 'unsupported';
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

/** Notification is absent in some browsers and in SSR, so it is never assumed present. */
interface WindowWithNotification extends Window {
  Notification?: typeof Notification;
}
