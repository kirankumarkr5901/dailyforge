import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule, Quote as QuoteIcon, Target } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { Goal } from '../../../core/goal/goal.types';
import { HomeApi } from '../../../core/home/home.api';
import { HomeSummary } from '../../../core/home/home.types';
import { PointsStore } from '../../../core/points/points.store';
import { LedgerEntry, PointsCategory } from '../../../core/points/points.types';
import { LogicalDate, formatLong } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { DayDetailSheetComponent } from '../day-detail-sheet/day-detail-sheet.component';
import { HeatmapComponent } from '../heatmap/heatmap.component';

type Period = 'today' | 'thisWeek' | 'thisMonth';

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

interface DayGroup {
  date: LogicalDate;
  entries: LedgerEntry[];
}

/**
 * Home (spec §8.1). Job metrics are still omitted entirely (spec's own "hidden entirely
 * when none exist" rule, which an absent field already accomplishes); active goals are
 * now shown, the one owner feedback specifically named as missing.
 *
 * The score card no longer repeats the total the header pill already shows — today,
 * this week, and this month are each clickable, switching which one's own category
 * breakdown renders below (owner feedback: "It's category score split should be shown
 * in the below" for whichever period is selected, not always the month).
 */
@Component({
  selector: 'df-home-page',
  imports: [
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfEmptyStateComponent,
    DfSkeletonComponent,
    DayDetailSheetComponent,
    HeatmapComponent,
  ],
  templateUrl: './home-page.component.html',
  styleUrl: './home-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePageComponent {
  private readonly api = inject(HomeApi);

  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);
  protected readonly points = inject(PointsStore);

  protected readonly quoteIcon = QuoteIcon;
  protected readonly targetIcon = Target;
  protected readonly categoryLabels = CATEGORY_LABELS;
  protected readonly formatLong = formatLong;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<HomeSummary | null>(null);
  protected readonly selectedDay = signal<LogicalDate | null>(null);
  protected readonly selectedPeriod = signal<Period>('thisMonth');

  protected readonly activeGoals = computed<Goal[]>(() => this.summary()?.activeGoals ?? []);

  protected readonly categoryBreakdown = computed(() => {
    const score = this.summary()?.score;
    if (!score) {
      return [];
    }
    const byCategory =
      this.selectedPeriod() === 'today'
        ? score.byCategoryToday
        : this.selectedPeriod() === 'thisWeek'
          ? score.byCategoryWeek
          : score.byCategory;
    return (Object.entries(byCategory) as [PointsCategory, number][])
      .filter(([, value]) => value !== 0)
      .sort((a, b) => b[1] - a[1]);
  });

  protected readonly maxCategoryValue = computed(() =>
    Math.max(1, ...this.categoryBreakdown().map(([, value]) => Math.abs(value))),
  );

  protected readonly dayGroups = computed<DayGroup[]>(() => {
    const entries = this.summary()?.recentLedger ?? [];
    const groups: DayGroup[] = [];
    for (const entry of entries) {
      const last = groups[groups.length - 1];
      if (last && last.date === entry.occurredOn) {
        last.entries.push(entry);
      } else {
        groups.push({ date: entry.occurredOn, entries: [entry] });
      }
    }
    return groups;
  });

  constructor() {
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.load();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.summary.set(null);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.summary.set(await firstValueFrom(this.api.summary()));
      // The header pill and this page's own score card must never disagree — both read
      // the shared store now, refreshed here so a stale header pill self-corrects the
      // moment the visitor lands on Home.
      void this.points.refresh();
    } catch {
      this.error.set('Could not load your home page. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  protected setPeriod(period: Period): void {
    this.selectedPeriod.set(period);
  }

  protected readonly periodLabel = computed(() => {
    switch (this.selectedPeriod()) {
      case 'today':
        return 'Today';
      case 'thisWeek':
        return 'This week';
      default:
        return 'This month';
    }
  });

  protected openDay(date: LogicalDate): void {
    this.selectedDay.set(date);
  }

  protected closeDay(): void {
    this.selectedDay.set(null);
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  protected barWidth(value: number): string {
    return `${Math.min(100, (Math.abs(value) / this.maxCategoryValue()) * 100)}%`;
  }
}
