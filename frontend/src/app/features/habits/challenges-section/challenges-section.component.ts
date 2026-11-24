import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { CircleCheck, LucideAngularModule, Plus, Trophy } from 'lucide-angular';

import { SessionStore } from '../../../core/auth/session.store';
import { Challenge, ChallengesApi } from '../../../core/challenges/challenges.api';
import { HabitsApi } from '../../../core/habits/habits.api';
import { PointsStore } from '../../../core/points/points.store';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * Challenges at the top of the Habits screen: "Plank · day 12 of 30", a progress bar,
 * and a "Done today" tick that is the challenge's own habit log. Reaching the length
 * offers a Claim for the bonus. Starting and ending one needs "Enable edit".
 */
@Component({
  selector: 'df-challenges-section',
  imports: [
    FormsModule,
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfInputComponent,
    DfSheetComponent,
    DfStepperInputComponent,
  ],
  templateUrl: './challenges-section.component.html',
  styleUrl: './challenges-section.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChallengesSectionComponent {
  private readonly api = inject(ChallengesApi);
  private readonly habitsApi = inject(HabitsApi);
  private readonly points = inject(PointsStore);
  private readonly toasts = inject(ToastService);
  protected readonly session = inject(SessionStore);

  readonly today = input.required<LogicalDate>();
  /** Changes whenever the habit board reloads, so a tick made there shows here too. */
  readonly refreshKey = input<unknown>(null);
  /** A tick made here changed a habit; the page reloads its board. */
  readonly habitsChanged = output<void>();

  protected readonly trophyIcon = Trophy;
  protected readonly doneIcon = CircleCheck;
  protected readonly plusIcon = Plus;

  protected readonly challenges = signal<Challenge[]>([]);
  protected readonly busy = signal<string | null>(null);
  protected readonly showFinished = signal(false);

  protected readonly current = computed(() =>
    this.challenges().filter((c) => c.status === 'ACTIVE' || c.status === 'REACHED'),
  );
  protected readonly finished = computed(() =>
    this.challenges().filter((c) => c.status === 'COMPLETED' || c.status === 'ABANDONED'),
  );

  protected readonly formOpen = signal(false);
  protected readonly title = signal('');
  protected readonly lengthDays = signal(30);
  protected readonly habitPoints = signal(10);
  protected readonly bonusPoints = signal(100);
  protected readonly creating = signal(false);

  constructor() {
    effect(() => {
      this.refreshKey();
      untracked(() => void this.load());
    });
  }

  private async load(): Promise<void> {
    try {
      this.challenges.set(await firstValueFrom(this.api.list()));
    } catch {
      // A side section: the habit board below still works without it.
    }
  }

  protected percent(c: Challenge): number {
    return (c.doneInARow / c.lengthDays) * 100;
  }

  protected async toggleToday(c: Challenge): Promise<void> {
    if (this.busy()) return;
    this.busy.set(c.id);
    try {
      const result = await firstValueFrom(
        c.doneToday ? this.habitsApi.unlog(c.habitId, this.today()) : this.habitsApi.log(c.habitId, this.today()),
      );
      this.points.applyEnvelope(result.points);
      const delta = result.points.delta;
      if (delta !== 0) {
        this.toasts.show(`${delta > 0 ? '+' : ''}${delta} pts`, { tone: delta > 0 ? 'earned' : 'penalty', heatStep: 1 });
      }
      await this.load();
      this.habitsChanged.emit();
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(null);
    }
  }

  protected async claim(c: Challenge): Promise<void> {
    if (this.busy()) return;
    this.busy.set(c.id);
    try {
      const result = await firstValueFrom(this.api.claim(c.id));
      this.points.applyEnvelope(result.points);
      this.toasts.show(`Challenge complete! +${result.points.delta} pts`, { tone: 'earned', heatStep: 3 });
      await this.load();
    } catch {
      this.toasts.show('Could not claim that. Try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(null);
    }
  }

  protected async abandon(c: Challenge): Promise<void> {
    if (this.busy()) return;
    this.busy.set(c.id);
    try {
      await firstValueFrom(this.api.abandon(c.id));
      await this.load();
      this.toasts.show('Challenge ended. Its habit stays on your board.');
    } catch {
      this.toasts.show('Could not end that challenge. Try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(null);
    }
  }

  protected openForm(): void {
    this.title.set('');
    this.lengthDays.set(30);
    this.habitPoints.set(10);
    this.bonusPoints.set(100);
    this.formOpen.set(true);
  }

  protected async create(): Promise<void> {
    const title = this.title().trim();
    if (!title || this.creating()) {
      if (!title) this.toasts.show('Give the challenge a name, e.g. "Plank".');
      return;
    }
    this.creating.set(true);
    try {
      await firstValueFrom(
        this.api.create({
          title,
          lengthDays: this.lengthDays(),
          habitPoints: this.habitPoints(),
          bonusPoints: this.bonusPoints(),
        }),
      );
      this.formOpen.set(false);
      await this.load();
      this.habitsChanged.emit();
      this.toasts.show(`${title}: day 1 of ${this.lengthDays()}. Go!`);
    } catch {
      this.toasts.show('Could not start that challenge. Try again.', { tone: 'penalty' });
    } finally {
      this.creating.set(false);
    }
  }
}
