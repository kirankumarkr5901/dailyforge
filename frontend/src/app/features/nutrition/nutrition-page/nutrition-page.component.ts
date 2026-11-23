import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { Beef, LucideAngularModule, Plus, Trash2 } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { ProteinApi } from '../../../core/nutrition/protein.api';
import { ProteinDay, ProteinDayWrite, ProteinEntry, ProteinFood } from '../../../core/nutrition/protein.types';
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
 * The protein meter: tap what you eat, watch today's total climb toward the target.
 * Reaching the target pays a few points once a day; an undo that drops below it takes
 * them back. The target and the food list live in Settings.
 */
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

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly day = signal<ProteinDay | null>(null);
  /** One write at a time, so a quick double tap cannot log the same food twice. */
  protected readonly busy = signal(false);

  protected readonly search = signal('');
  protected readonly visibleFoods = computed(() => {
    const foods = this.day()?.foods ?? [];
    const q = this.search().trim().toLowerCase();
    return q ? foods.filter((f) => f.name.toLowerCase().includes(q)) : foods;
  });

  protected readonly otherName = signal('');
  protected readonly otherGrams = signal(10);

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
    } catch {
      this.error.set('Could not load your protein for today. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  protected eat(food: ProteinFood): Promise<boolean> {
    return this.write(() => this.api.log(this.day()!.date, food.name, food.grams));
  }

  protected async eatOther(): Promise<void> {
    const name = this.otherName().trim() || 'Other';
    if (await this.write(() => this.api.log(this.day()!.date, name, this.otherGrams()))) {
      this.otherName.set('');
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
        this.toasts.show(`Protein target hit! +${delta} pts`, { tone: 'earned', heatStep: 1 });
      } else if (delta < 0) {
        this.toasts.show(`Back under the target. ${delta} pts`, { tone: 'penalty' });
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
