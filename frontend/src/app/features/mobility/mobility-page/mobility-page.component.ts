import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { CircleCheck, LucideAngularModule, Play, StretchHorizontal } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { MobilityApi, MobilityBoard, MobilityCompletion, MobilityFlow } from '../../../core/mobility/mobility.api';
import { PointsStore } from '../../../core/points/points.store';
import { SyncStore } from '../../../core/sync/sync.store';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../../shared/ui/df-empty-state/df-empty-state.component';
import { DfSkeletonComponent } from '../../../shared/ui/df-skeleton/df-skeleton.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';
import { MobilityPlayerComponent } from '../mobility-player/mobility-player.component';

/** The four guided mobility flows. Press play and follow the countdown. */
@Component({
  selector: 'df-mobility-page',
  imports: [
    LucideAngularModule,
    DfButtonComponent,
    DfCardComponent,
    DfEmptyStateComponent,
    DfSkeletonComponent,
    MobilityPlayerComponent,
  ],
  templateUrl: './mobility-page.component.html',
  styleUrl: './mobility-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MobilityPageComponent {
  private readonly api = inject(MobilityApi);
  private readonly toasts = inject(ToastService);
  private readonly points = inject(PointsStore);
  private readonly sync = inject(SyncStore);
  protected readonly session = inject(SessionStore);
  private readonly authSheet = inject(AuthSheetService);

  protected readonly flowIcon = StretchHorizontal;
  protected readonly playIcon = Play;
  protected readonly doneIcon = CircleCheck;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly board = signal<MobilityBoard | null>(null);
  protected readonly playing = signal<MobilityFlow | null>(null);

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
      this.error.set('Could not load the mobility flows. Check your connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  protected minutes(seconds: number): number {
    return Math.round(seconds / 60);
  }

  protected onCompleted(result: MobilityCompletion): void {
    this.board.set(result.board);
    this.points.applyEnvelope(result.points);
    if (result.points.delta > 0) {
      this.toasts.show(`Flow done! +${result.points.delta} pts`, { tone: 'earned', heatStep: 1 });
    }
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }
}
