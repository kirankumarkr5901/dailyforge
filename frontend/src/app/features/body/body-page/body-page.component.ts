import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ArrowDown, ArrowUp, LucideAngularModule, Minus, Plus, Scale, Trash2 } from 'lucide-angular';

import { AuthApi } from '../../../core/auth/auth.api';
import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { BodyApi } from '../../../core/body/body.api';
import { BmiBand, BodyMetric, BodySummary } from '../../../core/body/body.types';
import { LogicalDate, formatLong } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { BodyLogSheetComponent } from '../body-log-sheet/body-log-sheet.component';

const BAND_LABELS: Record<BmiBand, string> = {
  UNDERWEIGHT: 'Underweight',
  NORMAL: 'Normal',
  OVERWEIGHT: 'Overweight',
  OBESE: 'Obese',
};

/**
 * Body metrics (spec §8.8). No points anywhere here. Deltas show direction with a
 * plain arrow, never colour — the spec is explicit that a weight gain is not a failure
 * (a bulking user is not failing), so nothing here moralises.
 */
@Component({
  selector: 'df-body-page',
  imports: [
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfEmptyStateComponent,
    DfIconButtonComponent,
    DfSkeletonComponent,
    BodyLogSheetComponent,
  ],
  templateUrl: './body-page.component.html',
  styleUrl: './body-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BodyPageComponent {
  private readonly api = inject(BodyApi);
  private readonly authApi = inject(AuthApi);
  private readonly toasts = inject(ToastService);

  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);

  protected readonly plusIcon = Plus;
  protected readonly scaleIcon = Scale;
  protected readonly upIcon = ArrowUp;
  protected readonly downIcon = ArrowDown;
  protected readonly flatIcon = Minus;
  protected readonly deleteIcon = Trash2;
  protected readonly bandLabels = BAND_LABELS;
  protected readonly bandOrder: readonly BmiBand[] = ['UNDERWEIGHT', 'NORMAL', 'OVERWEIGHT', 'OBESE'];
  protected readonly formatLong = formatLong;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<BodySummary | null>(null);
  protected readonly todayDate = signal<LogicalDate | null>(null);
  protected readonly logSheetOpen = signal(false);

  constructor() {
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.loadAll();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.summary.set(null);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });
  }

  private async loadAll(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [today, summary] = await Promise.all([firstValueFrom(this.authApi.today()), firstValueFrom(this.api.summary())]);
      this.todayDate.set(today.date);
      this.summary.set(summary);
    } catch {
      this.error.set('Could not load your body metrics. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  private async refresh(): Promise<void> {
    this.summary.set(await firstValueFrom(this.api.summary()));
  }

  protected openLogSheet(): void {
    this.logSheetOpen.set(true);
  }

  protected closeLogSheet(): void {
    this.logSheetOpen.set(false);
  }

  protected async onLogged(): Promise<void> {
    this.closeLogSheet();
    await this.refresh();
    this.toasts.show('Weight logged.');
  }

  protected async deleteEntry(entry: BodyMetric): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(entry.id));
      await this.refresh();
      this.toasts.show('Entry removed.');
    } catch {
      this.toasts.show('Could not remove that entry. Try again.', { tone: 'penalty' });
    }
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  protected deltaIcon(value: number | null) {
    if (value === null || value === 0) return this.flatIcon;
    return value > 0 ? this.upIcon : this.downIcon;
  }

  protected deltaLabel(value: number | null): string {
    if (value === null) return 'no earlier log';
    const sign = value > 0 ? '+' : '';
    return `${sign}${value.toFixed(1)} kg`;
  }
}
