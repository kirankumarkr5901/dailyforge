import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

/**
 * "Install DailyForge" as something the app offers, rather than something the browser
 * might mention.
 *
 * Chrome decides on its own whether to surface an install prompt, and where it puts it
 * is easy to miss — a menu item behind an overflow on a page you are scrolling. Since
 * installing to the home screen is how this app is meant to be used on a phone, it
 * gets asked for in the open, in Settings, at a moment the user is already looking at
 * app-level choices.
 *
 * The browser only lets the prompt be shown in response to a real gesture, and only
 * once, which is why the event is captured on start and replayed on the button press.
 */
@Injectable({ providedIn: 'root' })
export class InstallService {
  private readonly document = inject(DOCUMENT);

  private readonly prompt = signal<BeforeInstallPromptEvent | null>(null);
  private readonly _installed = signal(false);

  /** True once the browser has told us an install is actually possible. */
  readonly canInstall = computed(() => this.prompt() !== null && !this._installed());
  readonly installed = this._installed.asReadonly();

  /** True when already running as an installed app, where offering to install is noise. */
  readonly isStandalone = signal(false);

  start(): void {
    const view = this.document.defaultView;
    if (!view) {
      return;
    }

    this.isStandalone.set(
      view.matchMedia?.('(display-mode: standalone)').matches === true ||
        // iOS Safari's own flag, which predates the standard media query.
        (view.navigator as NavigatorWithStandalone).standalone === true,
    );

    view.addEventListener('beforeinstallprompt', (event: Event) => {
      // Without this Chrome shows its own mini-infobar and the app never gets a say.
      event.preventDefault();
      this.prompt.set(event as BeforeInstallPromptEvent);
    });

    view.addEventListener('appinstalled', () => {
      this._installed.set(true);
      this.prompt.set(null);
    });
  }

  /** Returns what the user chose, or null if there was no prompt to show. */
  async install(): Promise<'accepted' | 'dismissed' | null> {
    const prompt = this.prompt();
    if (!prompt) {
      return null;
    }
    await prompt.prompt();
    const { outcome } = await prompt.userChoice;
    // The event is single-use: whatever they chose, it cannot be shown again.
    this.prompt.set(null);
    return outcome;
  }
}

/** Not in lib.dom yet; this is the shape Chromium fires. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

interface NavigatorWithStandalone extends Navigator {
  standalone?: boolean;
}
