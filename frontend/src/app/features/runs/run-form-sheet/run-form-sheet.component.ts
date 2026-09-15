import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ApiError } from '../../../core/api/api.types';
import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { PendingActionService } from '../../../core/auth/pending-action.service';
import { SessionStore } from '../../../core/auth/session.store';
import { RunsApi } from '../../../core/runs/runs.api';
import { LogRunPayload, Run, RunType, RunWriteResponse } from '../../../core/runs/runs.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';

const LONG_THRESHOLD_METRES = 10_000;

/**
 * Distance, duration and type (spec §8.5). Type locks to LONG at 10 km — the same rule
 * the backend enforces, shown here only so the choice never appears editable, never as
 * the thing deciding it: a distance the user types under 10 km after picking LONG
 * simply un-locks the interval/tempo choice again, it does not fight the server's own
 * decision.
 */
@Component({
  selector: 'df-run-form-sheet',
  imports: [FormsModule, DfButtonComponent, DfInputComponent, DfSheetComponent, DfStepperInputComponent],
  templateUrl: './run-form-sheet.component.html',
  styleUrl: './run-form-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunFormSheetComponent {
  private readonly api = inject(RunsApi);
  private readonly session = inject(SessionStore);
  private readonly pendingAction = inject(PendingActionService);
  private readonly authSheet = inject(AuthSheetService);

  readonly open = input.required<boolean>();
  readonly date = input<LogicalDate | null>(null);
  /** Run to edit in place, or null when logging a brand new one. */
  readonly editingRun = input<Run | null>(null);

  readonly closed = output<void>();
  readonly saved = output<RunWriteResponse>();

  protected readonly distanceKm = signal(5);
  protected readonly minutes = signal(25);
  protected readonly seconds = signal(0);
  protected readonly type = signal<RunType>('TEMPO');
  protected readonly feltEffort = signal<number | null>(null);
  protected readonly note = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly isEditing = computed(() => this.editingRun() !== null);
  protected readonly distanceMeters = computed(() => Math.round(this.distanceKm() * 1000));
  protected readonly isLong = computed(() => this.distanceMeters() >= LONG_THRESHOLD_METRES);
  protected readonly durationSeconds = computed(() => this.minutes() * 60 + this.seconds());

  protected readonly paceLabel = computed(() => {
    const km = this.distanceKm();
    const seconds = this.durationSeconds();
    if (km <= 0 || seconds <= 0) {
      return '—';
    }
    const paceSec = Math.round(seconds / km);
    const m = Math.floor(paceSec / 60);
    const s = paceSec % 60;
    return `${m}:${s.toString().padStart(2, '0')} /km`;
  });

  constructor() {
    effect(() => {
      if (!this.open()) {
        return;
      }
      this.error.set(null);
      const run = this.editingRun();
      if (run) {
        this.distanceKm.set(run.distanceMeters / 1000);
        this.minutes.set(Math.floor(run.durationSeconds / 60));
        this.seconds.set(run.durationSeconds % 60);
        this.type.set(run.type === 'LONG' ? 'TEMPO' : run.type);
        this.feltEffort.set(run.feltEffort);
        this.note.set(run.note ?? '');
      } else {
        this.distanceKm.set(5);
        this.minutes.set(25);
        this.seconds.set(0);
        this.type.set('TEMPO');
        this.feltEffort.set(null);
        this.note.set('');
      }
    });
  }

  protected async save(): Promise<void> {
    if (this.saving() || this.distanceMeters() <= 0 || this.durationSeconds() <= 0) {
      return;
    }
    if (!this.session.isAuthenticated()) {
      this.pendingAction.capture({
        description: this.isEditing() ? 'Run updated.' : 'Run logged.',
        run: () => this.performSave(),
      });
      this.authSheet.open('write');
      return;
    }
    await this.performSave();
  }

  private async performSave(): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      const payload: LogRunPayload = {
        // The parent always supplies this — a logical date is never computed here
        // (non-negotiable #5): it is either the server-resolved "today" or the date of
        // the run being edited.
        date: this.date()!,
        distanceMeters: this.distanceMeters(),
        durationSeconds: this.durationSeconds(),
        type: this.isLong() ? undefined : this.type(),
        note: this.note().trim() || undefined,
        feltEffort: this.feltEffort() ?? undefined,
      };
      const existing = this.editingRun();
      const response = existing
        ? await firstValueFrom(this.api.update(existing.id, payload))
        : await firstValueFrom(this.api.log(payload));
      this.saved.emit(response);
    } catch (error) {
      this.error.set(this.messageFor(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected close(): void {
    this.closed.emit();
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      if (body?.code === 'ENTRY_LOCKED') {
        return 'This day can no longer be changed.';
      }
      if (body?.code === 'OUT_OF_RANGE') {
        return body.message || 'That looks out of range. Check the value.';
      }
      if (body?.code === 'DAILY_CAP_REACHED') {
        return "You've reached today's cap for runs. It resets tomorrow.";
      }
      return body?.message ?? 'That did not save. Check your connection and try again.';
    }
    return 'That did not save. Check your connection and try again.';
  }
}
