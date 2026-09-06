import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { inject } from '@angular/core';
import { AuthSheetService } from './core/auth/auth-sheet.service';
import { AuthSheetComponent } from './features/auth/auth-sheet/auth-sheet.component';
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
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, DfToastHostComponent, AuthSheetComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly authSheet = inject(AuthSheetService);
}
