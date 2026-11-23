/** Mirrors the backend's WorkoutDtos (spec §6, §8.3). */

import { LogicalDate } from '../time/logical-date';
import { PointsEnvelope } from '../points/points.types';

export type ExerciseKind = 'STRENGTH' | 'CARDIO';
export type Equipment = 'DUMBBELL' | 'BARBELL' | 'BODYWEIGHT' | 'MACHINE' | 'CABLE' | 'NONE';
export type WeightMode = 'SINGLE' | 'COMBINED';

export interface Exercise {
  id: string;
  name: string;
  kind: ExerciseKind;
  equipment: Equipment;
  muscleGroups: string[];
  isElite: boolean;
  ownedByMe: boolean;
  archived: boolean;
  /** Sent back as If-Match when editing, so a stale device cannot overwrite a newer one. */
  version: number;
}

export interface CreateExercisePayload {
  name: string;
  kind: ExerciseKind;
  equipment: Equipment;
  muscleGroups?: string[];
  isElite?: boolean;
}

/** Every field optional: a PATCH changes only what it names. */
export interface UpdateExercisePayload {
  name?: string;
  kind?: ExerciseKind;
  equipment?: Equipment;
  muscleGroups?: string[];
  isElite?: boolean;
}

export interface PlanExercise {
  id: string;
  exerciseId: string;
  exerciseName: string;
  muscleGroups: string[];
  isElite: boolean;
  dayIndex: number;
  sortOrder: number;
  targetSets: number | null;
  targetReps: number | null;
  notes: string | null;
}

export interface WorkoutPlan {
  id: string;
  name: string;
  dayCount: number;
  dayLabels: string[];
  isActive: boolean;
  exercises: PlanExercise[];
}

export interface Pr {
  totalWeightKg: number;
  reps: number;
  achievedOn: LogicalDate;
}

export interface WorkoutSet {
  id: string;
  /** Echoed back on edit so a stale device cannot overwrite a newer one. */
  version: number;
  exerciseId: string;
  setNumber: number;
  enteredWeight: number;
  weightMode: WeightMode;
  addedWeight: number | null;
  reps: number;
  totalWeightKg: number;
}

export interface ExerciseBoardEntry {
  exerciseId: string;
  name: string;
  kind: ExerciseKind;
  equipment: Equipment;
  muscleGroups: string[];
  isElite: boolean;
  recentPr: Pr | null;
  lifetimePr: Pr | null;
  sets: WorkoutSet[];
  /** Only an exercise you own can be edited; the shared catalogue is not yours to rename. */
  ownedByMe: boolean;
  /** Echoed back as If-Match when editing. */
  version: number;
  /**
   * When you said you were finished with this exercise, or null.
   *
   * The third state the board needs: sets logged says the work started, this says it
   * finished. No set count can stand in for it.
   */
  completedAt: string | null;
  /** The plan's target for today; null on a freeform session or when none was set. */
  targetSets: number | null;
  targetReps: number | null;
  notes: string | null;
  /** Present only when last time every working set hit the target reps. */
  progression: Progression | null;
}

/** "Last time: 4 x 8 at 60 kg, every set on target. Try 62.5 kg." Weights are totals, as the board shows them. */
export interface Progression {
  lastDate: LogicalDate;
  lastWeightKg: number;
  lastSets: number;
  lastReps: number;
  suggestedWeightKg: number;
}

export interface WorkoutSession {
  id: string;
  date: LogicalDate;
  planId: string | null;
  dayIndex: number | null;
  completed: boolean;
  exercises: ExerciseBoardEntry[];
  /** This week is a deload: the targets on the board are already halved. */
  deload: boolean;
}

export interface LogSetPayload {
  date: LogicalDate;
  exerciseId: string;
  planId?: string | null;
  dayIndex?: number | null;
  enteredWeight?: number;
  weightMode: WeightMode;
  addedWeight?: number;
  reps: number;
}

export interface UpdateSetPayload {
  enteredWeight?: number;
  weightMode: WeightMode;
  addedWeight?: number;
  reps: number;
}

export interface SetWriteResponse {
  set: WorkoutSet;
  points: PointsEnvelope;
}

export interface DeleteSetResponse {
  points: PointsEnvelope;
}

export interface ExerciseHistoryEntry {
  date: LogicalDate;
  set: WorkoutSet;
}

/** Sets per primary muscle for one Monday-to-Sunday week, with the usual target band. */
export interface WeeklyVolume {
  weekStart: LogicalDate;
  weekEnd: LogicalDate;
  targetLow: number;
  targetHigh: number;
  muscles: { group: string; sets: number }[];
}
