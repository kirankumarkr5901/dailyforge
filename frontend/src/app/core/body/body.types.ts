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
  /** Tape measurements in cm; null when not taken that day. */
  waistCm: number | null;
  chestCm: number | null;
  armCm: number | null;
  hipsCm: number | null;
  thighCm: number | null;
}

export type MeasurementKey = 'waistCm' | 'chestCm' | 'armCm' | 'hipsCm' | 'thighCm';

export const MEASUREMENTS: readonly { key: MeasurementKey; label: string; min: number; max: number }[] = [
  { key: 'waistCm', label: 'Waist', min: 20, max: 250 },
  { key: 'chestCm', label: 'Chest', min: 20, max: 250 },
  { key: 'armCm', label: 'Arm', min: 10, max: 100 },
  { key: 'hipsCm', label: 'Hips', min: 20, max: 250 },
  { key: 'thighCm', label: 'Thigh', min: 10, max: 150 },
];

export interface LogWeightPayload {
  date: LogicalDate;
  weightKg: number;
  heightCm?: number;
  bodyFatPct?: number;
  note?: string;
  waistCm?: number;
  chestCm?: number;
  armCm?: number;
  hipsCm?: number;
  thighCm?: number;
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
  /** Latest 7-day average weight. */
  weeklyAverageKg: number | null;
  /** How much that average moved over two weeks; null without enough history. */
  weeklyAverageChange14Days: number | null;
  /** The average moved less than 0.2 kg in two weeks. */
  stalled: boolean;
}
