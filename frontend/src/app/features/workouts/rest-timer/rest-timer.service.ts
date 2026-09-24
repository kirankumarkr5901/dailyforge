import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';

const STORAGE_KEY = 'dailyforge.rest-timer-default-seconds';
/** The running timer's deadline, so a reload or a resurrected tab picks it back up. */
const DEADLINE_KEY = 'dailyforge.rest-timer-ends-at';
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
 *
 * Built around a deadline, not a countdown (owner feedback: "I will log the workout and
 * open Instagram… I want the timer to run in the background and notify me").
 *
 * The first version subtracted one from a counter on every tick of a one-second
 * interval. Hidden tabs do not get one tick a second: Chrome aligns a background tab's
 * timers to once a second at best and, after a few minutes, once a minute — so the
 * counter fell behind the wall clock the moment the app left the screen, and a
 * ninety-second rest quietly became several minutes. Now the only fact stored is *when
 * the rest ends*, and every display is derived from the clock. A tick that arrives late
 * shows the right number; a tick that never arrives costs nothing.
 *
 * Three things fire the finish, because no single one is reliable on a phone:
 *  - one timeout scheduled for the deadline, which a throttled tab still honours;
 *  - the tab becoming visible again, which catches a deadline that passed while the
 *    page was frozen outright — late, but the alternative is never;
 *  - the display interval, for the case where the page simply stayed open.
 * Whichever arrives first wins, and the others find nothing left to do.
 */
@Injectable({ providedIn: 'root' })
export class RestTimerService {
  private readonly document = inject(DOCUMENT);

  private readonly _secondsLeft = signal<number | null>(null);
  private readonly _defaultSeconds = signal(this.readDefault());

  /** Epoch milliseconds at which the current rest ends, or null when none is running. */
  private endsAt: number | null = null;
  private display: ReturnType<typeof setInterval> | null = null;
  private deadline: ReturnType<typeof setTimeout> | null = null;

  readonly secondsLeft = this._secondsLeft.asReadonly();
  readonly defaultSeconds = this._defaultSeconds.asReadonly();
  readonly running = computed(() => this._secondsLeft() !== null);

  constructor() {
    const view = this.document.defaultView;
    if (!view) {
      return;
    }
    // Coming back is the moment a frozen page learns how much time really passed.
    view.document.addEventListener('visibilitychange', () => this.reconcile());
    view.addEventListener('focus', () => this.reconcile());
    view.addEventListener('pageshow', () => this.reconcile());
    this.restore();
  }

  start(seconds?: number): void {
    this.clearTimers();
    const duration = seconds ?? this._defaultSeconds();
    this.endsAt = Date.now() + duration * 1000;
    this.safeStorage()?.setItem(DEADLINE_KEY, String(this.endsAt));
    this.arm();
    this.tick();
  }

  /** Adds or removes time from the running rest by moving the deadline itself. */
  adjust(deltaSeconds: number): void {
    if (this.endsAt === null) {
      return;
    }
    this.endsAt = Math.max(Date.now(), this.endsAt + deltaSeconds * 1000);
    this.safeStorage()?.setItem(DEADLINE_KEY, String(this.endsAt));
    this.arm();
    this.tick();
  }

  dismiss(): void {
    this.clearTimers();
    this.endsAt = null;
    this.safeStorage()?.removeItem(DEADLINE_KEY);
    this._secondsLeft.set(null);
  }

  setDefault(seconds: number): void {
    const clamped = Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, seconds));
    this._defaultSeconds.set(clamped);
    this.safeStorage()?.setItem(STORAGE_KEY, String(clamped));
  }

  /**
   * Asks for notification permission, and reports what the user chose.
   *
   * Deliberately called from a button rather than on load: an unprompted permission
   * dialog on first visit is the fastest way to get permanently denied, and a denial
   * cannot be undone from script.
   */
  async requestNotificationPermission(): Promise<NotificationPermission | 'unsupported'> {
    const Ctor = this.notificationApi();
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
    const Ctor = this.notificationApi();
    return Ctor ? Ctor.permission : 'unsupported';
  }

  // ---------------------------------------------------------------------------

  /** Schedules the finish for the deadline and the display for every second until then. */
  private arm(): void {
    this.clearTimers();
    if (this.endsAt === null) {
      return;
    }
    const view = this.document.defaultView;
    if (!view) {
      return;
    }
    this.deadline = view.setTimeout(() => this.reconcile(), Math.max(0, this.endsAt - Date.now()));
    this.display = view.setInterval(() => this.tick(), 1000);
  }

  /** Reads the clock and shows what is left; finishes if nothing is. */
  private tick(): void {
    if (this.endsAt === null) {
      return;
    }
    const remaining = Math.ceil((this.endsAt - Date.now()) / 1000);
    if (remaining <= 0) {
      this.finish();
    } else {
      this._secondsLeft.set(remaining);
    }
  }

  /**
   * What happens when the page wakes up, or when the deadline timer fires: the same
   * thing, because both are just "look at the clock". A deadline that passed while the
   * page was frozen finishes now — late, but a late buzz beats a rest that never ends.
   */
  private reconcile(): void {
    if (this.endsAt === null) {
      return;
    }
    if (this.document.visibilityState === 'hidden' && this.deadline === null) {
      // Hidden and nothing armed (a resurrected page): arm so the deadline can fire.
      this.arm();
    }
    this.tick();
  }

  /** A reload mid-rest carries on rather than forgetting it was ever started. */
  private restore(): void {
    const stored = this.safeStorage()?.getItem(DEADLINE_KEY);
    const endsAt = stored ? Number.parseInt(stored, 10) : NaN;
    if (!Number.isFinite(endsAt)) {
      return;
    }
    if (endsAt <= Date.now()) {
      // It ended while the app was closed. The user is looking at the app now, which
      // is all a notification would have achieved.
      this.safeStorage()?.removeItem(DEADLINE_KEY);
      return;
    }
    this.endsAt = endsAt;
    this.arm();
    this.tick();
  }

  /**
   * Reaching zero has to be noticeable without the screen being watched — the phone is
   * usually face-down on a bench, or showing something else entirely, by then.
   *
   * The notification goes through the service worker, not the Notification
   * constructor. That is not a preference: Android Chrome refuses the constructor
   * outright ("Illegal constructor") and requires showNotification() on a registration.
   * The first version used the constructor inside a try/catch, so on every Android
   * phone it threw, the catch swallowed it, and no notification ever appeared — the
   * feature looked implemented and had never once worked where it mattered. The
   * constructor is kept only as the fallback for a page with no worker, which in this
   * app means a development build.
   *
   * The vibration pattern rides on the notification too, because a direct
   * navigator.vibrate() needs the page to be visible — and the whole point is that it
   * is not.
   */
  private finish(): void {
    this.dismiss();

    const view = this.document.defaultView;
    if (!view) {
      return;
    }

    const pattern = [300, 120, 300];
    try {
      view.navigator?.vibrate?.(pattern);
    } catch {
      // Unsupported (iOS Safari, most desktops). The notification below still stands.
    }

    const Ctor = this.notificationApi();
    if (!Ctor || Ctor.permission !== 'granted') {
      return;
    }

    const options: NotificationOptions & { vibrate?: number[]; data?: unknown } = {
      body: 'Time for your next set.',
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      tag: 'dailyforge-rest-timer', // replaces, never stacks
      requireInteraction: false,
      vibrate: pattern,
      // Angular's worker reads this to bring the app forward when the notification is
      // tapped; without it a tap on Android dismisses the toast and nothing else.
      data: { onActionClick: { default: { operation: 'focusLastFocusedOrOpen', url: '/workouts' } } },
    };

    void this.showViaWorker(view, 'Rest is over', options).then((shown) => {
      if (shown) {
        return;
      }
      try {
        new Ctor('Rest is over', options);
      } catch {
        // No worker and a browser that refuses the constructor: nothing more to try.
      }
    });
  }

  /** True when a worker registration took the notification; false means fall back. */
  private async showViaWorker(view: Window, title: string, options: NotificationOptions): Promise<boolean> {
    const container = view.navigator?.serviceWorker;
    if (!container) {
      return false;
    }
    try {
      // getRegistration() rather than ready: ready never resolves when no worker is
      // registered (development), and a promise that never settles would leave the
      // fallback unreachable.
      const registration = await container.getRegistration();
      if (!registration) {
        return false;
      }
      await registration.showNotification(title, options);
      return true;
    } catch {
      return false;
    }
  }

  private clearTimers(): void {
    const view = this.document.defaultView;
    if (this.display !== null) {
      view?.clearInterval(this.display);
      this.display = null;
    }
    if (this.deadline !== null) {
      view?.clearTimeout(this.deadline);
      this.deadline = null;
    }
  }

  private notificationApi(): typeof Notification | undefined {
    return (this.document.defaultView as WindowWithNotification | undefined)?.Notification;
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
