import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { CircleCheck, LucideAngularModule } from 'lucide-angular';

import { Run, RunType } from '../../../core/runs/runs.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { isoWeek, mondayOf } from './run-week';

/** The two kinds of quality run, from the Cut Block plan. */
const QUALITY: Record<'INTERVAL' | 'TEMPO', { label: string; steps: string[] }> = {
  INTERVAL: {
    label: 'Intervals',
    steps: [
      '10 min easy jog to warm up',
      '6 × 2 min hard (breathing heavily, but in control), 2 min easy jog between',
      '10 min easy jog to cool down',
    ],
  },
  TEMPO: {
    label: 'Tempo',
    steps: [
      '10 min easy jog to warm up',
      '15–20 min at a comfortably hard pace (you can only speak short phrases)',
      '10 min easy jog to cool down',
    ],
  },
};

/**
 * This week's running plan: one quality run that alternates by week (odd ISO weeks are
 * intervals, even weeks tempo) and two easy runs. Logging a run as Interval or Tempo
 * this week marks the quality run done; easy runs are counted from everything else.
 * No points of its own: runs already earn them.
 */
@Component({
  selector: 'df-run-week-plan',
  imports: [LucideAngularModule, DfButtonComponent, DfCardComponent],
  templateUrl: './run-week-plan.component.html',
  styleUrl: './run-week-plan.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunWeekPlanComponent {
  readonly today = input.required<LogicalDate>();
  readonly runs = input.required<readonly Run[]>();
  /** "Log this run", with the quality type to pre-select. */
  readonly logRequested = output<RunType>();

  protected readonly doneIcon = CircleCheck;

  protected readonly qualityType = computed<'INTERVAL' | 'TEMPO'>(() =>
    isoWeek(this.today()) % 2 === 1 ? 'INTERVAL' : 'TEMPO',
  );
  protected readonly quality = computed(() => QUALITY[this.qualityType()]);
  protected readonly nextWeekLabel = computed(() => QUALITY[this.qualityType() === 'INTERVAL' ? 'TEMPO' : 'INTERVAL'].label);

  private readonly thisWeek = computed(() => {
    const start = mondayOf(this.today());
    return this.runs().filter((r) => r.date >= start && r.date <= this.today());
  });

  /** Any interval or tempo run this week counts as the quality run, even the other kind. */
  protected readonly qualityRun = computed(() =>
    this.thisWeek().find((r) => r.type === 'INTERVAL' || r.type === 'TEMPO') ?? null,
  );

  /** Easy runs: everything else this week, up to the two planned. */
  protected readonly easyDone = computed(() => {
    const quality = this.qualityRun();
    return Math.min(2, this.thisWeek().filter((r) => r !== quality).length);
  });
}
