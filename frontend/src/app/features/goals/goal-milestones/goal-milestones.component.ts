import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { GoalPlan, GoalPlannerApi } from '../../../core/goal/goal-planner.api';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * An AI-planned goal's milestones (tick them off) and this week's check-in: a note, and
 * when the AI suggests new dates, the proposal with Accept and Keep mine.
 */
@Component({
  selector: 'df-goal-milestones',
  imports: [DfButtonComponent],
  templateUrl: './goal-milestones.component.html',
  styleUrl: './goal-milestones.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalMilestonesComponent {
  private readonly api = inject(GoalPlannerApi);
  private readonly toasts = inject(ToastService);

  readonly plan = input.required<GoalPlan>();
  /** Every plan, as the server now has them. */
  readonly changed = output<GoalPlan[]>();

  protected readonly busy = signal(false);

  protected async toggle(id: string): Promise<void> {
    await this.run(() => firstValueFrom(this.api.toggleMilestone(id)));
  }

  protected async answer(accept: boolean): Promise<void> {
    await this.run(() => firstValueFrom(this.api.answerReview(this.plan().goalId, accept)));
  }

  private async run(call: () => Promise<GoalPlan[]>): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      this.changed.emit(await call());
    } catch {
      this.toasts.show('That did not save. Try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }

  protected formatDate(date: string): string {
    return new Date(date + 'T00:00:00Z').toLocaleDateString(undefined, {
      day: 'numeric',
      month: 'short',
      timeZone: 'UTC',
    });
  }

  protected overdue(date: string, doneOn: string | null): boolean {
    const today = new Date();
    const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    return !doneOn && date < iso;
  }
}
