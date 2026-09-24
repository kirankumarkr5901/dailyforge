import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule, Plus, Settings2, Target } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { PendingActionService } from '../../../core/auth/pending-action.service';
import { SessionStore } from '../../../core/auth/session.store';
import { HomeApi } from '../../../core/home/home.api';
import { PointsStore } from '../../../core/points/points.store';
import { Celebration, PointsCategory } from '../../../core/points/points.types';
import { monthsAgoStart } from '../../../core/time/calendar-grid';
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
import { DayDetailSheetComponent } from '../../../shared/day-detail-sheet/day-detail-sheet.component';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfDateStepperComponent } from '../../../shared/ui/df-date-stepper/df-date-stepper.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfCelebrationComponent } from '../../../shared/ui/df-celebration/df-celebration.component';
import { DfMonthCalendarComponent } from '../../../shared/ui/df-month-calendar/df-month-calendar.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { ExerciseCardComponent } from '../exercise-card/exercise-card.component';
import { ExerciseHistorySheetComponent } from '../exercise-history-sheet/exercise-history-sheet.component';
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
    DayDetailSheetComponent,
    DfButtonComponent,
    DfCardComponent,
    DfCelebrationComponent,
    DfDateStepperComponent,
    DfEmptyStateComponent,
    DfMonthCalendarComponent,
    DfSelectComponent,
    DfSkeletonComponent,
    ExerciseCardComponent,
    ExerciseHistorySheetComponent,
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
  private readonly homeApi = inject(HomeApi);
  private readonly points = inject(PointsStore);
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
  protected readonly celebrationTrigger = signal(0);
  protected readonly historySheetOpen = signal(false);
  protected readonly historyContext = signal<{ id: string; name: string; equipment: Equipment } | null>(null);
  /** null means "every muscle group" — the default, unfiltered view. */
  protected readonly muscleFilter = signal<string | null>(null);

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

  /** Every muscle group any exercise on today's board targets, for the filter chips —
   * membership, not just an exercise's primary (grouping) group, so filtering by
   * "triceps" still finds a chest-primary exercise that also works triceps. */
  protected readonly availableMuscleGroups = computed<string[]>(() => {
    const groups = new Set<string>();
    for (const entry of this.sortedExercises()) {
      for (const group of entry.muscleGroups) {
        groups.add(group);
      }
    }
    return [...groups].sort();
  });

  protected readonly filteredExercises = computed<ExerciseBoardEntry[]>(() => {
    const filter = this.muscleFilter();
    const exercises = this.sortedExercises();
    return filter ? exercises.filter((entry) => entry.muscleGroups.includes(filter)) : exercises;
  });

  /** Grouped by primary muscle group (spec §11's "exercises grouped by muscle group,
   * each as a log card") — an exercise with several targets groups under its first one,
   * so it appears exactly once rather than being duplicated across sections. Ungrouped
   * exercises (no muscle data at all) land in a trailing "Other" section instead of
   * scattering wherever they first appear. Done-sinks-to-bottom is preserved within
   * each group, since the grouping only buckets the already-sorted list. */
  protected readonly muscleGroupSections = computed<{ group: string; exercises: ExerciseBoardEntry[] }[]>(() => {
    const order: string[] = [];
    const buckets = new Map<string, ExerciseBoardEntry[]>();
    for (const entry of this.filteredExercises()) {
      const group = entry.muscleGroups[0] ?? 'Other';
      if (!buckets.has(group)) {
        buckets.set(group, []);
        order.push(group);
      }
      buckets.get(group)!.push(entry);
    }
    const otherIndex = order.indexOf('Other');
    if (otherIndex !== -1 && otherIndex !== order.length - 1) {
      order.splice(otherIndex, 1);
      order.push('Other');
    }
    return order.map((group) => ({ group, exercises: buckets.get(group)! }));
  });

  protected setMuscleFilter(group: string | null): void {
    this.muscleFilter.set(group);
  }

  /** Days with a logged workout, for the page's own history calendar (owner feedback:
   * "Workout calendar is not built in the workout page") — the same daily-summary data
   * the Home heatmap already reads, just filtered to hasWorkout and rendered as plain
   * marked/unmarked instead of the heatmap's multi-state colouring. */
  protected readonly workoutDates = signal<ReadonlyMap<LogicalDate, number>>(new Map());

  /** Which day the history calendar has open, if any — its own sheet, not the page date. */
  protected readonly historyDay = signal<LogicalDate | null>(null);
  protected readonly workoutCategories: readonly PointsCategory[] = ['WORKOUT'];

  private async loadWorkoutDates(today: LogicalDate): Promise<void> {
    try {
      const summaries = await firstValueFrom(this.homeApi.heatmap(monthsAgoStart(today, 11), today));
      // The day's workout points stand in for "how much was logged" — they scale with
      // the sets actually done, and they are exactly what the day sheet then itemises,
      // so the shade and the sheet can never tell different stories.
      this.workoutDates.set(
        new Map(summaries.filter((s) => s.hasWorkout).map((s) => [s.date, s.pointsByCategory.WORKOUT ?? 0])),
      );
    } catch {
      // The calendar just shows nothing marked; the rest of the page still works.
    }
  }

  protected openHistoryDay(date: LogicalDate): void {
    this.historyDay.set(date);
  }

  protected closeHistoryDay(): void {
    this.historyDay.set(null);
  }

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
      if (this.todayDate()) {
        void this.loadWorkoutDates(this.todayDate()!);
      }
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
    this.muscleFilter.set(null);
    await this.refreshSession(date);
  }

  protected async changePlan(planId: string): Promise<void> {
    this.selectedPlanId.set(planId || null);
    this.selectedDayIndex.set(1);
    this.muscleFilter.set(null);
    await this.refreshSession();
  }

  protected async changeDay(dayIndex: string): Promise<void> {
    this.selectedDayIndex.set(Number(dayIndex));
    this.muscleFilter.set(null);
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

  protected openHistorySheet(entry: ExerciseBoardEntry): void {
    this.historyContext.set({ id: entry.exerciseId, name: entry.name, equipment: entry.equipment });
    this.historySheetOpen.set(true);
  }

  protected closeHistorySheet(): void {
    this.historySheetOpen.set(false);
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

  protected async onSetSaved(responses: SetWriteResponse[]): Promise<void> {
    for (const response of responses) {
      this.points.applyEnvelope(response.points);
    }
    const wasEditing = this.editingSet() !== null;
    this.closeLogSheet();
    await this.refreshSession();
    if (this.todayDate()) {
      void this.loadWorkoutDates(this.todayDate()!);
    }

    if (!wasEditing) {
      this.restTimer.start();
    }

    const delta = responses.reduce((sum, r) => sum + r.points.delta, 0);
    const celebrations = responses.flatMap((r) => r.points.celebrations);
    const note = this.celebrationNote(celebrations);
    let message =
      responses.length > 1
        ? `${responses.length} sets logged. ${delta >= 0 ? '+' : ''}${delta} pts`
        : `${delta >= 0 ? '+' : ''}${delta} pts`;
    if (note) {
      message += ` — ${note}`;
    }
    this.toasts.show(message, {
      tone: delta > 0 ? 'earned' : delta < 0 ? 'penalty' : 'neutral',
      heatStep: delta !== 0 ? this.heatStepFor(Math.abs(delta)) : undefined,
    });

    if (celebrations.some((c) => c.type === 'WORKOUT_COMPLETE')) {
      this.celebrationTrigger.update((n) => n + 1);
    }
  }

  protected async deleteSet(set: WorkoutSet): Promise<void> {
    try {
      const response = await firstValueFrom(this.api.deleteSet(set.id));
      this.points.applyEnvelope(response.points);
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
