import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { GoalPlanDraft, GoalPlannerApi } from '../../../core/goal/goal-planner.api';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * "Plan a goal with AI": say what you want and by when; the AI drafts a goal with
 * milestones, habits to start, a skill path for a skill goal and exercises for the
 * workout plan. Everything is shown first and each part can be left out; Add creates
 * what's kept.
 */
@Component({
  selector: 'df-goal-planner-sheet',
  imports: [FormsModule, DfButtonComponent, DfInputComponent, DfSheetComponent],
  templateUrl: './goal-planner-sheet.component.html',
  styleUrl: './goal-planner-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalPlannerSheetComponent {
  private readonly api = inject(GoalPlannerApi);
  private readonly toasts = inject(ToastService);

  readonly open = input.required<boolean>();
  readonly closed = output<void>();
  /** A plan was added: the page reloads goals and plans. */
  readonly created = output<void>();

  protected readonly request = signal('');
  protected readonly targetDate = signal('');
  protected readonly drafting = signal(false);
  protected readonly saving = signal(false);
  protected readonly draft = signal<GoalPlanDraft | null>(null);
  protected readonly keepSkill = signal(true);
  protected readonly keepHabits = signal<boolean[]>([]);
  protected readonly keepAdds = signal<boolean[]>([]);

  protected readonly minDate = computed(() => isoDaysFromNow(7));
  protected readonly canDraft = computed(
    () => this.request().trim().length > 3 && this.targetDate() >= this.minDate(),
  );

  constructor() {
    effect(() => {
      if (this.open()) {
        untracked(() => {
          this.draft.set(null);
          if (!this.targetDate()) this.targetDate.set(isoDaysFromNow(90));
        });
      }
    });
  }

  protected async makeDraft(): Promise<void> {
    if (!this.canDraft() || this.drafting()) return;
    this.drafting.set(true);
    try {
      const d = await firstValueFrom(
        this.api.draft(this.request().trim(), this.targetDate() as LogicalDate),
      );
      this.draft.set(d);
      this.keepSkill.set(true);
      this.keepHabits.set(d.habits.map(() => true));
      this.keepAdds.set(d.workoutAdds.map(() => true));
    } catch (e) {
      this.toasts.show(errorMessage(e) ?? "Couldn't plan that right now. Try again.", {
        tone: 'penalty',
      });
    } finally {
      this.drafting.set(false);
    }
  }

  protected renameGoal(title: string): void {
    this.draft.update((d) => (d ? { ...d, goal: { ...d.goal, title } } : d));
  }

  protected removeMilestone(index: number): void {
    this.draft.update((d) =>
      d ? { ...d, milestones: d.milestones.filter((_, i) => i !== index) } : d,
    );
  }

  protected toggle(list: 'habits' | 'adds', index: number): void {
    const target = list === 'habits' ? this.keepHabits : this.keepAdds;
    target.update((k) => k.map((v, i) => (i === index ? !v : v)));
  }

  protected async accept(): Promise<void> {
    const d = this.draft();
    if (!d || this.saving()) return;
    this.saving.set(true);
    try {
      await firstValueFrom(
        this.api.accept({
          ...d,
          habits: d.habits.filter((_, i) => this.keepHabits()[i]),
          workoutAdds: d.workoutAdds.filter((_, i) => this.keepAdds()[i]),
          skill: this.keepSkill() ? d.skill : null,
        }),
      );
      this.toasts.show('Your goal plan is set up.');
      this.request.set('');
      this.draft.set(null);
      this.created.emit();
      this.closed.emit();
    } catch (e) {
      this.toasts.show(errorMessage(e) ?? 'Could not add the plan. Try again.', {
        tone: 'penalty',
      });
    } finally {
      this.saving.set(false);
    }
  }

  protected formatDate(date: string): string {
    return new Date(date + 'T00:00:00Z').toLocaleDateString(undefined, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'UTC',
    });
  }

  protected days(n: number): string {
    return n >= 7 ? 'every day' : `${n} day${n === 1 ? '' : 's'} a week`;
  }
}

function isoDaysFromNow(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function errorMessage(e: unknown): string | null {
  return e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
    ? e.error.message
    : null;
}
