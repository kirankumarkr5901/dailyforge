/** Mirrors the backend's WorkoutDtos (spec §6, §8.3). */

import { LogicalDate } from '../time/logical-date';
import { PointsEnvelope } from '../points/points.types';

export type ExerciseKind = 'STRENGTH' | 'CARDIO';
export type Equipment = 'DUMBBELL' | 'BARBELL' | 'BODYWEIGHT' | 'MACHINE' | 'NONE';
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
}

export interface CreateExercisePayload {
  name: string;
  kind: ExerciseKind;
  equipment: Equipment;
  muscleGroups?: string[];
  isElite?: boolean;
}

export interface PlanExercise {
  id: string;
  exerciseId: string;
  exerciseName: string;
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
  recentPr: Pr | null;
  lifetimePr: Pr | null;
  sets: WorkoutSet[];
}

export interface WorkoutSession {
  id: string;
  date: LogicalDate;
  planId: string | null;
  dayIndex: number | null;
  completed: boolean;
  exercises: ExerciseBoardEntry[];
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
