import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { Briefcase, LucideAngularModule, Plus } from 'lucide-angular';

import { AuthApi } from '../../../core/auth/auth.api';
import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { JobApi } from '../../../core/job/job.api';
import { InterviewStage, JobApplication, JobMetrics, JobStatus } from '../../../core/job/job.types';
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

/** How many rounds each interview stage runs to — the backend enforces the same ceilings. */
const STAGE_ROUNDS: Record<InterviewStage, number> = { TECHNICAL: 3, HR: 2 };

const STAGE_NAMES: Record<InterviewStage, string> = { TECHNICAL: 'Technical', HR: 'HR' };

/** Mid-sentence, "technical" is an ordinary word but "HR" is still an acronym. */
const STAGE_NAMES_INLINE: Record<InterviewStage, string> = { TECHNICAL: 'technical', HR: 'HR' };

function stageLabel(stage: InterviewStage, round: number): string {
  return `${STAGE_NAMES[stage]} round ${round}`;
}

/**
 * "Interview" on its own never said what was actually happening (owner feedback), so
 * the row's status control offers each interview round as its own option instead of a
 * status plus a second dropdown that only sometimes applies. The value carries the
 * stage and round with it; {@link JobsPageComponent.advance} parses it back apart.
 */
const INTERVIEW_OPTIONS: readonly DfSelectOption[] = (Object.keys(STAGE_ROUNDS) as InterviewStage[]).flatMap((stage) =>
  Array.from({ length: STAGE_ROUNDS[stage] }, (_, i) => ({
    value: `INTERVIEW:${stage}:${i + 1}`,
    label: stageLabel(stage, i + 1),
  })),
);

/** Every status, with INTERVIEW expanded into its rounds in the place it used to sit. */
const ROW_STATUS_OPTIONS: readonly DfSelectOption[] = (Object.keys(STATUS_LABELS) as JobStatus[]).flatMap((status) =>
  status === 'INTERVIEW' ? INTERVIEW_OPTIONS : [{ value: status, label: STATUS_LABELS[status] }],
);

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
  protected readonly rowStatusOptions = ROW_STATUS_OPTIONS;
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
    if (app.status === 'INTERVIEW' && app.interviewStage) {
      return stageLabel(app.interviewStage, app.currentRound);
    }
    if (app.status === 'INTERVIEW' && app.currentRound > 0) {
      return `Interview — Round ${app.currentRound}`;
    }
    if (app.status === 'REJECTED' && app.rejectedFromStatus) {
      // Rejected out of an interview names the round it fell at ("Rejected after HR
      // round 1"); rejected before ever interviewing is a screening rejection.
      if (app.rejectedFromStage && app.rejectedFromRound) {
        return `Rejected after ${STAGE_NAMES_INLINE[app.rejectedFromStage]} round ${app.rejectedFromRound}`;
      }
      return app.rejectedFromStatus === 'APPLIED' || app.rejectedFromStatus === 'ASSESSMENT'
        ? 'Rejected at screening'
        : 'Rejected after interview';
    }
    return this.statusLabels[app.status];
  }

  /** What the row's control shows as selected — an interview's own round, or the status. */
  protected rowStatusValue(app: JobApplication): string {
    return app.status === 'INTERVIEW' && app.interviewStage
      ? `INTERVIEW:${app.interviewStage}:${app.currentRound}`
      : app.status;
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

  /** `value` is either a plain status or an `INTERVIEW:STAGE:ROUND` triple. */
  protected async advance(app: JobApplication, value: string): Promise<void> {
    if (value === this.rowStatusValue(app)) {
      return;
    }
    const [status, stage, round] = value.split(':');
    const toStatus = status as JobStatus;
    const interviewStage = stage ? (stage as InterviewStage) : undefined;
    const roundNumber = round ? Number(round) : undefined;
    const label = interviewStage ? stageLabel(interviewStage, roundNumber!) : this.statusLabels[toStatus];

    try {
      await firstValueFrom(
        this.api.transition(app.id, { toStatus, roundNumber, interviewStage, occurredOn: this.todayDate()! }),
      );
      await this.refresh();
      this.toasts.show(`${app.company} moved to ${label}.`);
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
