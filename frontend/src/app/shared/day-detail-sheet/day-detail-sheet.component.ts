import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { HomeApi } from '../../core/home/home.api';
import { CategoryGroup, DayDetail } from '../../core/home/home.types';
import { PointsCategory } from '../../core/points/points.types';
import { LogicalDate, formatLong } from '../../core/time/logical-date';
import { DfSheetComponent } from '../ui/df-sheet/df-sheet.component';
import { DfSkeletonComponent } from '../ui/df-skeleton/df-skeleton.component';

const CATEGORY_LABELS: Record<PointsCategory, string> = {
  WORKOUT: 'Workout',
  RUN: 'Run',
  HABIT: 'Habit',
  ACTIVITY: 'Activity',
  GOAL: 'Goal',
  JOB: 'Job',
  REWARD: 'Reward',
  ADJUSTMENT: 'Adjustment',
};

/**
 * Tapping a heatmap or calendar cell opens this: what that day actually earned,
 * grouped by category (spec §8.1.1).
 *
 * Two filters run before anything renders, both from owner feedback:
 *
 * - Only point-contributing lines survive. An entry worth zero moved no score, so it is
 *   noise in a sheet whose whole subject is the score; a category left with nothing but
 *   zeroes disappears with them. A group that nets zero from a real +50 and a real −50
 *   is *not* noise and stays — the filter is per entry, never per group total.
 * - `only` narrows the whole sheet to one module's categories, which is what lets the
 *   Workout and Habit pages reuse this for their own calendars ("show the logged
 *   workouts / habits") instead of each growing a near-copy of it.
 */
@Component({
  selector: 'df-day-detail-sheet',
  imports: [DfSheetComponent, DfSkeletonComponent],
  templateUrl: './day-detail-sheet.component.html',
  styleUrl: './day-detail-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DayDetailSheetComponent {
  private readonly api = inject(HomeApi);

  readonly date = input<LogicalDate | null>(null);
  /** Empty means every category, the Home heatmap's own case. */
  readonly only = input<readonly PointsCategory[]>([]);
  /** What the total line counts, when the sheet is narrowed to one module. */
  readonly totalNoun = input('points');
  readonly closed = output<void>();

  protected readonly formatLong = formatLong;
  protected readonly categoryLabels = CATEGORY_LABELS;
  protected readonly loading = signal(false);
  private readonly detail = signal<DayDetail | null>(null);
  protected readonly isOpen = computed(() => this.date() !== null);

  /** Scoring lines only, narrowed to `only` when it is set. */
  protected readonly groups = computed<CategoryGroup[]>(() => {
    const detail = this.detail();
    if (!detail) {
      return [];
    }
    const only = this.only();
    return detail.groups
      .filter((group) => only.length === 0 || only.includes(group.category))
      .map((group) => ({ ...group, entries: group.entries.filter((entry) => entry.amount !== 0) }))
      .filter((group) => group.entries.length > 0);
  });

  /** Recomputed from what survived the filter, so it can never disagree with the list. */
  protected readonly total = computed(() =>
    this.groups().reduce((sum, group) => sum + group.entries.reduce((s, e) => s + e.amount, 0), 0),
  );

  protected readonly hasDetail = computed(() => this.detail() !== null);

  constructor() {
    effect(() => {
      const date = this.date();
      if (!date) {
        this.detail.set(null);
        return;
      }
      void this.load(date);
    });
  }

  private async load(date: LogicalDate): Promise<void> {
    this.loading.set(true);
    try {
      this.detail.set(await firstValueFrom(this.api.day(date)));
    } finally {
      this.loading.set(false);
    }
  }

  protected close(): void {
    this.closed.emit();
  }
}
