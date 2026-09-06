import {
  Apple,
  BookOpen,
  Brain,
  Bike,
  Cigarette,
  Coffee,
  Dumbbell,
  Droplet,
  Footprints,
  Guitar,
  HeartPulse,
  Languages,
  Moon,
  PenLine,
  Smile,
  Sun,
  Sunrise,
} from 'lucide-angular';
import { LucideIconData } from 'lucide-angular';

export interface HabitIconOption {
  /** Stored on the habit as-is (backend `icon` field is any string up to 40 chars). */
  key: string;
  label: string;
  icon: LucideIconData;
}

/**
 * A curated vocabulary rather than an open picker. Spec §9.6's plain-language rule
 * applies to iconography too: a fixed, named set reads consistently across every
 * habit card, where a free-for-all icon search would not.
 */
export const HABIT_ICONS: readonly HabitIconOption[] = [
  { key: 'book', label: 'Reading', icon: BookOpen },
  { key: 'water', label: 'Water', icon: Droplet },
  { key: 'dumbbell', label: 'Exercise', icon: Dumbbell },
  { key: 'run', label: 'Walk / run', icon: Footprints },
  { key: 'bike', label: 'Cycling', icon: Bike },
  { key: 'sunrise', label: 'Early start', icon: Sunrise },
  { key: 'sun', label: 'Sunlight', icon: Sun },
  { key: 'moon', label: 'Sleep', icon: Moon },
  { key: 'meditate', label: 'Meditation', icon: Brain },
  { key: 'heart', label: 'Health', icon: HeartPulse },
  { key: 'journal', label: 'Journaling', icon: PenLine },
  { key: 'language', label: 'Language', icon: Languages },
  { key: 'music', label: 'Music practice', icon: Guitar },
  { key: 'no-smoking', label: 'No smoking', icon: Cigarette },
  { key: 'diet', label: 'Diet', icon: Apple },
  { key: 'coffee', label: 'Coffee limit', icon: Coffee },
  { key: 'mood', label: 'Mood check-in', icon: Smile },
];

const BY_KEY = new Map(HABIT_ICONS.map((option) => [option.key, option]));

/** Falls back to the first option for an icon key from before the vocabulary changed. */
export function iconFor(key: string): LucideIconData {
  return (BY_KEY.get(key) ?? HABIT_ICONS[0]).icon;
}
