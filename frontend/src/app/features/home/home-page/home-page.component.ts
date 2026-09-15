import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule, Quote as QuoteIcon, Target } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { HomeApi } from '../../../core/home/home.api';
import { HomeSummary } from '../../../core/home/home.types';
import { LedgerEntry, PointsCategory } from '../../../core/points/points.types';
import { LogicalDate, formatLong } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfScorePillComponent } from '../../../shared/ui/df-score-pill/df-score-pill.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { DayDetailSheetComponent } from '../day-detail-sheet/day-detail-sheet.component';
import { HeatmapComponent } from '../heatmap/heatmap.component';

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
 * Home (spec §8.1). Goal progress and job metrics are omitted entirely — both modules
 * land at M7, and the spec's own rule is to hide those sections "entirely when none"
 * exist, which an absent field from the backend already accomplishes.
 */
@Component({
  selector: 'df-home-page',
  imports: [
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfEmptyStateComponent,
    DfScorePillComponent,
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

  protected readonly quoteIcon = QuoteIcon;
  protected readonly targetIcon = Target;
  protected readonly categoryLabels = CATEGORY_LABELS;
  protected readonly formatLong = formatLong;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<HomeSummary | null>(null);
  protected readonly selectedDay = signal<LogicalDate | null>(null);

  protected readonly categoryBreakdown = computed(() => {
    const byCategory = this.summary()?.score.byCategory;
    if (!byCategory) {
      return [];
    }
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
    } catch {
      this.error.set('Could not load your home page. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

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
