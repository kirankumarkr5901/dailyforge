import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { ChevronLeft, ChevronRight, LucideAngularModule } from 'lucide-angular';
import { LogicalDate, compare, formatLong, plusDays } from '../../../core/time/logical-date';

/**
 * Previous / next day, with a "Today" affordance that appears only when it would do
 * something.
 *
 * `today` is passed in rather than computed. The user's today belongs to the user's time
 * zone, which only the server knows (spec §4.2).
 */
@Component({
  selector: 'df-date-stepper',
  imports: [LucideAngularModule],
  templateUrl: './df-date-stepper.component.html',
  styleUrl: './df-date-stepper.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'df-date-stepper' },
})
export class DfDateStepperComponent {
  readonly value = model.required<LogicalDate>();
  readonly today = input.required<LogicalDate>();
  /** Optional bound; the next control disables at it. Habits may log into the future. */
  readonly max = input<LogicalDate | null>(null);

  protected readonly prevIcon = ChevronLeft;
  protected readonly nextIcon = ChevronRight;

  protected readonly label = computed(() => {
    const current = this.value();
    if (current === this.today()) {
      return 'Today';
    }
    if (current === plusDays(this.today(), -1)) {
      return 'Yesterday';
    }
    if (current === plusDays(this.today(), 1)) {
      return 'Tomorrow';
    }
    return formatLong(current);
  });

  /** Always the full date, so a screen reader is never told only "Yesterday". */
  protected readonly fullLabel = computed(() => formatLong(this.value()));

  protected readonly isToday = computed(() => this.value() === this.today());

  protected readonly atMax = computed(() => {
    const limit = this.max();
    return limit !== null && compare(this.value(), limit) >= 0;
  });

  protected step(days: number): void {
    this.value.set(plusDays(this.value(), days));
  }

  protected goToToday(): void {
    this.value.set(this.today());
  }
}
