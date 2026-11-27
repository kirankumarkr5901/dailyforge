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
  /** LIST (starter foods and Settings), or saved from a meal PHOTO or a BARCODE scan. */
  source: 'LIST' | 'PHOTO' | 'BARCODE';
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
/** One dish read from a meal photo by Gemini: estimates for the portion in the photo. */
export interface MealPhotoDish {
  name: string;
  portionG: number;
  proteinG: number;
  kcal: number;
  fatG: number;
  carbsG: number;
  fiberG: number;
  sugarG: number;
}

/** "What should I eat to hit protein?": `foodId` is set when it's one of the user's foods. */
export interface ProteinSuggestion {
  name: string;
  foodId: string | null;
  why: string;
  proteinG: number;
  kcal: number;
  fatG: number;
  carbsG: number;
  fiberG: number;
  sugarG: number;
}

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
  waterTargetMl: number;
  waterMl: number;
  entries: ProteinEntry[];
  foods: ProteinFood[];
}

export interface ProteinDayWrite {
  day: ProteinDay;
  points: PointsEnvelope;
}

/** One item of a meal: a copy of the food's values when the meal was saved. */
export interface MealItem extends NutritionAmounts {
  foodName: string;
}

/** A meal shortcut: logged with one tap, item by item. */
export interface Meal {
  id: string;
  name: string;
  proteinG: number;
  kcal: number;
  items: MealItem[];
}

/** A packaged food from Open Food Facts: values per 100 g. */
export interface BarcodeProduct {
  code: string;
  name: string;
  proteinG: number;
  kcal: number;
  fatG: number;
  carbsG: number;
  fiberG: number;
  sugarG: number;
  /** The product's own serving size in grams, when it has one. */
  servingG: number | null;
}
