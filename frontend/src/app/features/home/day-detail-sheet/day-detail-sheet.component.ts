import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { HomeApi } from '../../../core/home/home.api';
import { DayDetail } from '../../../core/home/home.types';
import { LogicalDate, formatLong } from '../../../core/time/logical-date';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';

/** Tapping a heatmap cell opens this: every activity that day, grouped by category (spec §8.1.1). */
@Component({
  selector: 'df-day-detail-sheet',
  imports: [DfSheetComponent, DfSkeletonComponent],
  templateUrl: './day-detail-sheet.component.html',
  styleUrl: './day-detail-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DayDetailSheetComponent {
  private readonly api = inject(HomeApi);

  readonly date = input<LogicalDate | null>(null);
  readonly closed = output<void>();

  protected readonly formatLong = formatLong;
  protected readonly loading = signal(false);
  protected readonly detail = signal<DayDetail | null>(null);
  protected readonly isOpen = computed(() => this.date() !== null);

  constructor() {
    effect(() => {
      const date = this.date();
      if (!date) {
        this.detail.set(null);
        return;
      }
      void this.load(date);
    });
  }

  private async load(date: LogicalDate): Promise<void> {
    this.loading.set(true);
    try {
      this.detail.set(await firstValueFrom(this.api.day(date)));
    } finally {
      this.loading.set(false);
    }
  }

  protected close(): void {
    this.closed.emit();
  }
}
