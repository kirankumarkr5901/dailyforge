import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ApiError } from '../../../core/api/api.types';
import { JobApi } from '../../../core/job/job.api';
import { JobApplication, JobSource } from '../../../core/job/job.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';

/** A new application (spec §8.7's field list). */
@Component({
  selector: 'df-job-form-sheet',
  imports: [FormsModule, DfButtonComponent, DfInputComponent, DfSelectComponent, DfSheetComponent],
  templateUrl: './job-form-sheet.component.html',
  styleUrl: './job-form-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JobFormSheetComponent {
  private readonly api = inject(JobApi);

  readonly open = input.required<boolean>();
  /** The application being edited, or null to create a new one. */
  readonly editing = input<JobApplication | null>(null);
  readonly date = input<LogicalDate | null>(null);

  readonly closed = output<void>();
  readonly saved = output<JobApplication>();

  protected readonly company = signal('');
  protected readonly role = signal('');
  protected readonly city = signal('');
  protected readonly source = signal<JobSource>('APPLIED');
  protected readonly referrerName = signal('');
  protected readonly referrerProfileUrl = signal('');
  protected readonly jobUrl = signal('');
  protected readonly referralId = signal('');
  /**
   * When the referral was asked for. Defaults to today on open, but stays editable —
   * a referral is often logged a few days after the ask, and the whole referral board
   * is built on this date being the real one.
   */
  protected readonly referralRequestedOn = signal('');
  protected readonly note = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly sourceOptions: readonly DfSelectOption[] = [
    { value: 'APPLIED', label: 'Applied directly' },
    { value: 'REFERRAL_REQUESTED', label: 'Referral requested' },
    { value: 'REFERRED', label: 'Referred' },
    { value: 'RECRUITER', label: 'Recruiter reached out' },
  ];

  /** Referral fields only exist for a source that actually involves one. */
  protected readonly isEditing = computed(() => this.editing() !== null);

  constructor() {
    // Fill from the application being edited each time the sheet opens on one; a fresh
    // sheet starts blank.
    effect(() => {
      if (!this.open()) {
        return;
      }
      const app = this.editing();
      this.error.set(null);
      if (!app) {
        this.reset();
        return;
      }
      this.company.set(app.company);
      this.role.set(app.role);
      this.city.set(app.city ?? '');
      this.source.set(app.source);
      this.referrerName.set(app.referrerName ?? '');
      this.referrerProfileUrl.set(app.referrerProfileUrl ?? '');
      this.jobUrl.set(app.jobUrl ?? '');
      this.referralId.set(app.referralId ?? '');
      this.referralRequestedOn.set(app.referralRequestedOn ?? '');
      this.note.set(app.note ?? '');
    });
  }

  protected readonly isReferral = computed(
    () => this.source() === 'REFERRAL_REQUESTED' || this.source() === 'REFERRED',
  );

  protected async save(): Promise<void> {
    if (this.saving() || !this.company().trim() || !this.role().trim()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const editing = this.editing();
      const app = await firstValueFrom(
        editing
          ? this.api.update(
              editing.id,
              {
                company: this.company().trim(),
                role: this.role().trim(),
                city: this.city().trim() || undefined,
                source: this.source(),
                referrerName: this.referrerName().trim() || undefined,
                referrerProfileUrl: this.referrerProfileUrl().trim() || undefined,
                jobUrl: this.jobUrl().trim() || undefined,
                referralId: this.isReferral() ? this.referralId().trim() || undefined : undefined,
                referralRequestedOn: this.isReferral() ? this.referralRequestedOn() || undefined : undefined,
                note: this.note().trim() || undefined,
              },
              editing.version,
            )
          : this.api.create({
          company: this.company().trim(),
          role: this.role().trim(),
          city: this.city().trim() || undefined,
          source: this.source(),
          referrerName: this.referrerName().trim() || undefined,
          referrerProfileUrl: this.referrerProfileUrl().trim() || undefined,
          jobUrl: this.jobUrl().trim() || undefined,
          referralId: this.isReferral() ? this.referralId().trim() || undefined : undefined,
          referralRequestedOn: this.isReferral() ? this.referralRequestedOn() || this.date()! : undefined,
          note: this.note().trim() || undefined,
          appliedOn: this.date()!,
        }),
      );
      this.saved.emit(app);
      this.reset();
    } catch (error) {
      this.error.set(this.messageFor(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected close(): void {
    this.closed.emit();
  }

  private reset(): void {
    this.company.set('');
    this.role.set('');
    this.city.set('');
    this.source.set('APPLIED');
    this.referrerName.set('');
    this.referrerProfileUrl.set('');
    this.jobUrl.set('');
    this.referralId.set('');
    this.referralRequestedOn.set('');
    this.note.set('');
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      return body?.message ?? 'That did not save. Check your connection and try again.';
    }
    return 'That did not save. Check your connection and try again.';
  }
}
