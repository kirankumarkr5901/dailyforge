import { DOCUMENT, Injectable, effect, inject, signal } from '@angular/core';

/**
 * `SYSTEM` honours `prefers-reduced-motion`. The two explicit values exist so the
 * design gallery and Settings can force either path, which is how the reduced-motion
 * requirement gets checked every session instead of once at M8.
 */
export type MotionChoice = 'SYSTEM' | 'FULL' | 'REDUCED';

const STORAGE_KEY = 'dailyforge.motion';

@Injectable({ providedIn: 'root' })
export class MotionService {
  private readonly document = inject(DOCUMENT);

  readonly choice = signal<MotionChoice>(this.read());

  constructor() {
    effect(() => this.apply(this.choice()));
  }

  set(choice: MotionChoice): void {
    this.choice.set(choice);
    this.persist(choice);
  }

  /** True when animation should be suppressed right now. */
  reduced(): boolean {
    const choice = this.choice();
    if (choice !== 'SYSTEM') {
      return choice === 'REDUCED';
    }
    return this.document.defaultView?.matchMedia('(prefers-reduced-motion: reduce)').matches ?? false;
  }

  private apply(choice: MotionChoice): void {
    const root = this.document.documentElement;
    if (choice === 'SYSTEM') {
      root.removeAttribute('data-motion');
    } else {
      root.setAttribute('data-motion', choice.toLowerCase());
    }
  }

  private read(): MotionChoice {
    const stored = this.safeStorage()?.getItem(STORAGE_KEY);
    return stored === 'FULL' || stored === 'REDUCED' || stored === 'SYSTEM' ? stored : 'SYSTEM';
  }

  private persist(choice: MotionChoice): void {
    this.safeStorage()?.setItem(STORAGE_KEY, choice);
  }

  private safeStorage(): Storage | null {
    try {
      return this.document.defaultView?.localStorage ?? null;
    } catch {
      return null;
    }
  }
}
