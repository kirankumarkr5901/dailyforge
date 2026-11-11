import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { Flame, LucideAngularModule, Swords, Trophy } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { ForgeApi } from '../../../core/forge/forge.api';
import { FORGE_PHASE_LABELS, ForgeOverview, ForgeQuest } from '../../../core/forge/forge.types';
import { PointsStore } from '../../../core/points/points.store';
import { SyncStore } from '../../../core/sync/sync.store';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { ForgeCalendarComponent } from '../forge-calendar/forge-calendar.component';
import { ForgeQuestComponent } from '../forge-quest/forge-quest.component';
import { ForgeQuizComponent } from '../forge-quiz/forge-quiz.component';
import { ForgeStatsComponent } from '../forge-stats/forge-stats.component';

/**
 * Forge 66 — a 66-day program built from a questionnaire about the user's life.
 *
 * Deliberately a world of its own inside the app (owner request): its own look, its own
 * progression (XP, levels, stats, streaks), and quests that never read habits or
 * workouts. The single bridge back is points: each completed quest pays into the main
 * score, and this page hands the server's envelope to PointsStore so the header total
 * moves with it. Every number shown here comes from the server.
 */
@Component({
  selector: 'df-forge-page',
  imports: [LucideAngularModule, ForgeQuizComponent, ForgeCalendarComponent, ForgeQuestComponent, ForgeStatsComponent],
  templateUrl: './forge-page.component.html',
  styleUrl: './forge-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgePageComponent {
  private readonly api = inject(ForgeApi);
  private readonly points = inject(PointsStore);
  private readonly toasts = inject(ToastService);
  private readonly sync = inject(SyncStore);
  protected readonly session = inject(SessionStore);
  private readonly authSheet = inject(AuthSheetService);

  protected readonly flameIcon = Flame;
  protected readonly swordsIcon = Swords;
  protected readonly trophyIcon = Trophy;

  protected readonly phaseLabels = FORGE_PHASE_LABELS;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly overview = signal<ForgeOverview | null>(null);
  protected readonly quizOpen = signal(false);
  protected readonly busyQuestId = signal<string | null>(null);
  protected readonly confirmingAbandon = signal(false);
  /** Shown briefly after a level-up; the number it celebrates. */
  protected readonly levelUpTo = signal<number | null>(null);

  protected readonly program = computed(() => this.overview()?.program ?? null);
  protected readonly profile = computed(() => this.overview()?.profile ?? null);

  protected readonly levelPercent = computed(() => {
    const p = this.profile();
    if (!p || p.xpForNextLevel <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((p.xpIntoLevel / p.xpForNextLevel) * 100));
  });

  protected readonly questsDone = computed(() => this.program()?.quests.filter((q) => q.done).length ?? 0);

  constructor() {
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.load();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.overview.set(null);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });

    // The day may have turned over while the tab sat in the background; re-reading is
    // also what settles any days that closed in the meantime.
    this.sync.refreshes.pipe(takeUntilDestroyed()).subscribe(() => {
      if (!this.quizOpen()) {
        void this.load();
      }
    });
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.overview.set(await firstValueFrom(this.api.overview()));
    } catch {
      this.error.set('Could not load Forge 66. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  protected openQuiz(): void {
    this.quizOpen.set(true);
  }

  protected onStarted(overview: ForgeOverview): void {
    this.overview.set(overview);
    this.quizOpen.set(false);
    this.toasts.show('Your 66 days start today.');
  }

  protected async toggle(quest: ForgeQuest): Promise<void> {
    if (this.busyQuestId()) {
      return;
    }
    this.busyQuestId.set(quest.id);
    try {
      const change = await firstValueFrom(quest.done ? this.api.uncomplete(quest.id) : this.api.complete(quest.id));
      this.overview.set(change.forge);
      this.points.applyEnvelope(change.points);

      if (change.leveledUp) {
        this.celebrateLevel(change.forge.profile.level);
      } else if (change.dayCompleted) {
        this.toasts.show('Day complete. Every quest done.', { heatStep: 3 });
      } else if (!quest.done) {
        this.toasts.show(`+${quest.xp} XP · +${change.points.delta} pts`, { heatStep: 2 });
      }
    } catch (error) {
      const message = (error as { error?: { message?: string } })?.error?.message;
      this.toasts.show(message ?? 'Could not save that. Try again.', { tone: 'penalty' });
      void this.load();
    } finally {
      this.busyQuestId.set(null);
    }
  }

  private celebrateLevel(level: number): void {
    this.levelUpTo.set(level);
    setTimeout(() => this.levelUpTo.set(null), 2600);
  }

  protected async abandon(): Promise<void> {
    try {
      this.overview.set(await firstValueFrom(this.api.abandon()));
      this.confirmingAbandon.set(false);
      this.toasts.show('Program ended. Your XP and level stay with you.');
    } catch {
      this.toasts.show('Could not end the program. Try again.', { tone: 'penalty' });
    }
  }

}
