import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Flame, GripVertical, Lock, LucideAngularModule, SquarePen } from 'lucide-angular';

import { CdkDragHandle } from '@angular/cdk/drag-drop';

import { ApiError } from '../../../core/api/api.types';
import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { PendingActionService } from '../../../core/auth/pending-action.service';
import { SessionStore } from '../../../core/auth/session.store';
import { HabitsApi } from '../../../core/habits/habits.api';
import { BoardEntry, HabitLogResponse } from '../../../core/habits/habits.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfChipComponent } from '../../../shared/ui/df-chip/df-chip.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { iconFor } from '../habit-icons';

export interface HabitToggled {
  entry: BoardEntry;
  checked: boolean;
  response: HabitLogResponse;
}

/**
 * One habit's row on the board for one date.
 *
 * A day older than yesterday is locked (spec §4.3) — shown as a lock, not a disabled
 * checkbox, because "why can't I tap this" deserves an icon that answers itself. Today
 * and yesterday tick live against the real endpoints; a future date is always shown as
 * PLANNED even when pre-ticked, so ticking ahead never reads as already earned.
 */
@Component({
  selector: 'df-habit-row',
  imports: [CdkDragHandle, LucideAngularModule, DfChipComponent, DfIconButtonComponent],
  templateUrl: './habit-row.component.html',
  styleUrl: './habit-row.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'df-habit-row-host' },
})
export class HabitRowComponent {
  private readonly api = inject(HabitsApi);
  private readonly session = inject(SessionStore);
  private readonly pendingAction = inject(PendingActionService);
  private readonly authSheet = inject(AuthSheetService);

  readonly entry = input.required<BoardEntry>();
  readonly date = input.required<LogicalDate>();
  /** Shows the drag handle. Off unless the containing list is actually a drop list. */
  readonly reorderable = input(false);

  protected readonly gripIcon = GripVertical;

  readonly toggled = output<HabitToggled>();
  readonly editRequested = output<string>();
  readonly errored = output<string>();

  protected readonly lockIcon = Lock;
  protected readonly flameIcon = Flame;
  protected readonly editIcon = SquarePen;

  protected readonly busy = signal(false);

  protected readonly icon = computed(() => iconFor(this.entry().icon));

  protected readonly checked = computed(() => {
    const entry = this.entry();
    return entry.state === 'DONE' || (entry.state === 'PLANNED' && entry.plannedDone);
  });

  protected readonly stateChip = computed<{ label: string; tone: 'neutral' | 'done' | 'penalty' | 'earned' } | null>(
    () => {
      switch (this.entry().state) {
        case 'MISSED':
          return { label: 'Missed', tone: 'penalty' };
        case 'PLANNED':
          return { label: 'Planned', tone: 'neutral' };
        default:
          return null;
      }
    },
  );

  protected async onToggle(next: boolean): Promise<void> {
    if (this.busy() || !this.entry().editable) {
      return;
    }

    if (!this.session.isAuthenticated()) {
      this.pendingAction.capture({
        description: `Sign in to log ${this.entry().name}.`,
        run: () => this.perform(next),
      });
      this.authSheet.open('write');
      return;
    }

    await this.perform(next);
  }

  private async perform(next: boolean): Promise<void> {
    this.busy.set(true);
    const entry = this.entry();
    try {
      const response = next
        ? await firstValueFrom(this.api.log(entry.id, this.date()))
        : await firstValueFrom(this.api.unlog(entry.id, this.date()));
      this.toggled.emit({ entry, checked: next, response });
    } catch (error) {
      this.errored.emit(this.messageFor(error));
    } finally {
      this.busy.set(false);
    }
  }

  protected requestEdit(event: Event): void {
    // The whole card is the toggle target now (spec feedback: no separate checkbox to
    // aim for) — the edit button sits inside it and must stop the click from also
    // toggling completion.
    event.stopPropagation();
    this.editRequested.emit(this.entry().id);
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      if (body?.code === 'HABIT_LOCKED') {
        return 'This day can no longer be changed.';
      }
      if (body?.code === 'DAILY_CAP_REACHED') {
        return "You've reached today's cap for habits. It resets tomorrow.";
      }
      return body?.message ?? 'That did not save. Check your connection and try again.';
    }
    return 'That did not save. Check your connection and try again.';
  }
}
