import { Award, Briefcase, Dumbbell, Footprints, House, Scale, Settings, Target } from 'lucide-angular';
import { LucideIconData } from 'lucide-angular';
import { StringKey } from '../../../core/i18n/strings';

export interface NavItem {
  readonly path: string;
  readonly labelKey: StringKey;
  readonly icon: LucideIconData;
  /** True for the four screens that earn a place in the bottom bar. */
  readonly primary: boolean;
  /** False until the milestone that builds it lands. */
  readonly ready: boolean;
}

/**
 * One list, two navigations.
 *
 * The bottom bar shows the four most-used screens plus More; the drawer and the desktop
 * rail show everything. Deriving both from one array is what stops them drifting apart,
 * which is the usual way a "More" menu ends up missing a screen.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { path: '/home', labelKey: 'nav.home', icon: House, primary: true, ready: false },
  { path: '/workout', labelKey: 'nav.workout', icon: Dumbbell, primary: true, ready: false },
  { path: '/habits', labelKey: 'nav.habits', icon: Target, primary: true, ready: false },
  { path: '/run', labelKey: 'nav.run', icon: Footprints, primary: true, ready: false },
  { path: '/goals', labelKey: 'nav.goals', icon: Award, primary: false, ready: false },
  { path: '/jobs', labelKey: 'nav.jobs', icon: Briefcase, primary: false, ready: false },
  { path: '/body', labelKey: 'nav.body', icon: Scale, primary: false, ready: false },
  { path: '/settings', labelKey: 'nav.settings', icon: Settings, primary: false, ready: true },
];

export const PRIMARY_NAV = NAV_ITEMS.filter((item) => item.primary);
