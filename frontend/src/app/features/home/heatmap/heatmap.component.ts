import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ChevronLeft, ChevronRight, Dumbbell, Footprints, LucideAngularModule, Moon, X } from 'lucide-angular';

import { HomeApi } from '../../../core/home/home.api';
import { DailySummary, DayState } from '../../../core/home/home.types';
import { MonthGrid, buildMonths, formatCellDate, monthsAgoStart } from '../../../core/time/calendar-grid';
import { LogicalDate } from '../../../core/time/logical-date';

/**
 * One month in view at a time, current month by default, with Prev/Next arrows to page
 * back through the loaded 12-month window (spec §8.1.1). A full year of grids at once
 * read as a wall of noise rather than "what did I do this month" — the question this
 * screen actually answers most days. Colour encodes points earned (5 steps); the glyph
 * in the corner encodes state — never the only carrier of meaning, since every cell also
 * carries a full `aria-label`.
 */
@Component({
  selector: 'df-heatmap',
  imports: [LucideAngularModule],
  templateUrl: './heatmap.component.html',
  styleUrl: './heatmap.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeatmapComponent {
  private readonly api = inject(HomeApi);

  readonly today = input.required<LogicalDate>();
  readonly cellSelected = output<LogicalDate>();

  protected readonly workoutIcon = Dumbbell;
  protected readonly runIcon = Footprints;
  protected readonly restIcon = Moon;
  protected readonly missedIcon = X;
  protected readonly prevIcon = ChevronLeft;
  protected readonly nextIcon = ChevronRight;

  protected readonly loading = signal(true);
  private readonly summaries = signal<Map<LogicalDate, DailySummary>>(new Map());

  protected readonly monthsBack = signal(0);

  private readonly allMonths = computed<MonthGrid[]>(() => buildMonths(this.today()));

  /** The single month currently in view — the last entry (this month) minus `monthsBack`. */
  protected readonly viewedMonth = computed<MonthGrid>(() => {
    const months = this.allMonths();
    const index = months.length - 1 - this.monthsBack();
    return months[index];
  });

  protected readonly canGoNewer = computed(() => this.monthsBack() > 0);
  protected readonly canGoOlder = computed(() => this.monthsBack() < this.allMonths().length - 1);

  constructor() {
    effect(() => {
      void this.load(this.today());
    });
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

  private async load(today: LogicalDate): Promise<void> {
    this.loading.set(true);
    this.monthsBack.set(0);
    try {
      const from = monthsAgoStart(today, 11);
      const summaries = await firstValueFrom(this.api.heatmap(from, today));
      this.summaries.set(new Map(summaries.map((s) => [s.date, s])));
    } finally {
      this.loading.set(false);
    }
  }

  protected summaryFor(date: LogicalDate): DailySummary | undefined {
    return this.summaries().get(date);
  }

  protected dayNumber(date: LogicalDate): number {
    return Number(date.slice(8, 10));
  }

  protected heatStep(summary: DailySummary | undefined): 0 | 1 | 2 | 3 | 4 {
    if (!summary || summary.state === 'EMPTY') {
      return 0;
    }
    const amount = Math.abs(summary.pointsTotal);
    if (amount >= 300) return 4;
    if (amount >= 100) return 3;
    if (amount >= 25) return 2;
    return amount > 0 ? 1 : 0;
  }

  protected iconFor(state: DayState | undefined) {
    switch (state) {
      case 'WORKOUT':
      case 'BOTH':
        return this.workoutIcon;
      case 'RUN':
        return this.runIcon;
      case 'REST':
        return this.restIcon;
      case 'MISSED':
        return this.missedIcon;
      default:
        return null;
    }
  }

  protected stateLabel(state: DayState | undefined): string {
    switch (state) {
      case 'BOTH':
        return 'workout and run';
      case 'WORKOUT':
        return 'workout';
      case 'RUN':
        return 'run';
      case 'REST':
        return 'rest day';
      case 'MISSED':
        return 'missed';
      default:
        return 'no data';
    }
  }

  protected ariaLabel(date: LogicalDate): string {
    const summary = this.summaryFor(date);
    if (!summary || summary.state === 'EMPTY') {
      return `${formatCellDate(date)}, no data`;
    }
    return `${formatCellDate(date)}, ${summary.pointsTotal} points, ${this.stateLabel(summary.state)}`;
  }

  /**
   * Whether this day has anything the detail sheet could show (owner feedback: the
   * sheet is for point-contributing activity, so a day with none of it should not open
   * one at all rather than opening an empty sheet).
   *
   * Not simply `pointsTotal !== 0`: a day that earns 50 and spends 50 on a reward nets
   * zero and still has two real lines to show, which the per-category amounts catch
   * because earning and spending land in different categories.
   */
  protected hasScoringActivity(date: LogicalDate): boolean {
    const summary = this.summaryFor(date);
    if (!summary) {
      return false;
    }
    return summary.pointsTotal !== 0 || Object.values(summary.pointsByCategory).some((amount) => amount !== 0);
  }

  protected select(date: LogicalDate | null): void {
    if (date && this.hasScoringActivity(date)) {
      this.cellSelected.emit(date);
    }
  }
}
