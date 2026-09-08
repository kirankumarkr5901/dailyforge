import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type DfButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type DfButtonSize = 'md' | 'lg';

/**
 * The button.
 *
 * Note what the primary variant is NOT: warm. In this system the loudest control is
 * cold steel, because heat is reserved for quantities the user earned. A primary button
 * is an instruction, not a reward.
 *
 * Attribute selector, so it stays a real <button> or <a> with native semantics,
 * keyboard behaviour, and form participation.
 */
@Component({
  selector: 'button[dfButton], a[dfButton]',
  templateUrl: './df-button.component.html',
  styleUrl: './df-button.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'df-button',
    '[class]': 'hostClasses()',
    '[attr.aria-busy]': 'loading() ? "true" : null',
    '[attr.data-loading]': 'loading() ? "" : null',
  },
})
export class DfButtonComponent {
  readonly variant = input<DfButtonVariant>('secondary');
  readonly size = input<DfButtonSize>('md');
  readonly loading = input(false);
  readonly fullWidth = input(false);

  protected readonly hostClasses = computed(() => {
    const classes = ['df-button', `df-button--${this.variant()}`, `df-button--${this.size()}`];
    if (this.fullWidth()) {
      classes.push('df-button--full');
    }
    return classes.join(' ');
  });
}
