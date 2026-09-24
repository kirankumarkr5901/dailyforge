/** Mirrors the backend's InsightDtos (spec §8.1, §8.1.1). */

import { Goal } from '../goal/goal.types';
import { LedgerEntry, PointsCategory, ScoreSnapshot } from '../points/points.types';
import { LogicalDate } from '../time/logical-date';

export interface Quote {
  id: string;
  text: string;
  author: string;
  source: string | null;
}

export interface HomeSummary {
  date: LogicalDate;
  quote: Quote | null;
  score: ScoreSnapshot;
  activeGoals: Goal[];
  recentLedger: LedgerEntry[];
}

export type DayState = 'BOTH' | 'WORKOUT' | 'RUN' | 'REST' | 'MISSED' | 'EMPTY';

export interface DailySummary {
  date: LogicalDate;
  pointsTotal: number;
  pointsByCategory: Partial<Record<PointsCategory, number>>;
  hasWorkout: boolean;
  hasRun: boolean;
  hasHabitCompletion: boolean;
  inactiveRunLength: number;
  state: DayState;
}

export interface CategoryGroup {
  category: PointsCategory;
  total: number;
  entries: LedgerEntry[];
}

export interface DayDetail {
  date: LogicalDate;
  total: number;
  groups: CategoryGroup[];
}
