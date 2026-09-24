import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { BodyMetric, MovingAveragePoint } from '../../../core/body/body.types';

const WIDTH = 320;
const HEIGHT = 120;
const PAD_X = 8;
const PAD_Y = 12;

/**
 * A weight trend chart — "body metrics graph is not built". BodySummary already
 * computed a moving-average series (movingAveragePoint[]) server-side; nothing ever
 * rendered it. Plain inline SVG rather than a charting library — the M4 README already
 * disclosed one is not yet part of the stack, and a line plus a scatter of raw points
 * does not need one.
 */
@Component({
  selector: 'df-body-weight-chart',
  templateUrl: './body-weight-chart.component.html',
  styleUrl: './body-weight-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BodyWeightChartComponent {
  readonly entries = input.required<readonly BodyMetric[]>();
  readonly movingAverage = input.required<readonly MovingAveragePoint[]>();

  protected readonly viewBox = `0 0 ${WIDTH} ${HEIGHT}`;

  private readonly range = computed(() => {
    const weights = this.entries().map((e) => e.weightKg);
    if (weights.length === 0) {
      return { min: 0, max: 1 };
    }
    const min = Math.min(...weights);
    const max = Math.max(...weights);
    // A flat line (every log the same weight) still needs a non-zero span to divide by.
    return min === max ? { min: min - 1, max: max + 1 } : { min, max };
  });

  private readonly dateOrder = computed(() => {
    const dates = [...new Set(this.entries().map((e) => e.date))].sort();
    return new Map(dates.map((date, i) => [date, dates.length > 1 ? i / (dates.length - 1) : 0.5]));
  });

  protected readonly linePath = computed(() => {
    const points = this.movingAverage();
    const order = this.dateOrder();
    if (points.length === 0) {
      return '';
    }
    return points
      .map((point, i) => {
        const x = this.xFor(order.get(point.date) ?? i / Math.max(1, points.length - 1));
        const y = this.yFor(point.average);
        return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  });

  protected readonly dots = computed(() => {
    const order = this.dateOrder();
    return this.entries().map((entry) => ({
      x: this.xFor(order.get(entry.date) ?? 0),
      y: this.yFor(entry.weightKg),
    }));
  });

  private xFor(fraction: number): number {
    return PAD_X + fraction * (WIDTH - PAD_X * 2);
  }

  private yFor(weight: number): number {
    const { min, max } = this.range();
    const fraction = (weight - min) / (max - min);
    // SVG y grows downward — a higher weight draws higher on the chart (smaller y).
    return HEIGHT - PAD_Y - fraction * (HEIGHT - PAD_Y * 2);
  }
}
