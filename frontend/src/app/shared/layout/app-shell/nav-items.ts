import { Award, Beef, PersonStanding, StretchHorizontal, Briefcase, Dumbbell, Flame, Footprints, Gift, House, PartyPopper, Scale, Settings, Target } from 'lucide-angular';
import { LucideIconData } from 'lucide-angular';
import { StringKey } from '../../../core/i18n/strings';
import { FeatureCode } from '../../../core/features/features';

export interface NavItem {
  readonly path: string;
  readonly labelKey: StringKey;
  readonly icon: LucideIconData;
  /** True for the four screens that earn a place in the bottom bar. */
  readonly primary: boolean;
  /** False until the milestone that builds it lands. */
  readonly ready: boolean;
  /** Set for screens that can be switched off in Settings → Features. */
  readonly feature?: FeatureCode;
}

/**
 * One list, two navigations.
 *
 * The bottom bar shows the four most-used screens plus More; the drawer and the desktop
 * rail show everything. Deriving both from one array is what stops them drifting apart,
 * which is the usual way a "More" menu ends up missing a screen.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { path: '/home', labelKey: 'nav.home', icon: House, primary: true, ready: true },
  { path: '/workout', labelKey: 'nav.workout', icon: Dumbbell, primary: true, ready: true },
  { path: '/habits', labelKey: 'nav.habits', icon: Target, primary: true, ready: true },
  { path: '/run', labelKey: 'nav.run', icon: Footprints, primary: true, ready: true },
  { path: '/forge', labelKey: 'nav.forge', icon: Flame, primary: false, ready: true, feature: 'FORGE' },
  { path: '/goals', labelKey: 'nav.goals', icon: Award, primary: false, ready: true, feature: 'GOALS' },
  { path: '/jobs', labelKey: 'nav.jobs', icon: Briefcase, primary: false, ready: true, feature: 'JOBS' },
  { path: '/body', labelKey: 'nav.body', icon: Scale, primary: false, ready: true },
  { path: '/skills', labelKey: 'nav.skills', icon: PersonStanding, primary: false, ready: true, feature: 'SKILLS' },
  { path: '/mobility', labelKey: 'nav.mobility', icon: StretchHorizontal, primary: false, ready: true, feature: 'MOBILITY' },
  { path: '/nutrition', labelKey: 'nav.nutrition', icon: Beef, primary: false, ready: true, feature: 'NUTRITION' },
  { path: '/rewards', labelKey: 'nav.rewards', icon: Gift, primary: false, ready: true },
  { path: '/milestones', labelKey: 'nav.milestones', icon: PartyPopper, primary: false, ready: true },
  { path: '/settings', labelKey: 'nav.settings', icon: Settings, primary: false, ready: true },
];

export const PRIMARY_NAV = NAV_ITEMS.filter((item) => item.primary);
