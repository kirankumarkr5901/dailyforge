/** Mirrors the backend's BodyDtos (spec §8.8). No points anywhere on this surface. */

import { LogicalDate } from '../time/logical-date';

export type BmiBand = 'UNDERWEIGHT' | 'NORMAL' | 'OVERWEIGHT' | 'OBESE';

export interface BodyMetric {
  id: string;
  date: LogicalDate;
  weightKg: number;
  heightCm: number | null;
  bodyFatPct: number | null;
  note: string | null;
}

export interface LogWeightPayload {
  date: LogicalDate;
  weightKg: number;
  heightCm?: number;
  bodyFatPct?: number;
  note?: string;
}

export interface MovingAveragePoint {
  date: LogicalDate;
  average: number;
}

export interface BodySummary {
  entries: BodyMetric[];
  currentWeightKg: number | null;
  bmi: number | null;
  band: BmiBand | null;
  deltaSinceLastLog: number | null;
  deltaSince30Days: number | null;
  movingAverage: MovingAveragePoint[];
  weeklyStreak: number;
}
