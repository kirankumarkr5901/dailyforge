import { ChangeDetectionStrategy, Component, effect, input, signal } from '@angular/core';

interface Particle {
  readonly id: number;
  readonly left: string;
  readonly delay: string;
  readonly duration: string;
  readonly drift: string;
  readonly heatStep: 1 | 2 | 3 | 4;
  readonly rotate: string;
}

const PARTICLE_COUNT = 18;

/**
 * A one-shot burst of warm-toned particles — "cold steel, earned heat" made literal for
 * the one moment the spec calls out by name: every set in a session logged (spec §8.3's
 * whole-session completion celebration). Fires once per `trigger()` tick and clears
 * itself; nothing lingers to distract from the next screen.
 *
 * `prefers-reduced-motion` swaps the particle burst for a brief static badge — the
 * moment still registers, nothing moves.
 */
@Component({
  selector: 'df-celebration',
  imports: [],
  templateUrl: './df-celebration.component.html',
  styleUrl: './df-celebration.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DfCelebrationComponent {
  /** Bump this input (e.g. a counter) to fire a new burst. */
  readonly trigger = input<number>(0);
  readonly label = input('Session complete!');

  protected readonly active = signal(false);
  protected readonly particles = signal<Particle[]>([]);

  constructor() {
    let last: number | undefined;
    effect(() => {
      const value = this.trigger();
      if (value === last || value === 0) {
        last = value;
        return;
      }
      last = value;
      this.fire();
    });
  }

  private fire(): void {
    this.particles.set(buildParticles());
    this.active.set(true);
    setTimeout(() => this.active.set(false), 1500);
  }
}

function buildParticles(): Particle[] {
  const particles: Particle[] = [];
  for (let i = 0; i < PARTICLE_COUNT; i++) {
    particles.push({
      id: i,
      left: `${Math.random() * 100}%`,
      delay: `${Math.random() * 150}ms`,
      duration: `${900 + Math.random() * 500}ms`,
      drift: `${(Math.random() - 0.5) * 80}px`,
      heatStep: (Math.floor(Math.random() * 4) + 1) as 1 | 2 | 3 | 4,
      rotate: `${Math.random() * 360}deg`,
    });
  }
  return particles;
}
