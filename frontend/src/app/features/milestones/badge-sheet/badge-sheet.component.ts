import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';

import { ApiError } from '../../../core/api/api.types';
import { MilestoneApi } from '../../../core/milestone/milestone.api';
import { Badge } from '../../../core/milestone/badge.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { badgeIcon, badgeProgressLabel, badgeProgressPercent } from '../badge-display';

/**
 * What a badge is, what it takes, and the one action it has.
 *
 * The owner asked that opening a badge say "what kind of badge it is, and what the user
 * should do to earn that badge" — so those two sentences are the body of this sheet,
 * not a tooltip. Both come from the badge row, which means the answer is written once,
 * in the catalogue, rather than inferred from a threshold at render time.
 */
@Component({
  selector: 'df-badge-sheet',
  imports: [LucideAngularModule, DfButtonComponent, DfSheetComponent],
  templateUrl: './badge-sheet.component.html',
  styleUrl: './badge-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BadgeSheetComponent {
  private readonly api = inject(MilestoneApi);

  readonly badge = input<Badge | null>(null);
  /** Which period the badge belongs to, passed back so a claim lands on the right one. */
  readonly anchor = input<LogicalDate | null>(null);

  readonly closed = output<void>();
  readonly claimed = output<Badge>();

  protected readonly claiming = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly icon = computed(() => {
    const badge = this.badge();
    return badge ? badgeIcon(badge.icon) : badgeIcon('');
  });

  protected readonly progressLabel = computed(() => {
    const badge = this.badge();
    return badge ? badgeProgressLabel(badge) : '';
  });

  protected readonly percent = computed(() => {
    const badge = this.badge();
    return badge ? badgeProgressPercent(badge) : 0;
  });

  protected readonly periodLabel = computed(() => {
    const badge = this.badge();
    if (!badge) {
      return '';
    }
    const [year, month] = badge.periodStart.split('-').map(Number);
    if (badge.period === 'YEAR') {
      return String(year);
    }
    return `${new Date(year, month - 1, 1).toLocaleString('en', { month: 'long' })} ${year}`;
  });

  protected async claim(): Promise<void> {
    const badge = this.badge();
    if (!badge || this.claiming() || !badge.claimable) {
      return;
    }
    this.claiming.set(true);
    this.error.set(null);
    try {
      const response = await firstValueFrom(this.api.claimBadge(badge.code, this.anchor() ?? undefined));
      this.claimed.emit(response.badge);
    } catch (error) {
      // The server's own words: it knows whether this was already claimed, or not yet
      // earned, and either answer is more useful than a generic failure.
      this.error.set(this.messageFor(error));
    } finally {
      this.claiming.set(false);
    }
  }

  protected close(): void {
    this.error.set(null);
    this.closed.emit();
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      if (body?.message) {
        return body.message;
      }
    }
    return 'Could not claim that badge. Check your connection and try again.';
  }
}
