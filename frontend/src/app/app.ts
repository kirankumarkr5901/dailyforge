import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { inject } from '@angular/core';
import { AuthSheetService } from './core/auth/auth-sheet.service';
import { OnboardingService } from './core/onboarding/onboarding.service';
import { ThemeService } from './core/theme/theme.service';
import { AuthSheetComponent } from './features/auth/auth-sheet/auth-sheet.component';
import { OnboardingTourComponent } from './features/onboarding/onboarding-tour/onboarding-tour.component';
import { DfToastHostComponent } from './shared/ui/df-toast/df-toast-host.component';

/**
 * The true root. The toast host is mounted here rather than inside AppShellComponent:
 * routes like /dev/ui and /dev/points sit as siblings of the shell, not children of it,
 * and a global overlay that only exists inside the shell is invisible on every route
 * outside it.
 *
 * The sign-in sheet is the same story, but deferred (see app.html): it pulls in
 * reactive forms and the Google Identity Services loader, and most page loads never
 * open it. Eagerly importing it here previously cost every visitor 18 kB gzipped they
 * would not use; @defer keeps it reachable from any route while paying that cost only
 * the moment someone actually opens it.
 *
 * `ThemeService` is injected here purely to construct it: it was previously only ever
 * injected from Settings and the dev gallery, both lazy routes, so its `data-theme`-
 * applying effect never ran at all until a visitor happened to open Settings — every
 * other screen silently fell back to the browser's own light/dark preference,
 * regardless of the visitor's actual choice or the product default. Constructing it
 * eagerly here (a `providedIn: 'root'` singleton, so this is the only place that needs
 * to) keeps it alive, and reactive, for the app's whole lifetime.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, DfToastHostComponent, AuthSheetComponent, OnboardingTourComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly authSheet = inject(AuthSheetService);
  protected readonly onboarding = inject(OnboardingService);
  private readonly theme = inject(ThemeService);
}
