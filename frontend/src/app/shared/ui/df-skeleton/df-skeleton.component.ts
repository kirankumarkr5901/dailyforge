import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * A loading placeholder. Deliberately plain: it holds space at the right size so the
 * layout does not jump when content arrives.
 *
 * It is `aria-hidden` and paired with a live region elsewhere, because a screen reader
 * should hear "loading" once, not read a wall of empty boxes.
 */
@Component({
  selector: 'df-skeleton',
  templateUrl: './df-skeleton.component.html',
  styleUrl: './df-skeleton.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'df-skeleton',
    'aria-hidden': 'true',
    '[style.width]': 'width()',
    '[style.height]': 'height()',
    '[style.border-radius]': 'radius()',
  },
})
export class DfSkeletonComponent {
  readonly width = input('100%');
  readonly height = input('1rem');
  readonly radius = input('var(--radius-control)');
}
