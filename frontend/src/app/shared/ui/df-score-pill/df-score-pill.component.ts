import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { MotionService } from '../../../core/motion/motion.service';

/**
 * Maps an earned amount onto the heat ramp. Step 0 is the unearned state and is cold:
 * a score of zero is not a quantity the user earned, so it may not be warm.
 */
export type DfHeatStep = 0 | 1 | 2 | 3 | 4;

@Component({
  selector: 'df-score-pill',
  templateUrl: './df-score-pill.component.html',
  styleUrl: './df-score-pill.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'df-score-pill',
    '[class.df-score-pill--blooming]': 'blooming()',
    '[class.df-score-pill--pending]': 'pending()',
    '[attr.data-heat]': 'heatStep()',
  },
})
export class DfScorePillComponent {
  private readonly motion = inject(MotionService);
  private readonly host = inject(ElementRef<HTMLElement>);

  /** Authoritative total from the server. */
  readonly total = input.required<number>();
  /** True while an optimistic local delta has not yet been confirmed by the server. */
  readonly pending = input(false);

  /**
   * Lower bounds for heat steps 1–4, by absolute amount.
   *
   * These are *display* thresholds — where a quantity sits on the visual ramp — not
   * point values, so they live here rather than in `points_rule_config`. Nothing here
   * decides what anything is worth; the server already did that.
   */
  readonly heatThresholds = input<readonly [number, number, number, number]>([0, 25, 100, 300]);

  protected readonly shown = signal<number | null>(null);
  protected readonly blooming = signal(false);
  protected readonly delta = signal<number | null>(null);

  protected readonly display = computed(() => {
    const value = this.shown() ?? this.total();
    return value.toLocaleString('en-GB');
  });

  protected readonly deltaLabel = computed(() => {
    const value = this.delta();
    if (value === null) {
      return null;
    }
    return value >= 0 ? `+${value}` : `${value}`;
  });

  /**
   * The pill is warm at rest, because a score is an earned quantity and this system
   * says an earned quantity is where warmth belongs. Its step follows the running
   * total until an award is in flight, when it follows that award instead — so a big
   * gain reads hotter than a small one at the moment it lands.
   */
  protected readonly heatStep = computed<DfHeatStep>(() => {
    const delta = this.delta();
    const amount = delta !== null ? Math.abs(delta) : Math.abs(this.total());
    // Nothing earned, nothing warm — the resting state of a new account is cold steel
    // like everything else on the page.
    return amount === 0 ? 0 : this.stepFor(amount);
  });

  private frame = 0;

  award(from: number, to: number): void {
    this.delta.set(to - from);

    if (this.motion.reduced()) {
      this.shown.set(to);
      this.clearDeltaSoon();
      return;
    }

    this.blooming.set(true);
    this.countUp(from, to);
  }

  private stepFor(amount: number): DfHeatStep {
    const [, two, three, four] = this.heatThresholds();
    if (amount >= four) return 4;
    if (amount >= three) return 3;
    if (amount >= two) return 2;
    return 1;
  }

  private countUp(from: number, to: number): void {
    cancelAnimationFrame(this.frame);

    const view = this.host.nativeElement.ownerDocument.defaultView;
    if (!view) {
      this.shown.set(to);
      return;
    }

    const duration = 700;
    const start = view.performance.now();

    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / duration);
      // Exponential settle: fast at first, then eases into the final value.
      const eased = 1 - Math.pow(1 - progress, 3);
      this.shown.set(Math.round(from + (to - from) * eased));

      if (progress < 1) {
        this.frame = view.requestAnimationFrame(tick);
      } else {
        this.shown.set(to);
        this.blooming.set(false);
        this.clearDeltaSoon();
      }
    };

    this.frame = view.requestAnimationFrame(tick);
  }

  private clearDeltaSoon(): void {
    const view = this.host.nativeElement.ownerDocument.defaultView;
    view?.setTimeout(() => this.delta.set(null), 1600);
  }
}
