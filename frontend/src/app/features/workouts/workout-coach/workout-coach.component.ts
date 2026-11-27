import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { AiStatusService } from '../../../core/ai/ai-status.service';
import { LogicalDate } from '../../../core/time/logical-date';
import { Coaching, WorkoutCoachApi } from '../../../core/workouts/workout-coach.api';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';

/**
 * The AI coach on the day's workout: a short pre-workout note, plateau / deload / swap
 * alerts and muscles short of their weekly sets. Loads by itself for the plan day shown;
 * the server keeps it for the day, so switching back and forth costs nothing.
 */
@Component({
  selector: 'df-workout-coach',
  imports: [DfButtonComponent, DfCardComponent],
  templateUrl: './workout-coach.component.html',
  styleUrl: './workout-coach.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkoutCoachComponent {
  private readonly api = inject(WorkoutCoachApi);
  protected readonly ai = inject(AiStatusService);

  readonly planId = input.required<string>();
  readonly dayIndex = input.required<number>();
  readonly date = input<LogicalDate | null>(null);

  protected readonly coaching = signal<Coaching | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  private request = 0;

  constructor() {
    effect(() => {
      const planId = this.planId();
      const dayIndex = this.dayIndex();
      const date = this.date();
      untracked(() => void this.load(planId, dayIndex, date));
    });
  }

  protected retry(): void {
    void this.load(this.planId(), this.dayIndex(), this.date());
  }

  private async load(planId: string, dayIndex: number, date: LogicalDate | null): Promise<void> {
    const mine = ++this.request;
    this.coaching.set(null);
    this.error.set(null);
    if (!(await this.ai.load())) return;
    this.loading.set(true);
    try {
      const coaching = await firstValueFrom(this.api.coach(planId, dayIndex, date));
      if (mine === this.request) this.coaching.set(coaching);
    } catch (e) {
      if (mine !== this.request) return;
      const message =
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
          ? e.error.message
          : null;
      this.error.set(message ?? 'The coach could not be reached.');
    } finally {
      if (mine === this.request) this.loading.set(false);
    }
  }

  protected label(type: 'PLATEAU' | 'DELOAD' | 'SWAP'): string {
    return type === 'PLATEAU' ? 'Stalled' : type === 'DELOAD' ? 'Ease off' : 'Swap';
  }
}
