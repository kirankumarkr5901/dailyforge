import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, firstValueFrom, of, switchMap, tap } from 'rxjs';
import { LucideAngularModule, Search, Star } from 'lucide-angular';

import { WorkoutsApi } from '../../../core/workouts/workouts.api';
import { Equipment, Exercise, ExerciseKind } from '../../../core/workouts/workouts.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfChipComponent } from '../../../shared/ui/df-chip/df-chip.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { equipmentLabel, equipmentTone } from '../equipment-tone';
import { EQUIPMENT_OPTIONS, KIND_OPTIONS, MUSCLE_GROUPS } from '../muscle-groups';

/**
 * "A search field that suggests from the shared catalog as the user types, with
 * 'Create «bench press»' always available as the last option" (spec §8.2) — never
 * blocking a name that is not already in the catalog.
 */
@Component({
  selector: 'df-exercise-picker-sheet',
  imports: [
    FormsModule,
    LucideAngularModule,
    DfButtonComponent,
    DfChipComponent,
    DfInputComponent,
    DfSelectComponent,
    DfSheetComponent,
  ],
  templateUrl: './exercise-picker-sheet.component.html',
  styleUrl: './exercise-picker-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExercisePickerSheetComponent {
  private readonly api = inject(WorkoutsApi);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.startSearchStream();
  }

  readonly open = input.required<boolean>();

  readonly closed = output<void>();
  readonly picked = output<Exercise>();

  protected readonly searchIcon = Search;
  protected readonly starIcon = Star;
  protected readonly query = signal('');
  protected readonly results = signal<Exercise[]>([]);
  protected readonly loading = signal(false);
  protected readonly creating = signal(false);

  protected readonly newKind = signal<ExerciseKind>('STRENGTH');
  protected readonly newEquipment = signal<Equipment>('BARBELL');
  protected readonly newMuscleGroups = signal<readonly string[]>([]);
  protected readonly newElite = signal(false);

  protected readonly muscleGroupOptions = MUSCLE_GROUPS;

  protected readonly equipmentTone = equipmentTone;
  protected readonly equipmentLabel = equipmentLabel;

  protected readonly kindOptions: readonly DfSelectOption[] = KIND_OPTIONS;

  protected readonly equipmentOptions: readonly DfSelectOption[] = EQUIPMENT_OPTIONS;

  /**
   * Typed characters go to a stream, not straight to the network.
   *
   * This used to fire a request per keystroke: typing "Cable crunch" meant thirteen
   * searches, all in flight together. Two problems came out of that. The results raced
   * — a slow response for "Cable cru" could land after the one for "Cable crunch" and
   * overwrite it with staler results. And when the access token expired mid-word, all
   * thirteen 401'd at once, which is exactly the pile-up that used to end up presenting
   * an already-rotated refresh token.
   *
   * debounceTime waits for a pause, distinctUntilChanged ignores a keystroke that did
   * not change the text (arrow keys, re-typing the same letter), and switchMap cancels
   * whatever is still in flight — so at most one search is ever outstanding, and the
   * one that answers is always the one for what is on screen. The spec asked for a
   * debounced search from the start; this is it.
   */
  private readonly queryInput = new Subject<string>();

  private startSearchStream(): void {
    this.queryInput
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        tap(() => this.loading.set(true)),
        switchMap((value) => this.api.searchExercises(value).pipe(catchError(() => of([])))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((results) => {
        this.results.set(results);
        this.loading.set(false);
      });
  }

  protected onQueryChange(value: string): void {
    this.query.set(value);
    this.queryInput.next(value);
  }

  protected select(exercise: Exercise): void {
    this.picked.emit(exercise);
    this.reset();
  }

  protected toggleMuscleGroup(group: string): void {
    this.newMuscleGroups.update((groups) =>
      groups.includes(group) ? groups.filter((g) => g !== group) : [...groups, group],
    );
  }

  protected async createNew(): Promise<void> {
    if (this.creating() || !this.query().trim()) {
      return;
    }
    this.creating.set(true);
    try {
      const exercise = await firstValueFrom(
        this.api.createExercise({
          name: this.query().trim(),
          kind: this.newKind(),
          equipment: this.newEquipment(),
          muscleGroups: [...this.newMuscleGroups()],
          isElite: this.newElite(),
        }),
      );
      this.picked.emit(exercise);
      this.reset();
    } finally {
      this.creating.set(false);
    }
  }

  protected close(): void {
    this.reset();
    this.closed.emit();
  }

  private reset(): void {
    this.query.set('');
    this.results.set([]);
    this.newMuscleGroups.set([]);
    this.newElite.set(false);
  }
}
