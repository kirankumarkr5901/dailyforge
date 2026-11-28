import { ChangeDetectionStrategy, Component, inject, signal, computed } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideAngularModule, Menu, MoreHorizontal, X } from 'lucide-angular';
import { AssistantComponent } from '../assistant/assistant.component';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { TPipe } from '../../../core/i18n/i18n.service';
import { PointsStore } from '../../../core/points/points.store';
import { DfScorePillComponent } from '../../ui/df-score-pill/df-score-pill.component';
import { NAV_ITEMS, PRIMARY_NAV } from './nav-items';

/**
 * The app shell: header, navigation, and the routed screen between them.
 *
 * Two navigations, one list. Below 1024px the four most-used screens sit in a bottom bar
 * within thumb reach, with the rest behind More; above it, a persistent rail replaces
 * both. Spec §8's hamburger stays on mobile for the full list.
 *
 * The score pill reads from the shared `PointsStore`, the one place the header, Home,
 * and every tracker's own mutations all update — so a redemption on Rewards or a set
 * logged on Workouts is reflected here immediately, with no manual refresh.
 */
@Component({
  selector: 'df-app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    LucideAngularModule,
    TPipe,
    DfScorePillComponent,
    AssistantComponent,
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);
  protected readonly points = inject(PointsStore);

  protected readonly menuIcon = Menu;
  protected readonly closeIcon = X;
  protected readonly moreIcon = MoreHorizontal;

  /** Switched-off features drop out of every navigation at once. */
  protected readonly navItems = computed(() => NAV_ITEMS.filter((i) => !i.feature || this.session.featureEnabled(i.feature)));
  protected readonly primaryNav = computed(() => PRIMARY_NAV.filter((i) => !i.feature || this.session.featureEnabled(i.feature)));

  protected readonly drawerOpen = signal(false);

  protected openDrawer(): void {
    this.drawerOpen.set(true);
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected signIn(): void {
    this.closeDrawer();
    this.authSheet.open('manual');
  }

}
