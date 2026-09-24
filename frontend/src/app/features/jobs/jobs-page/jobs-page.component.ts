import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { Briefcase, LucideAngularModule, Plus } from 'lucide-angular';

import { AuthApi } from '../../../core/auth/auth.api';
import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { JobApi } from '../../../core/job/job.api';
import { JobApplication, JobMetrics, JobStatus } from '../../../core/job/job.types';
import { LogicalDate, formatLong } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { JobFormSheetComponent } from '../job-form-sheet/job-form-sheet.component';

const STATUS_LABELS: Record<JobStatus, string> = {
  APPLIED: 'Applied',
  ASSESSMENT: 'Assessment',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
  GHOSTED: 'Ghosted',
};

const NEXT_STATUS_OPTIONS: readonly DfSelectOption[] = (Object.keys(STATUS_LABELS) as JobStatus[]).map((value) => ({
  value,
  label: STATUS_LABELS[value],
}));

/** The job pipeline (spec §8.7). Stage transitions are a per-row status change rather than a dedicated stepper sheet. */
@Component({
  selector: 'df-jobs-page',
  imports: [
    FormsModule,
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfEmptyStateComponent,
    DfSelectComponent,
    DfSkeletonComponent,
    JobFormSheetComponent,
  ],
  templateUrl: './jobs-page.component.html',
  styleUrl: './jobs-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobsPageComponent {
  private readonly api = inject(JobApi);
  private readonly authApi = inject(AuthApi);
  private readonly toasts = inject(ToastService);

  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);

  protected readonly plusIcon = Plus;
  protected readonly briefcaseIcon = Briefcase;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly statusOptions = NEXT_STATUS_OPTIONS;
  protected readonly formatLong = formatLong;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly applications = signal<JobApplication[]>([]);
  protected readonly metrics = signal<JobMetrics | null>(null);
  protected readonly todayDate = signal<LogicalDate | null>(null);
  protected readonly statusFilter = signal<JobStatus | ''>('');
  protected readonly formOpen = signal(false);

  constructor() {
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.loadAll();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.applications.set([]);
        this.metrics.set(null);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });
  }

  private async loadAll(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [today, apps, metrics] = await Promise.all([
        firstValueFrom(this.authApi.today()),
        firstValueFrom(this.api.list()),
        firstValueFrom(this.api.metrics()),
      ]);
      this.todayDate.set(today.date);
      this.applications.set(apps);
      this.metrics.set(metrics);
    } catch {
      this.error.set('Could not load your applications. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  private async refresh(): Promise<void> {
    const [apps, metrics] = await Promise.all([firstValueFrom(this.api.list()), firstValueFrom(this.api.metrics())]);
    this.applications.set(apps);
    this.metrics.set(metrics);
  }

  protected filteredApplications() {
    const filter = this.statusFilter();
    const apps = this.applications();
    return filter ? apps.filter((a) => a.status === filter) : apps;
  }

  /** Newest applied-on date first, each group already in that order from the API
   * (findAllByUserIdOrderByAppliedOnDesc) — same day-grouping pattern Home's own
   * activity log uses. */
  protected readonly groupedApplications = computed<{ date: LogicalDate; apps: JobApplication[] }[]>(() => {
    const groups: { date: LogicalDate; apps: JobApplication[] }[] = [];
    for (const app of this.filteredApplications()) {
      const last = groups[groups.length - 1];
      if (last && last.date === app.appliedOn) {
        last.apps.push(app);
      } else {
        groups.push({ date: app.appliedOn, apps: [app] });
      }
    }
    return groups;
  });

  /** A more specific line than the raw status where the timeline has more to say — an
   * interview round, or which stage a rejection actually came from (owner feedback:
   * "if application moved to rejected from applied then it got... rejected at
   * screening"). Purely a display label; the underlying status is unchanged. */
  protected displayLabel(app: JobApplication): string {
    if (app.status === 'INTERVIEW' && app.currentRound > 0) {
      return `Interview — Round ${app.currentRound}`;
    }
    if (app.status === 'REJECTED' && app.rejectedFromStatus) {
      return app.rejectedFromStatus === 'APPLIED' || app.rejectedFromStatus === 'ASSESSMENT'
        ? 'Rejected at screening'
        : 'Rejected after interview';
    }
    return this.statusLabels[app.status];
  }

  protected openForm(): void {
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.formOpen.set(false);
  }

  protected async onCreated(): Promise<void> {
    this.closeForm();
    await this.refresh();
    this.toasts.show('Application added.');
  }

  protected async advance(app: JobApplication, toStatus: JobStatus): Promise<void> {
    if (toStatus === app.status) {
      return;
    }
    try {
      const roundNumber = toStatus === 'INTERVIEW' ? app.currentRound + 1 : undefined;
      await firstValueFrom(this.api.transition(app.id, { toStatus, roundNumber, occurredOn: this.todayDate()! }));
      await this.refresh();
      this.toasts.show(`${app.company} moved to ${this.statusLabels[toStatus]}.`);
    } catch {
      this.toasts.show('Could not update that application. Try again.', { tone: 'penalty' });
    }
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  protected countFor(status: JobStatus): number {
    return this.metrics()?.countsByStatus[status] ?? 0;
  }
}
