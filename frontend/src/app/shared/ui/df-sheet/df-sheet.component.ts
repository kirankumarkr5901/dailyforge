import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, effect, inject, input, model } from '@angular/core';
import { DOCUMENT } from '@angular/core';
import { LucideAngularModule, X } from 'lucide-angular';

/**
 * A bottom sheet — the app's primary way of asking for input without leaving the page.
 *
 * Behaviour comes from CDK (`cdkTrapFocus` and its restore), the visuals are ours.
 * Escape closes, the backdrop closes, focus is trapped while open and returned to the
 * trigger on close, and the body cannot scroll behind it.
 *
 * It respects the bottom safe-area inset from day one, so the Capacitor wrap at M9 is
 * not a retrofit.
 */
@Component({
  selector: 'df-sheet',
  imports: [A11yModule, LucideAngularModule],
  templateUrl: './df-sheet.component.html',
  styleUrl: './df-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DfSheetComponent {
  private readonly document = inject(DOCUMENT);

  readonly open = model.required<boolean>();
  readonly heading = input.required<string>();
  /** Set false for a destructive confirmation that must be answered deliberately. */
  readonly dismissible = input(true);

  protected readonly closeIcon = X;

  constructor() {
    effect(() => {
      // Scroll lock. Cleared on close and on destroy by the same expression.
      this.document.body.classList.toggle('df-scroll-locked', this.open());
    });
  }

  protected close(): void {
    if (this.dismissible()) {
      this.open.set(false);
    }
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.close();
    }
  }
}
