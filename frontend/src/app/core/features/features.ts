/**
 * Features a user can switch off for now (Settings → Features). Off means hidden from
 * the menu, Home and reminders, with their automatic points paused; the data stays and
 * comes back when switched on. Home, Workout and Settings are never switchable.
 */
export type FeatureCode = 'NUTRITION' | 'GOALS' | 'FORGE' | 'JOBS' | 'SKILLS' | 'MOBILITY';

export const FEATURES: readonly { code: FeatureCode; label: string; hint: string }[] = [
  { code: 'NUTRITION', label: 'Nutrition', hint: 'Protein, calories and macros; calorie-budget points.' },
  { code: 'GOALS', label: 'Goals', hint: 'Goals and their rewards.' },
  { code: 'FORGE', label: 'Forge 66', hint: 'The 66-day program.' },
  { code: 'JOBS', label: 'Jobs', hint: 'The job application tracker.' },
  { code: 'SKILLS', label: 'Skills', hint: 'Handstand, crow + L-sit and flips.' },
  { code: 'MOBILITY', label: 'Mobility', hint: 'Guided flows and the Warm-up button.' },
];
