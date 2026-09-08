import { DOCUMENT, Injectable, effect, inject, signal } from '@angular/core';

/**
 * Three states, not a boolean. `SYSTEM` follows the OS and is the default, which is why
 * the attribute is removed rather than set to a value in that case: the stylesheet's
 * `prefers-color-scheme` branch is what should win.
 */
export type ThemeChoice = 'SYSTEM' | 'LIGHT' | 'DARK';

const STORAGE_KEY = 'dailyforge.theme';

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
    return stored === 'LIGHT' || stored === 'DARK' || stored === 'SYSTEM' ? stored : 'SYSTEM';
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
