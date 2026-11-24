import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Mirrors PushController.ConfigResponse. */
export interface PushConfig {
  /** False when the server has no VAPID keys: notifications are switched off there. */
  enabled: boolean;
  publicKey: string;
  /** "HH:mm:ss", or null when the daily reminder is off. */
  reminderTime: string | null;
  /** How many devices of this account are subscribed. */
  devices: number;
}

/**
 * Phone notifications through Web Push and Angular's service worker: subscribing this
 * device, the daily reminder time, and asking the server to push "Rest is over" so it
 * arrives even with the app in the background or closed.
 *
 * Only works in a production build (the service worker is off in development) and, on
 * iPhone, only once the app has been added to the Home Screen.
 */
@Injectable({ providedIn: 'root' })
export class PushService {
  private readonly http = inject(HttpClient);
  private readonly swPush = inject(SwPush, { optional: true });
  private readonly base = environment.apiBaseUrl;

  /** Whether this device currently has a push subscription. */
  readonly subscribedHere = signal(false);

  constructor() {
    this.swPush?.subscription.subscribe((sub) => this.subscribedHere.set(sub !== null));
  }

  /** False in development, in browsers without push, and on iPhone outside the installed app. */
  supported(): boolean {
    return !!this.swPush?.isEnabled && typeof window !== 'undefined' && 'Notification' in window;
  }

  config(): Promise<PushConfig> {
    return firstValueFrom(this.http.get<PushConfig>(`${this.base}/push/config`));
  }

  /** Asks the phone for permission and registers this device with the server. */
  async enableHere(publicKey: string): Promise<void> {
    if (!this.swPush) throw new Error('unsupported');
    const sub = await this.swPush.requestSubscription({ serverPublicKey: publicKey });
    const json = sub.toJSON();
    await firstValueFrom(
      this.http.post<void>(`${this.base}/push/subscriptions`, {
        endpoint: json.endpoint,
        keys: { p256dh: json.keys?.['p256dh'], auth: json.keys?.['auth'] },
      }),
    );
    this.subscribedHere.set(true);
  }

  async disableHere(): Promise<void> {
    const sub = this.swPush ? await firstValueFrom(this.swPush.subscription) : null;
    if (sub) {
      await firstValueFrom(this.http.delete<void>(`${this.base}/push/subscriptions`, { body: { endpoint: sub.endpoint } }));
      await this.swPush!.unsubscribe();
    }
    this.subscribedHere.set(false);
  }

  /** "HH:mm" or null to turn the reminder off. */
  setReminder(time: string | null): Promise<PushConfig> {
    return firstValueFrom(this.http.put<PushConfig>(`${this.base}/push/reminder`, { time }));
  }

  sendTest(): Promise<{ delivered: number }> {
    return firstValueFrom(this.http.post<{ delivered: number }>(`${this.base}/push/test`, {}));
  }

  /** Fire and forget: the in-app timer still works if this does not. */
  scheduleRest(seconds: number): void {
    if (!this.subscribedHere() || seconds < 1) return;
    this.http.post<void>(`${this.base}/push/rest`, { seconds: Math.min(900, Math.round(seconds)) }).subscribe({ error: () => {} });
  }

  cancelRest(): void {
    if (!this.subscribedHere()) return;
    this.http.delete<void>(`${this.base}/push/rest`).subscribe({ error: () => {} });
  }
}
