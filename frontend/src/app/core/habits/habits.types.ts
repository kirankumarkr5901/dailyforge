/** Mirrors the backend's HabitDtos (spec §4.3, §5.4, §8). */

import { LogicalDate } from '../time/logical-date';
import { PointsEnvelope } from '../points/points.types';

export type HabitType = 'NORMAL' | 'STRICT';

export type HabitDayState = 'DONE' | 'MISSED' | 'PENDING' | 'PLANNED';

export interface Habit {
  id: string;
  name: string;
  icon: string;
  points: number;
  type: HabitType;
  penaltyPoints: number;
  baseBonus: number;
  bonusMultiplier: number;
  /** Bitmask, bit0 = Monday .. bit6 = Sunday (spec §5.4). */
  scheduleDays: number;
  sortOrder: number;
  activeFrom: LogicalDate;
  archived: boolean;
}

export interface CreateHabitPayload {
  name: string;
  icon: string;
  points: number;
  type: HabitType;
  penaltyPoints?: number;
  /** Left unset to take the HABIT_CONSISTENCY default (spec §5.4). */
  baseBonus?: number;
  bonusMultiplier?: number;
  scheduleDays?: number;
}

export interface UpdateHabitPayload {
  name?: string;
  icon?: string;
  points?: number;
  type?: HabitType;
  penaltyPoints?: number;
  scheduleDays?: number;
}

export interface BoardEntry {
  id: string;
  name: string;
  icon: string;
  points: number;
  type: HabitType;
  state: HabitDayState;
  /** Only meaningful when state is PLANNED: has this future day already been pre-ticked? */
  plannedDone: boolean;
  currentStreak: number;
  bestStreak: number;
  editable: boolean;
}

export interface HabitBoard {
  date: LogicalDate;
  habits: BoardEntry[];
  /** Server-computed, never client-derived (spec §8.4). Null when nothing is close. */
  bonusHint: string | null;
}

export interface BonusPreview {
  /** The bonus a 7/14/21/28-day streak would earn at these settings (spec §8.2). */
  firstFourBonuses: number[];
}

export interface HabitLogResponse {
  points: PointsEnvelope;
}
