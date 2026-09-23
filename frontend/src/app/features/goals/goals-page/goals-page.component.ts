import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Award, LucideAngularModule, Plus } from 'lucide-angular';

import { AuthApi } from '../../../core/auth/auth.api';
import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { GoalApi } from '../../../core/goal/goal.api';
import { Goal } from '../../../core/goal/goal.types';
import { PointsStore } from '../../../core/points/points.store';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { GoalFormSheetComponent } from '../goal-form-sheet/goal-form-sheet.component';

/** Goals (spec §8.6). Progress bars are driven entirely by the server's own recompute — never estimated here. */
@Component({
  selector: 'df-goals-page',
  imports: [LucideAngularModule, DfButtonComponent, DfCardComponent, DfEmptyStateComponent, DfSkeletonComponent, GoalFormSheetComponent],
  templateUrl: './goals-page.component.html',
  styleUrl: './goals-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsPageComponent {
  private readonly api = inject(GoalApi);
  private readonly authApi = inject(AuthApi);
  private readonly toasts = inject(ToastService);
  private readonly points = inject(PointsStore);

  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);

  protected readonly plusIcon = Plus;
  protected readonly awardIcon = Award;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly goals = signal<Goal[]>([]);
  protected readonly todayDate = signal<LogicalDate | null>(null);
  protected readonly formOpen = signal(false);

  protected readonly activeGoals = computed(() => this.goals().filter((g) => g.status === 'ACTIVE'));
  protected readonly otherGoals = computed(() => this.goals().filter((g) => g.status !== 'ACTIVE'));

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
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.formOpen.set(false);
  }

  protected async onCreated(): Promise<void> {
    this.closeForm();
    await this.refresh();
    this.toasts.show('Goal created.');
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

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  private addDays(date: LogicalDate, days: number): LogicalDate {
    const [y, m, d] = date.split('-').map(Number);
    const shifted = new Date(Date.UTC(y, m - 1, d + days));
    return shifted.toISOString().slice(0, 10);
  }
}
