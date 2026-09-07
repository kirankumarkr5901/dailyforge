import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ArchiveRestore, LucideAngularModule, Plus, Settings2, SquarePen, Star, Trash2 } from 'lucide-angular';

import { WorkoutsApi } from '../../../core/workouts/workouts.api';
import { Exercise, PlanExercise, WorkoutPlan } from '../../../core/workouts/workouts.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ExercisePickerSheetComponent } from '../exercise-picker-sheet/exercise-picker-sheet.component';

/**
 * Plans and days (spec §8.2). Day 0 is rendered as "Extras" — the bucket for exercises
 * attached to a plan but not yet placed on a real day. Reordering is up/down rather
 * than drag-and-drop (spec's own keyboard-reachable fallback), the same trade the habit
 * planner made at M3.
 */
@Component({
  selector: 'df-plan-manager-sheet',
  imports: [
    FormsModule,
    NgTemplateOutlet,
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfIconButtonComponent,
    DfInputComponent,
    DfSelectComponent,
    DfSheetComponent,
    DfStepperInputComponent,
    ExercisePickerSheetComponent,
  ],
  templateUrl: './plan-manager-sheet.component.html',
  styleUrl: './plan-manager-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanManagerSheetComponent {
  private readonly api = inject(WorkoutsApi);

  readonly open = input.required<boolean>();
  readonly closed = output<void>();
  readonly changed = output<void>();

  protected readonly plusIcon = Plus;
  /** Templates cannot call the global String() directly. */
  protected readonly String = String;
  protected readonly deleteIcon = Trash2;
  protected readonly editIcon = SquarePen;
  protected readonly restoreIcon = ArchiveRestore;
  protected readonly eliteIcon = Star;
  protected readonly targetsIcon = Settings2;

  protected readonly plans = signal<WorkoutPlan[]>([]);
  protected readonly archivedPlans = signal<WorkoutPlan[]>([]);
  protected readonly showArchived = signal(false);
  protected readonly selectedPlanId = signal<string | null>(null);
  protected readonly newPlanName = signal('');
  protected readonly newPlanDays = signal(3);
  protected readonly creating = signal(false);
  protected readonly pickerOpen = signal(false);
  protected readonly pickerDayIndex = signal(0);

  /** The one day label (or the plan name, index -1) currently being edited inline. */
  protected readonly editingDayIndex = signal<number | null>(null);
  protected readonly dayLabelDraft = signal('');
  protected readonly editingPlanName = signal(false);
  protected readonly planNameDraft = signal('');

  protected readonly selectedPlan = computed(() => this.plans().find((p) => p.id === this.selectedPlanId()) ?? null);

  /** Every place an exercise can be moved to — Extras plus every real day — for the
   * "Move to" select on each plan-exercise row. */
  protected readonly moveOptions = computed<readonly DfSelectOption[]>(() => {
    const plan = this.selectedPlan();
    if (!plan) {
      return [];
    }
    return [
      { value: '0', label: 'Extras' },
      ...plan.dayLabels.map((label, i) => ({ value: String(i + 1), label })),
    ];
  });

  protected readonly editingExerciseId = signal<string | null>(null);
  /** 0 means "no target set" — df-stepper-input has no notion of null, and a target of
   * literally zero sets or reps would be meaningless anyway. */
  protected readonly targetSetsDraft = signal(0);
  protected readonly targetRepsDraft = signal(0);
  protected readonly notesDraft = signal('');

  protected readonly planOptions = computed<readonly DfSelectOption[]>(() =>
    this.plans().map((p) => ({ value: p.id, label: p.name + (p.isActive ? ' (active)' : '') })),
  );

  constructor() {
    effect(() => {
      if (this.open()) {
        void this.refresh();
      }
    });
  }

  private async refresh(): Promise<void> {
    const [list, archived] = await Promise.all([
      firstValueFrom(this.api.plans()),
      firstValueFrom(this.api.archivedPlans()),
    ]);
    this.plans.set(list);
    this.archivedPlans.set(archived);
    if (!this.selectedPlanId() && list.length > 0) {
      this.selectedPlanId.set(list.find((p) => p.isActive)?.id ?? list[0].id);
    }
  }

  protected extrasFor(plan: WorkoutPlan): PlanExercise[] {
    return plan.exercises.filter((e) => e.dayIndex === 0);
  }

  protected exercisesForDay(plan: WorkoutPlan, dayIndex: number): PlanExercise[] {
    return plan.exercises.filter((e) => e.dayIndex === dayIndex);
  }

  protected async createPlan(): Promise<void> {
    if (this.creating() || !this.newPlanName().trim()) {
      return;
    }
    this.creating.set(true);
    try {
      const plan = await firstValueFrom(this.api.createPlan(this.newPlanName().trim(), this.newPlanDays()));
      this.newPlanName.set('');
      this.newPlanDays.set(3);
      await this.refresh();
      this.selectedPlanId.set(plan.id);
      this.changed.emit();
    } finally {
      this.creating.set(false);
    }
  }

  protected async activate(planId: string): Promise<void> {
    await firstValueFrom(this.api.updatePlan(planId, { isActive: true }));
    await this.refresh();
    this.changed.emit();
  }

  protected async archive(planId: string): Promise<void> {
    await firstValueFrom(this.api.archivePlan(planId));
    if (this.selectedPlanId() === planId) {
      this.selectedPlanId.set(null);
    }
    await this.refresh();
    this.changed.emit();
  }

  protected async restore(planId: string): Promise<void> {
    await firstValueFrom(this.api.unarchivePlan(planId));
    await this.refresh();
    this.selectedPlanId.set(planId);
    this.changed.emit();
  }

  protected startRenamingDay(dayIndex: number, currentLabel: string): void {
    this.editingDayIndex.set(dayIndex);
    this.dayLabelDraft.set(currentLabel);
  }

  protected cancelRenamingDay(): void {
    this.editingDayIndex.set(null);
  }

  protected async saveDayLabel(planId: string): Promise<void> {
    const dayIndex = this.editingDayIndex();
    const label = this.dayLabelDraft().trim();
    if (dayIndex === null || !label) {
      return;
    }
    await firstValueFrom(this.api.relabelDay(planId, dayIndex, label));
    this.editingDayIndex.set(null);
    await this.refresh();
    this.changed.emit();
  }

  protected startRenamingPlan(currentName: string): void {
    this.editingPlanName.set(true);
    this.planNameDraft.set(currentName);
  }

  protected cancelRenamingPlan(): void {
    this.editingPlanName.set(false);
  }

  protected async savePlanName(planId: string): Promise<void> {
    const name = this.planNameDraft().trim();
    if (!name) {
      return;
    }
    await firstValueFrom(this.api.updatePlan(planId, { name }));
    this.editingPlanName.set(false);
    await this.refresh();
    this.changed.emit();
  }

  protected openPickerForDay(dayIndex: number): void {
    this.pickerDayIndex.set(dayIndex);
    this.pickerOpen.set(true);
  }

  protected async onExercisePicked(exercise: Exercise): Promise<void> {
    const plan = this.selectedPlan();
    this.pickerOpen.set(false);
    if (!plan) {
      return;
    }
    await firstValueFrom(this.api.addPlanExercise(plan.id, exercise.id, this.pickerDayIndex()));
    await this.refresh();
    this.changed.emit();
  }

  protected async removeExercise(planId: string, planExerciseId: string): Promise<void> {
    await firstValueFrom(this.api.removePlanExercise(planId, planExerciseId));
    await this.refresh();
    this.changed.emit();
  }

  protected async moveToDay(planId: string, planExerciseId: string, dayIndex: string): Promise<void> {
    await firstValueFrom(this.api.moveExercise(planId, planExerciseId, Number(dayIndex)));
    await this.refresh();
    this.changed.emit();
  }

  protected startEditingExercise(pe: PlanExercise): void {
    this.editingExerciseId.set(pe.id);
    this.targetSetsDraft.set(pe.targetSets ?? 0);
    this.targetRepsDraft.set(pe.targetReps ?? 0);
    this.notesDraft.set(pe.notes ?? '');
  }

  protected cancelEditingExercise(): void {
    this.editingExerciseId.set(null);
  }

  protected async saveExerciseTargets(planId: string): Promise<void> {
    const planExerciseId = this.editingExerciseId();
    if (!planExerciseId) {
      return;
    }
    await firstValueFrom(
      this.api.updatePlanExercise(planId, planExerciseId, {
        targetSets: this.targetSetsDraft() || null,
        targetReps: this.targetRepsDraft() || null,
        notes: this.notesDraft().trim() || null,
      }),
    );
    this.editingExerciseId.set(null);
    await this.refresh();
    this.changed.emit();
  }

  protected toggleArchivedView(): void {
    this.showArchived.update((v) => !v);
  }

  protected close(): void {
    this.closed.emit();
  }
}
