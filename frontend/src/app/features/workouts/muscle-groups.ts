/**
 * Every main muscle group a gym exercise typically targets — split out from the
 * broader groupings the catalog seed still uses (V6__seed_exercise_catalog.sql: "arms"
 * for a pull-up, "legs" for a squat) so a card can say "biceps" or "quads" specifically
 * rather than only the broad limb it belongs to.
 *
 * Shared by the picker (choosing them when creating) and the edit sheet (changing them
 * afterwards), so the two can never drift into offering different lists.
 */
export const MUSCLE_GROUPS = [
  'chest',
  'back',
  'lats',
  'traps',
  'shoulders',
  'biceps',
  'triceps',
  'forearms',
  'core',
  'obliques',
  'quads',
  'hamstrings',
  'glutes',
  'calves',
] as const;

export const KIND_OPTIONS = [
  { value: 'STRENGTH', label: 'Strength' },
  { value: 'CARDIO', label: 'Cardio' },
] as const;

export const EQUIPMENT_OPTIONS = [
  { value: 'BARBELL', label: 'Barbell' },
  { value: 'DUMBBELL', label: 'Dumbbell' },
  { value: 'MACHINE', label: 'Machine' },
  { value: 'CABLE', label: 'Cable' },
  { value: 'BODYWEIGHT', label: 'Bodyweight' },
  { value: 'NONE', label: 'None' },
] as const;
