import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ApiError } from '../../../core/api/api.types';
import { ActivityApi } from '../../../core/activity/activity.api';
import { ActivityPolarity, ActivityType } from '../../../core/activity/activity.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';

const POLARITY_OPTIONS: readonly DfSelectOption[] = [
  { value: 'POSITIVE', label: 'Positive — earns points' },
  { value: 'NEGATIVE', label: 'Negative — costs points' },
];

/** A new one-off activity type (spec §6 "activity") — a personal positive or negative action worth a fixed point value each time it happens. */
@Component({
  selector: 'df-activity-type-form-sheet',
  imports: [FormsModule, DfButtonComponent, DfInputComponent, DfSelectComponent, DfSheetComponent, DfStepperInputComponent],
  templateUrl: './activity-type-form-sheet.component.html',
  styleUrl: './activity-type-form-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityTypeFormSheetComponent {
  private readonly api = inject(ActivityApi);

  readonly open = input.required<boolean>();

  readonly closed = output<void>();
  readonly saved = output<ActivityType>();

  protected readonly polarityOptions = POLARITY_OPTIONS;

  protected readonly name = signal('');
  protected readonly polarity = signal<ActivityPolarity>('POSITIVE');
  protected readonly points = signal(5);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async save(): Promise<void> {
    if (this.saving() || !this.name().trim()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const type = await firstValueFrom(
        this.api.create({
          name: this.name().trim(),
          polarity: this.polarity(),
          points: this.points(),
          icon: this.polarity() === 'POSITIVE' ? 'sparkles' : 'frown',
        }),
      );
      this.saved.emit(type);
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
    this.name.set('');
    this.polarity.set('POSITIVE');
    this.points.set(5);
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      return body?.message ?? 'That did not save. Check your connection and try again.';
    }
    return 'That did not save. Check your connection and try again.';
  }
}
