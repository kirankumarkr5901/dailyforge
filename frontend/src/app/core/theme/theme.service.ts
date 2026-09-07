import { DOCUMENT, Injectable, effect, inject, signal } from '@angular/core';

/**
 * Three states, not a boolean. `SYSTEM` follows the OS. The product default is `DARK`
 * regardless of OS preference — "cold steel, earned heat" reads as intended in the dark
 * palette, and a visitor who has never touched Settings should land there.
 */
export type ThemeChoice = 'SYSTEM' | 'LIGHT' | 'DARK';

const STORAGE_KEY = 'dailyforge.theme';
const DEFAULT_CHOICE: ThemeChoice = 'DARK';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  readonly choice = signal<ThemeChoice>(this.read());

  constructor() {
    effect(() => this.apply(this.choice()));
  }

  set(choice: ThemeChoice): void {
    this.choice.set(choice);
    this.persist(choice);
  }

  /** The theme actually rendering right now, once SYSTEM is resolved. */
  resolved(): 'LIGHT' | 'DARK' {
    const choice = this.choice();
    if (choice !== 'SYSTEM') {
      return choice;
    }
    const prefersDark = this.document.defaultView?.matchMedia('(prefers-color-scheme: dark)').matches;
    return prefersDark ? 'DARK' : 'LIGHT';
  }

  private apply(choice: ThemeChoice): void {
    const root = this.document.documentElement;
    if (choice === 'SYSTEM') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', choice.toLowerCase());
    }
  }

  private read(): ThemeChoice {
    const stored = this.safeStorage()?.getItem(STORAGE_KEY);
    return stored === 'LIGHT' || stored === 'DARK' || stored === 'SYSTEM' ? stored : DEFAULT_CHOICE;
  }

  private persist(choice: ThemeChoice): void {
    this.safeStorage()?.setItem(STORAGE_KEY, choice);
  }

  /** Private browsing and blocked site data both throw on access, not on write. */
  private safeStorage(): Storage | null {
    try {
      return this.document.defaultView?.localStorage ?? null;
    } catch {
      return null;
    }
  }
}
