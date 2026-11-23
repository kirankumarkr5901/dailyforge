import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { WorkoutsApi } from '../../../core/workouts/workouts.api';
import { ExerciseBoardEntry } from '../../../core/workouts/workouts.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { RestTimerService } from '../rest-timer/rest-timer.service';

/** What the sheet edits: one card, and the plan row it comes from. */
export interface TargetsContext {
  planId: string;
  entry: ExerciseBoardEntry;
}

/**
 * Target sets, reps, rest time and notes for one exercise in the plan, edited straight
 * from its card. The same fields the plan manager edits, without opening the whole plan.
 * Zero sets or reps means "no target". Rest is either "my default" or a time of its
 * own, because zero seconds is a real choice (a superset), not "unset".
 */
@Component({
  selector: 'df-exercise-targets-sheet',
  imports: [FormsModule, DfButtonComponent, DfInputComponent, DfSheetComponent, DfStepperInputComponent],
  templateUrl: './exercise-targets-sheet.component.html',
  styleUrl: './exercise-targets-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseTargetsSheetComponent {
  private readonly api = inject(WorkoutsApi);
  private readonly toasts = inject(ToastService);
  protected readonly restTimer = inject(RestTimerService);

  readonly context = input<TargetsContext | null>(null);
  readonly closed = output<void>();
  readonly saved = output<void>();

  protected readonly open = computed(() => this.context() !== null);
  protected readonly heading = computed(() => 'Targets — ' + (this.context()?.entry.name ?? ''));

  protected readonly sets = signal(0);
  protected readonly reps = signal(0);
  /** Top of the range; 0 = a single number. */
  protected readonly repsMax = signal(0);
  protected readonly ownRest = signal(false);
  protected readonly restSeconds = signal(90);
  protected readonly notes = signal('');
  protected readonly saving = signal(false);

  constructor() {
    effect(() => {
      const ctx = this.context();
      if (!ctx) return;
      untracked(() => {
        this.sets.set(ctx.entry.targetSets ?? 0);
        this.reps.set(ctx.entry.targetReps ?? 0);
        this.repsMax.set(ctx.entry.targetRepsMax ?? 0);
        this.ownRest.set(typeof ctx.entry.restSeconds === 'number');
        this.restSeconds.set(ctx.entry.restSeconds ?? this.restTimer.defaultSeconds());
        this.notes.set(ctx.entry.notes ?? '');
      });
    });
  }

  protected close(): void {
    this.closed.emit();
  }

  protected async save(): Promise<void> {
    const ctx = this.context();
    if (!ctx?.entry.planExerciseId || this.saving()) return;
    if (this.repsMax() && this.repsMax() < this.reps()) {
      this.toasts.show('The top of the rep range has to be at least the bottom.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(
        this.api.updatePlanExercise(ctx.planId, ctx.entry.planExerciseId, {
          targetSets: this.sets() || null,
          targetReps: this.reps() || null,
          targetRepsMax: this.reps() && this.repsMax() > this.reps() ? this.repsMax() : null,
          notes: this.notes().trim() || null,
          restSeconds: this.ownRest() ? this.restSeconds() : null,
        }),
      );
      this.saved.emit();
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', { tone: 'penalty' });
    } finally {
      this.saving.set(false);
    }
  }
}
