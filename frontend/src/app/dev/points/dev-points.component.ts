import { ChangeDetectionStrategy, Component, effect, inject, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { CircleAlert, RefreshCw } from 'lucide-angular';

import { AuthSheetService } from '../../core/auth/auth-sheet.service';
import { SessionStore } from '../../core/auth/session.store';
import { PointsApi } from '../../core/points/points.api';
import { LedgerEntry, PointsCategory, ScoreSnapshot } from '../../core/points/points.types';
import { DfButtonComponent } from '../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../shared/ui/df-card/df-card.component';
import { DfChipComponent } from '../../shared/ui/df-chip/df-chip.component';
import { DfEmptyStateComponent } from '../../shared/ui/df-empty-state/df-empty-state.component';
import { DfIconButtonComponent } from '../../shared/ui/df-icon-button/df-icon-button.component';
import { DfInputComponent } from '../../shared/ui/df-input/df-input.component';
import { DfScorePillComponent } from '../../shared/ui/df-score-pill/df-score-pill.component';
import { DfSelectComponent, DfSelectOption } from '../../shared/ui/df-select/df-select.component';
import { DfStepperInputComponent } from '../../shared/ui/df-stepper-input/df-stepper-input.component';

/**
 * M2's own words: "No UI beyond a debug page." This is that page.
 *
 * It exists to prove the points engine end to end against the real backend — the same
 * score pill and primitives M0 built, now driven by real awards instead of the gallery's
 * simulated ones. Excluded from production builds; its one write path (debug award) does
 * not even exist on the server in production (@Profile("!prod")).
 */
@Component({
  selector: 'df-dev-points',
  imports: [
    ReactiveFormsModule,
    DfButtonComponent,
    DfCardComponent,
    DfChipComponent,
    DfEmptyStateComponent,
    DfIconButtonComponent,
    DfInputComponent,
    DfScorePillComponent,
    DfSelectComponent,
    DfStepperInputComponent,
  ],
  templateUrl: './dev-points.component.html',
  styleUrl: './dev-points.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DevPointsComponent {
  private readonly api = inject(PointsApi);
  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);

  protected readonly refreshIcon = RefreshCw;
  protected readonly alertIcon = CircleAlert;

  private readonly pill = viewChild<DfScorePillComponent>(DfScorePillComponent);

  protected readonly snapshot = signal<ScoreSnapshot | null>(null);
  protected readonly ledger = signal<LedgerEntry[]>([]);
  protected readonly loading = signal(false);
  protected readonly awarding = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly lastCelebrations = signal<string[]>([]);

  protected readonly categoryOptions: readonly DfSelectOption[] = [
    { value: 'HABIT', label: 'Habit' },
    { value: 'WORKOUT', label: 'Workout' },
    { value: 'RUN', label: 'Run' },
    { value: 'ACTIVITY', label: 'Activity' },
    { value: 'GOAL', label: 'Goal' },
    { value: 'REWARD', label: 'Reward' },
    { value: 'ADJUSTMENT', label: 'Adjustment' },
  ];

  /**
   * A few real rule codes from spec §5.4, so the celebration mapping (WORKOUT_PR → PR,
   * HABIT_COMMITMENT → ALL_HABITS_DONE, …) is actually exercisable from this form rather
   * than needing a made-up code that maps to nothing.
   */
  protected readonly ruleCodeOptions: readonly DfSelectOption[] = [
    { value: 'HABIT_BASE', label: 'HABIT_BASE — no celebration' },
    { value: 'HABIT_CONSISTENCY', label: 'HABIT_CONSISTENCY — streak' },
    { value: 'HABIT_COMMITMENT', label: 'HABIT_COMMITMENT — all habits done' },
    { value: 'WORKOUT_SET', label: 'WORKOUT_SET — no celebration' },
    { value: 'WORKOUT_PR', label: 'WORKOUT_PR — personal record' },
    { value: 'WORKOUT_SESSION_COMPLETE', label: 'WORKOUT_SESSION_COMPLETE — 0 pts, celebrates' },
    { value: 'RUN_DISTANCE', label: 'RUN_DISTANCE — no celebration' },
    { value: 'RUN_MILESTONE', label: 'RUN_MILESTONE — milestone' },
    { value: 'ADJUSTMENT', label: 'ADJUSTMENT — manual correction' },
  ];

  protected readonly form = new FormGroup({
    category: new FormControl<PointsCategory>('HABIT', { nonNullable: true }),
    ruleCode: new FormControl('HABIT_BASE', { nonNullable: true }),
    amount: new FormControl(10, { nonNullable: true }),
    description: new FormControl('Test award', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    // A reactive effect rather than a one-time constructor check: this page is often
    // reached anonymous and then signed into right here (the empty-ledger screenshot
    // that motivated this fix was exactly that flow), and a constructor-time check
    // cannot see a status change that happens after construction.
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.refresh();
      }
      wasAuthenticated = isAuthenticated;
    });
  }

  protected async refresh(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [snapshot, ledgerPage] = await Promise.all([
        firstValueFrom(this.api.snapshot()),
        firstValueFrom(this.api.ledger(0, 20)),
      ]);
      this.snapshot.set(snapshot);
      this.ledger.set(ledgerPage.content);
    } catch (error) {
      this.error.set(this.messageFor(error));
    } finally {
      this.loading.set(false);
    }
  }

  protected async award(): Promise<void> {
    if (this.awarding()) {
      return;
    }
    this.awarding.set(true);
    this.error.set(null);
    const { category, ruleCode, amount, description } = this.form.getRawValue();
    const before = this.snapshot()?.total ?? 0;

    try {
      const envelope = await firstValueFrom(this.api.debugAward(category, ruleCode, amount, description));
      this.lastCelebrations.set(envelope.celebrations.map((c) => c.type));
      this.pill()?.award(before, envelope.newTotal);
      await this.refresh();
    } catch (error) {
      this.error.set(this.messageFor(error));
    } finally {
      this.awarding.set(false);
    }
  }

  protected async recalculate(): Promise<void> {
    this.loading.set(true);
    try {
      const snapshot = await firstValueFrom(this.api.recalculate());
      this.snapshot.set(snapshot);
    } catch (error) {
      this.error.set(this.messageFor(error));
    } finally {
      this.loading.set(false);
    }
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as { code?: string; message?: string } | null;
      if (error.status === 501) {
        return 'The debug award endpoint is disabled on this server (production profile).';
      }
      if (body?.code === 'DAILY_CAP_REACHED') {
        return "You've reached today's cap for this category. It resets tomorrow.";
      }
      return body?.message ?? 'Something went wrong. Check the console.';
    }
    return 'Something went wrong. Check the console.';
  }
}
