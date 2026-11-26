import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { HomeApi } from '../../../core/home/home.api';
import { WeekReview } from '../../../core/home/home.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { SessionStore } from '../../../core/auth/session.store';

function utcDay(date: LogicalDate): Date {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

/**
 * The weekly review, shown on Home on Sunday (this week, as it finishes) and Monday
 * (last week, looking back), and hidden the rest of the week. Plain numbers, no chart:
 * a handful of facts reads faster on a phone than any plot of them.
 */
@Component({
  selector: 'df-week-review',
  imports: [DfCardComponent],
  templateUrl: './week-review.component.html',
  styleUrl: './week-review.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WeekReviewComponent {
  private readonly api = inject(HomeApi);
  protected readonly session = inject(SessionStore);

  /** Today, in the user's own time zone. */
  readonly today = input.required<LogicalDate>();

  protected readonly review = signal<WeekReview | null>(null);

  /** Sunday reviews this week; Monday reviews last week; other days show nothing. */
  private readonly target = computed<{ date: LogicalDate; title: string } | null>(() => {
    const d = utcDay(this.today());
    const dow = d.getUTCDay();
    if (dow === 0) return { date: this.today(), title: 'This week' };
    if (dow === 1) {
      d.setUTCDate(d.getUTCDate() - 7);
      return { date: d.toISOString().slice(0, 10) as LogicalDate, title: 'Last week' };
    }
    return null;
  });

  protected readonly title = computed(() => this.target()?.title ?? '');

  constructor() {
    effect(() => {
      const target = this.target();
      untracked(() => void this.load(target?.date ?? null));
    });
  }

  private async load(date: LogicalDate | null): Promise<void> {
    this.review.set(null);
    if (!date) return;
    try {
      this.review.set(await firstValueFrom(this.api.week(date)));
    } catch {
      // A review is a nice-to-have; Home works without it.
    }
  }

  protected range(r: WeekReview): string {
    const fmt = (s: LogicalDate) =>
      utcDay(s).toLocaleDateString(undefined, { day: 'numeric', month: 'short', timeZone: 'UTC' });
    return `${fmt(r.weekStart)} – ${fmt(r.weekEnd)}`;
  }

  protected weight(r: WeekReview): string {
    if (r.weightChangeKg === null) return '—';
    const v = Math.round(r.weightChangeKg * 10) / 10;
    return `${v > 0 ? '+' : ''}${v} kg`;
  }
}
