import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ProteinApi } from '../../../core/nutrition/protein.api';
import { PointsStore } from '../../../core/points/points.store';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * The water meter on Home (owner request: just above the calendar). Loads today's water
 * on its own and adds or undoes glasses right here; the Nutrition screen has the same
 * meter. Reaching the target pays points once a day.
 */
@Component({
  selector: 'df-water-card',
  imports: [DfButtonComponent, DfCardComponent],
  templateUrl: './water-card.component.html',
  styleUrl: './water-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WaterCardComponent {
  private readonly api = inject(ProteinApi);
  private readonly points = inject(PointsStore);
  private readonly toasts = inject(ToastService);

  protected readonly date = signal<LogicalDate | null>(null);
  protected readonly ml = signal(0);
  protected readonly target = signal(3000);
  protected readonly busy = signal(false);
  protected readonly loaded = signal(false);

  protected readonly percent = computed(() => (this.target() ? Math.min(100, (this.ml() / this.target()) * 100) : 0));
  protected readonly reached = computed(() => this.ml() >= this.target());

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const day = await firstValueFrom(this.api.day());
      this.date.set(day.date);
      this.ml.set(day.waterMl);
      this.target.set(day.waterTargetMl);
      this.loaded.set(true);
    } catch {
      // Home works without it.
    }
  }

  protected litres(ml: number): string {
    return (Math.round(ml / 100) / 10).toString();
  }

  protected async add(deltaMl: number): Promise<void> {
    const date = this.date();
    if (!date || this.busy()) return;
    this.busy.set(true);
    try {
      const result = await firstValueFrom(this.api.addWater(date, deltaMl));
      this.ml.set(result.day.waterMl);
      this.target.set(result.day.waterTargetMl);
      this.points.applyEnvelope(result.points);
      const delta = result.points.delta;
      if (delta !== 0) {
        this.toasts.show(delta > 0 ? `Water target hit! +${delta} pts` : `Back under the water target. ${delta} pts`, {
          tone: delta > 0 ? 'earned' : 'penalty',
          heatStep: 1,
        });
      }
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }
}
