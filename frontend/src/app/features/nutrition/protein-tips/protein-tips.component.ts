import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ProteinApi } from '../../../core/nutrition/protein.api';
import {
  MealIdea,
  ProteinDayWrite,
  ProteinSuggestion,
} from '../../../core/nutrition/protein.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * Under the protein meter, two AI helpers: "What to eat for protein?" (two or three
 * single options, the user's own foods first; Add logs one) and "Fill my day" (one meal
 * that closes today's protein, fiber and calorie gaps together; Add all logs it).
 */
@Component({
  selector: 'df-protein-tips',
  imports: [DfButtonComponent],
  templateUrl: './protein-tips.component.html',
  styleUrl: './protein-tips.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProteinTipsComponent {
  private readonly api = inject(ProteinApi);
  private readonly toasts = inject(ToastService);

  readonly date = input.required<LogicalDate>();
  /** Protein target hit: only "Fill my day" is offered then. */
  readonly reached = input(false);
  /** Something was logged: the page applies the day and points. */
  readonly logged = output<ProteinDayWrite>();

  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly asking = signal<'tips' | 'meal' | null>(null);
  protected readonly tips = signal<ProteinSuggestion[] | null>(null);
  protected readonly meal = signal<MealIdea | null>(null);
  /** What was already shown, sent with the next ask so it brings something new. */
  private shownTips: string[] = [];
  private shownMeals: string[] = [];

  protected readonly mealTotals = computed(() => {
    const t = (this.meal()?.items ?? []).reduce(
      (a, i) => ({
        proteinG: a.proteinG + i.proteinG,
        kcal: a.kcal + i.kcal,
        fiberG: a.fiberG + i.fiberG,
      }),
      { proteinG: 0, kcal: 0, fiberG: 0 },
    );
    return {
      proteinG: Math.round(t.proteinG * 10) / 10,
      kcal: Math.round(t.kcal),
      fiberG: Math.round(t.fiberG * 10) / 10,
    };
  });
  protected readonly hasEstimates = computed(() =>
    (this.meal()?.items ?? []).some((i) => !i.foodId),
  );

  protected async ask(): Promise<void> {
    if (this.loading()) return;
    this.loading.set(true);
    this.asking.set('tips');
    try {
      const { suggestions } = await firstValueFrom(
        this.api.suggestions(this.date(), this.shownTips),
      );
      this.shownTips = [...this.shownTips, ...suggestions.map((s) => s.name)].slice(-20);
      this.tips.set(suggestions);
      this.meal.set(null);
    } catch (e) {
      this.toasts.show(errorMessage(e) ?? "Couldn't get suggestions right now. Try again.", {
        tone: 'penalty',
      });
    } finally {
      this.loading.set(false);
      this.asking.set(null);
    }
  }

  /** "Fill my day": one meal that closes today's gaps together. */
  protected async fillDay(): Promise<void> {
    if (this.loading()) return;
    this.loading.set(true);
    this.asking.set('meal');
    try {
      const meal = await firstValueFrom(this.api.fillDay(this.date(), this.shownMeals));
      this.shownMeals = [...this.shownMeals, meal.title, ...meal.items.map((i) => i.name)].slice(
        -20,
      );
      this.meal.set(meal);
      this.tips.set(null);
    } catch (e) {
      this.toasts.show(errorMessage(e) ?? "Couldn't get a meal idea right now. Try again.", {
        tone: 'penalty',
      });
    } finally {
      this.loading.set(false);
      this.asking.set(null);
    }
  }

  protected async add(tip: ProteinSuggestion): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      const result = await firstValueFrom(
        this.api.log(this.date(), tip.name, {
          grams: tip.proteinG,
          kcal: tip.kcal,
          fatG: tip.fatG,
          carbsG: tip.carbsG,
          fiberG: tip.fiberG,
          sugarG: tip.sugarG,
        }),
      );
      this.tips.update((list) => list?.filter((t) => t !== tip) ?? null);
      this.logged.emit(result);
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', {
        tone: 'penalty',
      });
    } finally {
      this.busy.set(false);
    }
  }

  protected async addMeal(): Promise<void> {
    const m = this.meal();
    if (!m || this.busy()) return;
    this.busy.set(true);
    try {
      const result = await firstValueFrom(
        this.api.logMany(
          this.date(),
          m.items.map((i) => ({
            foodName: i.name,
            grams: i.proteinG,
            kcal: i.kcal,
            fatG: i.fatG,
            carbsG: i.carbsG,
            fiberG: i.fiberG,
            sugarG: i.sugarG,
          })),
        ),
      );
      this.meal.set(null);
      this.logged.emit(result);
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', {
        tone: 'penalty',
      });
    } finally {
      this.busy.set(false);
    }
  }
}

function errorMessage(e: unknown): string | null {
  return e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
    ? e.error.message
    : null;
}
