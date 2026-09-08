/** Mirrors the backend's PointsDtos (spec §5, §7). */

export type PointsCategory =
  | 'WORKOUT'
  | 'RUN'
  | 'HABIT'
  | 'ACTIVITY'
  | 'GOAL'
  | 'JOB'
  | 'REWARD'
  | 'ADJUSTMENT';

export interface LedgerEntry {
  id: string;
  occurredOn: string;
  category: PointsCategory;
  ruleCode: string;
  amount: number;
  description: string;
  reversed: boolean;
  isReversal: boolean;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface Celebration {
  type: 'PR' | 'STREAK' | 'MILESTONE' | 'ALL_HABITS_DONE' | 'WORKOUT_COMPLETE';
  details: Record<string, unknown>;
}

/** What every mutating endpoint embeds (spec §7): the caller never has to ask separately. */
export interface PointsEnvelope {
  delta: number;
  newTotal: number;
  celebrations: Celebration[];
}

export interface ScoreSnapshot {
  total: number;
  today: number;
  thisWeek: number;
  thisMonth: number;
  byCategory: Record<PointsCategory, number>;
}
