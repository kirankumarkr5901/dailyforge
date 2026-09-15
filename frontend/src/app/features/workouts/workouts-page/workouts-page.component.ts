import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule, Plus, Settings2, Target } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { PendingActionService } from '../../../core/auth/pending-action.service';
import { SessionStore } from '../../../core/auth/session.store';
import { Celebration } from '../../../core/points/points.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { WorkoutsApi } from '../../../core/workouts/workouts.api';
import {
  Equipment,
  ExerciseBoardEntry,
  SetWriteResponse,
  WorkoutPlan,
  WorkoutSession,
  WorkoutSet,
} from '../../../core/workouts/workouts.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfDateStepperComponent } from '../../../shared/ui/df-date-stepper/df-date-stepper.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { ExerciseCardComponent } from '../exercise-card/exercise-card.component';
import { ExercisePickerSheetComponent } from '../exercise-picker-sheet/exercise-picker-sheet.component';
import { LogSetSheetComponent } from '../log-set-sheet/log-set-sheet.component';
import { PlanManagerSheetComponent } from '../plan-manager-sheet/plan-manager-sheet.component';
import { RestTimerComponent } from '../rest-timer/rest-timer.component';
import { RestTimerService } from '../rest-timer/rest-timer.service';

/**
 * The workout tracker (spec §8.3): a date, a plan day, and every exercise scheduled
 * for it as a log card. The board's own data decides everything shown — PRs, sets,
 * completion — this component only renders it and issues the writes.
 */
@Component({
  selector: 'df-workouts-page',
  imports: [
    FormsModule,
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfDateStepperComponent,
    DfEmptyStateComponent,
    DfSelectComponent,
    DfSkeletonComponent,
    ExerciseCardComponent,
    ExercisePickerSheetComponent,
    LogSetSheetComponent,
    PlanManagerSheetComponent,
    RestTimerComponent,
  ],
  templateUrl: './workouts-page.component.html',
  styleUrl: './workouts-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutsPageComponent {
  private readonly api = inject(WorkoutsApi);
  private readonly toasts = inject(ToastService);
  private readonly restTimer = inject(RestTimerService);
  private readonly pendingAction = inject(PendingActionService);

  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);

  protected readonly plusIcon = Plus;
  protected readonly settingsIcon = Settings2;
  protected readonly targetIcon = Target;
  /** Templates cannot call the global String() directly. */
  protected readonly String = String;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly plans = signal<WorkoutPlan[]>([]);
  protected readonly todayDate = signal<LogicalDate | null>(null);
  protected readonly viewedDate = signal<LogicalDate | null>(null);
  protected readonly selectedPlanId = signal<string | null>(null);
  protected readonly selectedDayIndex = signal<number>(1);
  protected readonly workoutSession = signal<WorkoutSession | null>(null);

  protected readonly planManagerOpen = signal(false);
  protected readonly pickerOpen = signal(false);
  protected readonly logSheetOpen = signal(false);
  protected readonly logSheetContext = signal<{ id: string; name: string; equipment: Equipment } | null>(null);
  protected readonly editingSet = signal<WorkoutSet | null>(null);

  protected readonly selectedPlan = computed(() => this.plans().find((p) => p.id === this.selectedPlanId()) ?? null);

  protected readonly dayOptions = computed<readonly DfSelectOption[]>(() => {
    const plan = this.selectedPlan();
    if (!plan) {
      return [];
    }
    return plan.dayLabels.map((label, i) => ({ value: String(i + 1), label }));
  });

  protected readonly planOptions = computed<readonly DfSelectOption[]>(() =>
    this.plans().map((p) => ({ value: p.id, label: p.name })),
  );

  /** Unfinished cards first, done ones sink to the bottom (spec §8.3). */
  protected readonly sortedExercises = computed<ExerciseBoardEntry[]>(() => {
    const exercises = this.workoutSession()?.exercises ?? [];
    return [...exercises].sort((a, b) => Number(a.sets.length > 0) - Number(b.sets.length > 0));
  });

  constructor() {
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.loadAll();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.plans.set([]);
        this.workoutSession.set(null);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });
  }

  private async loadAll(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const plans = await firstValueFrom(this.api.plans());
      this.plans.set(plans);
      const active = plans.find((p) => p.isActive) ?? plans[0] ?? null;
      this.selectedPlanId.set(active?.id ?? null);
      await this.refreshSession();
    } catch {
      this.error.set('Could not load your workouts. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  private async refreshSession(date?: LogicalDate): Promise<void> {
    const plan = this.selectedPlan();
    try {
      const session = await firstValueFrom(
        this.api.session(date ?? this.viewedDate() ?? '', plan?.id ?? null, plan ? this.selectedDayIndex() : null),
      );
      this.workoutSession.set(session);
      if (this.todayDate() === null) {
        this.todayDate.set(session.date);
      }
      this.viewedDate.set(session.date);
    } catch {
      this.toasts.show('Could not load this session. Check your connection.', { tone: 'penalty' });
    }
  }

  protected async changeDate(date: LogicalDate): Promise<void> {
    this.viewedDate.set(date);
    await this.refreshSession(date);
  }

  protected async changePlan(planId: string): Promise<void> {
    this.selectedPlanId.set(planId || null);
    this.selectedDayIndex.set(1);
    await this.refreshSession();
  }

  protected async changeDay(dayIndex: string): Promise<void> {
    this.selectedDayIndex.set(Number(dayIndex));
    await this.refreshSession();
  }

  protected openLogSheetForCard(entry: ExerciseBoardEntry): void {
    this.editingSet.set(null);
    this.logSheetContext.set({ id: entry.exerciseId, name: entry.name, equipment: entry.equipment });
    this.logSheetOpen.set(true);
  }

  protected openEditSheet(entry: ExerciseBoardEntry, set: WorkoutSet): void {
    this.editingSet.set(set);
    this.logSheetContext.set({ id: entry.exerciseId, name: entry.name, equipment: entry.equipment });
    this.logSheetOpen.set(true);
  }

  protected openAddExercise(): void {
    if (!this.session.isAuthenticated()) {
      this.pendingAction.capture({ description: 'Sign in to add an exercise.', run: () => this.pickerOpen.set(true) });
      this.authSheet.open('write');
      return;
    }
    this.pickerOpen.set(true);
  }

  protected onExercisePicked(exercise: { id: string; name: string; equipment: Equipment }): void {
    this.pickerOpen.set(false);
    this.editingSet.set(null);
    this.logSheetContext.set({ id: exercise.id, name: exercise.name, equipment: exercise.equipment });
    this.logSheetOpen.set(true);
  }

  protected closeLogSheet(): void {
    this.logSheetOpen.set(false);
    this.editingSet.set(null);
    this.logSheetContext.set(null);
  }

  protected async onSetSaved(response: SetWriteResponse): Promise<void> {
    const wasEditing = this.editingSet() !== null;
    this.closeLogSheet();
    await this.refreshSession();

    if (!wasEditing) {
      this.restTimer.start();
    }

    const delta = response.points.delta;
    const note = this.celebrationNote(response.points.celebrations);
    let message = `${delta >= 0 ? '+' : ''}${delta} pts`;
    if (note) {
      message += ` — ${note}`;
    }
    this.toasts.show(message, {
      tone: delta > 0 ? 'earned' : delta < 0 ? 'penalty' : 'neutral',
      heatStep: delta !== 0 ? this.heatStepFor(Math.abs(delta)) : undefined,
    });
  }

  protected async deleteSet(set: WorkoutSet): Promise<void> {
    try {
      const response = await firstValueFrom(this.api.deleteSet(set.id));
      await this.refreshSession();
      this.toasts.show(`Set removed. ${response.points.delta} pts`, {
        tone: response.points.delta < 0 ? 'penalty' : 'neutral',
        actionLabel: undefined,
      });
    } catch {
      this.toasts.show('Could not remove that set. Try again.', { tone: 'penalty' });
    }
  }

  protected openPlanManager(): void {
    this.planManagerOpen.set(true);
  }

  protected async onPlansChanged(): Promise<void> {
    const plans = await firstValueFrom(this.api.plans());
    this.plans.set(plans);
    if (!plans.some((p) => p.id === this.selectedPlanId())) {
      this.selectedPlanId.set(plans.find((p) => p.isActive)?.id ?? plans[0]?.id ?? null);
    }
    await this.refreshSession();
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  private heatStepFor(amount: number): 1 | 2 | 3 | 4 {
    if (amount >= 100) return 4;
    if (amount >= 50) return 3;
    if (amount >= 20) return 2;
    return 1;
  }

  private celebrationNote(celebrations: Celebration[]): string | null {
    if (celebrations.some((c) => c.type === 'PR')) {
      return 'personal record!';
    }
    if (celebrations.some((c) => c.type === 'WORKOUT_COMPLETE')) {
      return 'session complete!';
    }
    return null;
  }
}
