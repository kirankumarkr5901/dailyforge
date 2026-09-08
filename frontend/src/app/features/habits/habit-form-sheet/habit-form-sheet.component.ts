import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule, Trash2 } from 'lucide-angular';

import { ApiError } from '../../../core/api/api.types';
import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { PendingActionService } from '../../../core/auth/pending-action.service';
import { SessionStore } from '../../../core/auth/session.store';
import { HabitsApi } from '../../../core/habits/habits.api';
import { CreateHabitPayload, Habit, HabitType, UpdateHabitPayload } from '../../../core/habits/habits.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { DfSwitchComponent } from '../../../shared/ui/df-switch/df-switch.component';
import { HABIT_ICONS } from '../habit-icons';

const WEEKDAY_LABELS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'] as const;
const WEEKDAY_NAMES = [
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
  'Sunday',
] as const;
const EVERY_DAY = 127;

/**
 * Create or edit a habit.
 *
 * The consistency-bonus fields (base amount, multiplier) can only be set at creation
 * (spec's own `UpdateHabitRequest` never touches them) — a habit's bonus curve is
 * fixed once its streak starts, or the "14-day bonus" a user was chasing would keep
 * moving under them. Editing shows exactly the fields the update endpoint accepts.
 */
@Component({
  selector: 'df-habit-form-sheet',
  imports: [
    FormsModule,
    LucideAngularModule,
    DfButtonComponent,
    DfInputComponent,
    DfSheetComponent,
    DfStepperInputComponent,
    DfSwitchComponent,
  ],
  templateUrl: './habit-form-sheet.component.html',
  styleUrl: './habit-form-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HabitFormSheetComponent {
  private readonly api = inject(HabitsApi);
  private readonly session = inject(SessionStore);
  private readonly pendingAction = inject(PendingActionService);
  private readonly authSheet = inject(AuthSheetService);

  readonly open = input.required<boolean>();
  /** Null means "creating a new habit"; otherwise the habit being edited. */
  readonly habit = input<Habit | null>(null);

  readonly closed = output<void>();
  readonly saved = output<Habit>();
  readonly deleted = output<string>();

  protected readonly icons = HABIT_ICONS;
  protected readonly weekdayLabels = WEEKDAY_LABELS;
  protected readonly weekdayNames = WEEKDAY_NAMES;
  protected readonly deleteIcon = Trash2;

  protected readonly name = signal('');
  protected readonly icon = signal(HABIT_ICONS[0].key);
  protected readonly points = signal(10);
  protected readonly type = signal<HabitType>('NORMAL');
  protected readonly penaltyPoints = signal(0);
  protected readonly scheduleDays = signal(EVERY_DAY);
  protected readonly useCustomBonus = signal(false);
  protected readonly baseBonus = signal(20);
  protected readonly bonusMultiplier = signal(1.5);

  protected readonly saving = signal(false);
  protected readonly confirmingDelete = signal(false);
  protected readonly deleting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly preview = signal<number[]>([]);

  protected readonly saveAttempted = signal(false);

  protected readonly isEditing = computed(() => this.habit() !== null);
  protected readonly heading = computed(() => (this.isEditing() ? 'Edit habit' : 'New habit'));
  protected readonly nameInvalid = computed(() => this.name().trim().length === 0);
  /** Withheld until a save is attempted, so a blank field on open does not open already erroring. */
  protected readonly showNameError = computed(() => this.saveAttempted() && this.nameInvalid());

  constructor() {
    // Re-seed the form whenever a different habit is opened (or the sheet reopens for
    // creation) rather than only on first render — inputs change identity on the parent
    // signal's next selection, and this effect is the one place that has to notice.
    effect(() => {
      if (!this.open()) {
        return;
      }
      const habit = this.habit();
      this.confirmingDelete.set(false);
      this.error.set(null);
      this.saveAttempted.set(false);
      if (habit) {
        this.name.set(habit.name);
        this.icon.set(habit.icon);
        this.points.set(habit.points);
        this.type.set(habit.type);
        this.penaltyPoints.set(habit.penaltyPoints);
        this.scheduleDays.set(habit.scheduleDays);
      } else {
        this.name.set('');
        this.icon.set(HABIT_ICONS[0].key);
        this.points.set(10);
        this.type.set('NORMAL');
        this.penaltyPoints.set(0);
        this.scheduleDays.set(EVERY_DAY);
        this.useCustomBonus.set(false);
        this.baseBonus.set(20);
        this.bonusMultiplier.set(1.5);
      }
    });

    // The live preview (spec §8.2): recompute whenever a create-time bonus input
    // changes. Reads through the anonymous-readable endpoint, so it works even before
    // the sheet's own save requires a session.
    effect(() => {
      if (this.isEditing()) {
        return;
      }
      const custom = this.useCustomBonus();
      const base = custom ? this.baseBonus() : undefined;
      const multiplier = custom ? this.bonusMultiplier() : undefined;
      void this.loadPreview(base, multiplier);
    });
  }

  private async loadPreview(baseBonus: number | undefined, bonusMultiplier: number | undefined): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.bonusPreview(baseBonus, bonusMultiplier));
      this.preview.set(result.firstFourBonuses);
    } catch {
      // The preview is a nicety, not the save path — a failed fetch just leaves the
      // last-known values on screen rather than raising an error over it.
    }
  }

  protected toggleDay(bit: number): void {
    this.scheduleDays.update((mask) => {
      const next = mask ^ (1 << bit);
      return next === 0 ? mask : next; // never allow a schedule with no days at all
    });
  }

  protected isDayOn(bit: number): boolean {
    return (this.scheduleDays() & (1 << bit)) !== 0;
  }

  protected async save(): Promise<void> {
    this.saveAttempted.set(true);
    if (this.saving() || this.nameInvalid()) {
      return;
    }

    if (!this.session.isAuthenticated()) {
      this.pendingAction.capture({
        description: this.isEditing() ? 'Habit updated.' : 'Habit created.',
        run: () => this.performSave(),
      });
      this.authSheet.open('write');
      return;
    }

    await this.performSave();
  }

  private async performSave(): Promise<void> {
    this.saving.set(true);
    this.error.set(null);

    try {
      const existing = this.habit();
      let result: Habit;
      if (existing) {
        const payload: UpdateHabitPayload = {
          name: this.name().trim(),
          icon: this.icon(),
          points: this.points(),
          type: this.type(),
          penaltyPoints: this.type() === 'STRICT' ? this.penaltyPoints() : 0,
          scheduleDays: this.scheduleDays(),
        };
        result = await firstValueFrom(this.api.update(existing.id, payload, existing.version));
      } else {
        const payload: CreateHabitPayload = {
          name: this.name().trim(),
          icon: this.icon(),
          points: this.points(),
          type: this.type(),
          penaltyPoints: this.type() === 'STRICT' ? this.penaltyPoints() : 0,
          scheduleDays: this.scheduleDays(),
          ...(this.useCustomBonus()
            ? { baseBonus: this.baseBonus(), bonusMultiplier: this.bonusMultiplier() }
            : {}),
        };
        result = await firstValueFrom(this.api.create(payload));
      }
      this.saved.emit(result);
    } catch (error) {
      this.error.set(this.messageFor(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected requestDelete(): void {
    if (!this.confirmingDelete()) {
      this.confirmingDelete.set(true);
      return;
    }
    void this.performDelete();
  }

  private async performDelete(): Promise<void> {
    const existing = this.habit();
    if (!existing || this.deleting()) {
      return;
    }
    this.deleting.set(true);
    try {
      await firstValueFrom(this.api.delete(existing.id));
      this.deleted.emit(existing.id);
    } catch (error) {
      this.error.set(this.messageFor(error));
      this.confirmingDelete.set(false);
    } finally {
      this.deleting.set(false);
    }
  }

  protected close(): void {
    this.closed.emit();
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      if (body?.code === 'VALIDATION_FAILED' || body?.code === 'OUT_OF_RANGE') {
        return body.message || 'Check the form and try again.';
      }
      return body?.message ?? 'That did not save. Check your connection and try again.';
    }
    return 'That did not save. Check your connection and try again.';
  }
}
