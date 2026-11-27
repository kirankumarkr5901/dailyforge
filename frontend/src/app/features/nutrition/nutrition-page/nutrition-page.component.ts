import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { Beef, ChevronDown, LucideAngularModule, Plus, Trash2 } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { ProteinApi } from '../../../core/nutrition/protein.api';
import { Meal, ProteinDay, ProteinDayWrite, ProteinEntry, ProteinFood } from '../../../core/nutrition/protein.types';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { BarcodeScannerComponent } from '../barcode-scanner/barcode-scanner.component';
import { MealPhotoComponent } from '../meal-photo/meal-photo.component';
import { PointsStore } from '../../../core/points/points.store';
import { SyncStore } from '../../../core/sync/sync.store';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * The nutrition meter: tap what you eat, and see protein climb toward its target,
 * calories against the day's budget, and fat against its target. Reaching the protein
 * target pays points once a day; a day that ends within the calorie budget pays once it
 * is over. The targets and the food list live in Settings.
 */
/** Whether the food list on Nutrition is unfolded. Folded until opened the first time. */
const FOODS_OPEN_KEY = 'dailyforge.nutrition-foods-open';

function readFoodsOpen(): boolean {
  try {
    return localStorage.getItem(FOODS_OPEN_KEY) === '1';
  } catch {
    return false;
  }
}

@Component({
  selector: 'df-nutrition-page',
  imports: [
    FormsModule,
    RouterLink,
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfEmptyStateComponent,
    DfIconButtonComponent,
    DfInputComponent,
    DfSkeletonComponent,
    DfStepperInputComponent,
    DfSheetComponent,
    BarcodeScannerComponent,
    MealPhotoComponent,
  ],
  templateUrl: './nutrition-page.component.html',
  styleUrl: './nutrition-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NutritionPageComponent {
  private readonly api = inject(ProteinApi);
  private readonly toasts = inject(ToastService);
  private readonly points = inject(PointsStore);
  private readonly sync = inject(SyncStore);
  protected readonly session = inject(SessionStore);
  private readonly authSheet = inject(AuthSheetService);

  protected readonly foodIcon = Beef;
  protected readonly plusIcon = Plus;
  protected readonly deleteIcon = Trash2;
  protected readonly chevronIcon = ChevronDown;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly day = signal<ProteinDay | null>(null);
  /** One write at a time, so a quick double tap cannot log the same food twice. */
  protected readonly busy = signal(false);

  protected readonly meals = signal<Meal[]>([]);
  protected readonly scannerOpen = signal(false);
  /** "Snap a meal" shows only when the server has a Gemini key. */
  protected readonly mealPhotoEnabled = signal(false);
  protected readonly mealPhotoOpen = signal(false);

  /** A scanned product was added: same bookkeeping as any other write. */
  protected onScanned(result: ProteinDayWrite): void {
    this.day.set(result.day);
    this.points.applyEnvelope(result.points);
    if (result.points.delta > 0) {
      this.toasts.show(`Protein target hit! +${result.points.delta} pts`, { tone: 'earned', heatStep: 1 });
    } else {
      this.toasts.show('Added.');
    }
  }

  protected reload(): void {
    void this.load();
  }

  /** "Save as a meal": which of today's entries go in, and its name. */
  protected readonly mealSheetOpen = signal(false);
  protected readonly mealName = signal('');
  protected readonly mealPicks = signal<ReadonlySet<string>>(new Set());

  protected readonly search = signal('');
  /** The one-tap food list folds away (owner request); the choice is remembered. */
  protected readonly foodsOpen = signal(readFoodsOpen());

  protected toggleFoods(): void {
    const open = !this.foodsOpen();
    this.foodsOpen.set(open);
    try {
      localStorage.setItem(FOODS_OPEN_KEY, open ? '1' : '0');
    } catch {
      // Storage blocked: it still folds for this visit.
    }
  }

  protected readonly visibleFoods = computed(() => {
    const foods = this.day()?.foods ?? [];
    const q = this.search().trim().toLowerCase();
    return q ? foods.filter((f) => f.name.toLowerCase().includes(q)) : foods;
  });

  protected readonly otherName = signal('');
  protected readonly otherGrams = signal(0);
  protected readonly otherKcal = signal(0);
  protected readonly otherFat = signal(0);
  protected readonly otherCarbs = signal(0);
  protected readonly otherFiber = signal(0);
  protected readonly otherSugar = signal(0);

  /** Calories against the budget: what is left, or how far over. */
  protected readonly calories = computed(() => {
    const d = this.day();
    if (!d) return null;
    const left = d.calorieBudget - d.totalKcal;
    return {
      percent: d.calorieBudget > 0 ? Math.min(100, (d.totalKcal / d.calorieBudget) * 100) : 0,
      over: left < 0,
      label: left >= 0 ? `${left.toLocaleString()} kcal left` : `${(-left).toLocaleString()} kcal over`,
    };
  });

  /**
   * Fat, carbs, fiber and sugar, drawn the same way. Fat, carbs and fiber are targets;
   * sugar is a limit, so going over it turns the bar to the warning tone and says so.
   */
  protected readonly macroRows = computed(() => {
    const d = this.day();
    if (!d) return [];
    const row = (name: string, total: number, target: number, limit = false) => ({
      name,
      total,
      target,
      limit,
      over: limit && total > target,
      percent: target > 0 ? Math.min(100, (total / target) * 100) : 0,
    });
    return [
      row('Fat', d.totalFatG, d.fatTargetG),
      row('Carbs', d.totalCarbsG, d.carbsTargetG),
      row('Fiber', d.totalFiberG, d.fiberTargetG),
      row('Sugar', d.totalSugarG, d.sugarLimitG, true),
    ];
  });

  protected foodLabel(f: { grams: number; kcal: number; fatG: number; carbsG: number; fiberG: number; sugarG: number }): string {
    const parts: string[] = [];
    if (f.grams) parts.push(`${f.grams} g protein`);
    if (f.kcal) parts.push(`${f.kcal} kcal`);
    if (f.carbsG) parts.push(`${f.carbsG} g carbs`);
    if (f.fatG) parts.push(`${f.fatG} g fat`);
    if (f.fiberG) parts.push(`${f.fiberG} g fiber`);
    if (f.sugarG) parts.push(`${f.sugarG} g sugar`);
    return parts.join(' · ');
  }

  protected readonly percent = computed(() => {
    const d = this.day();
    return d && d.targetG > 0 ? Math.min(100, (d.totalG / d.targetG) * 100) : 0;
  });

  protected readonly reached = computed(() => {
    const d = this.day();
    return !!d && d.totalG >= d.targetG;
  });

  protected readonly message = computed(() => {
    const d = this.day();
    if (!d) return '';
    const left = d.targetG - d.totalG;
    if (d.totalG === 0) return 'Nothing yet today. Eggs or curd at breakfast is an easy start.';
    if (left <= 0) return 'Target hit for today.';
    if (d.totalG < d.targetG / 2) return 'Good start. Make sure lunch has a solid protein source.';
    if (left > 20) return `${left} g to go. A whey scoop or paneer will close the gap.`;
    return `Almost there, just ${left} g left.`;
  });

  constructor() {
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.load();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.day.set(null);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });
    this.sync.refreshes.pipe(takeUntilDestroyed()).subscribe(() => void this.load());
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.day.set(await firstValueFrom(this.api.day()));
      void this.loadMeals();
      void this.loadMealPhotoStatus();
    } catch {
      this.error.set('Could not load your protein for today. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  private async loadMealPhotoStatus(): Promise<void> {
    try {
      this.mealPhotoEnabled.set((await firstValueFrom(this.api.mealPhotoStatus())).enabled);
    } catch {
      // Without it the button just stays hidden.
    }
  }

  /** Dishes from a meal photo were added. */
  protected onMealPhoto(result: ProteinDayWrite): void {
    this.onScanned(result);
  }

  private async loadMeals(): Promise<void> {
    try {
      this.meals.set(await firstValueFrom(this.api.meals()));
    } catch {
      // Meals are a shortcut; the foods below still work without them.
    }
  }

  protected addWater(deltaMl: number): Promise<boolean> {
    return this.write(() => this.api.addWater(this.day()!.date, deltaMl));
  }

  protected litres(ml: number): string {
    return (Math.round(ml / 100) / 10).toString();
  }

  protected logMeal(meal: Meal): Promise<boolean> {
    return this.write(() => this.api.logMeal(meal.id, this.day()!.date));
  }

  protected openMealSheet(): void {
    const entries = this.day()?.entries ?? [];
    this.mealName.set('');
    this.mealPicks.set(new Set(entries.map((e) => e.id)));
    this.mealSheetOpen.set(true);
  }

  protected toggleMealPick(id: string): void {
    this.mealPicks.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  protected async saveMeal(): Promise<void> {
    const name = this.mealName().trim();
    const items = (this.day()?.entries ?? [])
      .filter((e) => this.mealPicks().has(e.id))
      .map((e) => ({
        foodName: e.foodName,
        grams: e.grams,
        kcal: e.kcal,
        fatG: e.fatG,
        carbsG: e.carbsG,
        fiberG: e.fiberG,
        sugarG: e.sugarG,
      }));
    if (!name || items.length === 0) {
      this.toasts.show(!name ? 'Give the meal a name, e.g. "My breakfast".' : 'Pick at least one item.');
      return;
    }
    try {
      await firstValueFrom(this.api.createMeal(name, items));
      this.mealSheetOpen.set(false);
      await this.loadMeals();
      this.toasts.show(`Saved "${name}". Tap it under Meals to log it again.`);
    } catch {
      this.toasts.show('Could not save that meal. Try again.', { tone: 'penalty' });
    }
  }

  protected eat(food: ProteinFood): Promise<boolean> {
    return this.write(() =>
      this.api.log(this.day()!.date, food.name, {
        grams: food.grams,
        kcal: food.kcal,
        fatG: food.fatG,
        carbsG: food.carbsG,
        fiberG: food.fiberG,
        sugarG: food.sugarG,
      }),
    );
  }

  protected async eatOther(): Promise<void> {
    const name = this.otherName().trim() || 'Other';
    const amounts = {
      grams: this.otherGrams(),
      kcal: this.otherKcal(),
      fatG: this.otherFat(),
      carbsG: this.otherCarbs(),
      fiberG: this.otherFiber(),
      sugarG: this.otherSugar(),
    };
    if (!amounts.grams && !amounts.kcal && !amounts.fatG) {
      this.toasts.show('Enter at least one of protein, calories or fat.');
      return;
    }
    if (await this.write(() => this.api.log(this.day()!.date, name, amounts))) {
      this.otherName.set('');
      this.otherGrams.set(0);
      this.otherKcal.set(0);
      this.otherFat.set(0);
      this.otherCarbs.set(0);
      this.otherFiber.set(0);
      this.otherSugar.set(0);
    }
  }

  protected remove(entry: ProteinEntry): Promise<boolean> {
    return this.write(() => this.api.deleteLog(entry.id));
  }

  private async write(call: () => Observable<ProteinDayWrite>): Promise<boolean> {
    if (this.busy() || !this.day()) {
      return false;
    }
    this.busy.set(true);
    try {
      const result = await firstValueFrom(call());
      this.day.set(result.day);
      this.points.applyEnvelope(result.points);
      const delta = result.points.delta;
      if (delta > 0) {
        this.toasts.show(`Target hit! +${delta} pts`, { tone: 'earned', heatStep: 1 });
      } else if (delta < 0) {
        this.toasts.show(`Back under a target. ${delta} pts`, { tone: 'penalty' });
      }
      return true;
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', { tone: 'penalty' });
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }
}
