import { Check, Dumbbell, Flame, Footprints, LucideIconData, Medal, Target } from 'lucide-angular';

import { Badge, BadgeMetric } from '../../core/milestone/badge.types';

/**
 * The icon each badge row names. Kept as a lookup rather than sent from the server:
 * the server has no business knowing which icon set this client draws with, and an
 * unknown name falling back to a medal is better than a blank square.
 */
const ICONS: Record<string, LucideIconData> = {
  dumbbell: Dumbbell,
  check: Check,
  footprints: Footprints,
  flame: Flame,
  target: Target,
};

export function badgeIcon(name: string): LucideIconData {
  return ICONS[name] ?? Medal;
}

/**
 * Progress in the metric's own units, because "12 of 15 workout days" tells you what to
 * do tonight and "80%" does not.
 *
 * Distance is the one metric stored in units nobody thinks in — metres — so it is shown
 * in kilometres, at one decimal place while it still matters and whole once it does not.
 */
export function badgeProgressLabel(badge: Badge): string {
  if (badge.metric === 'RUN_DISTANCE_METERS') {
    return `${km(badge.value)} of ${km(badge.threshold)} km`;
  }
  return `${badge.value.toLocaleString()} of ${badge.threshold.toLocaleString()} ${unitFor(badge.metric, badge.threshold)}`;
}

function km(metres: number): string {
  const value = metres / 1000;
  return value >= 100 ? String(Math.round(value)) : value.toFixed(1).replace(/\.0$/, '');
}

function unitFor(metric: BadgeMetric, count: number): string {
  switch (metric) {
    case 'TOTAL_POINTS':
      return 'points';
    case 'WORKOUT_DAYS':
      return count === 1 ? 'workout day' : 'workout days';
    case 'RUN_DAYS':
      return count === 1 ? 'run day' : 'run days';
    case 'HABITS_COMPLETED':
      return count === 1 ? 'habit tick' : 'habit ticks';
    case 'GOALS_COMPLETED':
      return count === 1 ? 'goal' : 'goals';
    default:
      return '';
  }
}

/** How full the bar is. Capped, because overshooting a badge does not overflow it. */
export function badgeProgressPercent(badge: Badge): number {
  if (badge.threshold <= 0) {
    return 0;
  }
  return Math.min(100, Math.max(0, (badge.value / badge.threshold) * 100));
}
