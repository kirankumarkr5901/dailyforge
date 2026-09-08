import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type DfCardElevation = 'flat' | 'raised';

/**
 * A card is a 1px rule and a padded box. It is flat by default and has no shadow,
 * because in this system a shadow means "floating above the page", and a card that sits
 * in the page is not floating. Only sheets, popovers and the score pill float.
 */
@Component({
  selector: 'df-card',
  templateUrl: './df-card.component.html',
  styleUrl: './df-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'hostClasses()' },
})
export class DfCardComponent {
  readonly elevation = input<DfCardElevation>('flat');
  /** Removes the internal padding for cards whose content manages its own edges. */
  readonly flush = input(false);

  protected readonly hostClasses = computed(() => {
    const classes = ['df-card', `df-card--${this.elevation()}`];
    if (this.flush()) {
      classes.push('df-card--flush');
    }
    return classes.join(' ');
  });
}
