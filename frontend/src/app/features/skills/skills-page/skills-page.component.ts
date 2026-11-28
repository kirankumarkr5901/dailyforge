import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, firstValueFrom } from 'rxjs';
import { CircleCheck, LucideAngularModule, PersonStanding, ShieldAlert } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { PointsStore } from '../../../core/points/points.store';
import { Skill, SkillBoard, SkillWrite, SkillsApi } from '../../../core/skills/skills.api';
import { SyncStore } from '../../../core/sync/sync.store';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * Skill work from the Cut Block plan: handstand, crow + L-sit and flips. Each skill
 * shows the drills for the level you are on, what it takes to move up, and a "done
 * today" mark that pays a few points once a day. Do it after the warm-up, while fresh.
 */
@Component({
  selector: 'df-skills-page',
  imports: [LucideAngularModule, DfButtonComponent, DfCardComponent, DfEmptyStateComponent, DfSkeletonComponent],
  templateUrl: './skills-page.component.html',
  styleUrl: './skills-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkillsPageComponent {
  private readonly api = inject(SkillsApi);
  private readonly toasts = inject(ToastService);
  private readonly points = inject(PointsStore);
  private readonly sync = inject(SyncStore);
  protected readonly session = inject(SessionStore);
  private readonly authSheet = inject(AuthSheetService);

  protected readonly skillIcon = PersonStanding;
  protected readonly doneIcon = CircleCheck;
  protected readonly safetyIcon = ShieldAlert;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly board = signal<SkillBoard | null>(null);
  /** The skill with a request in flight, so its buttons cannot be double-tapped. */
  protected readonly busy = signal<string | null>(null);
  /** Skills whose other levels are expanded. */
  protected readonly showAll = signal<ReadonlySet<string>>(new Set());

  constructor() {
    let wasAuthenticated = false;
    effect(() => {
      const isAuthenticated = this.session.isAuthenticated();
      if (isAuthenticated && !wasAuthenticated) {
        void this.load();
      }
      if (!isAuthenticated && this.session.isResolved()) {
        this.board.set(null);
        this.loading.set(false);
      }
      wasAuthenticated = isAuthenticated;
    });
    this.sync.refreshes.pipe(takeUntilDestroyed()).subscribe(() => void this.load());
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.board.set(await firstValueFrom(this.api.board()));
    } catch {
      this.error.set('Could not load your skills. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  protected currentLevel(skill: Skill) {
    return skill.levels[skill.level - 1];
  }

  protected toggleAll(code: string): void {
    this.showAll.update((set) => {
      const next = new Set(set);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  }

  /** A custom skill waiting for the second tap to remove it. */
  protected readonly armedRemove = signal<string | null>(null);

  protected async removeCustom(skill: Skill): Promise<void> {
    if (this.busy()) return;
    this.busy.set(skill.code);
    try {
      this.board.set(await firstValueFrom(this.api.removeCustom(skill.code)));
      this.armedRemove.set(null);
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(null);
    }
  }

  protected async setLevel(skill: Skill, level: number): Promise<void> {
    if (level === skill.level || this.busy()) return;
    this.busy.set(skill.code);
    try {
      this.board.set(await firstValueFrom(this.api.setLevel(skill.code, level)));
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(null);
    }
  }

  protected toggleDone(skill: Skill): Promise<void> {
    const date = this.board()!.date;
    return this.write(
      skill.code,
      skill.doneOnDate ? this.api.markNotDone(skill.code, date) : this.api.markDone(skill.code, date),
    );
  }

  private async write(code: string, call: Observable<SkillWrite>): Promise<void> {
    if (this.busy()) return;
    this.busy.set(code);
    try {
      const result = await firstValueFrom(call);
      this.board.set(result.board);
      this.points.applyEnvelope(result.points);
      const delta = result.points.delta;
      if (delta !== 0) {
        this.toasts.show(`${delta > 0 ? '+' : ''}${delta} pts`, { tone: delta > 0 ? 'earned' : 'penalty', heatStep: 1 });
      }
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(null);
    }
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }
}
