import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { formatLong } from '../../../core/time/logical-date';
import { Equipment, ExerciseHistoryEntry } from '../../../core/workouts/workouts.types';
import { WorkoutsApi } from '../../../core/workouts/workouts.api';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';

/**
 * Every past set for one exercise, newest first — the "history" the M4 README once
 * disclosed as not yet built. A plain list, not a chart: a charting library is still
 * not part of the stack, and a dated list already answers "what did I lift last time".
 */
@Component({
  selector: 'df-exercise-history-sheet',
  imports: [DfSheetComponent, DfSkeletonComponent],
  templateUrl: './exercise-history-sheet.component.html',
  styleUrl: './exercise-history-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseHistorySheetComponent {
  private readonly api = inject(WorkoutsApi);

  readonly open = input.required<boolean>();
  readonly exerciseId = input<string | null>(null);
  readonly exerciseName = input('');
  readonly equipment = input<Equipment>('BARBELL');

  readonly closed = output<void>();

  protected readonly formatLong = formatLong;
  protected readonly loading = signal(false);
  protected readonly entries = signal<ExerciseHistoryEntry[]>([]);

  constructor() {
    effect(() => {
      const id = this.exerciseId();
      if (!this.open() || !id) {
        return;
      }
      void this.load(id);
    });
  }

  private async load(exerciseId: string): Promise<void> {
    this.loading.set(true);
    try {
      this.entries.set(await firstValueFrom(this.api.exerciseHistory(exerciseId)));
    } finally {
      this.loading.set(false);
    }
  }

  protected setLabel(entry: ExerciseHistoryEntry): string {
    if (this.equipment() === 'BODYWEIGHT') {
      const added = entry.set.addedWeight ?? 0;
      return added > 0 ? `BW + ${added} kg × ${entry.set.reps}` : `BW × ${entry.set.reps}`;
    }
    return `${entry.set.totalWeightKg} kg × ${entry.set.reps}`;
  }

  protected close(): void {
    this.closed.emit();
  }
}
