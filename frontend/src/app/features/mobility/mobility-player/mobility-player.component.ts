import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { MobilityApi, MobilityCompletion, MobilityFlow } from '../../../core/mobility/mobility.api';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';

/**
 * The guided flow player: one move at a time, a countdown, what to feel, and a beep at
 * each switch. Timing comes from a deadline, not from counting ticks, so a throttled
 * background tab cannot stretch a 30-second hold into a minute. Playing the last move
 * to its end records the flow (and its points) for today.
 */
@Component({
  selector: 'df-mobility-player',
  imports: [DfButtonComponent],
  templateUrl: './mobility-player.component.html',
  styleUrl: './mobility-player.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'close()' },
})
export class MobilityPlayerComponent {
  private readonly api = inject(MobilityApi);

  /** The flow to play; null keeps the player closed. */
  readonly flow = input<MobilityFlow | null>(null);
  readonly closed = output<void>();
  /** Emitted once the finished flow has been recorded, with what it paid. */
  readonly completed = output<MobilityCompletion>();

  protected readonly index = signal(0);
  protected readonly secondsLeft = signal(0);
  protected readonly paused = signal(false);
  protected readonly finished = signal(false);
  protected readonly saveFailed = signal(false);

  protected readonly move = computed(() => this.flow()?.moves[this.index()] ?? null);
  protected readonly next = computed(() => this.flow()?.moves[this.index() + 1] ?? null);
  protected readonly progress = computed(() => {
    const flow = this.flow();
    if (!flow) return 0;
    if (this.finished()) return 100;
    const before = flow.moves.slice(0, this.index()).reduce((a, m) => a + m.seconds, 0);
    const current = this.move()?.seconds ?? 0;
    return ((before + current - this.secondsLeft()) / flow.totalSeconds) * 100;
  });

  private readonly pauseButton = viewChild<ElementRef<HTMLButtonElement>>('pauseButton');
  private endsAt = 0;
  private timer: ReturnType<typeof setInterval> | null = null;
  private audio: AudioContext | null = null;
  private lastFocus: HTMLElement | null = null;

  constructor() {
    effect(() => {
      const flow = this.flow();
      untracked(() => (flow ? this.start() : this.stop()));
    });
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  private start(): void {
    this.lastFocus = document.activeElement as HTMLElement | null;
    this.finished.set(false);
    this.saveFailed.set(false);
    this.paused.set(false);
    this.load(0);
    this.beep(660, 0.15);
    this.stopTimer();
    this.timer = setInterval(() => this.tick(), 250);
    setTimeout(() => this.pauseButton()?.nativeElement.focus());
  }

  private stop(): void {
    this.stopTimer();
    this.lastFocus?.focus?.();
    this.lastFocus = null;
  }

  private stopTimer(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  private load(i: number): void {
    const move = this.flow()!.moves[i];
    this.index.set(i);
    this.secondsLeft.set(move.seconds);
    this.endsAt = Date.now() + move.seconds * 1000;
  }

  private tick(): void {
    if (this.paused() || this.finished() || !this.flow()) return;
    const left = Math.max(0, Math.ceil((this.endsAt - Date.now()) / 1000));
    if (left !== this.secondsLeft()) {
      this.secondsLeft.set(left);
      if (left > 0 && left <= 3) this.beep(520, 0.08);
    }
    if (left === 0) this.advance();
  }

  private advance(): void {
    const flow = this.flow()!;
    if (this.index() < flow.moves.length - 1) {
      this.beep(780, 0.2);
      this.load(this.index() + 1);
    } else {
      void this.finish();
    }
  }

  private async finish(): Promise<void> {
    this.finished.set(true);
    this.stopTimer();
    this.beep(990, 0.35);
    try {
      this.completed.emit(await firstValueFrom(this.api.complete(this.flow()!.code)));
    } catch {
      this.saveFailed.set(true);
    }
  }

  protected retrySave(): void {
    this.saveFailed.set(false);
    void this.finish();
  }

  protected togglePause(): void {
    if (this.finished()) return;
    if (this.paused()) {
      // Resume where it stopped: the deadline moves by however long the pause lasted.
      this.endsAt = Date.now() + this.secondsLeft() * 1000;
    }
    this.paused.update((p) => !p);
  }

  protected skip(): void {
    if (!this.finished()) this.advance();
  }

  protected back(): void {
    if (!this.finished() && this.index() > 0) {
      this.load(this.index() - 1);
    }
  }

  protected close(): void {
    if (this.flow()) this.closed.emit();
  }

  private beep(freq: number, seconds: number): void {
    try {
      this.audio ??= new AudioContext();
      const osc = this.audio.createOscillator();
      const gain = this.audio.createGain();
      osc.frequency.value = freq;
      osc.connect(gain);
      gain.connect(this.audio.destination);
      gain.gain.setValueAtTime(0.18, this.audio.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, this.audio.currentTime + seconds);
      osc.start();
      osc.stop(this.audio.currentTime + seconds);
    } catch {
      // No audio (blocked or unsupported): the countdown still works silently.
    }
  }
}
