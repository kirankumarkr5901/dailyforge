/** Mirrors the backend's ForgeDtos (Forge 66). */

import { PointsEnvelope } from '../points/points.types';
import { LogicalDate } from '../time/logical-date';

export type ForgeArea = 'FITNESS' | 'FOCUS' | 'HEALTH' | 'MINDSET' | 'LEARNING' | 'SOCIAL';
export type ForgeMode = 'NORMAL' | 'HARD';
export type ForgeDayStatus = 'DONE' | 'PARTIAL' | 'MISSED' | 'TODAY' | 'UPCOMING';

export interface ForgeOption {
  code: string;
  label: string;
}

export interface ForgeQuestion {
  code: string;
  prompt: string;
  hint: string | null;
  /** 1 for an ordinary question; more for "choose up to N". */
  maxPicks: number;
  options: ForgeOption[];
}

export interface ForgeQuestionnaire {
  lengthDays: number;
  questions: ForgeQuestion[];
}

export interface ForgeStartPayload {
  answers: Record<string, string[]>;
  mode: ForgeMode;
}

export interface ForgeStat {
  area: ForgeArea;
  xp: number;
}

export interface ForgeProfile {
  level: number;
  totalXp: number;
  xpIntoLevel: number;
  xpForNextLevel: number;
  currentStreak: number;
  bestStreak: number;
  daysCompleted: number;
  stats: ForgeStat[];
}

export interface ForgeQuest {
  id: string;
  code: string;
  title: string;
  description: string;
  area: ForgeArea;
  tier: number;
  xp: number;
  /** What completing it pays into the main score — computed by the server. */
  points: number;
  done: boolean;
}

export interface ForgeCalendarDay {
  date: LogicalDate;
  dayNumber: number;
  done: number;
  total: number;
  status: ForgeDayStatus;
}

export interface ForgeProgram {
  id: string;
  mode: ForgeMode;
  startDate: LogicalDate;
  endDate: LogicalDate;
  lengthDays: number;
  dayNumber: number;
  phase: number;
  focusAreas: ForgeArea[];
  quests: ForgeQuest[];
  dayComplete: boolean;
  calendar: ForgeCalendarDay[];
}

export interface ForgeFinishedProgram {
  id: string;
  status: 'COMPLETED' | 'ABANDONED';
  startDate: LogicalDate;
  endDate: LogicalDate;
  lengthDays: number;
  daysCompleted: number;
}

export interface ForgeOverview {
  today: LogicalDate;
  profile: ForgeProfile;
  program: ForgeProgram | null;
  lastProgram: ForgeFinishedProgram | null;
}

export interface ForgeQuestChange {
  forge: ForgeOverview;
  points: PointsEnvelope;
  leveledUp: boolean;
  dayCompleted: boolean;
}

export const FORGE_AREA_LABELS: Record<ForgeArea, string> = {
  FITNESS: 'Fitness',
  FOCUS: 'Focus',
  HEALTH: 'Health',
  MINDSET: 'Mindset',
  LEARNING: 'Learning',
  SOCIAL: 'Social',
};

export const FORGE_PHASE_LABELS: Record<number, string> = {
  1: 'Foundation',
  2: 'Build',
  3: 'Forge',
};
