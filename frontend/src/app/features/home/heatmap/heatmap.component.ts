import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Dumbbell, Footprints, LucideAngularModule, Moon, X } from 'lucide-angular';

import { HomeApi } from '../../../core/home/home.api';
import { DailySummary, DayState } from '../../../core/home/home.types';
import { LogicalDate } from '../../../core/time/logical-date';

interface MonthGrid {
  label: string;
  /** Each week is 7 cells, Monday first; null holes pad the first/last week. */
  weeks: (LogicalDate | null)[][];
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

/**
 * 12 months, scrollable, current month in view (spec §8.1.1). Colour encodes points
 * earned (5 steps); the glyph in the corner encodes state — never the only carrier of
 * meaning, since every cell also carries a full `aria-label`.
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

  protected readonly loading = signal(true);
  private readonly summaries = signal<Map<LogicalDate, DailySummary>>(new Map());

  protected readonly months = computed<MonthGrid[]>(() => buildMonths(this.today()));

  constructor() {
    effect(() => {
      void this.load(this.today());
    });
  }

  private async load(today: LogicalDate): Promise<void> {
    this.loading.set(true);
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

  protected select(date: LogicalDate | null): void {
    if (date) {
      this.cellSelected.emit(date);
    }
  }
}

function formatCellDate(date: LogicalDate): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'long', timeZone: 'UTC' }).format(
    new Date(Date.UTC(year, month - 1, day)),
  );
}

/** The first day of the month `monthsBack` months before `date`'s month — presentation-only calendar math on an already-provided date. */
function monthsAgoStart(date: LogicalDate, monthsBack: number): LogicalDate {
  const [year, month] = date.split('-').map(Number);
  const total = (year * 12 + (month - 1)) - monthsBack;
  const y = Math.floor(total / 12);
  const m = total % 12;
  return `${y.toString().padStart(4, '0')}-${(m + 1).toString().padStart(2, '0')}-01`;
}

function buildMonths(today: LogicalDate): MonthGrid[] {
  const [todayYear, todayMonth] = today.split('-').map(Number);
  const months: MonthGrid[] = [];

  for (let back = 11; back >= 0; back--) {
    const total = todayYear * 12 + (todayMonth - 1) - back;
    const year = Math.floor(total / 12);
    const month = total % 12; // 0-indexed
    months.push(buildMonth(year, month));
  }
  return months;
}

function buildMonth(year: number, month0: number): MonthGrid {
  const daysInMonth = new Date(Date.UTC(year, month0 + 1, 0)).getUTCDate();
  const firstWeekday = new Date(Date.UTC(year, month0, 1)).getUTCDay(); // 0=Sun..6=Sat
  const mondayFirstOffset = (firstWeekday + 6) % 7; // 0=Mon..6=Sun

  const dates: (LogicalDate | null)[] = new Array(mondayFirstOffset).fill(null);
  for (let d = 1; d <= daysInMonth; d++) {
    dates.push(`${year.toString().padStart(4, '0')}-${(month0 + 1).toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}`);
  }
  while (dates.length % 7 !== 0) {
    dates.push(null);
  }

  const weeks: (LogicalDate | null)[][] = [];
  for (let i = 0; i < dates.length; i += 7) {
    weeks.push(dates.slice(i, i + 7));
  }

  return { label: `${MONTH_NAMES[month0]} ${year}`, weeks };
}
