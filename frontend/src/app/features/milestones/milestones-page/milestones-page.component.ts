import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  Award,
  ChevronLeft,
  ChevronRight,
  Dumbbell,
  Footprints,
  LucideAngularModule,
  PartyPopper,
  Target,
} from 'lucide-angular';

import { HomeApi } from '../../../core/home/home.api';
import { MilestoneApi } from '../../../core/milestone/milestone.api';
import { Badge } from '../../../core/milestone/badge.types';
import { MilestoneRecap, RecapPeriod } from '../../../core/milestone/milestone.types';
import { PointsStore } from '../../../core/points/points.store';
import { BadgeSheetComponent } from '../badge-sheet/badge-sheet.component';
import { BadgeTileComponent } from '../badge-tile/badge-tile.component';
import { PointsCategory } from '../../../core/points/points.types';
import { monthsAgoStart, yearsAgoStart } from '../../../core/time/calendar-grid';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

const CATEGORY_LABELS: Record<PointsCategory, string> = {
  WORKOUT: 'Workout',
  RUN: 'Run',
  HABIT: 'Habit',
  ACTIVITY: 'Activity',
  GOAL: 'Goal',
  JOB: 'Job',
  REWARD: 'Reward',
  BADGE: 'Badge',
  ADJUSTMENT: 'Adjustment',
};

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

/**
 * Item 7 of the second feedback round — "milestones for every month, yearly" — the
 * auto-generated-recap half of the owner's "both" answer (the other half, user-defined
 * milestone goals, is the new YEAR option on the existing Goals feature rather than a
 * parallel screen here). Assembles nothing itself: every number on this page is read
 * straight from `MilestoneApi.recap()`, which in turn only reads what each tracker
 * already logged.
 */
@Component({
  selector: 'df-milestones-page',
  imports: [
    LucideAngularModule,
    DfCardComponent,
    DfEmptyStateComponent,
    DfIconButtonComponent,
    DfSkeletonComponent,
    BadgeSheetComponent,
    BadgeTileComponent,
  ],
  templateUrl: './milestones-page.component.html',
  styleUrl: './milestones-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MilestonesPageComponent {
  private readonly api = inject(MilestoneApi);
  private readonly homeApi = inject(HomeApi);
  private readonly toasts = inject(ToastService);
  private readonly points = inject(PointsStore);

  protected readonly icons = { PartyPopper, Award, Dumbbell, Footprints, Target, ChevronLeft, ChevronRight };

  protected readonly period = signal<RecapPeriod>('MONTH');
  protected readonly stepsBack = signal(0);
  protected readonly todayDate = signal<LogicalDate | null>(null);
  protected readonly recap = signal<MilestoneRecap | null>(null);
  protected readonly loading = signal(true);
  protected readonly categoryLabels = CATEGORY_LABELS;

  protected readonly badges = signal<Badge[]>([]);
  /** The badge whose sheet is open, or null. */
  protected readonly openBadge = signal<Badge | null>(null);

  /** Claimable first: it is the only state with something to do about it. */
  protected readonly sortedBadges = computed<Badge[]>(() =>
    [...this.badges()].sort((a, b) => Number(b.claimable) - Number(a.claimable)),
  );

  protected readonly claimableCount = computed(() => this.badges().filter((b) => b.claimable).length);

  protected readonly anchor = computed<LogicalDate | null>(() => {
    const today = this.todayDate();
    if (!today) {
      return null;
    }
    return this.period() === 'YEAR'
      ? yearsAgoStart(today, this.stepsBack())
      : monthsAgoStart(today, this.stepsBack());
  });

  protected readonly canGoNewer = computed(() => this.stepsBack() > 0);

  protected readonly periodLabel = computed(() => {
    const recap = this.recap();
    if (!recap) {
      return '';
    }
    if (this.period() === 'YEAR') {
      return recap.startDate.slice(0, 4);
    }
    const [year, month] = recap.startDate.split('-').map(Number);
    return `${MONTH_NAMES[month - 1]} ${year}`;
  });

  protected readonly categoryBreakdown = computed(() => {
    const recap = this.recap();
    if (!recap) {
      return [];
    }
    return (Object.entries(recap.byCategory) as [PointsCategory, number][])
      .filter(([, amount]) => amount !== 0)
      .sort((a, b) => b[1] - a[1]);
  });

  protected readonly isEmpty = computed(() => {
    const recap = this.recap();
    if (!recap) {
      return false;
    }
    return (
      recap.totalPoints === 0 &&
      recap.workoutDays === 0 &&
      recap.runDays === 0 &&
      recap.habitsCompleted === 0 &&
      recap.goalsCompleted === 0
    );
  });

  constructor() {
    effect(() => {
      const anchor = this.anchor();
      const period = this.period();
      if (!anchor) {
        return;
      }
      void this.loadRecap(period, anchor);
      void this.loadBadges(period, anchor);
    });

    void this.init();
  }

  private async init(): Promise<void> {
    try {
      const summary = await firstValueFrom(this.homeApi.summary());
      this.todayDate.set(summary.date);
    } catch {
      this.toasts.show('Could not load milestones. Check your connection.', { tone: 'penalty' });
    }
  }

  private async loadRecap(period: RecapPeriod, anchor: LogicalDate): Promise<void> {
    this.loading.set(true);
    try {
      this.recap.set(await firstValueFrom(this.api.recap(period, anchor)));
    } catch {
      this.toasts.show('Could not load that recap. Check your connection.', { tone: 'penalty' });
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Badges load separately from the recap and are allowed to fail on their own.
   *
   * A frontend deploy reaches the CDN minutes before the backend finishes building, so
   * a newly added endpoint is briefly missing in production. Folding this into the
   * recap's own load would mean that window took the whole recap down with it — which
   * is exactly what happened when the referral list was added, and is not a mistake
   * worth making twice.
   */
  private async loadBadges(period: RecapPeriod, anchor: LogicalDate): Promise<void> {
    try {
      this.badges.set(await firstValueFrom(this.api.badges(period, anchor)));
    } catch {
      this.badges.set([]);
    }
  }

  protected onBadgeOpened(badge: Badge): void {
    this.openBadge.set(badge);
  }

  /**
   * A claim moves the score, so the shared store is refreshed rather than left to
   * disagree with the header pill until the next navigation.
   */
  protected async onBadgeClaimed(claimed: Badge): Promise<void> {
    this.badges.update((all) => all.map((b) => (b.code === claimed.code ? claimed : b)));
    this.openBadge.set(null);
    this.toasts.show(`${claimed.name} claimed — +${claimed.points} points.`);
    void this.points.refresh();
    const anchor = this.anchor();
    if (anchor) {
      void this.loadRecap(this.period(), anchor);
    }
  }

  /**
   * A signed amount with the right glyph. Rewards are spent, so their category total is
   * negative, and a template that prefixed "+" to everything printed "+-50" for them.
   * The minus is a real minus sign, not a hyphen, so it is the same width as the plus.
   */
  protected signed(amount: number): string {
    return amount < 0 ? `−${Math.abs(amount).toLocaleString()}` : `+${amount.toLocaleString()}`;
  }

  protected setPeriod(period: RecapPeriod): void {
    if (this.period() === period) {
      return;
    }
    this.period.set(period);
    this.stepsBack.set(0);
  }

  protected older(): void {
    this.stepsBack.update((n) => n + 1);
  }

  protected newer(): void {
    if (!this.canGoNewer()) {
      return;
    }
    this.stepsBack.update((n) => n - 1);
  }

  protected distanceLabel(distanceMeters: number): string {
    return `${(distanceMeters / 1000).toFixed(distanceMeters % 1000 === 0 ? 0 : 1)} km`;
  }
}
