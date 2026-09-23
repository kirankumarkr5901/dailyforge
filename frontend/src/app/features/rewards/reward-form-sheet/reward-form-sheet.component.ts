import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ApiError } from '../../../core/api/api.types';
import { RewardApi } from '../../../core/reward/reward.api';
import { Reward } from '../../../core/reward/reward.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { DfSwitchComponent } from '../../../shared/ui/df-switch/df-switch.component';

/** A new reward — something points can be spent on (spec §8.9). */
@Component({
  selector: 'df-reward-form-sheet',
  imports: [FormsModule, DfButtonComponent, DfInputComponent, DfSheetComponent, DfStepperInputComponent, DfSwitchComponent],
  templateUrl: './reward-form-sheet.component.html',
  styleUrl: './reward-form-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RewardFormSheetComponent {
  private readonly api = inject(RewardApi);

  readonly open = input.required<boolean>();

  readonly closed = output<void>();
  readonly saved = output<Reward>();

  protected readonly name = signal('');
  protected readonly cost = signal(100);
  protected readonly isRepeatable = signal(true);
  protected readonly hasStock = signal(false);
  protected readonly stock = signal(1);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async save(): Promise<void> {
    if (this.saving() || !this.name().trim()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const reward = await firstValueFrom(
        this.api.create({
          name: this.name().trim(),
          cost: this.cost(),
          icon: 'gift',
          isRepeatable: this.isRepeatable(),
          stock: this.hasStock() ? this.stock() : undefined,
        }),
      );
      this.saved.emit(reward);
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
    this.cost.set(100);
    this.isRepeatable.set(true);
    this.hasStock.set(false);
    this.stock.set(1);
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      return body?.message ?? 'That did not save. Check your connection and try again.';
    }
    return 'That did not save. Check your connection and try again.';
  }
}
