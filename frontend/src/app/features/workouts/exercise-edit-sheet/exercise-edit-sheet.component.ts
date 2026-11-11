import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule, Star } from 'lucide-angular';

import { WorkoutsApi } from '../../../core/workouts/workouts.api';
import { Equipment, Exercise, ExerciseKind } from '../../../core/workouts/workouts.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { EQUIPMENT_OPTIONS, KIND_OPTIONS, MUSCLE_GROUPS } from '../muscle-groups';

/** What the workouts board knows about the exercise being edited. */
export interface ExerciseEditTarget {
  id: string;
  name: string;
  kind: ExerciseKind;
  equipment: Equipment;
  muscleGroups: string[];
  isElite: boolean;
  version: number;
}

/**
 * Correcting an exercise you created, after the fact.
 *
 * Creating one happens mid-search, in a hurry, with a half-typed name — so a lat
 * pulldown ends up filed under "back" when you meant "lats", or as a machine when it is
 * a cable. Until now the only remedy was to delete it, which takes every set ever
 * logged against it with it. The same fields the picker offers on creation are offered
 * here, and nothing else: an exercise's identity is its name, kind, equipment and
 * muscles.
 *
 * The version the board handed us travels back as If-Match, so a phone editing a copy
 * it loaded days ago is refused rather than allowed to undo a change made on a laptop
 * since (see StaleWrite on the backend).
 */
@Component({
  selector: 'df-exercise-edit-sheet',
  imports: [
    FormsModule,
    LucideAngularModule,
    DfButtonComponent,
    DfInputComponent,
    DfSelectComponent,
    DfSheetComponent,
  ],
  templateUrl: './exercise-edit-sheet.component.html',
  styleUrl: './exercise-edit-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseEditSheetComponent {
  private readonly api = inject(WorkoutsApi);

  readonly exercise = input<ExerciseEditTarget | null>(null);

  readonly closed = output<void>();
  readonly saved = output<Exercise>();

  protected readonly starIcon = Star;

  protected readonly name = signal('');
  protected readonly kind = signal<ExerciseKind>('STRENGTH');
  protected readonly equipment = signal<Equipment>('BARBELL');
  protected readonly muscleGroups = signal<readonly string[]>([]);
  protected readonly elite = signal(false);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly muscleGroupOptions = MUSCLE_GROUPS;
  protected readonly kindOptions: readonly DfSelectOption[] = KIND_OPTIONS;
  protected readonly equipmentOptions: readonly DfSelectOption[] = EQUIPMENT_OPTIONS;

  constructor() {
    // The form is filled from whichever exercise the board opened it for, and refilled
    // if a different one is opened without the component being destroyed in between.
    effect(() => {
      const target = this.exercise();
      if (!target) {
        return;
      }
      this.name.set(target.name);
      this.kind.set(target.kind);
      this.equipment.set(target.equipment);
      this.muscleGroups.set([...target.muscleGroups]);
      this.elite.set(target.isElite);
      this.error.set(null);
    });
  }

  protected toggleMuscleGroup(group: string): void {
    this.muscleGroups.update((groups) =>
      groups.includes(group) ? groups.filter((g) => g !== group) : [...groups, group],
    );
  }

  protected async save(): Promise<void> {
    const target = this.exercise();
    const name = this.name().trim();
    if (!target || this.saving() || !name) {
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    try {
      const updated = await firstValueFrom(
        this.api.updateExercise(
          target.id,
          {
            name,
            kind: this.kind(),
            equipment: this.equipment(),
            muscleGroups: [...this.muscleGroups()],
            isElite: this.elite(),
          },
          target.version,
        ),
      );
      this.saved.emit(updated);
    } catch {
      // A stale write is caught by its own interceptor, which explains the conflict and
      // reloads; anything else is reported here so the sheet stays open with the edits
      // still in it rather than closing over a change that did not happen.
      this.error.set('Could not save that change. Check your connection and try again.');
    } finally {
      this.saving.set(false);
    }
  }

  protected close(): void {
    this.closed.emit();
  }
}
