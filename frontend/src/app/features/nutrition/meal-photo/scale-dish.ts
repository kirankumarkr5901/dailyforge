import { MealItem, MealPhotoDish } from '../../../core/nutrition/protein.types';

/**
 * A dish's estimate scaled to the portion the user set, as an entry to log: whole
 * numbers, inside the app's per-entry limits, the name trimmed to 60 characters.
 */
export function scaleDish(estimate: MealPhotoDish, name: string, portionG: number): MealItem {
  const k = estimate.portionG > 0 ? portionG / estimate.portionG : 1;
  const n = (v: number, max: number) => Math.max(0, Math.min(max, Math.round(v * k)));
  return {
    foodName: name.trim().slice(0, 60),
    grams: n(estimate.proteinG, 200),
    kcal: n(estimate.kcal, 3000),
    fatG: n(estimate.fatG, 300),
    carbsG: n(estimate.carbsG, 500),
    fiberG: n(estimate.fiberG, 100),
    sugarG: n(estimate.sugarG, 300),
  };
}
