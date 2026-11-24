import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ChevronRight, Circle, CircleCheck, LucideAngularModule } from 'lucide-angular';

import { Challenge, ChallengesApi } from '../../../core/challenges/challenges.api';
import { HomeApi } from '../../../core/home/home.api';
import { HabitsApi } from '../../../core/habits/habits.api';
import { ProteinApi } from '../../../core/nutrition/protein.api';
import { PointsStore } from '../../../core/points/points.store';
import { SkillsApi } from '../../../core/skills/skills.api';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/** One line of the checklist: either tickable here, or a link to where it is done. */
interface Item {
  key: string;
  label: string;
  detail: string | null;
  done: boolean;
  /** Tickable on Home: the habit to log or unlog. */
  habitId?: string;
  /** Otherwise: the screen where it is done. */
  link?: string;
}

/**
 * Today on Home: everything due today in one list, ticked from here where a tick is all
 * it takes (habits, challenges), and one tap away where it needs its own screen (the
 * workout, skill blocks, protein). Each part loads on its own, so one failing only
 * drops its rows.
 */
@Component({
  selector: 'df-today-checklist',
  imports: [RouterLink, LucideAngularModule, DfCardComponent],
  templateUrl: './today-checklist.component.html',
  styleUrl: './today-checklist.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodayChecklistComponent {
  private readonly habits = inject(HabitsApi);
  private readonly challengesApi = inject(ChallengesApi);
  private readonly skills = inject(SkillsApi);
  private readonly protein = inject(ProteinApi);
  private readonly home = inject(HomeApi);
  private readonly points = inject(PointsStore);
  private readonly toasts = inject(ToastService);

  readonly date = input.required<LogicalDate>();

  protected readonly doneIcon = CircleCheck;
  protected readonly openIcon = Circle;
  protected readonly linkIcon = ChevronRight;

  protected readonly items = signal<Item[] | null>(null);
  protected readonly busy = signal<string | null>(null);

  protected readonly doneCount = computed(() => this.items()?.filter((i) => i.done).length ?? 0);

  constructor() {
    effect(() => {
      const date = this.date();
      untracked(() => void this.load(date));
    });
  }

  private async load(date: LogicalDate): Promise<void> {
    const [board, challenges, skills, protein, heat] = await Promise.allSettled([
      firstValueFrom(this.habits.board(date)),
      firstValueFrom(this.challengesApi.list()),
      firstValueFrom(this.skills.board(date)),
      firstValueFrom(this.protein.day(date)),
      firstValueFrom(this.home.heatmap(date, date)),
    ]);
    const items: Item[] = [];

    const running: Challenge[] =
      challenges.status === 'fulfilled' ? challenges.value.filter((c) => c.status === 'ACTIVE') : [];
    for (const c of running) {
      items.push({
        key: 'c-' + c.id,
        label: c.title,
        detail: `Challenge · day ${c.dayNumber} of ${c.lengthDays}`,
        done: c.doneToday,
        habitId: c.habitId,
      });
    }
    const challengeHabits = new Set(running.map((c) => c.habitId));

    if (board.status === 'fulfilled') {
      for (const h of board.value.habits) {
        if (challengeHabits.has(h.id) || (h.state !== 'PENDING' && h.state !== 'DONE')) continue;
        items.push({
          key: 'h-' + h.id,
          label: h.name,
          detail: h.currentStreak > 1 ? `${h.currentStreak}-day streak` : null,
          done: h.state === 'DONE',
          habitId: h.id,
        });
      }
    }

    if (heat.status === 'fulfilled') {
      const trained = heat.value.some((d) => d.hasWorkout);
      items.push({ key: 'workout', label: 'Workout', detail: trained ? 'Logged today' : null, done: trained, link: '/workout' });
    }

    if (skills.status === 'fulfilled') {
      const doneSkills = skills.value.skills.filter((s) => s.doneOnDate).length;
      items.push({
        key: 'skills',
        label: 'Skill block',
        detail: doneSkills > 0 ? `${doneSkills} done today` : 'Handstand, crow + L-sit or flips',
        done: doneSkills > 0,
        link: '/skills',
      });
    }

    if (protein.status === 'fulfilled') {
      const p = protein.value;
      items.push({
        key: 'protein',
        label: 'Protein',
        detail: `${p.totalG} / ${p.targetG} g`,
        done: p.totalG >= p.targetG,
        link: '/nutrition',
      });
    }

    this.items.set(items);
  }

  protected async toggle(item: Item): Promise<void> {
    if (!item.habitId || this.busy()) return;
    this.busy.set(item.key);
    try {
      const result = await firstValueFrom(
        item.done ? this.habits.unlog(item.habitId, this.date()) : this.habits.log(item.habitId, this.date()),
      );
      this.points.applyEnvelope(result.points);
      const delta = result.points.delta;
      if (delta !== 0) {
        this.toasts.show(`${delta > 0 ? '+' : ''}${delta} pts`, { tone: delta > 0 ? 'earned' : 'penalty', heatStep: 1 });
      }
      await this.load(this.date());
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(null);
    }
  }
}
