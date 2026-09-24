import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { AuthApi } from '../../../core/auth/auth.api';
import { SessionStore } from '../../../core/auth/session.store';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';

/**
 * A one-time prompt shown right after someone's first habit is created (spec §5.4's
 * commitment bonus needs an amount before it can ever fire, and nobody has set one yet
 * at that point). Skipping leaves it at zero, which the reconciliation calculator
 * already treats as "no bonus configured" — so skipping is a real, safe choice.
 */
@Component({
  selector: 'df-commitment-bonus-sheet',
  imports: [FormsModule, DfButtonComponent, DfSheetComponent, DfStepperInputComponent],
  templateUrl: './commitment-bonus-sheet.component.html',
  styleUrl: './commitment-bonus-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommitmentBonusSheetComponent {
  private readonly api = inject(AuthApi);
  private readonly session = inject(SessionStore);

  readonly open = input.required<boolean>();
  readonly closed = output<void>();

  protected readonly amount = signal(50);
  protected readonly saving = signal(false);

  protected close(): void {
    this.closed.emit();
  }

  protected async skip(): Promise<void> {
    this.closed.emit();
  }

  protected async save(): Promise<void> {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    try {
      const settings = await firstValueFrom(
        this.api.updateSettings({ commitmentBonus: this.amount() }, this.session.user()?.settings.version),
      );
      const user = this.session.user();
      if (user) {
        this.session.patchUser({ ...user, settings });
      }
    } finally {
      this.saving.set(false);
      this.closed.emit();
    }
  }
}
