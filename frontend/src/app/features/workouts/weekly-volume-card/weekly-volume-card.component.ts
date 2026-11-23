import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { WeeklyVolume } from '../../../core/workouts/workouts.types';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';

/**
 * Hard sets per muscle this week, one bar per muscle, against the usual 10–20 target
 * band. A single series, so no legend: the title names it, and each row shows its
 * count as text so the numbers never depend on reading the bar length.
 */
@Component({
  selector: 'df-weekly-volume-card',
  imports: [DfCardComponent],
  templateUrl: './weekly-volume-card.component.html',
  styleUrl: './weekly-volume-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WeeklyVolumeCardComponent {
  readonly volume = input.required<WeeklyVolume>();

  /** The axis runs a little past the band so a muscle above 20 still has room. */
  protected readonly max = computed(() => {
    const v = this.volume();
    return Math.max(v.targetHigh + 5, ...v.muscles.map((m) => m.sets));
  });

  protected readonly band = computed(() => {
    const v = this.volume();
    return { left: (v.targetLow / this.max()) * 100, width: ((v.targetHigh - v.targetLow) / this.max()) * 100 };
  });

  protected width(sets: number): number {
    return (sets / this.max()) * 100;
  }

  protected status(sets: number): string {
    const v = this.volume();
    if (sets < v.targetLow) return `${sets} sets, below the ${v.targetLow}–${v.targetHigh} target`;
    if (sets > v.targetHigh) return `${sets} sets, above the ${v.targetLow}–${v.targetHigh} target`;
    return `${sets} sets, within the ${v.targetLow}–${v.targetHigh} target`;
  }
}
