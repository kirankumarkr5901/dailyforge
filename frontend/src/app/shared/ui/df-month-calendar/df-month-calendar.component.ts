import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { LucideAngularModule, ChevronLeft, ChevronRight, Check } from 'lucide-angular';

import { MonthGrid, buildMonths, formatCellDate } from '../../../core/time/calendar-grid';
import { LogicalDate } from '../../../core/time/logical-date';

/**
 * A single-month calendar, current month by default, Prev/Next paging back through a
 * 12-month window — the same interaction the Home heatmap already established, reused
 * here as a generic primitive (marked/unmarked per day) rather than the heatmap's own
 * 5-step heat colouring and multi-state glyphs. Built for the Workout and Habit pages,
 * each wanting their own scoped history view rather than sending every visitor back to
 * Home to see it (owner feedback).
 */
@Component({
  selector: 'df-month-calendar',
  imports: [LucideAngularModule],
  templateUrl: './df-month-calendar.component.html',
  styleUrl: './df-month-calendar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DfMonthCalendarComponent {
  readonly today = input.required<LogicalDate>();
  /** Dates to mark as "done" — plain membership, no intensity. */
  readonly markedDates = input.required<ReadonlySet<LogicalDate>>();
  readonly markedLabel = input('logged');
  /** 'done' is the habit-adherence green tokens.css itself carves out as the one
   * exception to "earned is always warm" (habits.md's own reasoning); 'earned' is the
   * warm heat tone everything else in the app uses. */
  readonly tone = input<'earned' | 'done'>('earned');

  readonly cellSelected = output<LogicalDate>();

  protected readonly prevIcon = ChevronLeft;
  protected readonly nextIcon = ChevronRight;
  protected readonly checkIcon = Check;

  protected readonly monthsBack = signal(0);

  private readonly allMonths = computed<MonthGrid[]>(() => buildMonths(this.today()));

  protected readonly viewedMonth = computed<MonthGrid>(() => {
    const months = this.allMonths();
    const index = months.length - 1 - this.monthsBack();
    return months[index];
  });

  protected readonly canGoNewer = computed(() => this.monthsBack() > 0);
  protected readonly canGoOlder = computed(() => this.monthsBack() < this.allMonths().length - 1);

  protected olderMonth(): void {
    if (this.canGoOlder()) {
      this.monthsBack.update((n) => n + 1);
    }
  }

  protected newerMonth(): void {
    if (this.canGoNewer()) {
      this.monthsBack.update((n) => n - 1);
    }
  }

  protected dayNumber(date: LogicalDate): number {
    return Number(date.slice(8, 10));
  }

  protected isMarked(date: LogicalDate): boolean {
    return this.markedDates().has(date);
  }

  protected ariaLabel(date: LogicalDate): string {
    return `${formatCellDate(date)}${this.isMarked(date) ? `, ${this.markedLabel()}` : ''}`;
  }

  protected select(date: LogicalDate | null): void {
    if (date) {
      this.cellSelected.emit(date);
    }
  }
}
