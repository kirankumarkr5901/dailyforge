import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs';

import { ToastService } from '../../shared/ui/df-toast/toast.service';

/**
 * How a deploy reaches a device that has already installed the app.
 *
 * A service worker exists to serve the app from cache, which is also how an installed
 * PWA ends up running a build from weeks ago: it keeps working, so nothing ever forces
 * it to notice. That is the same class of problem as stale data, one level up — an old
 * client talking to a new API is how "it works on my phone but not my laptop" starts.
 *
 * So: check on start and periodically, and when a new build is ready, say so and offer
 * the reload rather than pulling the page out from under someone mid-set. The one case
 * that reloads without asking is an unrecoverable cache, where the alternative is an
 * app that cannot load at all.
 */
@Injectable({ providedIn: 'root' })
export class AppUpdateService {
  private readonly updates = inject(SwUpdate);
  private readonly toasts = inject(ToastService);
  private readonly document = inject(DOCUMENT);

  /** Often enough that a fix lands the same day, rarely enough to be invisible. */
  private static readonly CHECK_INTERVAL_MS = 60 * 60_000;

  start(): void {
    if (!this.updates.isEnabled) {
      return; // development, or a browser with no service-worker support
    }

    this.updates.versionUpdates
      .pipe(filter((event): event is VersionReadyEvent => event.type === 'VERSION_READY'))
      .subscribe(() => {
        this.toasts.show('A new version of DailyForge is ready.', {
          actionLabel: 'Reload',
          action: () => void this.activate(),
          durationMs: 0, // stays until acted on
        });
      });

    // A cache the worker cannot repair leaves the app unable to start at all, so this
    // one reloads itself rather than offering a choice nobody can act on.
    this.updates.unrecoverable.subscribe(() => {
      this.document.defaultView?.location.reload();
    });

    void this.updates.checkForUpdate();
    this.document.defaultView?.setInterval(() => void this.updates.checkForUpdate(), AppUpdateService.CHECK_INTERVAL_MS);
  }

  private async activate(): Promise<void> {
    await this.updates.activateUpdate();
    this.document.defaultView?.location.reload();
  }
}
