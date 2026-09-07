import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule, Search } from 'lucide-angular';

import { WorkoutsApi } from '../../../core/workouts/workouts.api';
import { Equipment, Exercise, ExerciseKind } from '../../../core/workouts/workouts.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfChipComponent } from '../../../shared/ui/df-chip/df-chip.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { equipmentLabel, equipmentTone } from '../equipment-tone';

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

  readonly open = input.required<boolean>();

  readonly closed = output<void>();
  readonly picked = output<Exercise>();

  protected readonly searchIcon = Search;
  protected readonly query = signal('');
  protected readonly results = signal<Exercise[]>([]);
  protected readonly loading = signal(false);
  protected readonly creating = signal(false);

  protected readonly newKind = signal<ExerciseKind>('STRENGTH');
  protected readonly newEquipment = signal<Equipment>('BARBELL');

  protected readonly equipmentTone = equipmentTone;
  protected readonly equipmentLabel = equipmentLabel;

  protected readonly kindOptions: readonly DfSelectOption[] = [
    { value: 'STRENGTH', label: 'Strength' },
    { value: 'CARDIO', label: 'Cardio' },
  ];

  protected readonly equipmentOptions: readonly DfSelectOption[] = [
    { value: 'BARBELL', label: 'Barbell' },
    { value: 'DUMBBELL', label: 'Dumbbell' },
    { value: 'MACHINE', label: 'Machine' },
    { value: 'BODYWEIGHT', label: 'Bodyweight' },
    { value: 'NONE', label: 'None' },
  ];

  protected async onQueryChange(value: string): Promise<void> {
    this.query.set(value);
    this.loading.set(true);
    try {
      this.results.set(await firstValueFrom(this.api.searchExercises(value)));
    } finally {
      this.loading.set(false);
    }
  }

  protected select(exercise: Exercise): void {
    this.picked.emit(exercise);
    this.reset();
  }

  protected async createNew(): Promise<void> {
    if (this.creating() || !this.query().trim()) {
      return;
    }
    this.creating.set(true);
    try {
      const exercise = await firstValueFrom(
        this.api.createExercise({ name: this.query().trim(), kind: this.newKind(), equipment: this.newEquipment() }),
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
  }
}
