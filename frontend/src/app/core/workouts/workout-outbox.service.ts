import { HttpErrorResponse } from '@angular/common/http';
import { DOCUMENT, Injectable, inject, signal } from '@angular/core';
import { Subject, firstValueFrom } from 'rxjs';

import { ToastService } from '../../shared/ui/df-toast/toast.service';
import { LogicalDate } from '../time/logical-date';
import { WorkoutsApi } from './workouts.api';
import { LogSetPayload } from './workouts.types';

const STORAGE_KEY = 'dailyforge.workout-outbox';

/** A set logged with no signal, waiting on this phone to be sent. */
export interface PendingSet {
  kind: 'set';
  clientId: string;
  payload: LogSetPayload & { clientId: string };
  /** For showing it on its card: "60 kg × 8". */
  label: string;
}

/** "Complete" pressed with no signal. */
export interface PendingComplete {
  kind: 'complete';
  clientId: string;
  sessionId: string;
  exerciseId: string;
  date: LogicalDate;
}

export type PendingWrite = PendingSet | PendingComplete;

/** True when a request failed because there was no connection, not because the server said no. */
export function isOffline(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 0;
}

/**
 * Workout writes made with no signal (a basement gym): kept on this phone, in order,
 * and sent when the connection returns. Each set carries its own id, so a send that
 * reached the server even though its answer was lost is never saved twice when retried.
 *
 * Kept in localStorage, so closing the app does not lose them. Sending stops at the
 * first failure for lack of signal and resumes on the next 'online' event or retry tick;
 * a write the server refuses outright (a day that can no longer be changed) is dropped
 * with a message, since retrying it can never succeed.
 */
@Injectable({ providedIn: 'root' })
export class WorkoutOutboxService {
  private readonly api = inject(WorkoutsApi);
  private readonly toasts = inject(ToastService);
  private readonly document = inject(DOCUMENT);

  private readonly _pending = signal<PendingWrite[]>(this.read());
  readonly pending = this._pending.asReadonly();

  /** Fires after queued writes reach the server, so the board can reload. */
  readonly flushed = new Subject<void>();

  private flushing = false;

  constructor() {
    const view = this.document.defaultView;
    view?.addEventListener('online', () => void this.flush());
    view?.setInterval(() => {
      if (this._pending().length > 0) void this.flush();
    }, 30_000);
    if (this._pending().length > 0) void this.flush();
  }

  newId(): string {
    return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  enqueue(write: PendingWrite): void {
    this._pending.update((list) => [...list, write]);
    this.write();
  }

  /** Sends what is waiting, oldest first; stops at the first "no signal". */
  async flush(): Promise<void> {
    if (this.flushing || this._pending().length === 0) return;
    this.flushing = true;
    let sent = 0;
    try {
      while (this._pending().length > 0) {
        const next = this._pending()[0];
        try {
          if (next.kind === 'set') {
            await firstValueFrom(this.api.logSet(next.payload));
          } else {
            await firstValueFrom(this.api.completeExercise(next.sessionId, next.exerciseId));
          }
          sent++;
        } catch (error) {
          if (isOffline(error)) {
            break; // still no signal; try again later
          }
          this.toasts.show(
            next.kind === 'set'
              ? `A set saved offline (${next.label}) could not be sent and was dropped.`
              : 'An exercise marked complete offline could not be sent.',
            { tone: 'penalty' },
          );
        }
        this._pending.update((list) => list.slice(1));
        this.write();
      }
    } finally {
      this.flushing = false;
    }
    if (sent > 0) {
      this.toasts.show(sent === 1 ? 'Your offline set was sent.' : `${sent} offline writes were sent.`);
      this.flushed.next();
    }
  }

  private read(): PendingWrite[] {
    try {
      const raw = this.document.defaultView?.localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as PendingWrite[]) : [];
    } catch {
      return [];
    }
  }

  private write(): void {
    try {
      this.document.defaultView?.localStorage.setItem(STORAGE_KEY, JSON.stringify(this._pending()));
    } catch {
      // Storage full or blocked: the queue still lives in memory for this session.
    }
  }
}
