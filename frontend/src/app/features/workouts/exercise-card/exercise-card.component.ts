import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { CircleCheck, History, LucideAngularModule, Plus, SquarePen, Trash2 } from 'lucide-angular';

import { ExerciseBoardEntry, WorkoutSet } from '../../../core/workouts/workouts.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfChipComponent } from '../../../shared/ui/df-chip/df-chip.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { equipmentLabel, equipmentTone } from '../equipment-tone';

/**
 * One exercise's log card (spec §8.3): PRs, logged sets, and the Log action. A card
 * with at least one set gets the "done" treatment — the parent is what actually moves
 * it to the bottom of the list, since ordering is a property of the whole board, not
 * of one card.
 */
@Component({
  selector: 'df-exercise-card',
  imports: [LucideAngularModule, DfButtonComponent, DfCardComponent, DfChipComponent, DfIconButtonComponent],
  templateUrl: './exercise-card.component.html',
  styleUrl: './exercise-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.exercise-card--done]': 'done()' },
})
export class ExerciseCardComponent {
  readonly entry = input.required<ExerciseBoardEntry>();

  readonly logRequested = output<void>();
  readonly editSetRequested = output<WorkoutSet>();
  readonly deleteSetRequested = output<WorkoutSet>();
  readonly historyRequested = output<void>();
  /** Only ever emitted for an exercise the viewer owns; the template guards it. */
  readonly editRequested = output<void>();

  protected readonly plusIcon = Plus;
  protected readonly editIcon = SquarePen;
  protected readonly deleteIcon = Trash2;
  protected readonly doneIcon = CircleCheck;
  protected readonly historyIcon = History;

  protected readonly equipmentTone = equipmentTone;
  protected readonly equipmentLabel = equipmentLabel;

  protected readonly done = computed(() => this.entry().sets.length > 0);

  protected prLabel(pr: { totalWeightKg: number; reps: number } | null): string {
    if (!pr) {
      return '—';
    }
    if (this.entry().equipment === 'BODYWEIGHT') {
      return pr.totalWeightKg > 0 ? `BW + ${pr.totalWeightKg} kg × ${pr.reps}` : `BW × ${pr.reps}`;
    }
    return `${pr.totalWeightKg} kg × ${pr.reps}`;
  }

  protected setLabel(set: WorkoutSet): string {
    if (this.entry().equipment === 'BODYWEIGHT') {
      const added = set.addedWeight ?? 0;
      return added > 0 ? `BW + ${added} kg × ${set.reps}` : `BW × ${set.reps}`;
    }
    return `${set.totalWeightKg} kg × ${set.reps}`;
  }
}
