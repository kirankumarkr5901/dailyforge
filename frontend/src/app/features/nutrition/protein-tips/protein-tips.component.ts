import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ProteinApi } from '../../../core/nutrition/protein.api';
import { ProteinDayWrite, ProteinSuggestion } from '../../../core/nutrition/protein.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * "What should I eat to hit protein?" under the protein meter: asks the AI for two or
 * three options that fit what's left today, from the user's own foods first. Tapping
 * Add logs it straight away.
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
  /** An option was logged: the page applies the day and points. */
  readonly logged = output<ProteinDayWrite>();

  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly tips = signal<ProteinSuggestion[] | null>(null);

  protected async ask(): Promise<void> {
    if (this.loading()) return;
    this.loading.set(true);
    try {
      const { suggestions } = await firstValueFrom(this.api.suggestions(this.date()));
      this.tips.set(suggestions);
    } catch (e) {
      const message =
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
          ? e.error.message
          : null;
      this.toasts.show(message ?? "Couldn't get suggestions right now. Try again.", {
        tone: 'penalty',
      });
    } finally {
      this.loading.set(false);
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
}
