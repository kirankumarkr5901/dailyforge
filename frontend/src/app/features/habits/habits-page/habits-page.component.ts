import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { Frown, LucideAngularModule, Plus, Sparkles, Target, Trash2 } from 'lucide-angular';

import { CdkDrag, CdkDragDrop, CdkDragPlaceholder, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { SyncStore } from '../../../core/sync/sync.store';
import { ActivityApi } from '../../../core/activity/activity.api';
import { ActivityLog, ActivityType } from '../../../core/activity/activity.types';
import { HabitsApi } from '../../../core/habits/habits.api';
import { HomeApi } from '../../../core/home/home.api';
import { PointsStore } from '../../../core/points/points.store';
import { Celebration, PointsCategory } from '../../../core/points/points.types';
import { Habit, HabitBoard } from '../../../core/habits/habits.types';
import { monthsAgoStart } from '../../../core/time/calendar-grid';
import { LogicalDate } from '../../../core/time/logical-date';
import { DayDetailSheetComponent } from '../../../shared/day-detail-sheet/day-detail-sheet.component';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfDateStepperComponent } from '../../../shared/ui/df-date-stepper/df-date-stepper.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { DfMonthCalendarComponent } from '../../../shared/ui/df-month-calendar/df-month-calendar.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { ActivityTypeFormSheetComponent } from '../../activities/activity-type-form-sheet/activity-type-form-sheet.component';
import { CommitmentBonusSheetComponent } from '../commitment-bonus-sheet/commitment-bonus-sheet.component';
import { HabitFormSheetComponent } from '../habit-form-sheet/habit-form-sheet.component';
import { HabitRowComponent, HabitToggled } from '../habit-row/habit-row.component';

/**
 * The habit planner and tracker (spec §4.3, §8): one screen, a date stepper, and every
 * habit scheduled for whatever date is in view.
 *
 * The board is the single source of truth for state, streaks and the bonus hint — this
 * component never computes any of those itself, only renders what `/habits/board`
 * returned (non-negotiable #4).
 */
@Component({
  selector: 'df-habits-page',
  imports: [
    CdkDrag,
    CdkDragPlaceholder,
    CdkDropList,
    LucideAngularModule,
    DayDetailSheetComponent,
    DfButtonComponent,
    DfCardComponent,
    DfDateStepperComponent,
    DfEmptyStateComponent,
    DfIconButtonComponent,
    DfMonthCalendarComponent,
    DfSkeletonComponent,
    ActivityTypeFormSheetComponent,
    CommitmentBonusSheetComponent,
    HabitFormSheetComponent,
    HabitRowComponent,
  ],
  templateUrl: './habits-page.component.html',
  styleUrl: './habits-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HabitsPageComponent {
  private readonly api = inject(HabitsApi);
  private readonly homeApi = inject(HomeApi);
  private readonly activityApi = inject(ActivityApi);
  private readonly toasts = inject(ToastService);
  private readonly points = inject(PointsStore);

  private readonly sync = inject(SyncStore);
  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);

  protected readonly targetIcon = Target;
  protected readonly plusIcon = Plus;
  protected readonly positiveIcon = Sparkles;
  protected readonly negativeIcon = Frown;
  protected readonly deleteIcon = Trash2;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly board = signal<HabitBoard | null>(null);
  protected readonly habitsList = signal<Habit[]>([]);
  protected readonly todayDate = signal<LogicalDate | null>(null);
  protected readonly viewedDate = signal<LogicalDate | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly editingHabit = signal<Habit | null>(null);
  protected readonly commitmentSheetOpen = signal(false);

  /** Days with at least one habit completed, for the page's own history calendar
   * (owner feedback: "Habit calender is not built in habit page"). Green tone —
   * habit-adherence is the one state the design system itself carves out as an
   * exception to "earned is always warm" (see habit-row's own note). */
  protected readonly habitDates = signal<ReadonlyMap<LogicalDate, number>>(new Map());

  /** Which day the history calendar has open, if any — its own sheet, not the page date. */
  protected readonly historyDay = signal<LogicalDate | null>(null);
  protected readonly habitCategories: readonly PointsCategory[] = ['HABIT'];

  private async loadHabitDates(today: LogicalDate): Promise<void> {
    try {
      const summaries = await firstValueFrom(this.homeApi.heatmap(monthsAgoStart(today, 11), today));
      // The day's habit points stand in for "how much was logged" — more ticks and a
      // longer streak both earn more, and they are exactly what the day sheet then
      // itemises, so the shade and the sheet can never tell different stories.
      this.habitDates.set(
        new Map(summaries.filter((s) => s.hasHabitCompletion).map((s) => [s.date, s.pointsByCategory.HABIT ?? 0])),
      );
    } catch {
      // The calendar just shows nothing marked; the rest of the page still works.
    }
  }

  /** True while a reorder is in flight, so a second drag cannot race the first. */
  protected readonly reordering = signal(false);

  /**
   * Reordering is optimistic: the list moves under the finger immediately and the new
   * order is sent afterwards. A drag that only settled once the server agreed would
   * feel broken on a cold free-tier API, and the cost of being wrong is small — on
   * failure the board is reloaded and the row goes back where it was.
   *
   * Order is a property of the habit, not of the day being viewed, so this is sent for
   * the whole list rather than per date.
   */
  protected async onHabitDropped(event: CdkDragDrop<unknown>): Promise<void> {
    const board = this.board();
    if (!board || event.previousIndex === event.currentIndex) {
      return;
    }

    const reordered = [...board.habits];
    moveItemInArray(reordered, event.previousIndex, event.currentIndex);
    this.board.set({ ...board, habits: reordered });

    this.reordering.set(true);
    try {
      await firstValueFrom(this.api.reorder(reordered.map((entry) => entry.id)));
      this.habitsList.set(await firstValueFrom(this.api.list()));
    } catch {
      this.toasts.show('Could not save that order. Putting it back.', { tone: 'penalty' });
      await this.refreshBoard();
    } finally {
      this.reordering.set(false);
    }
  }

  protected openHistoryDay(date: LogicalDate): void {
    this.historyDay.set(date);
  }

  protected closeHistoryDay(): void {
    this.historyDay.set(null);
  }

  /** Positive and negative one-off activities (spec §6 "activity") — a new section
   * here rather than a whole separate route (owner feedback: "build one new section of
   * positive and negative actions" under Habits), since both are personal-behaviour
   * logging in the same everyday sense a habit tick is. */
  protected readonly activityTypes = signal<ActivityType[]>([]);
  protected readonly recentActivityLogs = signal<ActivityLog[]>([]);
  protected readonly activityFormOpen = signal(false);
  protected readonly loggingActivityId = signal<string | null>(null);

  /**
   * Habits and activities are two boards on one route, one at a time (owner feedback:
   * "Habits and Activities should act as switch or filter button"). Stacking both made
   * the page a long scroll where the second half was rarely what you came for; the
   * switch keeps the route and its date context, and shows the one you asked for.
   */
  protected readonly view = signal<'habits' | 'activities'>('habits');

  protected setView(view: 'habits' | 'activities'): void {
    this.view.set(view);
  }

  /** Grouped by polarity — the only "type" an activity has (owner feedback: "Activities
   * should be grouped by it's type"). Positive first: the section you are meant to spend
   * most of your time in. A group with nothing in it is dropped rather than shown empty. */
  protected readonly activityGroups = computed<{ polarity: 'POSITIVE' | 'NEGATIVE'; label: string; types: ActivityType[] }[]>(() => {
    const all = this.activityTypes();
    return (
      [
        { polarity: 'POSITIVE' as const, label: 'Positive' },
        { polarity: 'NEGATIVE' as const, label: 'Negative' },
      ]
        .map((group) => ({ ...group, types: all.filter((type) => type.polarity === group.polarity) }))
        .filter((group) => group.types.length > 0)
    );
  });

  private async loadActivities(): Promise<void> {
    try {
      const [types, logs] = await Promise.all([
        firstValueFrom(this.activityApi.list()),
        firstValueFrom(this.activityApi.recentLogs()),
      ]);
      this.activityTypes.set(types);
      this.recentActivityLogs.set(logs.slice(0, 10));
    } catch {
      // The section just shows nothing; the habit board above still works.
    }
  }

  protected openActivityForm(): void {
    this.activityFormOpen.set(true);
  }

  protected closeActivityForm(): void {
    this.activityFormOpen.set(false);
  }

  protected async onActivityTypeCreated(type: ActivityType): Promise<void> {
    this.closeActivityForm();
    this.activityTypes.update((types) => [...types, type]);
    this.toasts.show('Activity created.');
  }

  protected async logActivity(type: ActivityType): Promise<void> {
    const date = this.todayDate();
    if (this.loggingActivityId() || !date) {
      return;
    }
    this.loggingActivityId.set(type.id);
    try {
      const response = await firstValueFrom(this.activityApi.log(type.id, { date, count: 1 }));
      this.points.applyEnvelope(response.points);
      this.recentActivityLogs.update((logs) => [response.log, ...logs].slice(0, 10));
      const delta = response.points.delta;
      this.toasts.show(`${type.name}. ${delta >= 0 ? '+' : ''}${delta} pts`, {
        tone: delta > 0 ? 'earned' : delta < 0 ? 'penalty' : 'neutral',
        actionLabel: 'Undo',
        action: () => void this.undoActivityLog(response.log.id),
      });
    } catch {
      this.toasts.show('Could not log that. Try again.', { tone: 'penalty' });
    } finally {
      this.loggingActivityId.set(null);
    }
  }

  private async undoActivityLog(logId: string): Promise<void> {
    try {
      const response = await firstValueFrom(this.activityApi.deleteLog(logId));
      this.points.applyEnvelope(response.points);
      this.recentActivityLogs.update((logs) => logs.filter((l) => l.id !== logId));
    } catch {
      this.toasts.show('Could not undo that. Try again.', { tone: 'penalty' });
    }
  }

  protected async archiveActivityType(type: ActivityType): Promise<void> {
    try {
      await firstValueFrom(this.activityApi.archive(type.id));
      this.activityTypes.update((types) => types.filter((t) => t.id !== type.id));
      this.toasts.show('Activity removed.');
    } catch {
      this.toasts.show('Could not remove that activity. Try again.', { tone: 'penalty' });
    }
  }

  constructor() {
    // Anonymous browsing is real elsewhere in the app, but a habit board is entirely
    // this user's own logged history — there is nothing generic to show a guest, so the
    // fetch waits for a real session rather than racing it (dev-points' own pattern).
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.loadAll();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.board.set(null);
        this.habitsList.set([]);
        this.activityTypes.set([]);
        this.recentActivityLogs.set([]);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });

    // Re-read whenever this device may be behind: the tab came back after a while,
    // the network returned, a session was restored, or the server just refused a
    // write as stale (SyncStore).
    this.sync.refreshes.pipe(takeUntilDestroyed()).subscribe((reason) => {
      if (reason === 'conflict') {
        this.closeForm();
      }
      void this.loadAll();
    });
  }

  private async loadAll(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [board, list] = await Promise.all([
        firstValueFrom(this.api.board(this.viewedDate() ?? undefined)),
        firstValueFrom(this.api.list()),
      ]);
      this.applyBoard(board);
      this.habitsList.set(list);
      if (this.todayDate()) {
        void this.loadHabitDates(this.todayDate()!);
      }
      void this.loadActivities();
    } catch {
      this.error.set('Could not load your habits. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  private applyBoard(board: HabitBoard): void {
    this.board.set(board);
    if (this.todayDate() === null) {
      this.todayDate.set(board.date);
    }
    this.viewedDate.set(board.date);
  }

  protected async changeDate(date: LogicalDate): Promise<void> {
    this.viewedDate.set(date);
    await this.refreshBoard(date);
  }

  private async refreshBoard(date?: LogicalDate): Promise<void> {
    try {
      const board = await firstValueFrom(this.api.board(date ?? this.viewedDate() ?? undefined));
      this.applyBoard(board);
    } catch {
      this.toasts.show('Could not refresh your habits. Check your connection.', { tone: 'penalty' });
    }
  }

  protected openCreate(): void {
    this.editingHabit.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(id: string): void {
    this.editingHabit.set(this.habitsList().find((habit) => habit.id === id) ?? null);
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.formOpen.set(false);
  }

  protected async onSaved(): Promise<void> {
    const isFirstHabit = this.habitsList().length === 0;
    const wasCreating = this.editingHabit() === null;
    this.formOpen.set(false);
    await this.loadAll();

    // The commitment bonus (spec §5.4) needs an amount before it means anything, and
    // nobody has had a reason to set one before their first habit exists.
    if (wasCreating && isFirstHabit && (this.session.user()?.settings.commitmentBonus ?? 0) === 0) {
      this.commitmentSheetOpen.set(true);
    }
  }

  protected async onDeleted(): Promise<void> {
    this.formOpen.set(false);
    await this.loadAll();
    this.toasts.show('Habit deleted.');
  }

  protected closeCommitmentSheet(): void {
    this.commitmentSheetOpen.set(false);
  }

  protected async onToggled({ entry, checked, response }: HabitToggled): Promise<void> {
    this.points.applyEnvelope(response.points);
    const delta = response.points.delta;
    const note = this.celebrationNote(response.points.celebrations);
    const sign = delta > 0 ? '+' : '';
    let message = `${entry.name} ${checked ? 'logged' : 'undone'}. ${sign}${delta} pts`;
    if (note) {
      message += ` — ${note}`;
    }

    this.toasts.show(message, {
      tone: delta > 0 ? 'earned' : delta < 0 ? 'penalty' : 'neutral',
      heatStep: delta !== 0 ? this.heatStepFor(Math.abs(delta)) : undefined,
      actionLabel: 'Undo',
      action: () => void this.undoToggle(entry.id, !checked),
    });

    await this.refreshBoard();
    if (this.todayDate()) {
      void this.loadHabitDates(this.todayDate()!);
    }
  }

  private async undoToggle(habitId: string, checked: boolean): Promise<void> {
    const date = this.viewedDate();
    if (!date) {
      return;
    }
    try {
      const response = checked
        ? await firstValueFrom(this.api.log(habitId, date))
        : await firstValueFrom(this.api.unlog(habitId, date));
      this.points.applyEnvelope(response.points);
      await this.refreshBoard();
    } catch {
      this.toasts.show('Could not undo that. Try again.', { tone: 'penalty' });
    }
  }

  protected onRowError(message: string): void {
    this.toasts.show(message, { tone: 'penalty' });
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
    if (celebrations.some((c) => c.type === 'STREAK')) {
      return 'streak bonus!';
    }
    if (celebrations.some((c) => c.type === 'ALL_HABITS_DONE')) {
      return 'all habits done today!';
    }
    return null;
  }
}
