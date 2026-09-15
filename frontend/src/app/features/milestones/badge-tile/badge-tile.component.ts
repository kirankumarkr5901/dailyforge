import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

import { Badge } from '../../../core/milestone/badge.types';
import { badgeIcon, badgeProgressLabel, badgeProgressPercent } from '../badge-display';

/**
 * One badge in the grid: what it is, how far along, and whether it wants anything.
 *
 * The whole tile is the button. A badge has exactly one thing to say beyond its
 * summary — what it takes to earn it — and hiding that behind a separate info affordance
 * would make the common case (tapping to find out) feel like a mistake.
 */
@Component({
  selector: 'df-badge-tile',
  imports: [LucideAngularModule],
  templateUrl: './badge-tile.component.html',
  styleUrl: './badge-tile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BadgeTileComponent {
  readonly badge = input.required<Badge>();

  readonly opened = output<Badge>();

  protected readonly icon = computed(() => badgeIcon(this.badge().icon));
  protected readonly progressLabel = computed(() => badgeProgressLabel(this.badge()));
  protected readonly percent = computed(() => badgeProgressPercent(this.badge()));

  /** Three states, and only one of them is an instruction. */
  protected readonly state = computed<'claimed' | 'claimable' | 'locked'>(() => {
    const badge = this.badge();
    if (badge.claimed) {
      return 'claimed';
    }
    return badge.claimable ? 'claimable' : 'locked';
  });
}
