import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideAngularModule, Menu, MoreHorizontal, X } from 'lucide-angular';

import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { SessionStore } from '../../../core/auth/session.store';
import { TPipe } from '../../../core/i18n/i18n.service';
import { DfScorePillComponent } from '../../ui/df-score-pill/df-score-pill.component';
import { NAV_ITEMS, PRIMARY_NAV } from './nav-items';

/**
 * The app shell: header, navigation, and the routed screen between them.
 *
 * Two navigations, one list. Below 1024px the four most-used screens sit in a bottom bar
 * within thumb reach, with the rest behind More; above it, a persistent rail replaces
 * both. Spec §8's hamburger stays on mobile for the full list.
 *
 * The score pill is present but static at M1 — there is no points engine until M2, and a
 * number invented on the client would violate the rule that the frontend never decides
 * what was earned.
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
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  protected readonly session = inject(SessionStore);
  protected readonly authSheet = inject(AuthSheetService);

  protected readonly menuIcon = Menu;
  protected readonly closeIcon = X;
  protected readonly moreIcon = MoreHorizontal;

  protected readonly navItems = NAV_ITEMS;
  protected readonly primaryNav = PRIMARY_NAV;

  protected readonly drawerOpen = signal(false);

  /**
   * Zero until M2. Shown rather than hidden so the shell's proportions are honest from
   * the start — a header that grows a pill later shifts every screen beneath it.
   */
  protected readonly score = signal(0);

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

  protected async signOut(): Promise<void> {
    this.closeDrawer();
    await this.session.logout();
  }
}
