import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type DfChipTone = 'neutral' | 'equipment' | 'done' | 'penalty' | 'earned';
export type DfEquipment = 'barbell' | 'dumbbell' | 'machine' | 'cable' | 'bodyweight' | 'cardio';

/**
 * A small label. Tones map to meaning, never to decoration.
 *
 * `earned` is the only warm tone and is reserved for a quantity the user earned — a
 * points value, a bonus, a PR. Everything else is cold.
 */
@Component({
  selector: 'df-chip',
  templateUrl: './df-chip.component.html',
  styleUrl: './df-chip.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': 'hostClasses()',
    '[attr.data-heat]': "tone() === 'earned' ? step() : null",
  },
})
export class DfChipComponent {
  readonly tone = input<DfChipTone>('neutral');
  /** Only read when tone is `equipment`. */
  readonly equipment = input<DfEquipment>('barbell');
  /**
   * Only read when tone is `earned`. The step on the heat ramp, from the magnitude of
   * the quantity being shown, so a small gain and a milestone do not arrive at the same
   * temperature.
   */
  readonly step = input<1 | 2 | 3 | 4>(2);

  protected readonly hostClasses = computed(() => {
    const classes = ['df-chip', `df-chip--${this.tone()}`];
    if (this.tone() === 'equipment') {
      classes.push(`df-chip--equip-${this.equipment()}`);
    }
    return classes.join(' ');
  });
}
