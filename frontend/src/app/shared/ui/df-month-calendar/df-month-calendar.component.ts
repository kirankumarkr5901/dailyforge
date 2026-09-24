import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { LucideAngularModule, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-angular';

import { MonthGrid, buildMonths, formatCellDate } from '../../../core/time/calendar-grid';
import { LogicalDate } from '../../../core/time/logical-date';

/**
 * A single-month calendar, current month by default, Prev/Next paging back through a
 * 12-month window — the same interaction the Home heatmap already established, reused
 * here as a generic primitive. Built for the Workout and Habit pages, each wanting
 * their own scoped history view rather than sending every visitor back to Home to see
 * it (owner feedback).
 *
 * Three things came out of the owner using it:
 *
 * - It starts collapsed. A full month grid above the thing you actually came to the
 *   page to do (log today's sets, tick today's habits) pushed that work below the fold
 *   every visit, to show history most visits do not need.
 * - Cells carry intensity, not just membership: `valueByDate` is how much was logged
 *   that day, and the shade says how much at a glance.
 * - The steps are derived from the month's own busiest day rather than fixed cutoffs,
 *   so the same component reads correctly for a scale of habit ticks and a scale of
 *   workout volume without either being told what "a lot" means (and without inventing
 *   magic numbers, which this codebase does not allow).
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
  /** How much was logged each day — absent or zero means nothing was. */
  readonly valueByDate = input.required<ReadonlyMap<LogicalDate, number>>();
  readonly heading = input('History');
  readonly markedLabel = input('logged');
  /** 'done' is the habit-adherence green tokens.css itself carves out as the one
   * exception to "earned is always warm" (habits.md's own reasoning); 'earned' is the
   * warm heat tone everything else in the app uses. */
  readonly tone = input<'earned' | 'done'>('earned');

  readonly cellSelected = output<LogicalDate>();

  protected readonly prevIcon = ChevronLeft;
  protected readonly nextIcon = ChevronRight;
  protected readonly expandIcon = ChevronDown;

  protected readonly expanded = signal(false);
  protected readonly monthsBack = signal(0);

  private readonly allMonths = computed<MonthGrid[]>(() => buildMonths(this.today()));

  protected readonly viewedMonth = computed<MonthGrid>(() => {
    const months = this.allMonths();
    const index = months.length - 1 - this.monthsBack();
    return months[index];
  });

  protected readonly canGoNewer = computed(() => this.monthsBack() > 0);
  protected readonly canGoOlder = computed(() => this.monthsBack() < this.allMonths().length - 1);

  /** Every logged day in the month on screen — the summary the collapsed header shows. */
  private readonly monthValues = computed<number[]>(() =>
    this.viewedMonth()
      .weeks.flat()
      .filter((date): date is LogicalDate => date !== null)
      .map((date) => this.valueByDate().get(date) ?? 0)
      .filter((value) => value > 0),
  );

  protected readonly loggedDayCount = computed(() => this.monthValues().length);

  /** The month's own busiest day, which the four shades are cut from. */
  private readonly peak = computed(() => Math.max(0, ...this.monthValues()));

  protected toggle(): void {
    this.expanded.update((open) => !open);
  }

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

  /** 0 for a day with nothing logged, then four shades scaled to this month's peak. */
  protected heatStep(date: LogicalDate): 0 | 1 | 2 | 3 | 4 {
    const value = this.valueByDate().get(date) ?? 0;
    if (value <= 0) {
      return 0;
    }
    const peak = this.peak();
    if (peak <= 0) {
      return 0;
    }
    const share = value / peak;
    if (share > 0.75) return 4;
    if (share > 0.5) return 3;
    if (share > 0.25) return 2;
    return 1;
  }

  protected hasValue(date: LogicalDate): boolean {
    return (this.valueByDate().get(date) ?? 0) > 0;
  }

  protected ariaLabel(date: LogicalDate): string {
    return `${formatCellDate(date)}${this.hasValue(date) ? `, ${this.markedLabel()}` : ''}`;
  }

  protected select(date: LogicalDate | null): void {
    if (date && this.hasValue(date)) {
      this.cellSelected.emit(date);
    }
  }
}
