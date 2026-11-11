import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { ForgeCalendarDay, ForgeDayStatus } from '../../../core/forge/forge.types';
import { formatLong } from '../../../core/time/logical-date';

const STATUS_WORDS: Record<ForgeDayStatus, string> = {
  DONE: 'complete',
  PARTIAL: 'partly done',
  MISSED: 'missed',
  TODAY: 'today',
  UPCOMING: 'still to come',
};

/** The whole program at a glance: one cell per day, coloured by how that day went. */
@Component({
  selector: 'df-forge-calendar',
  templateUrl: './forge-calendar.component.html',
  styleUrl: './forge-calendar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgeCalendarComponent {
  readonly days = input.required<ForgeCalendarDay[]>();

  protected label(day: ForgeCalendarDay): string {
    const counts = day.status === 'UPCOMING' ? '' : `, ${day.done} of ${day.total} quests`;
    return `Day ${day.dayNumber}, ${formatLong(day.date)}: ${STATUS_WORDS[day.status]}${counts}`;
  }
}
