import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Flame, LucideAngularModule, LucideIconData, Target, Timer, Undo2, X, Zap } from 'lucide-angular';
import { OnboardingService } from '../../../core/onboarding/onboarding.service';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';

interface Step {
  readonly icon: LucideIconData;
  readonly title: string;
  readonly body: string;
  readonly action?: { readonly label: string; readonly route: string };
}

/**
 * The 5-step first-launch guide (spec §8.11), skippable at every step and replayable
 * from Settings. A full-screen takeover rather than the bottom-sheet primitive: a tour
 * introduces the whole app, and anchoring it to the bottom the way an ordinary form
 * sheet is would read as one more thing to fill in, not a welcome.
 */
@Component({
  selector: 'df-onboarding-tour',
  imports: [LucideAngularModule, DfButtonComponent],
  templateUrl: './onboarding-tour.component.html',
  styleUrl: './onboarding-tour.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingTourComponent {
  private readonly onboarding = inject(OnboardingService);
  private readonly router = inject(Router);

  protected readonly closeIcon = X;

  protected readonly steps: readonly Step[] = [
    {
      icon: Zap,
      title: 'Everything you log becomes points',
      body: 'Every workout, run, habit and activity feeds one score. That score is yours to spend.',
    },
    {
      icon: Target,
      title: 'Build a plan first',
      body: 'A workout or habit plan is what turns logging into a habit itself.',
      action: { label: 'Open the planner', route: '/habits' },
    },
    {
      icon: Timer,
      title: 'Log in two taps',
      body: 'The bottom bar puts your most-used screens one tap away, and logging is one tap from there.',
    },
    {
      icon: Flame,
      title: 'Streaks pay bonuses',
      body: 'Every 7 days of a habit streak earns a growing bonus — the longer it runs, the more it pays.',
    },
    {
      icon: Undo2,
      title: 'Undo is always available',
      body: 'Every points-earning action can be undone. Your points come back with it, every time.',
    },
  ];

  protected readonly index = signal(0);
  protected readonly isLast = () => this.index() === this.steps.length - 1;

  protected next(): void {
    if (this.isLast()) {
      void this.onboarding.finish();
    } else {
      this.index.update((i) => i + 1);
    }
  }

  protected back(): void {
    this.index.update((i) => Math.max(0, i - 1));
  }

  protected goTo(i: number): void {
    this.index.set(i);
  }

  protected skip(): void {
    this.onboarding.skip();
  }

  protected async runAction(route: string): Promise<void> {
    await this.onboarding.finish();
    void this.router.navigateByUrl(route);
  }
}
