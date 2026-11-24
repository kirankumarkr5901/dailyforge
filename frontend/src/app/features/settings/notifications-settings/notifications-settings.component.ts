import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { PushConfig, PushService } from '../../../core/push/push.service';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * Settings → Notifications: turn phone notifications on for this device, pick the time
 * of the daily reminder, and send a test. "Rest is over" pushes need nothing more than
 * notifications being on here.
 */
@Component({
  selector: 'df-notifications-settings',
  imports: [FormsModule, DfButtonComponent, DfCardComponent],
  templateUrl: './notifications-settings.component.html',
  styleUrl: './notifications-settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationsSettingsComponent {
  protected readonly push = inject(PushService);
  private readonly toasts = inject(ToastService);

  protected readonly config = signal<PushConfig | null>(null);
  protected readonly loadFailed = signal(false);
  protected readonly busy = signal(false);
  /** "HH:mm", or '' for off. */
  protected readonly timeDraft = signal('');
  protected readonly supported = this.push.supported();

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const config = await this.push.config();
      this.config.set(config);
      this.timeDraft.set(config.reminderTime ? config.reminderTime.slice(0, 5) : '');
    } catch {
      this.loadFailed.set(true);
    }
  }

  protected savedTime(): string {
    return this.config()?.reminderTime?.slice(0, 5) ?? '';
  }

  protected async enable(): Promise<void> {
    const config = this.config();
    if (!config || this.busy()) return;
    this.busy.set(true);
    try {
      await this.push.enableHere(config.publicKey);
      await this.load();
      this.toasts.show('Notifications are on for this device.');
    } catch {
      this.toasts.show(
        typeof Notification !== 'undefined' && Notification.permission === 'denied'
          ? 'Notifications are blocked for this app. Allow them in your phone settings, then try again.'
          : 'Could not turn notifications on. Try again.',
        { tone: 'penalty' },
      );
    } finally {
      this.busy.set(false);
    }
  }

  protected async disable(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      await this.push.disableHere();
      await this.load();
      this.toasts.show('Notifications are off for this device.');
    } catch {
      this.toasts.show('Could not turn notifications off. Try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }

  protected async saveTime(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      const time = this.timeDraft() || null;
      this.config.set(await this.push.setReminder(time));
      this.toasts.show(time ? `Daily reminder set for ${time}.` : 'Daily reminder off.');
    } catch {
      this.toasts.show('That did not save. Try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }

  protected async test(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      const { delivered } = await this.push.sendTest();
      this.toasts.show(delivered > 0 ? 'Test sent. It should arrive in a few seconds.' : 'No device received it. Turn notifications on here first.');
    } catch {
      this.toasts.show('Could not send a test. Try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }
}
