import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { SessionStore } from '../auth/session.store';
import { PointsStore } from '../points/points.store';

/**
 * The third of the three shared stores (spec §10), and the answer to the multi-device
 * question: *when* does a screen stop trusting what it is showing?
 *
 * The app is used from a desktop and a phone. A tab left open on the desktop for days
 * is showing days-old numbers, and the danger is not the stale display — it is the user
 * acting on it, editing a habit whose real version has moved on. The server refuses
 * that write (see StaleWrite on the backend), but a refusal the user has to recover
 * from by hand is a worse experience than never having shown them stale data.
 *
 * So every screen re-reads when any of these says its copy might be behind:
 *
 *  - the tab becomes visible again after being hidden for longer than a moment,
 *  - the device comes back online,
 *  - a session is restored or a user signs in,
 *  - the server just rejected a write as stale (the recovery path — re-read, then the
 *    user can decide again against the truth).
 *
 * A screen subscribes once and reloads; nothing here knows what any screen holds.
 */
@Injectable({ providedIn: 'root' })
export class SyncStore {
  private readonly document = inject(DOCUMENT);
  private readonly session = inject(SessionStore);
  private readonly points = inject(PointsStore);
  private readonly destroyRef = inject(DestroyRef);

  private readonly refresh$ = new Subject<SyncReason>();

  /** Screens subscribe to this and reload themselves. */
  readonly refreshes: Observable<SyncReason> = this.refresh$.asObservable();

  private readonly _lastSyncedAt = signal<number | null>(null);
  readonly lastSyncedAt = this._lastSyncedAt.asReadonly();

  private readonly _online = signal(true);
  readonly online = this._online.asReadonly();

  /** True while the tab has been away long enough that what it shows may be behind. */
  readonly stale = computed(() => {
    const last = this._lastSyncedAt();
    return last !== null && Date.now() - last > STALE_AFTER_MS;
  });

  /**
   * Long enough to ignore an alt-tab, short enough that coming back to a tab left open
   * overnight always re-reads. Below this a hidden tab is treated as still current.
   */
  private static readonly HIDDEN_GRACE_MS = 30_000;

  private hiddenSince: number | null = null;

  constructor() {
    const view = this.document.defaultView;
    if (!view) {
      return; // server-side render: nothing to listen to
    }

    this._online.set(view.navigator.onLine);

    const onVisibility = () => {
      if (this.document.visibilityState === 'hidden') {
        this.hiddenSince = Date.now();
        return;
      }
      const away = this.hiddenSince === null ? 0 : Date.now() - this.hiddenSince;
      this.hiddenSince = null;
      if (away > SyncStore.HIDDEN_GRACE_MS) {
        this.request('visible');
      }
    };
    const onOnline = () => {
      this._online.set(true);
      this.request('online');
    };
    const onOffline = () => this._online.set(false);

    this.document.addEventListener('visibilitychange', onVisibility);
    view.addEventListener('online', onOnline);
    view.addEventListener('offline', onOffline);

    this.destroyRef.onDestroy(() => {
      this.document.removeEventListener('visibilitychange', onVisibility);
      view.removeEventListener('online', onOnline);
      view.removeEventListener('offline', onOffline);
    });

    // Signing in is the moment a device is most likely to be holding an old picture —
    // it is exactly the "I opened the desktop after a few days" case.
    let wasAuthenticated: boolean | null = null;
    effect(() => {
      if (!this.session.isResolved()) {
        return;
      }
      const authenticated = this.session.isAuthenticated();
      if (authenticated === wasAuthenticated) {
        return;
      }
      wasAuthenticated = authenticated;
      if (authenticated) {
        this.request('signed-in');
      }
    });
  }

  /**
   * Ask every subscribed screen to re-read. Safe to call often: screens reload their
   * own data, and the score is refreshed here once rather than by each of them.
   */
  request(reason: SyncReason): void {
    if (!this.session.isAuthenticated()) {
      return;
    }
    this._lastSyncedAt.set(Date.now());
    void this.points.refresh();
    this.refresh$.next(reason);
  }
}

export type SyncReason = 'visible' | 'online' | 'signed-in' | 'conflict' | 'manual';

/** After this long without a sync, what a screen is showing is treated as possibly behind. */
const STALE_AFTER_MS = 5 * 60_000;
