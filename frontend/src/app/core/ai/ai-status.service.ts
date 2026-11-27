import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Whether the AI features (meal reading, coaches, AI Forge plans) are set up on the
 * server. Asked once per visit; screens hide their AI parts while it's false.
 */
@Injectable({ providedIn: 'root' })
export class AiStatusService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;
  /** The one request in flight or done; every caller waits on the same one. */
  private pending: Promise<boolean> | null = null;

  readonly enabled = signal(false);

  /** Safe to call often and from several screens at once: only one call asks the server. */
  load(): Promise<boolean> {
    this.pending ??= firstValueFrom(
      this.http.get<{ enabled: boolean }>(`${this.base}/ai/status`),
    ).then(
      (status) => {
        this.enabled.set(status.enabled);
        return status.enabled;
      },
      () => {
        this.pending = null; // Try again next time.
        return false;
      },
    );
    return this.pending;
  }
}
