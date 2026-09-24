import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ApiError } from '../../../core/api/api.types';
import { RewardApi } from '../../../core/reward/reward.api';
import { Reward, RewardTier } from '../../../core/reward/reward.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSelectComponent, DfSelectOption } from '../../../shared/ui/df-select/df-select.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { DfSwitchComponent } from '../../../shared/ui/df-switch/df-switch.component';

const TIER_OPTIONS: readonly DfSelectOption[] = [
  { value: 'MICRO', label: 'Micro — daily' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
];

/**
 * A reward — something points can be spent on (spec §8.9). Creates one, or edits an
 * existing one when `reward` is set (owner feedback: "Rewards also should be editable"),
 * because the alternative for a mistyped cost was archive-and-retype, which also buried
 * the redemption history under a second reward with the same name.
 */
@Component({
  selector: 'df-reward-form-sheet',
  imports: [
    FormsModule,
    DfButtonComponent,
    DfInputComponent,
    DfSelectComponent,
    DfSheetComponent,
    DfStepperInputComponent,
    DfSwitchComponent,
  ],
  templateUrl: './reward-form-sheet.component.html',
  styleUrl: './reward-form-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RewardFormSheetComponent {
  private readonly api = inject(RewardApi);

  readonly open = input.required<boolean>();
  /** Null creates; set edits that reward in place. */
  readonly reward = input<Reward | null>(null);

  readonly closed = output<void>();
  readonly saved = output<Reward>();

  protected readonly tierOptions = TIER_OPTIONS;
  protected readonly isEditing = computed(() => this.reward() !== null);

  protected readonly name = signal('');
  protected readonly cost = signal(100);
  protected readonly tier = signal<RewardTier>('MICRO');
  protected readonly isRepeatable = signal(true);
  protected readonly hasStock = signal(false);
  protected readonly stock = signal(1);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Opening the sheet loads whatever it was opened for — an existing reward's own
    // values when editing, a clean form when creating.
    effect(() => {
      if (!this.open()) {
        return;
      }
      const existing = this.reward();
      if (existing) {
        this.name.set(existing.name);
        this.cost.set(existing.cost);
        this.tier.set(existing.tier);
        this.isRepeatable.set(existing.isRepeatable);
        this.hasStock.set(existing.stock !== null);
        this.stock.set(existing.stock ?? 1);
        this.error.set(null);
      } else {
        this.reset();
      }
    });
  }

  protected async save(): Promise<void> {
    if (this.saving() || !this.name().trim()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const payload = {
        name: this.name().trim(),
        cost: this.cost(),
        icon: this.reward()?.icon ?? 'gift',
        tier: this.tier(),
        isRepeatable: this.isRepeatable(),
        stock: this.hasStock() ? this.stock() : undefined,
      };
      const existing = this.reward();
      const reward = await firstValueFrom(
        existing ? this.api.update(existing.id, payload, existing.version) : this.api.create(payload),
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
    this.tier.set('MICRO');
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
