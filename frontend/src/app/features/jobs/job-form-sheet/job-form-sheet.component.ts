import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
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
  readonly date = input<LogicalDate | null>(null);

  readonly closed = output<void>();
  readonly saved = output<JobApplication>();

  protected readonly company = signal('');
  protected readonly role = signal('');
  protected readonly city = signal('');
  protected readonly source = signal<JobSource>('APPLIED');
  protected readonly referrerName = signal('');
  protected readonly note = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly sourceOptions: readonly DfSelectOption[] = [
    { value: 'APPLIED', label: 'Applied directly' },
    { value: 'REFERRAL_REQUESTED', label: 'Referral requested' },
    { value: 'REFERRED', label: 'Referred' },
    { value: 'RECRUITER', label: 'Recruiter reached out' },
  ];

  protected async save(): Promise<void> {
    if (this.saving() || !this.company().trim() || !this.role().trim()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const app = await firstValueFrom(
        this.api.create({
          company: this.company().trim(),
          role: this.role().trim(),
          city: this.city().trim() || undefined,
          source: this.source(),
          referrerName: this.referrerName().trim() || undefined,
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
