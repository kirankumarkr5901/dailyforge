import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { AiStatusService } from '../../../core/ai/ai-status.service';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';

interface Projection {
  month: LogicalDate;
  headline: string;
  lines: string[];
  reality: string;
  lever: string;
}

/**
 * "Future you", on Home on the 1st of each month: where the last two months' pace
 * leads in 90 days, from measured trends, with a reality check and one lever. Hidden
 * every other day.
 */
@Component({
  selector: 'df-future-you',
  imports: [DfCardComponent],
  templateUrl: './future-you.component.html',
  styleUrl: './future-you.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FutureYouComponent {
  private readonly http = inject(HttpClient);
  private readonly ai = inject(AiStatusService);

  /** Today, in the user's own time zone. */
  readonly today = input.required<LogicalDate>();

  protected readonly projection = signal<Projection | null>(null);

  constructor() {
    effect(() => {
      const today = this.today();
      untracked(() => void this.load(today));
    });
  }

  private async load(today: LogicalDate): Promise<void> {
    this.projection.set(null);
    if (!today.endsWith('-01') || !(await this.ai.load())) return;
    try {
      this.projection.set(
        await firstValueFrom(
          this.http.get<Projection | null>(`${environment.apiBaseUrl}/insights/future`),
        ),
      );
    } catch {
      // A nice-to-have; Home works without it.
    }
  }
}
