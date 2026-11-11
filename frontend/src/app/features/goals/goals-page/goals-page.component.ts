import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { Award, EyeOff, LucideAngularModule, Plus, SquarePen, Trash2 } from 'lucide-angular';

import { AuthApi } from '../../../core/auth/auth.api';
import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { SyncStore } from '../../../core/sync/sync.store';
import { GoalApi } from '../../../core/goal/goal.api';
import { Goal, GoalStatus } from '../../../core/goal/goal.types';
import { PointsStore } from '../../../core/points/points.store';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { GoalFormSheetComponent } from '../goal-form-sheet/goal-form-sheet.component';

/** Goals (spec §8.6). Progress bars are driven entirely by the server's own recompute — never estimated here. */
@Component({
  selector: 'df-goals-page',
  imports: [
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfEmptyStateComponent,
    DfIconButtonComponent,
    DfSkeletonComponent,
    GoalFormSheetComponent,
  ],
  templateUrl: './goals-page.component.html',
  styleUrl: './goals-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsPageComponent {
  private readonly api = inject(GoalApi);
  private readonly authApi = inject(AuthApi);
  private readonly toasts = inject(ToastService);
  private readonly points = inject(PointsStore);

  private readonly sync = inject(SyncStore);
  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);

  protected readonly plusIcon = Plus;
  protected readonly awardIcon = Award;
  protected readonly editIcon = SquarePen;
  protected readonly hideIcon = EyeOff;
  protected readonly deleteIcon = Trash2;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly goals = signal<Goal[]>([]);
  protected readonly todayDate = signal<LogicalDate | null>(null);
  protected readonly formOpen = signal(false);
  /** The goal the form is editing, or null when it is creating. */
  protected readonly editingGoal = signal<Goal | null>(null);

  protected readonly activeGoals = computed(() => this.goals().filter((g) => g.status === 'ACTIVE'));

  /**
   * History, minus anything archived (owner feedback).
   *
   * Archiving is how a goal is hidden, so a board that still showed archived goals gave
   * the action nothing to do. They are not deleted — the API still returns them, and
   * the points a completed one paid stay in the ledger either way.
   */
  protected readonly otherGoals = computed(() =>
    this.goals().filter((g) => g.status !== 'ACTIVE' && g.status !== 'ARCHIVED'),
  );

  constructor() {
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.loadAll();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.goals.set([]);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });

    // Re-read whenever this device may be behind: the tab came back after a while,
    // the network returned, a session was restored, or the server just refused a
    // write as stale (SyncStore).
    this.sync.refreshes.pipe(takeUntilDestroyed()).subscribe(() => void this.loadAll());
  }

  private async loadAll(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [today, goals] = await Promise.all([firstValueFrom(this.authApi.today()), firstValueFrom(this.api.list())]);
      this.todayDate.set(today.date);
      this.goals.set(goals);
    } catch {
      this.error.set('Could not load your goals. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  private async refresh(): Promise<void> {
    this.goals.set(await firstValueFrom(this.api.list()));
  }

  protected openForm(): void {
    this.editingGoal.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(goal: Goal): void {
    this.editingGoal.set(goal);
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.formOpen.set(false);
  }

  protected async onSaved(): Promise<void> {
    const wasEditing = this.editingGoal() !== null;
    this.closeForm();
    await this.refresh();
    this.toasts.show(wasEditing ? 'Goal updated.' : 'Goal created.');
  }

  protected canClaim(goal: Goal): boolean {
    return goal.targetValue != null && goal.currentValue >= goal.targetValue;
  }

  protected async complete(goal: Goal): Promise<void> {
    try {
      await firstValueFrom(this.api.complete(goal.id));
      await this.refresh();
      // The complete endpoint returns the goal, not a points envelope (unlike most
      // mutating endpoints, spec §7) — a full resync rather than applyEnvelope.
      void this.points.refresh();
      this.toasts.show(`+${goal.rewardPoints} pts — goal complete!`, { tone: 'earned' });
    } catch {
      this.toasts.show('Could not complete that goal. Try again.', { tone: 'penalty' });
    }
  }

  protected async reopen(goal: Goal): Promise<void> {
    try {
      await firstValueFrom(this.api.reopen(goal.id));
      await this.refresh();
      void this.points.refresh();
      this.toasts.show('Goal reopened.');
    } catch {
      this.toasts.show('Could not reopen that goal. Try again.', { tone: 'penalty' });
    }
  }

  protected async extend(goal: Goal): Promise<void> {
    if (!this.todayDate()) {
      return;
    }
    const newEndDate = this.addDays(this.todayDate()!, 7);
    try {
      await firstValueFrom(this.api.extend(goal.id, newEndDate));
      await this.refresh();
      this.toasts.show('Goal extended by a week.');
    } catch {
      this.toasts.show('Could not extend that goal. Try again.', { tone: 'penalty' });
    }
  }

  protected async archive(goal: Goal): Promise<void> {
    try {
      await firstValueFrom(this.api.archive(goal.id));
      await this.refresh();
      this.toasts.show('Goal archived.');
    } catch {
      this.toasts.show('Could not archive that goal. Try again.', { tone: 'penalty' });
    }
  }

  /**
   * Hides a goal from the board without destroying it. Archiving, not deleting: the
   * difference matters for a completed goal, whose reward is in the ledger.
   */
  protected async hide(goal: Goal): Promise<void> {
    try {
      await firstValueFrom(this.api.archive(goal.id));
      await this.refresh();
      this.toasts.show('Goal hidden.');
    } catch {
      this.toasts.show('Could not hide that goal. Try again.', { tone: 'penalty' });
    }
  }

  /**
   * Deleting is irreversible and has no Undo toast to fall back on — the row is gone —
   * so the button arms on the first press and acts on the second, disarming itself
   * after a few seconds. A native confirm() would be the quick way, but nothing else in
   * this app uses one, and a browser dialog in the middle of a bespoke design system
   * looks like a bug.
   */
  protected readonly armedDeleteId = signal<string | null>(null);
  private disarmTimer?: ReturnType<typeof setTimeout>;

  protected armDelete(goal: Goal): void {
    this.armedDeleteId.set(goal.id);
    clearTimeout(this.disarmTimer);
    this.disarmTimer = setTimeout(() => this.armedDeleteId.set(null), 4000);
  }

  /**
   * Deletes a goal outright. A completed goal's reward is reversed on the server as
   * part of the delete, so the score cannot keep points for a goal that no longer
   * exists — which is why this is a separate action from hiding rather than a tidier
   * version of it.
   */
  protected async remove(goal: Goal): Promise<void> {
    clearTimeout(this.disarmTimer);
    this.armedDeleteId.set(null);
    try {
      await firstValueFrom(this.api.delete(goal.id));
      await this.refresh();
      void this.points.refresh();
      this.toasts.show('Goal deleted.');
    } catch {
      this.toasts.show('Could not delete that goal. Try again.', { tone: 'penalty' });
    }
  }

  /** Sentence case; the raw enum shouting at the user is not a label. */
  protected statusLabel(status: GoalStatus): string {
    return status.charAt(0) + status.slice(1).toLowerCase();
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  private addDays(date: LogicalDate, days: number): LogicalDate {
    const [y, m, d] = date.split('-').map(Number);
    const shifted = new Date(Date.UTC(y, m - 1, d + days));
    return shifted.toISOString().slice(0, 10);
  }
}
