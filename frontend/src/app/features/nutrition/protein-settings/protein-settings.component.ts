import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ChevronDown, LucideAngularModule, Plus, SquarePen, Trash2 } from 'lucide-angular';

import { SessionStore } from '../../../core/auth/session.store';
import { ProteinApi } from '../../../core/nutrition/protein.api';
import { ProteinFood } from '../../../core/nutrition/protein.types';
import { PointsStore } from '../../../core/points/points.store';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEditingNoteComponent } from '../../../shared/ui/df-editing-note/df-editing-note.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * The protein section of Settings: the daily target, and the one-tap food list.
 * The target can always be changed. The food list is structure, so adding, changing
 * and removing foods waits for "Enable edit", the same as activity types.
 */
@Component({
  selector: 'df-protein-settings',
  imports: [
    FormsModule,
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfEditingNoteComponent,
    DfIconButtonComponent,
    DfInputComponent,
    DfStepperInputComponent,
  ],
  templateUrl: './protein-settings.component.html',
  styleUrl: './protein-settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProteinSettingsComponent {
  private readonly api = inject(ProteinApi);
  private readonly toasts = inject(ToastService);
  private readonly points = inject(PointsStore);
  protected readonly session = inject(SessionStore);

  protected readonly plusIcon = Plus;
  protected readonly chevronIcon = ChevronDown;
  protected readonly editIcon = SquarePen;
  protected readonly deleteIcon = Trash2;

  protected readonly loaded = signal(false);
  protected readonly savedTarget = signal(140);
  protected readonly targetDraft = signal(140);
  protected readonly foods = signal<ProteinFood[]>([]);
  /** The list is long; it stays folded until asked for. */
  protected readonly foodsOpen = signal(false);

  protected readonly newName = signal('');
  protected readonly newGrams = signal(10);

  protected readonly editingId = signal<string | null>(null);
  protected readonly editName = signal('');
  protected readonly editGrams = signal(10);

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const day = await firstValueFrom(this.api.day());
      this.savedTarget.set(day.targetG);
      this.targetDraft.set(day.targetG);
      this.foods.set(day.foods);
      this.loaded.set(true);
    } catch {
      this.toasts.show('Could not load your protein settings.', { tone: 'penalty' });
    }
  }

  protected async saveTarget(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.setTarget(this.targetDraft()));
      this.savedTarget.set(result.day.targetG);
      this.points.applyEnvelope(result.points);
      this.toasts.show(`Protein target set to ${result.day.targetG} g.`);
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', { tone: 'penalty' });
    }
  }

  protected async addFood(): Promise<void> {
    const name = this.newName().trim();
    if (!name) {
      this.toasts.show('Give the food a name first.');
      return;
    }
    try {
      const food = await firstValueFrom(this.api.addFood(name, this.newGrams()));
      this.foods.update((list) => [...list, food]);
      this.newName.set('');
    } catch {
      this.toasts.show('Could not add that food. Try again.', { tone: 'penalty' });
    }
  }

  protected startEdit(food: ProteinFood): void {
    this.editingId.set(food.id);
    this.editName.set(food.name);
    this.editGrams.set(food.grams);
  }

  protected async saveEdit(): Promise<void> {
    const id = this.editingId();
    if (!id) return;
    try {
      const food = await firstValueFrom(this.api.updateFood(id, { name: this.editName().trim(), grams: this.editGrams() }));
      this.foods.update((list) => list.map((f) => (f.id === id ? food : f)));
      this.editingId.set(null);
    } catch {
      this.toasts.show('That did not save. Try again.', { tone: 'penalty' });
    }
  }

  protected async removeFood(food: ProteinFood): Promise<void> {
    try {
      await firstValueFrom(this.api.deleteFood(food.id));
      this.foods.update((list) => list.filter((f) => f.id !== food.id));
    } catch {
      this.toasts.show('Could not remove that food. Try again.', { tone: 'penalty' });
    }
  }
}
