/** Mirrors the backend's GoalDtos (spec §5.4, §8.6). */

import { LogicalDate } from '../time/logical-date';

export type GoalKind = 'HABIT_ADHERENCE' | 'EXERCISE_TARGET' | 'RUN_DISTANCE' | 'BODY_METRIC' | 'CUSTOM';
export type GoalPeriodType = 'WEEK' | 'MONTH' | 'YEAR' | 'TARGET_DATE';
export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'FAILED' | 'ARCHIVED';

export interface Goal {
  id: string;
  title: string;
  description: string | null;
  kind: GoalKind;
  periodType: GoalPeriodType;
  startDate: LogicalDate;
  endDate: LogicalDate | null;
  rewardPoints: number;
  status: GoalStatus;
  habitId: string | null;
  exerciseId: string | null;
  targetValue: number | null;
  currentValue: number;
  progressFraction: number;
}

/**
 * Every field optional: a PATCH changes only what it names. Kind, period, start date and
 * what the goal measures are not here — changing what a goal counts halfway through
 * would mean its progress so far was measuring something else.
 */
export interface UpdateGoalPayload {
  title?: string;
  description?: string;
  targetDate?: LogicalDate;
  rewardPoints?: number;
  targetValue?: number;
}

export interface CreateGoalPayload {
  title: string;
  description?: string;
  kind: GoalKind;
  periodType: GoalPeriodType;
  startDate: LogicalDate;
  targetDate?: LogicalDate;
  rewardPoints?: number;
  habitId?: string;
  exerciseId?: string;
  targetValue?: number;
}
