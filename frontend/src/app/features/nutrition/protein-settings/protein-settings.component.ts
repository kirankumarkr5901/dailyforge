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
  protected readonly savedBudget = signal(2250);
  protected readonly budgetDraft = signal(2250);
  protected readonly savedFat = signal(70);
  protected readonly fatDraft = signal(70);
  protected readonly foods = signal<ProteinFood[]>([]);
  /** The list is long; it stays folded until asked for. */
  protected readonly foodsOpen = signal(false);

  protected readonly newName = signal('');
  protected readonly newGrams = signal(0);
  protected readonly newKcal = signal(0);
  protected readonly newFat = signal(0);

  protected readonly editingId = signal<string | null>(null);
  protected readonly editName = signal('');
  protected readonly editGrams = signal(0);
  protected readonly editKcal = signal(0);
  protected readonly editFat = signal(0);

  protected targetsChanged(): boolean {
    return (
      this.targetDraft() !== this.savedTarget() ||
      this.budgetDraft() !== this.savedBudget() ||
      this.fatDraft() !== this.savedFat()
    );
  }

  protected foodLabel(f: ProteinFood): string {
    return `${f.grams} g protein · ${f.kcal} kcal · ${f.fatG} g fat`;
  }

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const day = await firstValueFrom(this.api.day());
      this.applyTargets(day.targetG, day.calorieBudget, day.fatTargetG);
      this.foods.set(day.foods);
      this.loaded.set(true);
    } catch {
      this.toasts.show('Could not load your protein settings.', { tone: 'penalty' });
    }
  }

  private applyTargets(protein: number, budget: number, fat: number): void {
    this.savedTarget.set(protein);
    this.targetDraft.set(protein);
    this.savedBudget.set(budget);
    this.budgetDraft.set(budget);
    this.savedFat.set(fat);
    this.fatDraft.set(fat);
  }

  protected async saveTargets(): Promise<void> {
    try {
      const result = await firstValueFrom(
        this.api.setTargets({ targetG: this.targetDraft(), calorieBudget: this.budgetDraft(), fatTargetG: this.fatDraft() }),
      );
      this.applyTargets(result.day.targetG, result.day.calorieBudget, result.day.fatTargetG);
      this.points.applyEnvelope(result.points);
      this.toasts.show('Targets saved.');
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
      if (!this.newGrams() && !this.newKcal() && !this.newFat()) {
        this.toasts.show('Enter at least one of protein, calories or fat.');
        return;
      }
      const food = await firstValueFrom(
        this.api.addFood(name, { grams: this.newGrams(), kcal: this.newKcal(), fatG: this.newFat() }),
      );
      this.foods.update((list) => [...list, food]);
      this.newName.set('');
      this.newGrams.set(0);
      this.newKcal.set(0);
      this.newFat.set(0);
    } catch {
      this.toasts.show('Could not add that food. Try again.', { tone: 'penalty' });
    }
  }

  protected startEdit(food: ProteinFood): void {
    this.editingId.set(food.id);
    this.editName.set(food.name);
    this.editGrams.set(food.grams);
    this.editKcal.set(food.kcal);
    this.editFat.set(food.fatG);
  }

  protected async saveEdit(): Promise<void> {
    const id = this.editingId();
    if (!id) return;
    try {
      const food = await firstValueFrom(
        this.api.updateFood(id, {
          name: this.editName().trim(),
          grams: this.editGrams(),
          kcal: this.editKcal(),
          fatG: this.editFat(),
        }),
      );
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
