import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule, Footprints, Plus, SquarePen, Trash2 } from 'lucide-angular';

import { AuthApi } from '../../../core/auth/auth.api';
import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { PendingActionService } from '../../../core/auth/pending-action.service';
import { SessionStore } from '../../../core/auth/session.store';
import { SyncStore } from '../../../core/sync/sync.store';
import { PointsStore } from '../../../core/points/points.store';
import { Celebration } from '../../../core/points/points.types';
import { Bracket, Run, RunRecords, RunWriteResponse } from '../../../core/runs/runs.types';
import { RunsApi } from '../../../core/runs/runs.api';
import { LogicalDate, formatLong } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfChipComponent } from '../../../shared/ui/df-chip/df-chip.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { DfScorePillComponent } from '../../../shared/ui/df-score-pill/df-score-pill.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { RunFormSheetComponent } from '../run-form-sheet/run-form-sheet.component';

const BRACKET_LABELS: Record<Bracket, string> = {
  D5K: '5 K',
  D10K: '10 K',
  D15K: '15 K',
  D21K: '21 K',
  D25K: '25 K',
  D42K: '42 K',
  D50K: '50 K',
};

/**
 * The run tracker (spec §8.5): a running-only lifetime total, PR sections by distance
 * and pace, bracket bests, and the full history — all read from the server, never
 * computed here (non-negotiable #4).
 */
@Component({
  selector: 'df-runs-page',
  imports: [
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfChipComponent,
    DfEmptyStateComponent,
    DfIconButtonComponent,
    DfScorePillComponent,
    DfSkeletonComponent,
    RunFormSheetComponent,
  ],
  templateUrl: './runs-page.component.html',
  styleUrl: './runs-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunsPageComponent {
  private readonly api = inject(RunsApi);
  private readonly points = inject(PointsStore);
  private readonly authApi = inject(AuthApi);
  private readonly toasts = inject(ToastService);
  private readonly pendingAction = inject(PendingActionService);

  private readonly sync = inject(SyncStore);
  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);

  protected readonly plusIcon = Plus;
  protected readonly footprintsIcon = Footprints;
  protected readonly editIcon = SquarePen;
  protected readonly deleteIcon = Trash2;
  protected readonly bracketLabels = BRACKET_LABELS;
  protected readonly brackets = Object.keys(BRACKET_LABELS) as Bracket[];
  protected readonly formatLong = formatLong;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly runs = signal<Run[]>([]);
  protected readonly records = signal<RunRecords | null>(null);
  protected readonly todayDate = signal<LogicalDate | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly editingRun = signal<Run | null>(null);

  constructor() {
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.loadAll();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.runs.set([]);
        this.records.set(null);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });

    // Re-read whenever this device may be behind: the tab came back after a while,
    // the network returned, a session was restored, or the server just refused a
    // write as stale (SyncStore).
    this.sync.refreshes.pipe(takeUntilDestroyed()).subscribe(() => void this.loadAll());
  }

  private async loadAll(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [today, runs, records] = await Promise.all([
        firstValueFrom(this.authApi.today()),
        firstValueFrom(this.api.list()),
        firstValueFrom(this.api.records()),
      ]);
      this.todayDate.set(today.date);
      this.runs.set(runs);
      this.records.set(records);
    } catch {
      this.error.set('Could not load your runs. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  private async refresh(): Promise<void> {
    try {
      const [runs, records] = await Promise.all([firstValueFrom(this.api.list()), firstValueFrom(this.api.records())]);
      this.runs.set(runs);
      this.records.set(records);
    } catch {
      this.toasts.show('Could not refresh your runs. Check your connection.', { tone: 'penalty' });
    }
  }

  protected openLogSheet(): void {
    if (!this.session.isAuthenticated()) {
      this.pendingAction.capture({ description: 'Sign in to log a run.', run: () => this.formOpen.set(true) });
      this.authSheet.open('write');
      return;
    }
    this.editingRun.set(null);
    this.formOpen.set(true);
  }

  protected openEditSheet(run: Run): void {
    this.editingRun.set(run);
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.formOpen.set(false);
    this.editingRun.set(null);
  }

  protected async onRunSaved(response: RunWriteResponse): Promise<void> {
    this.points.applyEnvelope(response.points);
    this.closeForm();
    await this.refresh();

    const delta = response.points.delta;
    const note = this.celebrationNote(response.points.celebrations);
    let message = `${delta >= 0 ? '+' : ''}${delta} pts`;
    if (note) {
      message += ` — ${note}`;
    }
    this.toasts.show(message, {
      tone: delta > 0 ? 'earned' : delta < 0 ? 'penalty' : 'neutral',
      heatStep: delta !== 0 ? this.heatStepFor(Math.abs(delta)) : undefined,
    });
  }

  protected async deleteRun(run: Run): Promise<void> {
    try {
      const response = await firstValueFrom(this.api.delete(run.id));
      this.points.applyEnvelope(response.points);
      await this.refresh();
      this.toasts.show(`Run removed. ${response.points.delta} pts`, {
        tone: response.points.delta < 0 ? 'penalty' : 'neutral',
      });
    } catch {
      this.toasts.show('Could not remove that run. Try again.', { tone: 'penalty' });
    }
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  protected paceLabel(paceSecPerKm: number): string {
    const m = Math.floor(paceSecPerKm / 60);
    const s = paceSecPerKm % 60;
    return `${m}:${s.toString().padStart(2, '0')} /km`;
  }

  protected durationLabel(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return h > 0 ? `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}` : `${m}:${s.toString().padStart(2, '0')}`;
  }

  protected distanceLabel(distanceMeters: number): string {
    return `${(distanceMeters / 1000).toFixed(distanceMeters % 1000 === 0 ? 0 : 1)} km`;
  }

  private heatStepFor(amount: number): 1 | 2 | 3 | 4 {
    if (amount >= 100) return 4;
    if (amount >= 50) return 3;
    if (amount >= 20) return 2;
    return 1;
  }

  private celebrationNote(celebrations: Celebration[]): string | null {
    if (celebrations.some((c) => c.type === 'MILESTONE')) {
      return 'milestone!';
    }
    return null;
  }
}
