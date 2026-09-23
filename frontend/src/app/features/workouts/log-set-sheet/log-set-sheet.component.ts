import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ApiError } from '../../../core/api/api.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { WorkoutsApi } from '../../../core/workouts/workouts.api';
import { Equipment, SetWriteResponse, WeightMode, WorkoutSet } from '../../../core/workouts/workouts.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';

/**
 * Weight / reps / mode, prefilled from the most recent log of the exercise (spec §8.3).
 * Editing an existing set reuses the same form, PATCHing instead of POSTing.
 */
@Component({
  selector: 'df-log-set-sheet',
  imports: [FormsModule, DfButtonComponent, DfSheetComponent, DfStepperInputComponent],
  templateUrl: './log-set-sheet.component.html',
  styleUrl: './log-set-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogSetSheetComponent {
  private readonly api = inject(WorkoutsApi);

  readonly open = input.required<boolean>();
  readonly exerciseId = input<string | null>(null);
  readonly exerciseName = input('');
  readonly equipment = input<Equipment>('BARBELL');
  readonly date = input<LogicalDate | null>(null);
  readonly planId = input<string | null>(null);
  readonly dayIndex = input<number | null>(null);
  /** Set to edit in place, or null when logging a brand new set. */
  readonly editingSet = input<WorkoutSet | null>(null);
  /** Seeds sensible defaults from the last set logged for this exercise. */
  readonly lastSet = input<WorkoutSet | null>(null);

  readonly closed = output<void>();
  /** One entry per set actually written — always one when editing, possibly several
   * when logging fresh sets at once. */
  readonly saved = output<SetWriteResponse[]>();

  protected readonly weight = signal(0);
  protected readonly weightMode = signal<WeightMode>('COMBINED');
  protected readonly addedWeight = signal(0);
  protected readonly reps = signal(8);
  /** How many identical sets to log in one go — logging is one tap per exercise, not
   * one tap per set, the same way a gym-goer says "three sets of eight" once. Always 1
   * when editing an existing set, since that always edits exactly the one set. */
  protected readonly setCount = signal(1);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly isBodyweight = computed(() => this.equipment() === 'BODYWEIGHT');
  protected readonly isEditing = computed(() => this.editingSet() !== null);

  protected readonly computedTotal = computed(() => {
    if (this.isBodyweight()) {
      return this.addedWeight();
    }
    return this.weightMode() === 'SINGLE' ? this.weight() * 2 : this.weight();
  });

  constructor() {
    effect(() => {
      if (!this.open()) {
        return;
      }
      this.error.set(null);
      const editing = this.editingSet();
      const seed = editing ?? this.lastSet();
      if (seed) {
        this.weight.set(seed.enteredWeight);
        this.weightMode.set(seed.weightMode);
        this.addedWeight.set(seed.addedWeight ?? 0);
        this.reps.set(seed.reps);
      } else {
        this.weight.set(0);
        this.weightMode.set('COMBINED');
        this.addedWeight.set(0);
        this.reps.set(8);
      }
      this.setCount.set(1);
    });
  }

  protected async save(): Promise<void> {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const editing = this.editingSet();
      if (editing) {
        const response = await firstValueFrom(
          this.api.updateSet(editing.id, {
            enteredWeight: this.isBodyweight() ? undefined : this.weight(),
            weightMode: this.weightMode(),
            addedWeight: this.isBodyweight() ? this.addedWeight() : undefined,
            reps: this.reps(),
          }),
        );
        this.saved.emit([response]);
      } else {
        // Sequential, not parallel: each write reconciles PRs against the ones before
        // it in the same request (the backend has no bulk endpoint), so a second set
        // that beats the first as this exercise's new PR has to see the first already
        // landed. Whatever lands is emitted even if a later one in the batch fails —
        // sets already saved on the server must not silently vanish from the UI.
        const responses: SetWriteResponse[] = [];
        try {
          for (let i = 0; i < this.setCount(); i++) {
            responses.push(
              await firstValueFrom(
                this.api.logSet({
                  date: this.date()!,
                  exerciseId: this.exerciseId()!,
                  planId: this.planId(),
                  dayIndex: this.dayIndex(),
                  enteredWeight: this.isBodyweight() ? undefined : this.weight(),
                  weightMode: this.weightMode(),
                  addedWeight: this.isBodyweight() ? this.addedWeight() : undefined,
                  reps: this.reps(),
                }),
              ),
            );
          }
        } finally {
          if (responses.length > 0) {
            this.saved.emit(responses);
          }
        }
      }
    } catch (error) {
      this.error.set(this.messageFor(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected close(): void {
    this.closed.emit();
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      if (body?.code === 'ENTRY_LOCKED') {
        return 'This day can no longer be changed.';
      }
      if (body?.code === 'OUT_OF_RANGE') {
        return body.message || 'That looks out of range. Check the value.';
      }
      if (body?.code === 'DAILY_CAP_REACHED') {
        return "You've reached today's cap for workouts. It resets tomorrow.";
      }
      return body?.message ?? 'That did not save. Check your connection and try again.';
    }
    return 'That did not save. Check your connection and try again.';
  }
}
