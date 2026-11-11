/** Mirrors the backend's InsightDtos badge records. */

import { PointsEnvelope } from '../points/points.types';
import { LogicalDate } from '../time/logical-date';
import { RecapPeriod } from './milestone.types';

/**
 * Which figure a badge is scored against. Every one already exists on the recap — a
 * badge puts a threshold and a name on a number the page above it already shows.
 */
export type BadgeMetric =
  | 'TOTAL_POINTS'
  | 'WORKOUT_DAYS'
  | 'RUN_DAYS'
  | 'RUN_DISTANCE_METERS'
  | 'HABITS_COMPLETED'
  | 'GOALS_COMPLETED';

export interface Badge {
  code: string;
  name: string;
  /** What kind of badge this is. */
  description: string;
  /** What to do to earn it, in plain terms. */
  criteria: string;
  period: RecapPeriod;
  metric: BadgeMetric;
  /** Where this user has got to, in the metric's own units. */
  value: number;
  threshold: number;
  /** What claiming it pays. */
  points: number;
  icon: string;
  periodStart: LogicalDate;
  periodEnd: LogicalDate;
  earned: boolean;
  claimed: boolean;
  /** Earned and not yet taken — the only state with an action attached. */
  claimable: boolean;
  claimedAt: string | null;
}

export interface BadgeClaimResponse {
  badge: Badge;
  points: PointsEnvelope;
}
