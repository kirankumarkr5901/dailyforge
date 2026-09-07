import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SessionStore } from '../auth/session.store';
import { PointsApi } from './points.api';
import { PointsEnvelope, ScoreSnapshot } from './points.types';

/**
 * The one shared source of truth for "what is the user's score right now" (see the spec's §10
 * three shared stores). Every screen that shows a total — the header pill above all —
 * reads from here rather than holding its own copy, so a redemption on Rewards is
 * reflected in the header without a manual refresh, and vice versa.
 *
 * The frontend never calculates points (PRODUCT.md invariant 4): `applyEnvelope` only ever
 * copies the `newTotal` a mutating endpoint already computed and returned. The finer
 * today/this-week/this-month breakdown isn't part of that envelope, so it goes stale
 * until the next full `refresh()` — an acceptable gap since only the total is shown
 * outside the Home page itself.
 */
@Injectable({ providedIn: 'root' })
export class PointsStore {
  private readonly api = inject(PointsApi);
  private readonly session = inject(SessionStore);

  private readonly _snapshot = signal<ScoreSnapshot | null>(null);
  readonly snapshot = this._snapshot.asReadonly();
  readonly total = computed(() => this._snapshot()?.total ?? 0);

  constructor() {
    let lastAuthState: boolean | null = null;
    effect(() => {
      if (!this.session.isResolved()) {
        return;
      }
      const authenticated = this.session.isAuthenticated();
      if (authenticated === lastAuthState) {
        return;
      }
      lastAuthState = authenticated;
      if (authenticated) {
        void this.refresh();
      } else {
        this._snapshot.set(null);
      }
    });
  }

  async refresh(): Promise<void> {
    if (!this.session.isAuthenticated()) {
      return;
    }
    try {
      const snapshot = await firstValueFrom(this.api.snapshot());
      this._snapshot.set(snapshot);
    } catch {
      // The header pill just keeps showing the last known total; the next mutation's
      // envelope or a later refresh will correct it.
    }
  }

  /** Cheap update from a mutating endpoint's own embedded PointsEnvelope (spec §7). */
  applyEnvelope(envelope: PointsEnvelope): void {
    const current = this._snapshot();
    this._snapshot.set({
      total: envelope.newTotal,
      today: current?.today ?? 0,
      thisWeek: current?.thisWeek ?? 0,
      thisMonth: current?.thisMonth ?? 0,
      byCategory: current?.byCategory ?? ({} as ScoreSnapshot['byCategory']),
    });
  }
}
