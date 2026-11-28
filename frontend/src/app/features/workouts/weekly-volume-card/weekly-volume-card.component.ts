import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { WorkoutsApi } from '../../../core/workouts/workouts.api';
import { MuscleVolume, WeeklyVolume } from '../../../core/workouts/workouts.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * Hard sets per muscle this week, one bar per muscle, each against its own weekly target
 * band (set by the AI from the person's training, or by them). Reaching a muscle's
 * minimum pays points; every main muscle reaching it pays a bonus. A single series, so no
 * legend: each row shows its count and target as text.
 */
@Component({
  selector: 'df-weekly-volume-card',
  imports: [FormsModule, DfButtonComponent, DfCardComponent, DfStepperInputComponent],
  templateUrl: './weekly-volume-card.component.html',
  styleUrl: './weekly-volume-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WeeklyVolumeCardComponent {
  private readonly api = inject(WorkoutsApi);
  private readonly toasts = inject(ToastService);

  readonly volume = input.required<WeeklyVolume>();
  /** Edits replace what the page passed in, until it passes a new week. */
  protected readonly current = linkedSignal(() => this.volume());

  protected readonly editing = signal<string | null>(null);
  protected readonly editMin = signal(0);
  protected readonly editMax = signal(0);
  protected readonly saving = signal(false);

  /** The axis runs a little past the highest target so a muscle above it still has room. */
  protected readonly max = computed(() => {
    const v = this.current();
    return Math.max(10, ...v.muscles.map((m) => Math.max(m.sets, (m.max ?? 0) + 3)));
  });

  protected band(m: MuscleVolume): { left: number; width: number } | null {
    if (m.min === null || m.max === null) return null;
    return { left: (m.min / this.max()) * 100, width: ((m.max - m.min) / this.max()) * 100 };
  }

  protected width(sets: number): number {
    return (sets / this.max()) * 100;
  }

  protected reached(m: MuscleVolume): boolean {
    return m.min !== null && m.sets >= m.min;
  }

  protected status(m: MuscleVolume): string {
    if (m.min === null || m.max === null) return `${m.sets} sets`;
    if (m.sets < m.min)
      return `${m.sets} sets, ${m.min - m.sets} to go for the ${m.min}–${m.max} target`;
    if (m.sets > m.max) return `${m.sets} sets, above the ${m.min}–${m.max} target`;
    return `${m.sets} sets, within the ${m.min}–${m.max} target`;
  }

  protected edit(m: MuscleVolume): void {
    if (m.min === null || m.max === null) return;
    this.editing.set(m.group);
    this.editMin.set(m.min);
    this.editMax.set(m.max);
  }

  protected async save(): Promise<void> {
    const muscle = this.editing();
    if (!muscle || this.saving()) return;
    const min = this.editMin();
    const max = Math.max(min, this.editMax());
    this.saving.set(true);
    try {
      this.current.set(await firstValueFrom(this.api.setMuscleTarget(muscle, min, max)));
      this.editing.set(null);
    } catch {
      this.toasts.show('That did not save. Try again.', { tone: 'penalty' });
    } finally {
      this.saving.set(false);
    }
  }
}
