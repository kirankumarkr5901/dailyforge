import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { Equipment, ExerciseHistoryEntry } from '../../../../core/workouts/workouts.types';
import { LogicalDate } from '../../../../core/time/logical-date';

interface Point {
  date: LogicalDate;
  value: number;
  reps: number;
  x: number;
  y: number;
}

const W = 320;
const H = 150;
const PAD = { top: 16, right: 12, bottom: 22, left: 36 };

function shortDate(date: LogicalDate): string {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString(undefined, { day: 'numeric', month: 'short', timeZone: 'UTC' });
}

/**
 * Best set per session over time: one point per day, the heaviest set that day (ties go
 * to more reps). Bodyweight work with no added weight plots best reps instead, since its
 * weight would be a flat zero. A single series, so no legend; the history list under it
 * is the table view. Tap a point to read it.
 */
@Component({
  selector: 'df-progress-chart',
  templateUrl: './progress-chart.component.html',
  styleUrl: './progress-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgressChartComponent {
  readonly entries = input.required<readonly ExerciseHistoryEntry[]>();
  readonly equipment = input<Equipment>('BARBELL');

  protected readonly W = W;
  protected readonly H = H;
  protected readonly PAD = PAD;

  protected readonly selected = signal<number | null>(null);

  /** Reps when every set is plain bodyweight; otherwise the weight. */
  protected readonly byReps = computed(
    () => this.equipment() === 'BODYWEIGHT' && this.entries().every((e) => (e.set.addedWeight ?? 0) === 0),
  );

  private readonly best = computed(() => {
    const perDay = new Map<LogicalDate, { value: number; reps: number }>();
    for (const e of this.entries()) {
      const value = this.byReps() ? e.set.reps : e.set.totalWeightKg;
      const current = perDay.get(e.date);
      if (!current || value > current.value || (value === current.value && e.set.reps > current.reps)) {
        perDay.set(e.date, { value, reps: e.set.reps });
      }
    }
    return [...perDay.entries()].sort(([a], [b]) => (a < b ? -1 : 1)).map(([date, v]) => ({ date, ...v }));
  });

  protected readonly scale = computed(() => {
    const values = this.best().map((p) => p.value);
    let min = Math.min(...values);
    let max = Math.max(...values);
    if (min === max) {
      min = Math.max(0, min - 5);
      max = max + 5;
    }
    const pad = (max - min) * 0.1;
    return { min: Math.max(0, min - pad), max: max + pad };
  });

  protected readonly points = computed<Point[]>(() => {
    const best = this.best();
    const { min, max } = this.scale();
    const innerW = W - PAD.left - PAD.right;
    const innerH = H - PAD.top - PAD.bottom;
    return best.map((p, i) => ({
      ...p,
      x: PAD.left + (best.length === 1 ? innerW / 2 : (i / (best.length - 1)) * innerW),
      y: PAD.top + (1 - (p.value - min) / (max - min)) * innerH,
    }));
  });

  protected readonly path = computed(() =>
    this.points().map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' '),
  );

  /** Three horizontal guides: bottom, middle, top of the scale. */
  protected readonly gridlines = computed(() => {
    const { min, max } = this.scale();
    const innerH = H - PAD.top - PAD.bottom;
    return [0, 0.5, 1].map((f) => ({
      y: PAD.top + (1 - f) * innerH,
      label: String(Math.round(min + f * (max - min))),
    }));
  });

  protected readonly summary = computed(() => {
    const pts = this.points();
    if (pts.length < 2) return '';
    const first = pts[0];
    const last = pts[pts.length - 1];
    const unit = this.byReps() ? ' reps' : ' kg';
    return `Best set per session, from ${first.value}${unit} on ${shortDate(first.date)} to ${last.value}${unit} on ${shortDate(last.date)}.`;
  });

  protected readonly firstLabel = computed(() => (this.points().length ? shortDate(this.points()[0].date) : ''));
  protected readonly lastLabel = computed(() => {
    const pts = this.points();
    return pts.length ? shortDate(pts[pts.length - 1].date) : '';
  });

  protected label(p: Point): string {
    const value = this.byReps()
      ? `${p.value} reps`
      : this.equipment() === 'BODYWEIGHT'
        ? `BW + ${p.value} kg × ${p.reps}`
        : `${p.value} kg × ${p.reps}`;
    return `${value} · ${shortDate(p.date)}`;
  }

  protected toggle(i: number): void {
    this.selected.update((s) => (s === i ? null : i));
  }
}
