import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { CircleCheck, History, LucideAngularModule, Plus, RotateCcw, SquarePen, Trash2 } from 'lucide-angular';

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
  host: { '[class]': '"exercise-card--" + state()' },
})
export class ExerciseCardComponent {
  readonly entry = input.required<ExerciseBoardEntry>();

  readonly logRequested = output<void>();
  readonly editSetRequested = output<WorkoutSet>();
  readonly deleteSetRequested = output<WorkoutSet>();
  readonly historyRequested = output<void>();
  /** Only ever emitted for an exercise the viewer owns; the template guards it. */
  readonly editRequested = output<void>();
  /** "I am finished with this one." */
  readonly completeRequested = output<void>();
  /** Taking that back. */
  readonly reopenRequested = output<void>();

  protected readonly plusIcon = Plus;
  protected readonly editIcon = SquarePen;
  protected readonly deleteIcon = Trash2;
  protected readonly doneIcon = CircleCheck;
  protected readonly historyIcon = History;
  protected readonly reopenIcon = RotateCcw;

  protected readonly equipmentTone = equipmentTone;
  protected readonly equipmentLabel = equipmentLabel;

  /**
   * Three states, and the middle one is the point.
   *
   * "Done" used to mean "has at least one set", which conflated an exercise you are
   * halfway through with one you have finished — and sank the card you were actively
   * working on to the bottom the moment you logged your first set.
   */
  protected readonly state = computed<'todo' | 'in-progress' | 'done'>(() => {
    const entry = this.entry();
    if (entry.completedAt) {
      return 'done';
    }
    return entry.sets.length > 0 ? 'in-progress' : 'todo';
  });

  protected readonly done = computed(() => this.state() === 'done');

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
