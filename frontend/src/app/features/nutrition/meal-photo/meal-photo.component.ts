import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ProteinApi } from '../../../core/nutrition/protein.api';
import { MealItem, MealPhotoDish, ProteinDayWrite } from '../../../core/nutrition/protein.types';
import { shrink } from '../../../core/photos/photos.api';
import { LogicalDate } from '../../../core/time/logical-date';
import { scaleDish } from './scale-dish';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/** A dish as the user reviews it: the estimate, the portion they set, and whether it goes in. */
interface Row {
  estimate: MealPhotoDish;
  name: string;
  portionG: number;
  include: boolean;
  /** Saved to the food list as it stands; a new name or portion can be saved again. */
  saved: boolean;
}

/** Gemini doesn't need a big photo to see a plate; a small one is quicker on a phone network. */
const PHOTO_SIDE = 1024;

/**
 * "Snap a meal": take or pick a photo, the server asks Google Gemini what's on the plate,
 * and the dishes come back as estimates. Each one can be renamed, its portion changed
 * (the nutrition scales with it) or left out, then they're added to the day in one go.
 */
@Component({
  selector: 'df-meal-photo',
  imports: [
    FormsModule,
    DfButtonComponent,
    DfInputComponent,
    DfSheetComponent,
    DfStepperInputComponent,
  ],
  templateUrl: './meal-photo.component.html',
  styleUrl: './meal-photo.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MealPhotoComponent {
  private readonly api = inject(ProteinApi);
  private readonly toasts = inject(ToastService);

  readonly open = input.required<boolean>();
  readonly date = input.required<LogicalDate>();
  readonly closed = output<void>();
  /** Dishes were added: the page applies the day and points. */
  readonly logged = output<ProteinDayWrite>();
  /** A dish was saved as a food: the page reloads its food list. */
  readonly foodSaved = output<void>();

  protected readonly status = signal<
    'pick' | 'describe' | 'reading' | 'review' | 'empty' | 'error'
  >('pick');
  /** "Describe it": what the user ate, typed or spoken with the keyboard's mic. */
  protected readonly description = signal('');
  protected readonly error = signal('');
  protected readonly preview = signal<string | null>(null);
  protected readonly rows = signal<Row[]>([]);
  protected readonly busy = signal(false);

  protected readonly chosen = computed(() => this.rows().filter((r) => r.include && r.name.trim()));
  protected readonly totals = computed(() =>
    this.chosen()
      .map((r) => this.amounts(r))
      .reduce((a, b) => ({ grams: a.grams + b.grams, kcal: a.kcal + b.kcal }), {
        grams: 0,
        kcal: 0,
      }),
  );

  constructor() {
    // Each opening starts fresh.
    effect(() => {
      if (this.open()) untracked(() => this.reset());
    });
  }

  private reset(): void {
    this.status.set('pick');
    this.description.set('');
    this.error.set('');
    this.setPreview(null);
    this.rows.set([]);
  }

  private setPreview(url: string | null): void {
    const old = this.preview();
    if (old) URL.revokeObjectURL(old);
    this.preview.set(url);
  }

  protected close(): void {
    this.setPreview(null);
    this.closed.emit();
  }

  protected async onFile(event: Event): Promise<void> {
    const inputEl = event.target as HTMLInputElement;
    const file = inputEl.files?.[0];
    inputEl.value = '';
    if (!file) return;
    await this.read(async () => {
      const blob = await shrink(file, PHOTO_SIDE);
      this.setPreview(URL.createObjectURL(blob));
      return firstValueFrom(this.api.readMealPhoto(await toBase64(blob)));
    });
  }

  protected async readDescription(): Promise<void> {
    const text = this.description().trim();
    if (!text) return;
    await this.read(() => firstValueFrom(this.api.readMealText(text)));
  }

  /** Photo or words: the same estimates, the same review. */
  private async read(ask: () => Promise<{ dishes: MealPhotoDish[] }>): Promise<void> {
    this.status.set('reading');
    try {
      const { dishes } = await ask();
      this.rows.set(
        dishes.map((d) => ({
          estimate: d,
          name: d.name,
          portionG: Math.max(1, d.portionG || 100),
          include: true,
          saved: false,
        })),
      );
      this.status.set(dishes.length ? 'review' : 'empty');
    } catch (e) {
      this.error.set(messageFor(e));
      this.status.set('error');
    }
  }

  protected update(index: number, change: Partial<Row>): void {
    // A new name or portion is a different food, so it can be saved again.
    const resave = 'name' in change || 'portionG' in change ? { saved: false } : {};
    this.rows.update((rows) =>
      rows.map((r, i) => (i === index ? { ...r, ...resave, ...change } : r)),
    );
  }

  /** The estimate scaled to the portion the user set. */
  protected amounts(row: Row): MealItem {
    return scaleDish(row.estimate, row.name, row.portionG);
  }

  protected async addToToday(): Promise<void> {
    // An entry needs some protein, calories or fat; plain water or black tea has none.
    const items = this.chosen()
      .map((r) => this.amounts(r))
      .filter((a) => a.grams + a.kcal + a.fatG > 0);
    if (!items.length || this.busy()) return;
    this.busy.set(true);
    try {
      const result = await firstValueFrom(this.api.logMany(this.date(), items));
      this.logged.emit(result);
      this.close();
    } catch (e) {
      this.toasts.show(messageFor(e), { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }

  /**
   * Saves a dish to the food list at the portion set, named with that portion
   * ("Chicken biryani 300 g"), so next time it's one tap.
   */
  protected async saveAsFood(index: number): Promise<void> {
    const row = this.rows()[index];
    if (!row || this.busy()) return;
    const a = this.amounts(row);
    if (!a.foodName || a.grams + a.kcal + a.fatG === 0) return;
    this.busy.set(true);
    try {
      const { foodName, ...amounts } = a;
      await firstValueFrom(
        this.api.addFood(`${foodName} ${row.portionG} g`.slice(0, 60), amounts, 'PHOTO'),
      );
      this.update(index, { saved: true });
      this.foodSaved.emit();
      this.toasts.show('Saved to your foods.');
    } catch {
      this.toasts.show('Could not save it as a food. Try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }

  protected tryAgain(): void {
    this.reset();
  }
}

function toBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

function messageFor(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) return 'No connection. Try again when you are online.';
    const message = e.error?.message;
    if (typeof message === 'string' && message) return message;
  }
  if (e instanceof Error && e.message === 'too-large')
    return 'That photo is too large. Try another one.';
  return "Couldn't read the photo. Try again.";
}
