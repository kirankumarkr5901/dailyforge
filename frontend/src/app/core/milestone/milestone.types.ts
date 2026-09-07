/** Mirrors the backend's MilestoneRecapResponse (spec §7 extension — "milestones for
 * every month, yearly", owner feedback). */

import { PointsCategory } from '../points/points.types';
import { LogicalDate } from '../time/logical-date';

export type RecapPeriod = 'MONTH' | 'YEAR';

export interface MilestoneRecap {
  startDate: LogicalDate;
  endDate: LogicalDate;
  totalPoints: number;
  byCategory: Partial<Record<PointsCategory, number>>;
  workoutDays: number;
  runDays: number;
  runDistanceMeters: number;
  habitsCompleted: number;
  goalsCompleted: number;
}
