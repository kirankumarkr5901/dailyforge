/** Mirrors the backend's ProteinDtos. */

import { LogicalDate } from '../time/logical-date';
import { PointsEnvelope } from '../points/points.types';

/** `grams` is protein, kept under its original name. */
export interface ProteinFood {
  id: string;
  name: string;
  grams: number;
  kcal: number;
  fatG: number;
  carbsG: number;
  fiberG: number;
  sugarG: number;
}

export interface ProteinEntry {
  id: string;
  foodName: string;
  grams: number;
  kcal: number;
  fatG: number;
  carbsG: number;
  fiberG: number;
  sugarG: number;
}

/** What one entry or food adds. At least one must be above zero. */
export interface NutritionAmounts {
  grams: number;
  kcal: number;
  fatG: number;
  carbsG: number;
  fiberG: number;
  sugarG: number;
}

export interface ProteinDay {
  date: LogicalDate;
  targetG: number;
  totalG: number;
  /** Calories to stay under today. */
  calorieBudget: number;
  totalKcal: number;
  fatTargetG: number;
  totalFatG: number;
  carbsTargetG: number;
  totalCarbsG: number;
  fiberTargetG: number;
  totalFiberG: number;
  /** Sugar is a limit to stay under. */
  sugarLimitG: number;
  totalSugarG: number;
  entries: ProteinEntry[];
  foods: ProteinFood[];
}

export interface ProteinDayWrite {
  day: ProteinDay;
  points: PointsEnvelope;
}
