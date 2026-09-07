import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Gift, LucideAngularModule, Plus, Trash2 } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { PointsStore } from '../../../core/points/points.store';
import { RewardApi } from '../../../core/reward/reward.api';
import { Reward, RewardTier } from '../../../core/reward/reward.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { DfScorePillComponent } from '../../../shared/ui/df-score-pill/df-score-pill.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { RewardFormSheetComponent } from '../reward-form-sheet/reward-form-sheet.component';

/**
 * Rewards (spec §8.9) — the spend side of the loop the plan's own first line promises.
 * The redeem button's disabled reason ("Costs 500. You have 340.") reads the score
 * fetched from the server; it never decides on its own whether a redemption is allowed
 * — the backend re-checks the same thing and is the only answer that actually counts.
 */
@Component({
  selector: 'df-rewards-page',
  imports: [
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfEmptyStateComponent,
    DfIconButtonComponent,
    DfScorePillComponent,
    DfSkeletonComponent,
    RewardFormSheetComponent,
  ],
  templateUrl: './rewards-page.component.html',
  styleUrl: './rewards-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RewardsPageComponent {
  private readonly api = inject(RewardApi);
  private readonly toasts = inject(ToastService);

  protected readonly points = inject(PointsStore);

  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);

  protected readonly plusIcon = Plus;
  protected readonly giftIcon = Gift;
  protected readonly deleteIcon = Trash2;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly rewards = signal<Reward[]>([]);
  protected readonly formOpen = signal(false);
  protected readonly redeemingId = signal<string | null>(null);
  /** null means "every tier" — the default, unfiltered view. */
  protected readonly tierFilter = signal<RewardTier | null>(null);

  private static readonly TIER_ORDER: readonly RewardTier[] = ['MICRO', 'WEEKLY', 'MONTHLY'];
  private static readonly TIER_LABELS: Record<RewardTier, string> = {
    MICRO: 'Micro — daily',
    WEEKLY: 'Weekly',
    MONTHLY: 'Monthly',
  };

  protected readonly tierFilterOptions = RewardsPageComponent.TIER_ORDER.map((tier) => ({
    tier,
    label: RewardsPageComponent.TIER_LABELS[tier],
  }));

  /** Three sections, owner feedback's own order — a small daily treat first, a monthly
   * splurge last. A tier with nothing in it is skipped rather than shown empty. The
   * clickable tier filter above narrows this to one tier at a time. */
  protected readonly tierSections = computed<{ tier: RewardTier; label: string; rewards: Reward[] }[]>(() => {
    const all = this.rewards();
    const filter = this.tierFilter();
    const order = filter ? [filter] : RewardsPageComponent.TIER_ORDER;
    return order
      .map((tier) => ({
        tier,
        label: RewardsPageComponent.TIER_LABELS[tier],
        rewards: all.filter((r) => r.tier === tier),
      }))
      .filter((section) => section.rewards.length > 0);
  });

  protected setTierFilter(tier: RewardTier | null): void {
    this.tierFilter.set(tier);
  }

  constructor() {
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.loadAll();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.rewards.set([]);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });
  }

  private async loadAll(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [rewards] = await Promise.all([firstValueFrom(this.api.list()), this.points.refresh()]);
      this.rewards.set(rewards);
    } catch {
      this.error.set('Could not load your rewards. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  private async refresh(): Promise<void> {
    this.rewards.set(await firstValueFrom(this.api.list()));
  }

  protected openForm(): void {
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.formOpen.set(false);
  }

  protected async onCreated(): Promise<void> {
    this.closeForm();
    await this.refresh();
    this.toasts.show('Reward created.');
  }

  protected canAfford(reward: Reward): boolean {
    return this.points.total() >= reward.cost && !this.isOutOfStock(reward);
  }

  protected isOutOfStock(reward: Reward): boolean {
    return reward.stock !== null && reward.stock <= 0;
  }

  protected redeemReason(reward: Reward): string {
    if (this.isOutOfStock(reward)) {
      return 'Out of stock';
    }
    return `Costs ${reward.cost}. You have ${this.points.total()}.`;
  }

  protected async redeem(reward: Reward): Promise<void> {
    if (this.redeemingId()) {
      return;
    }
    this.redeemingId.set(reward.id);
    try {
      const response = await firstValueFrom(this.api.redeem(reward.id));
      this.points.applyEnvelope(response.points);
      await this.refresh();
      const redemptionId = response.redemptionId;
      this.toasts.show(`Redeemed ${reward.name}. ${response.points.delta} pts`, {
        tone: 'penalty',
        actionLabel: 'Undo',
        action: () => void this.undoRedeem(redemptionId),
      });
    } catch (error) {
      this.toasts.show(this.messageFor(error), { tone: 'penalty' });
    } finally {
      this.redeemingId.set(null);
    }
  }

  private async undoRedeem(redemptionId: string): Promise<void> {
    try {
      const response = await firstValueFrom(this.api.refund(redemptionId));
      this.points.applyEnvelope(response.points);
      await this.refresh();
      this.toasts.show('Redemption refunded.');
    } catch {
      this.toasts.show('Could not refund that. Try again.', { tone: 'penalty' });
    }
  }

  protected async archive(reward: Reward): Promise<void> {
    try {
      await firstValueFrom(this.api.archive(reward.id));
      await this.refresh();
      this.toasts.show('Reward removed.');
    } catch {
      this.toasts.show('Could not remove that reward. Try again.', { tone: 'penalty' });
    }
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  private messageFor(error: unknown): string {
    const body = (error as { error?: { message?: string } })?.error;
    return body?.message ?? 'Could not redeem that. Try again.';
  }
}
