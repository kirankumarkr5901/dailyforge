import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ArrowLeft, LucideAngularModule } from 'lucide-angular';

import { ForgeApi } from '../../../core/forge/forge.api';
import { ForgeMode, ForgeOverview, ForgeQuestion, ForgeQuestionnaire } from '../../../core/forge/forge.types';

/**
 * The questionnaire, one question per screen, then the mode choice. It only collects
 * answers — the server scores them and builds the plan, so nothing here decides which
 * areas a person needs (PRODUCT.md invariant 4, in spirit: the client renders, it
 * does not judge).
 */
@Component({
  selector: 'df-forge-quiz',
  imports: [LucideAngularModule],
  templateUrl: './forge-quiz.component.html',
  styleUrl: './forge-quiz.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgeQuizComponent {
  private readonly api = inject(ForgeApi);

  readonly started = output<ForgeOverview>();
  readonly cancelled = output<void>();

  protected readonly backIcon = ArrowLeft;

  protected readonly questionnaire = signal<ForgeQuestionnaire | null>(null);
  protected readonly loadError = signal(false);
  /** 0..questions-1 are questions; questions.length is the mode step. */
  protected readonly step = signal(0);
  protected readonly answers = signal<Record<string, string[]>>({});
  protected readonly mode = signal<ForgeMode | null>(null);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected readonly questionCount = computed(() => this.questionnaire()?.questions.length ?? 0);
  protected readonly onModeStep = computed(() => this.questionnaire() !== null && this.step() === this.questionCount());
  protected readonly current = computed<ForgeQuestion | null>(
    () => this.questionnaire()?.questions[this.step()] ?? null,
  );
  /** Counts the mode step too, so the bar only fills when everything is chosen. */
  protected readonly progressPercent = computed(() => {
    const total = this.questionCount() + 1;
    return Math.round((this.step() / total) * 100);
  });

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loadError.set(false);
    try {
      this.questionnaire.set(await firstValueFrom(this.api.questionnaire()));
    } catch {
      this.loadError.set(true);
    }
  }

  protected picked(question: ForgeQuestion): string[] {
    return this.answers()[question.code] ?? [];
  }

  protected isPicked(question: ForgeQuestion, code: string): boolean {
    return this.picked(question).includes(code);
  }

  protected choose(question: ForgeQuestion, code: string): void {
    if (question.maxPicks === 1) {
      this.answers.update((a) => ({ ...a, [question.code]: [code] }));
      // A single-choice answer moves straight on; there is nothing else to decide.
      this.step.update((s) => s + 1);
      return;
    }

    const current = this.picked(question);
    let next: string[];
    if (current.includes(code)) {
      next = current.filter((c) => c !== code);
    } else if (current.length < question.maxPicks) {
      next = [...current, code];
    } else {
      // At the limit: replace the oldest pick rather than refusing the tap.
      next = [...current.slice(1), code];
    }
    this.answers.update((a) => ({ ...a, [question.code]: next }));
  }

  protected canContinue(question: ForgeQuestion): boolean {
    return this.picked(question).length > 0;
  }

  protected next(): void {
    this.step.update((s) => s + 1);
  }

  protected back(): void {
    if (this.step() === 0) {
      this.cancelled.emit();
      return;
    }
    this.step.update((s) => s - 1);
  }

  protected async begin(): Promise<void> {
    const mode = this.mode();
    if (!mode || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.submitError.set(null);
    try {
      const overview = await firstValueFrom(this.api.start({ answers: this.answers(), mode }));
      this.started.emit(overview);
    } catch (error) {
      const message = (error as { error?: { message?: string } })?.error?.message;
      this.submitError.set(message ?? 'Could not build your plan. Check your connection and try again.');
    } finally {
      this.submitting.set(false);
    }
  }
}
