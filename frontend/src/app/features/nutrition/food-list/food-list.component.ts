import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChevronDown, LucideAngularModule } from 'lucide-angular';

import { ProteinFood } from '../../../core/nutrition/protein.types';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';

/**
 * A foldable, searchable list of one-tap foods on Nutrition ("Your foods", "Saved
 * foods"). Folded until opened the first time; the phone remembers the choice under
 * `storageKey`.
 */
@Component({
  selector: 'df-food-list',
  imports: [FormsModule, LucideAngularModule, DfInputComponent],
  templateUrl: './food-list.component.html',
  styleUrl: './food-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FoodListComponent {
  readonly heading = input.required<string>();
  readonly foods = input.required<ProteinFood[]>();
  readonly storageKey = input.required<string>();
  /** Shown when the list has no foods at all. */
  readonly emptyText = input('No foods yet.');
  readonly busy = input(false);
  /** A food was tapped: the page logs it. */
  readonly eat = output<ProteinFood>();

  protected readonly chevronIcon = ChevronDown;
  protected readonly search = signal('');
  private readonly openOverride = signal<boolean | null>(null);
  /** Unique per list so aria-controls points at the right panel. */
  protected readonly panelId = `food-list-${Math.random().toString(36).slice(2, 8)}`;

  protected readonly open = computed(() => this.openOverride() ?? this.readOpen());

  protected readonly visible = computed(() => {
    const q = this.search().trim().toLowerCase();
    const foods = this.foods();
    return q ? foods.filter((f) => f.name.toLowerCase().includes(q)) : foods;
  });

  protected toggle(): void {
    const open = !this.open();
    this.openOverride.set(open);
    try {
      localStorage.setItem(this.storageKey(), open ? '1' : '0');
    } catch {
      // Storage blocked: it still folds for this visit.
    }
  }

  private readOpen(): boolean {
    try {
      return localStorage.getItem(this.storageKey()) === '1';
    } catch {
      return false;
    }
  }
}
