import { Injectable, signal } from '@angular/core';

export interface DfToast {
  readonly id: number;
  readonly message: string;
  /** Present when the toast offers a way back, e.g. "Undo". */
  readonly actionLabel?: string;
  readonly action?: () => void;
  readonly tone: 'neutral' | 'earned' | 'penalty';
  /** Only meaningful on an `earned` toast: its step on the heat ramp. */
  readonly heatStep?: 1 | 2 | 3 | 4;
  readonly durationMs: number;
}

let nextId = 0;

/**
 * Toasts.
 *
 * The undo window is six seconds (spec §8 shell), which is the whole reason this exists:
 * every points-earning action is reversible from the toast that announces it. The timer
 * is per toast rather than global so a second log does not cut the first one's window
 * short.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly items = signal<readonly DfToast[]>([]);
  readonly toasts = this.items.asReadonly();

  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  show(
    message: string,
    options: {
      actionLabel?: string;
      action?: () => void;
      tone?: DfToast['tone'];
      heatStep?: DfToast['heatStep'];
      durationMs?: number;
    } = {},
  ): number {
    const toast: DfToast = {
      id: nextId++,
      message,
      actionLabel: options.actionLabel,
      action: options.action,
      tone: options.tone ?? 'neutral',
      heatStep: options.heatStep,
      durationMs: options.durationMs ?? 6000,
    };

    // Three at a time; the oldest leaves rather than the stack growing off screen.
    this.items.update((current) => [...current, toast].slice(-3));
    this.arm(toast);
    return toast.id;
  }

  dismiss(id: number): void {
    this.clearTimer(id);
    this.items.update((current) => current.filter((toast) => toast.id !== id));
  }

  /** Runs the toast's action and dismisses it, e.g. when Undo is pressed. */
  invoke(id: number): void {
    const toast = this.items().find((item) => item.id === id);
    toast?.action?.();
    this.dismiss(id);
  }

  /** Holds the window open while a pointer or focus is on the toast. */
  pause(id: number): void {
    this.clearTimer(id);
  }

  resume(id: number): void {
    const toast = this.items().find((item) => item.id === id);
    if (toast) {
      this.arm(toast);
    }
  }

  private arm(toast: DfToast): void {
    this.clearTimer(toast.id);
    this.timers.set(
      toast.id,
      setTimeout(() => this.dismiss(toast.id), toast.durationMs),
    );
  }

  private clearTimer(id: number): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
  }
}
