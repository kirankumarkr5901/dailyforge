import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { FORGE_AREA_LABELS, ForgeArea, ForgeStat } from '../../../core/forge/forge.types';

/** One bar per life area, scaled to the strongest: the shape matters more than the number. */
@Component({
  selector: 'df-forge-stats',
  templateUrl: './forge-stats.component.html',
  styleUrl: './forge-stats.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgeStatsComponent {
  readonly stats = input.required<ForgeStat[]>();
  readonly focusAreas = input<ForgeArea[]>([]);

  protected readonly bars = computed(() => {
    const stats = this.stats();
    const max = Math.max(1, ...stats.map((s) => s.xp));
    const focus = new Set(this.focusAreas());
    return stats.map((s) => ({
      area: s.area,
      areaClass: `area--${s.area.toLowerCase()}`,
      label: FORGE_AREA_LABELS[s.area],
      xp: s.xp,
      percent: Math.round((s.xp / max) * 100),
      focus: focus.has(s.area),
    }));
  });
}
