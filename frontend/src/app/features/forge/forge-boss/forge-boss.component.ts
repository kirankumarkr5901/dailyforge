import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { FORGE_AREA_LABELS, ForgeBoss } from '../../../core/forge/forge.types';

/**
 * This week's boss on the Forge screen: who it is, how far the fight has got (quests
 * done in its area this week) and what beating it pays. An escaped boss comes back the
 * next week stronger, and says so.
 */
@Component({
  selector: 'df-forge-boss',
  templateUrl: './forge-boss.component.html',
  styleUrl: './forge-boss.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': "'area--' + boss().area.toLowerCase()" },
})
export class ForgeBossComponent {
  readonly boss = input.required<ForgeBoss>();

  protected readonly areaLabel = computed(() => FORGE_AREA_LABELS[this.boss().area]);
  protected readonly percent = computed(() => {
    const b = this.boss();
    return b.questsTotal > 0 ? Math.round((b.questsDone / b.questsTotal) * 100) : 0;
  });
  protected readonly defeated = computed(() => this.boss().status === 'DEFEATED');
}
