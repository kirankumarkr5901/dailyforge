import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LucideAngularModule, LucideIconData } from 'lucide-angular';

export type DfIconButtonVariant = 'ghost' | 'outline' | 'solid';

/**
 * An icon-only button. `label` is required rather than optional: an icon button without
 * an accessible name is a button nobody using a screen reader can identify, and making
 * the input required means that cannot be forgotten.
 */
@Component({
  selector: 'button[dfIconButton]',
  imports: [LucideAngularModule],
  templateUrl: './df-icon-button.component.html',
  styleUrl: './df-icon-button.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': 'hostClasses()',
    '[attr.aria-label]': 'label()',
    '[attr.title]': 'label()',
  },
})
export class DfIconButtonComponent {
  /** Icon data from lucide-angular, e.g. `Plus`. */
  readonly icon = input.required<LucideIconData>();
  readonly label = input.required<string>();
  readonly variant = input<DfIconButtonVariant>('ghost');
  readonly size = input(20);

  protected readonly hostClasses = computed(
    () => `df-icon-button df-icon-button--${this.variant()}`,
  );
}
