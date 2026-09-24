import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ApiError } from '../../../core/api/api.types';
import { GoalApi } from '../../../core/goal/goal.api';
import { Goal, GoalKind, GoalPeriodType } from '../../../core/goal/goal.types';
import { HabitsApi } from '../../../core/habits/habits.api';
import { LogicalDate } from '../../../core/time/logical-date';
import { WorkoutsApi } from '../../../core/workouts/workouts.api';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

const KIND_OPTIONS: readonly DfSelectOption[] = [
  { value: 'HABIT_ADHERENCE', label: 'Habit adherence' },
  { value: 'EXERCISE_TARGET', label: 'Exercise target' },
  { value: 'RUN_DISTANCE', label: 'Run distance' },
  { value: 'BODY_METRIC', label: 'Body metric' },
  { value: 'CUSTOM', label: 'Custom (manual)' },
];

const PERIOD_OPTIONS: readonly DfSelectOption[] = [
  { value: 'WEEK', label: 'This week' },
  { value: 'MONTH', label: 'This month' },
  { value: 'YEAR', label: 'This year' },
  { value: 'TARGET_DATE', label: 'By a target date' },
];

/** A new goal (spec §8.6) — the target fields shown depend on which kind is picked. */
@Component({
  selector: 'df-goal-form-sheet',
  imports: [FormsModule, DfButtonComponent, DfInputComponent, DfSelectComponent, DfSheetComponent, DfStepperInputComponent],
  templateUrl: './goal-form-sheet.component.html',
  styleUrl: './goal-form-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalFormSheetComponent {
  private readonly api = inject(GoalApi);
  private readonly habitsApi = inject(HabitsApi);
  private readonly workoutsApi = inject(WorkoutsApi);
  private readonly toasts = inject(ToastService);

  readonly open = input.required<boolean>();
  readonly date = input<LogicalDate | null>(null);

  readonly closed = output<void>();
  readonly saved = output<Goal>();

  protected readonly kindOptions = KIND_OPTIONS;
  protected readonly periodOptions = PERIOD_OPTIONS;

  protected readonly title = signal('');
  protected readonly kind = signal<GoalKind>('CUSTOM');
  protected readonly periodType = signal<GoalPeriodType>('WEEK');
  protected readonly targetDate = signal('');
  protected readonly rewardPoints = signal(100);
  protected readonly habitId = signal('');
  protected readonly exerciseId = signal('');
  protected readonly targetValue = signal(1);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly habitOptions = signal<DfSelectOption[]>([]);
  protected readonly exerciseOptions = signal<DfSelectOption[]>([]);
  protected readonly optionsLoading = signal(false);

  protected readonly targetValueLabel = computed(() => {
    switch (this.kind()) {
      case 'HABIT_ADHERENCE':
        return 'Streak length (days)';
      case 'EXERCISE_TARGET':
        return 'Target weight (kg)';
      case 'RUN_DISTANCE':
        return 'Target distance (km)';
      case 'BODY_METRIC':
        return 'Target weight (kg)';
      default:
        return 'Target value';
    }
  });

  constructor() {
    effect(() => {
      if (!this.open()) {
        return;
      }
      this.reset();
      void this.loadOptions();
    });
  }

  private async loadOptions(): Promise<void> {
    this.optionsLoading.set(true);
    try {
      const [habits, exercises] = await Promise.all([
        firstValueFrom(this.habitsApi.list()),
        firstValueFrom(this.workoutsApi.searchExercises()),
      ]);
      this.habitOptions.set(habits.map((h) => ({ value: h.id, label: h.name })));
      this.exerciseOptions.set(exercises.map((e) => ({ value: e.id, label: e.name })));
    } catch {
      // Previously silent: a failed fetch here left both dropdowns empty with no
      // explanation at all, reading as "the list just doesn't exist" rather than "this
      // did not load". A toast at least says which of those it actually is.
      this.toasts.show('Could not load your habits and exercises. Check your connection.', { tone: 'penalty' });
    } finally {
      this.optionsLoading.set(false);
    }
  }

  protected async save(): Promise<void> {
    if (this.saving() || !this.title().trim()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const goal = await firstValueFrom(
        this.api.create({
          title: this.title().trim(),
          kind: this.kind(),
          periodType: this.periodType(),
          startDate: this.date()!,
          targetDate: this.periodType() === 'TARGET_DATE' ? this.targetDate() : undefined,
          rewardPoints: this.rewardPoints(),
          habitId: this.kind() === 'HABIT_ADHERENCE' ? this.habitId() : undefined,
          exerciseId: this.kind() === 'EXERCISE_TARGET' ? this.exerciseId() : undefined,
          targetValue: this.kind() !== 'CUSTOM' ? this.targetValue() : undefined,
        }),
      );
      this.saved.emit(goal);
    } catch (error) {
      this.error.set(this.messageFor(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected close(): void {
    this.closed.emit();
  }

  private reset(): void {
    this.title.set('');
    this.kind.set('CUSTOM');
    this.periodType.set('WEEK');
    this.targetDate.set('');
    this.rewardPoints.set(100);
    this.habitId.set('');
    this.exerciseId.set('');
    this.targetValue.set(1);
    this.error.set(null);
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      return body?.message ?? 'That did not save. Check your connection and try again.';
    }
    return 'That did not save. Check your connection and try again.';
  }
}
