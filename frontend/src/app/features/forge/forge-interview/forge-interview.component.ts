import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ArrowLeft, LucideAngularModule } from 'lucide-angular';

import { ForgeApi } from '../../../core/forge/forge.api';
import {
  ForgeAiQuestion,
  ForgeAiTurn,
  ForgeMode,
  ForgeOverview,
} from '../../../core/forge/forge.types';

/** Where an unfinished interview waits, so closing the app doesn't lose the answers. */
const SAVED_KEY = 'dailyforge.forge-interview';
/** Matches the server: the interview ends by then, and may end on request after the minimum. */
const MAX_TURNS = 30;
const MIN_TURNS = 12;

interface Saved {
  turns: ForgeAiTurn[];
  asked: ForgeAiQuestion[];
}

/**
 * The AI interview for a personal Forge 66 plan: one question at a time, each chosen
 * from the answers so far, with tap options and room to answer in your own words. Then
 * the mode, and the AI builds the plan. Answers are kept on the phone until it starts.
 */
@Component({
  selector: 'df-forge-interview',
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './forge-interview.component.html',
  styleUrls: ['../forge-quiz/forge-quiz.component.scss', './forge-interview.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgeInterviewComponent {
  private readonly api = inject(ForgeApi);

  readonly started = output<ForgeOverview>();
  readonly cancelled = output<void>();

  protected readonly backIcon = ArrowLeft;
  protected readonly minTurns = MIN_TURNS;

  protected readonly turns = signal<ForgeAiTurn[]>([]);
  /** Every question asked; the one at index turns.length is waiting for an answer. */
  protected readonly asked = signal<ForgeAiQuestion[]>([]);
  protected readonly picks = signal<string[]>([]);
  protected readonly text = signal('');
  protected readonly thinking = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly mode = signal<ForgeMode | null>(null);
  protected readonly submitting = signal(false);

  protected readonly current = computed(() => this.asked()[this.turns().length] ?? null);
  protected readonly onModeStep = computed(
    () => !this.thinking() && !this.error() && this.current()?.done === true,
  );
  protected readonly progressPercent = computed(() =>
    this.onModeStep() ? 100 : Math.min(95, Math.round((this.turns().length / MAX_TURNS) * 100)),
  );
  protected readonly canAnswer = computed(
    () => this.picks().length > 0 || this.text().trim().length > 0,
  );

  constructor() {
    const saved = readSaved();
    if (saved) {
      this.turns.set(saved.turns);
      this.asked.set(saved.asked);
    }
    if (!this.current()) {
      void this.ask();
    }
  }

  /** Asks the server for the next question (or "done") from the answers so far. */
  protected async ask(): Promise<void> {
    this.thinking.set(true);
    this.error.set(null);
    try {
      const next = await firstValueFrom(this.api.aiInterview(this.turns()));
      this.asked.update((a) => [...a.slice(0, this.turns().length), next]);
      this.save();
    } catch (e) {
      const message = (e as { error?: { message?: string } })?.error?.message;
      this.error.set(message ?? 'Could not reach the AI. Check your connection and try again.');
    } finally {
      this.thinking.set(false);
    }
  }

  protected toggle(option: string): void {
    const q = this.current();
    if (!q) return;
    if (!q.multi) {
      this.picks.set(this.picks()[0] === option ? [] : [option]);
      return;
    }
    this.picks.update((p) => (p.includes(option) ? p.filter((x) => x !== option) : [...p, option]));
  }

  protected async next(): Promise<void> {
    const q = this.current();
    if (!q || !this.canAnswer() || this.thinking()) return;
    const typed = this.text().trim();
    const answer = [this.picks().join('; '), typed].filter((x) => x).join(' — ');
    this.turns.update((t) => [...t, { question: q.question, answer: answer.slice(0, 500) }]);
    this.picks.set([]);
    this.text.set('');
    this.save();
    await this.ask();
  }

  /** Enough answers: skip to the mode and let the AI plan from what it has. */
  protected finishNow(): void {
    this.asked.update((a) => [
      ...a.slice(0, this.turns().length),
      { done: true, question: '', hint: '', options: [], multi: false },
    ]);
    this.save();
  }

  protected back(): void {
    if (this.thinking() || this.submitting()) return;
    const turns = this.turns();
    if (turns.length === 0) {
      this.cancelled.emit();
      return;
    }
    // Reopen the last answer for editing; the questions after it no longer apply.
    const last = turns[turns.length - 1];
    this.turns.set(turns.slice(0, -1));
    this.asked.update((a) => a.slice(0, turns.length));
    const q = this.asked()[turns.length - 1];
    const parts = last.answer.split(' — ');
    const options = q?.options ?? [];
    const picked = (parts[0] ?? '').split('; ').filter((p) => options.includes(p));
    this.picks.set(picked);
    this.text.set(picked.length ? parts.slice(1).join(' — ') : last.answer);
    this.error.set(null);
    this.save();
  }

  protected startOver(): void {
    clearSaved();
    this.turns.set([]);
    this.asked.set([]);
    this.picks.set([]);
    this.text.set('');
    void this.ask();
  }

  protected async begin(): Promise<void> {
    const mode = this.mode();
    if (!mode || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    try {
      const overview = await firstValueFrom(this.api.aiStart(this.turns(), mode));
      clearSaved();
      this.started.emit(overview);
    } catch (e) {
      const message = (e as { error?: { message?: string } })?.error?.message;
      this.error.set(message ?? 'Could not build your plan. Check your connection and try again.');
    } finally {
      this.submitting.set(false);
    }
  }

  private save(): void {
    try {
      localStorage.setItem(
        SAVED_KEY,
        JSON.stringify({ turns: this.turns(), asked: this.asked() } satisfies Saved),
      );
    } catch {
      // Storage blocked: the interview still works, it just won't survive a reload.
    }
  }
}

function readSaved(): Saved | null {
  try {
    const raw = localStorage.getItem(SAVED_KEY);
    const saved = raw ? (JSON.parse(raw) as Saved) : null;
    return saved && Array.isArray(saved.turns) && Array.isArray(saved.asked) ? saved : null;
  } catch {
    return null;
  }
}

function clearSaved(): void {
  try {
    localStorage.removeItem(SAVED_KEY);
  } catch {
    // Nothing to clear.
  }
}
