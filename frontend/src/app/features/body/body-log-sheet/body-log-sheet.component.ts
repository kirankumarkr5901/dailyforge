import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ApiError } from '../../../core/api/api.types';
import { BodyApi } from '../../../core/body/body.api';
import { BodyMetric } from '../../../core/body/body.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';

/** Weight (and optional body fat), with a date — height comes from Settings unless overridden here (spec §8.8). */
@Component({
  selector: 'df-body-log-sheet',
  imports: [FormsModule, DfButtonComponent, DfInputComponent, DfSheetComponent, DfStepperInputComponent],
  templateUrl: './body-log-sheet.component.html',
  styleUrl: './body-log-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BodyLogSheetComponent {
  private readonly api = inject(BodyApi);

  readonly open = input.required<boolean>();
  readonly date = input<LogicalDate | null>(null);

  readonly closed = output<void>();
  readonly saved = output<BodyMetric>();

  protected readonly weightKg = signal(70);
  protected readonly heightCm = signal(0);
  protected readonly bodyFatPct = signal(0);
  protected readonly note = signal('');
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async save(): Promise<void> {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const metric = await firstValueFrom(
        this.api.log({
          date: this.date()!,
          weightKg: this.weightKg(),
          heightCm: this.heightCm() > 0 ? this.heightCm() : undefined,
          bodyFatPct: this.bodyFatPct() > 0 ? this.bodyFatPct() : undefined,
          note: this.note().trim() || undefined,
        }),
      );
      this.saved.emit(metric);
      this.note.set('');
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
      return body?.message ?? 'That did not save. Check your connection and try again.';
    }
    return 'That did not save. Check your connection and try again.';
  }
}
